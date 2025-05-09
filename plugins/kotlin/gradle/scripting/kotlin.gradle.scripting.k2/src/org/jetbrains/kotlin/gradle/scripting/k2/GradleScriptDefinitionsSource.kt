// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package org.jetbrains.kotlin.gradle.scripting.k2

import com.intellij.openapi.components.Service
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import org.jetbrains.kotlin.idea.core.script.k2.K2ScriptDefinitionProvider
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinition
import org.jetbrains.kotlin.scripting.definitions.ScriptDefinitionsSource
import java.util.concurrent.atomic.AtomicReference

class GradleScriptDefinitionsSource(val project: Project) : ScriptDefinitionsSource {
    override val definitions: Sequence<ScriptDefinition>
        get() = GradleScriptDefinitionsHolder.getInstance(project).definitions
}

@Service(Service.Level.PROJECT)
class GradleScriptDefinitionsHolder(val project: Project) { //TODO persist
    val definitions: Sequence<ScriptDefinition>
        get() = _definitions.get().asSequence()

    private val _definitions: AtomicReference<List<ScriptDefinition>> = AtomicReference(listOf())

    fun updateDefinitions(templateDefinitions: List<ScriptDefinition>) {
        _definitions.set(templateDefinitions)
        K2ScriptDefinitionProvider.getInstance(project).reloadDefinitionsFromSources()
    }

    companion object {
        fun getInstance(project: Project): GradleScriptDefinitionsHolder = project.service<GradleScriptDefinitionsHolder>()
    }
}