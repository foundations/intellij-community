// Copyright 2000-2023 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.
package com.intellij.platform.ide.menu

import com.intellij.openapi.actionSystem.ActionGroup
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.ModalityState
import com.intellij.openapi.application.asContextElement
import com.intellij.openapi.util.Disposer
import com.intellij.util.concurrency.ThreadingAssertions
import com.intellij.util.concurrency.annotations.RequiresEdt
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.job
import kotlinx.coroutines.launch
import java.awt.event.WindowAdapter
import java.awt.event.WindowEvent
import java.util.function.Consumer
import javax.swing.JFrame

internal class LinuxIdeMenuBar(coroutineScope: CoroutineScope, frame: JFrame, customMenuGroup: ActionGroup?)
  : IdeJMenuBar(coroutineScope, frame, customMenuGroup) {
  companion object {
    fun doBindAppMenuOfParent(frame: JFrame, parentFrame: JFrame) {
      val globalMenu = (parentFrame.jMenuBar as? LinuxIdeMenuBar)?.globalMenu ?: return
      if (GlobalMenuLinux.isPresented()) {
        // all children of IdeFrame mustn't show swing-menubar
        frame.jMenuBar?.isVisible = false
      }

      frame.addWindowListener(object : WindowAdapter() {
        override fun windowClosing(e: WindowEvent?) {
          globalMenu.unbindWindow(frame)
        }

        override fun windowOpened(e: WindowEvent?) {
          globalMenu.bindNewWindow(frame)
        }
      })
    }
  }

  private var globalMenu: GlobalMenuLinux? = null

  override val isDarkMenu: Boolean
    get() = globalMenu != null

  override fun updateGlobalMenuRoots() {
    super.updateGlobalMenuRoots()

    globalMenu?.setRoots(rootMenuItems)
  }

  override fun doInstallAppMenuIfNeeded(frame: JFrame) {
    if (globalMenu != null || !GlobalMenuLinux.isAvailable()) {
      return
    }

    val globalMenuLinux = GlobalMenuLinux.create(frame)
    globalMenu = globalMenuLinux
    coroutineScope.coroutineContext.job.invokeOnCompletion {
      Disposer.dispose(globalMenuLinux)
    }
    updateMenuActions(forceRebuild = true)
  }

  override fun onToggleFullScreen(isFullScreen: Boolean) {
    globalMenu?.toggle(!isFullScreen)
  }
}

@RequiresEdt
fun collectGlobalMenu(scope: CoroutineScope, globalMenuListener: Consumer<Boolean>) {
  ThreadingAssertions.assertEventDispatchThread()

  if (GlobalMenuLinux.isAvailable()) {
    scope.launch(Dispatchers.EDT + ModalityState.any().asContextElement()) {
      GlobalMenuLinux.isPresentedMutable.collect {
        globalMenuListener.accept(it)
      }
    }
  }
}
