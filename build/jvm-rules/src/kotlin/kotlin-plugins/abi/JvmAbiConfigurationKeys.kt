// Copyright 2000-2025 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.jvm.abi

import org.jetbrains.kotlin.config.CompilerConfigurationKey

internal object JvmAbiConfigurationKeys {
    val REMOVE_DEBUG_INFO: CompilerConfigurationKey<Boolean> =
        CompilerConfigurationKey.create(JvmAbiCommandLineProcessor.REMOVE_DEBUG_INFO_OPTION.description)
    val REMOVE_DATA_CLASS_COPY_IF_CONSTRUCTOR_IS_PRIVATE: CompilerConfigurationKey<Boolean> =
        CompilerConfigurationKey.create(JvmAbiCommandLineProcessor.REMOVE_DATA_CLASS_COPY_IF_CONSTRUCTOR_IS_PRIVATE_OPTION.description)
    val PRESERVE_DECLARATION_ORDER: CompilerConfigurationKey<Boolean> =
        CompilerConfigurationKey.create(JvmAbiCommandLineProcessor.PRESERVE_DECLARATION_ORDER_OPTION.description)
    val REMOVE_PRIVATE_CLASSES: CompilerConfigurationKey<Boolean> =
        CompilerConfigurationKey.create(JvmAbiCommandLineProcessor.REMOVE_PRIVATE_CLASSES_OPTION.description)
    val TREAT_INTERNAL_AS_PRIVATE: CompilerConfigurationKey<Boolean> =
        CompilerConfigurationKey.create(JvmAbiCommandLineProcessor.TREAT_INTERNAL_AS_PRIVATE_OPTION.description)
}
