package org.linkki.lsp.intellij

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiFile
import com.redhat.devtools.lsp4ij.features.semanticTokens.SemanticTokensColorsProvider

class LinkkiSemanticTokensProvider : SemanticTokensColorsProvider {

    override fun getTextAttributesKey(
        tokenType: String,
        tokenModifiers: List<String>,
        file: PsiFile
    ): TextAttributesKey? {
        return when (tokenType) {
            "linkkiComponent" -> DefaultLanguageHighlighterColors.METADATA // Highlight Linkki UI elements
            "method" -> DefaultLanguageHighlighterColors.FUNCTION_DECLARATION
            "variable" -> DefaultLanguageHighlighterColors.LOCAL_VARIABLE
            "class" -> DefaultLanguageHighlighterColors.CLASS_NAME
            else -> null
        }
    }
}