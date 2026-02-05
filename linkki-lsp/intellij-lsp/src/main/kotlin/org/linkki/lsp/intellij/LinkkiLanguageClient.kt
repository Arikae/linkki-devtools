package org.linkki.lsp.intellij

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.redhat.devtools.lsp4ij.client.LanguageClientImpl
import org.eclipse.lsp4j.Range
import org.eclipse.lsp4j.jsonrpc.services.JsonNotification
import java.net.URI
import java.nio.file.Paths

class LinkkiLanguageClient(project: Project) : LanguageClientImpl(project) {

    @JsonNotification("linkki/applySnippet")
    fun applySnippet(uri: String, range: Range, snippet: String) {
        ApplicationManager.getApplication().invokeLater {
            applySnippetInternal(uri, range, snippet)
        }
    }

    private fun applySnippetInternal(uri: String, range: Range, snippet: String) {
        try {
            val path = Paths.get(URI(uri))
            val virtualFile = LocalFileSystem.getInstance().findFileByNioFile(path) ?: return

            val fileEditorManager = FileEditorManager.getInstance(project)
            val editor = fileEditorManager.getAllEditors(virtualFile)
                .filterIsInstance<TextEditor>()
                .firstOrNull()?.editor ?: return

            val document = editor.document

            WriteCommandAction.runWriteCommandAction(project) {
                val startOffset = document.getLineStartOffset(range.start.line) + range.start.character

                // Simple snippet processing: remove tab stops, keep default text
                val cleanSnippet = snippet
                    .replace(Regex("\\$\\{\\d+:([^}]+)\\}"), "$1")
                    .replace(Regex("\\$\\d+"), "")

                document.insertString(startOffset, cleanSnippet)
            }
        } catch (e: Exception) {
            // Ignore errors
        }
    }
}