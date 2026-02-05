package org.linkki.lsp.intellij

import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.client.features.LSPDefinitionFeature

class LinkkiLSPDefinitionFeature : LSPDefinitionFeature() {
    override fun isEnabled(file: PsiFile): Boolean {
        if (file.fileType.name == "Properties") {
            return true
        }
        return super.isEnabled(file)
    }
}
