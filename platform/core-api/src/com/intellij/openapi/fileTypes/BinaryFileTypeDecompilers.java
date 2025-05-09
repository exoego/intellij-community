// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.openapi.fileTypes;

import com.intellij.openapi.application.Application;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.extensions.ExtensionPointName;
import com.intellij.openapi.fileEditor.FileDocumentManager;
import com.intellij.util.KeyedLazyInstance;

/**
 * @see BinaryFileDecompiler
 */
@Service
public final class BinaryFileTypeDecompilers extends FileTypeExtension<BinaryFileDecompiler> {
  private static final ExtensionPointName<KeyedLazyInstance<BinaryFileDecompiler>> EP_NAME =
    new ExtensionPointName<>("com.intellij.filetype.decompiler");

  private BinaryFileTypeDecompilers() {
    super(EP_NAME);
    Application app = ApplicationManager.getApplication();
    if (!app.isUnitTestMode()) {
      EP_NAME.addChangeListener(() -> notifyDecompilerSetChange(), app);
    }
  }

  public void notifyDecompilerSetChange() {
    ApplicationManager.getApplication().invokeLater(() -> FileDocumentManager.getInstance().reloadBinaryFiles(), ModalityState.nonModal());
  }

  public static BinaryFileTypeDecompilers getInstance() {
    return ApplicationManager.getApplication().getService(BinaryFileTypeDecompilers.class);
  }
}
