package org.linkki.lsp.intellij

import com.intellij.openapi.project.Project
import com.redhat.devtools.lsp4ij.LanguageServerFactory
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import com.redhat.devtools.lsp4ij.client.features.LSPClientFeatures
import com.redhat.devtools.lsp4ij.server.StreamConnectionProvider

class LinkkiLanguageServerFactory : LanguageServerFactory {
    override fun createConnectionProvider(project: Project): StreamConnectionProvider {
        return LinkkiStreamConnectionProvider(project)
    }

    override fun createLanguageClient(project: Project): LanguageClientImpl {
        return LinkkiLanguageClient(project)
    }

    override fun createClientFeatures(): LSPClientFeatures {
        return LSPClientFeatures()
            .setDefinitionFeature(LinkkiLSPDefinitionFeature())
    }
}