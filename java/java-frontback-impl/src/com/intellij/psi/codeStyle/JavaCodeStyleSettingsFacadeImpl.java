// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.psi.codeStyle;

import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

public final class JavaCodeStyleSettingsFacadeImpl extends JavaCodeStyleSettingsFacade {
  private final CodeStyleSettingsManager myManager;

  public JavaCodeStyleSettingsFacadeImpl(@NotNull Project project) {
    myManager = CodeStyleSettingsManager.getInstance(project);
  }

  @Override
  public boolean useFQClassNames() {
    return myManager.getCurrentSettings().getCustomSettings(JavaCodeStyleSettings.class).USE_FQ_CLASS_NAMES;
  }

  @Override
  public boolean isGenerateFinalParameters() {
    return myManager.getCurrentSettings().getCustomSettings(JavaCodeStyleSettings.class).GENERATE_FINAL_PARAMETERS;
  }

}
