// Copyright 2000-2022 JetBrains s.r.o. and contributors. Use of this source code is governed by the Apache 2.0 license.

package org.jetbrains.kotlin.idea.stubs

import com.intellij.codeInsight.daemon.impl.DaemonCodeAnalyzerImpl
import com.intellij.codeInsight.daemon.impl.HighlightInfo
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.project.DumbService
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.impl.cache.CacheManager
import com.intellij.psi.impl.source.tree.TreeElement
import com.intellij.psi.impl.source.tree.TreeUtil
import com.intellij.psi.search.GlobalSearchScope
import com.intellij.psi.search.UsageSearchContext
import com.intellij.testFramework.ExpectedHighlightingData
import org.jetbrains.kotlin.idea.test.AbstractMultiModuleTest

abstract class AbstractMultiHighlightingTest : AbstractMultiModuleTest() {

    protected open val shouldCheckLineMarkers = false

    protected open val shouldCheckResult = true

    override fun checkHighlighting(data: ExpectedHighlightingData): Collection<HighlightInfo> {
        data.init()
        PsiDocumentManager.getInstance(myProject).commitAllDocuments()

        //to load text
        ApplicationManager.getApplication().runWriteAction { TreeUtil.clearCaches(myFile.node as TreeElement) }

        //to initialize caches
        if (!DumbService.isDumb(project)) {
            CacheManager.getInstance(myProject)
                .getFilesWithWord("XXX", UsageSearchContext.IN_COMMENTS, GlobalSearchScope.allScope(myProject), true)
        }

        val infos = doHighlighting()

        val text = myEditor.document.text
        if (shouldCheckLineMarkers) {
            data.checkLineMarkers(myFile, DaemonCodeAnalyzerImpl.getLineMarkers(getDocument(file), project), text)
        }
        if (shouldCheckResult) {
            data.checkResult(myFile, infos, text)
        }
        return infos
    }
}