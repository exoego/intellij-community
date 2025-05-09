// Copyright 2000-2024 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.idea.debugger.evaluate

import org.jetbrains.kotlin.descriptors.*
import org.jetbrains.kotlin.descriptors.annotations.Annotations
import org.jetbrains.kotlin.descriptors.impl.PropertyDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertyGetterDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.PropertySetterDescriptorImpl
import org.jetbrains.kotlin.descriptors.impl.ValueParameterDescriptorImpl
import org.jetbrains.kotlin.idea.base.facet.platform.platform
import org.jetbrains.kotlin.idea.core.util.CodeFragmentUtils
import org.jetbrains.kotlin.incremental.KotlinLookupLocation
import org.jetbrains.kotlin.incremental.components.LookupLocation
import org.jetbrains.kotlin.incremental.components.NoLookupLocation
import org.jetbrains.kotlin.load.java.components.JavaSourceElementFactoryImpl
import org.jetbrains.kotlin.load.java.components.TypeUsage
import org.jetbrains.kotlin.load.java.descriptors.JavaClassDescriptor
import org.jetbrains.kotlin.load.java.lazy.descriptors.LazyJavaClassDescriptor
import org.jetbrains.kotlin.load.java.lazy.types.toAttributes
import org.jetbrains.kotlin.name.Name
import org.jetbrains.kotlin.platform.isCommon
import org.jetbrains.kotlin.platform.jvm.isJvm
import org.jetbrains.kotlin.psi.KtCodeFragment
import org.jetbrains.kotlin.psi.KtFile
import org.jetbrains.kotlin.resolve.DescriptorFactory
import org.jetbrains.kotlin.resolve.descriptorUtil.getSuperClassNotAny
import org.jetbrains.kotlin.resolve.scopes.DescriptorKindFilter
import org.jetbrains.kotlin.resolve.scopes.SyntheticScope
import org.jetbrains.kotlin.resolve.scopes.getDescriptorsFiltered
import org.jetbrains.kotlin.synthetic.JavaSyntheticPropertiesScope
import org.jetbrains.kotlin.synthetic.SyntheticScopeProviderExtension
import org.jetbrains.kotlin.types.KotlinType
import org.jetbrains.kotlin.types.typeUtil.replaceArgumentsWithStarProjections

class DebuggerFieldSyntheticScopeProvider : SyntheticScopeProviderExtension {
    override fun getScopes(
        moduleDescriptor: ModuleDescriptor,
        javaSyntheticPropertiesScope: JavaSyntheticPropertiesScope
    ): List<SyntheticScope> {
        return listOf<SyntheticScope>(DebuggerFieldSyntheticScope(javaSyntheticPropertiesScope))
    }
}

class DebuggerFieldSyntheticScope(private val javaSyntheticPropertiesScope: JavaSyntheticPropertiesScope) : SyntheticScope.Default() {
    private val javaSourceElementFactory = JavaSourceElementFactoryImpl()

    override fun getSyntheticExtensionProperties(
        receiverTypes: Collection<KotlinType>,
        name: Name,
        location: LookupLocation
    ): Collection<PropertyDescriptor> {
        return getSyntheticExtensionProperties(receiverTypes, location).filter { it.name == name }
    }

    override fun getSyntheticExtensionProperties(
        receiverTypes: Collection<KotlinType>,
        location: LookupLocation
    ): Collection<PropertyDescriptor> {
        if (!isInEvaluator(location)) {
            return emptyList()
        }

        val result = mutableListOf<PropertyDescriptor>()
        for (type in receiverTypes) {
            val clazz = type.constructor.declarationDescriptor as? ClassDescriptor ?: continue
            result += getSyntheticPropertiesForClass(clazz)
        }
        return result
    }

    private fun isInEvaluator(location: LookupLocation): Boolean {
        val element = (location as? KotlinLookupLocation)?.element ?: return false
        val containingFile = element.containingFile?.takeIf { it.isValid } as? KtFile ?: return false

        val platform = containingFile.platform
        if (!platform.isJvm() && !platform.isCommon()) {
            return false
        }

        return containingFile is KtCodeFragment && containingFile.getCopyableUserData(CodeFragmentUtils.RUNTIME_TYPE_EVALUATOR) != null
    }

    private fun getSyntheticPropertiesForClass(clazz: ClassDescriptor): Collection<PropertyDescriptor> {
        val collected = mutableMapOf<Name, PropertyDescriptor>()

        val syntheticPropertyNames = javaSyntheticPropertiesScope
            .getSyntheticExtensionProperties(listOf(clazz.defaultType), NoLookupLocation.FROM_SYNTHETIC_SCOPE)
            .mapTo(mutableSetOf()) { it.name }

        collectPropertiesWithParent(clazz, syntheticPropertyNames, collected)
        return collected.values
    }

    private tailrec fun collectPropertiesWithParent(
        clazz: ClassDescriptor,
        syntheticNames: Set<Name>,
        consumer: MutableMap<Name, PropertyDescriptor>
    ) {
        when (clazz) {
            is LazyJavaClassDescriptor -> collectJavaProperties(clazz, syntheticNames, consumer)
            is JavaClassDescriptor -> error("Unsupported Java class type")
            else -> collectKotlinProperties(clazz, consumer)
        }

        val superClass = clazz.getSuperClassNotAny()
        if (superClass != null) {
            collectPropertiesWithParent(superClass, syntheticNames, consumer)
        }
    }

    private fun collectKotlinProperties(clazz: ClassDescriptor, consumer: MutableMap<Name, PropertyDescriptor>) {
        for (descriptor in clazz.unsubstitutedMemberScope.getDescriptorsFiltered(DescriptorKindFilter.VARIABLES)) {
            val propertyDescriptor = descriptor as? PropertyDescriptor ?: continue
            val name = propertyDescriptor.name
            if (propertyDescriptor.backingField == null || name in consumer) continue

            val type = propertyDescriptor.type
            val sourceElement = propertyDescriptor.source

            val isVar = propertyDescriptor.isVar
            consumer[name] = createSyntheticPropertyDescriptor(
                clazz,
                type,
                name,
                isVar,
                KotlinDebuggerEvaluationBundle.message("backing.field"),
                sourceElement
            )
        }
    }

    private fun collectJavaProperties(
        clazz: LazyJavaClassDescriptor,
        syntheticNames: Set<Name>,
        consumer: MutableMap<Name, PropertyDescriptor>
    ) {
        for (field in clazz.jClass.fields) {
            val fieldName = field.name
            if (field.isEnumEntry || field.isStatic || fieldName in consumer || fieldName !in syntheticNames) continue

            val typeResolver = clazz.outerContext.typeResolver

            val type = typeResolver.transformJavaType(field.type, TypeUsage.COMMON.toAttributes()).replaceArgumentsWithStarProjections()
            val sourceElement = javaSourceElementFactory.source(field)

            val isVar = !field.isFinal
            consumer[fieldName] = createSyntheticPropertyDescriptor(
                clazz,
                type,
                fieldName,
                isVar,
                KotlinDebuggerEvaluationBundle.message("java.field"),
                sourceElement
            )
        }
    }

    private fun createSyntheticPropertyDescriptor(
        clazz: ClassDescriptor,
        type: KotlinType,
        fieldName: Name,
        isVar: Boolean,
        description: String,
        sourceElement: SourceElement,
    ): PropertyDescriptor {
        val propertyDescriptor = DebuggerFieldPropertyDescriptor(clazz, fieldName.asString(), description, isVar)

        val extensionReceiverParameter = DescriptorFactory.createExtensionReceiverParameterForCallable(
            propertyDescriptor,
            clazz.defaultType.replaceArgumentsWithStarProjections(),
            Annotations.EMPTY
        )

        propertyDescriptor.setType(type, emptyList(), null, extensionReceiverParameter, emptyList())

        val getter = PropertyGetterDescriptorImpl(
            propertyDescriptor, Annotations.EMPTY, Modality.FINAL,
            DescriptorVisibilities.PUBLIC, false, false, false,
            CallableMemberDescriptor.Kind.SYNTHESIZED,
            null, sourceElement
        ).apply { initialize(type) }

        val setter = if (isVar) PropertySetterDescriptorImpl(
            propertyDescriptor, Annotations.EMPTY, Modality.FINAL,
            DescriptorVisibilities.PUBLIC, false, false, false,
            CallableMemberDescriptor.Kind.SYNTHESIZED,
            null, sourceElement
        ).apply {
            val setterValueParameter = ValueParameterDescriptorImpl(
                this, null, 0, Annotations.EMPTY, Name.identifier("value"), type,
                declaresDefaultValue = false, isCrossinline = false, isNoinline = false,
                varargElementType = null, source = sourceElement
            )
            initialize(setterValueParameter)
        } else null

        propertyDescriptor.initialize(getter, setter)

        return propertyDescriptor
    }
}

internal class DebuggerFieldPropertyDescriptor(
    containingDeclaration: DeclarationDescriptor,
    val fieldName: String,
    val description: String,
    isVar: Boolean
) : PropertyDescriptorImpl(
    containingDeclaration,
    null,
    Annotations.EMPTY,
    Modality.FINAL,
    DescriptorVisibilities.PUBLIC,
    /*isVar = */isVar,
    Name.identifier(fieldName + "_field"),
    CallableMemberDescriptor.Kind.SYNTHESIZED,
    SourceElement.NO_SOURCE,
    /*lateInit = */false,
    /*isConst = */false,
    /*isExpect = */false,
    /*isActual = */false,
    /*isExternal = */false,
    /*isDelegated = */false
) {
    override val getter: PropertyGetterDescriptorImpl?
        get() = null
}
