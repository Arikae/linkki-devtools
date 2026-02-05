package org.linkki.lsp.intellij

import com.intellij.codeInsight.template.TemplateManager
import com.intellij.codeInsight.template.impl.ConstantNode
import com.intellij.openapi.actionSystem.AnActionEvent
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.TextEditor
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.vfs.LocalFileSystem
import com.redhat.devtools.lsp4ij.commands.LSPCommand
import com.redhat.devtools.lsp4ij.commands.LSPCommandAction
import org.eclipse.lsp4j.Range
import java.net.URI
import java.nio.file.Paths
import java.util.regex.Pattern

class LinkkiApplySnippetCommandAction : LSPCommandAction(), DumbAware {

    override fun commandPerformed(command: LSPCommand, e: AnActionEvent) {
        val args = command.arguments

        if (args.size < 3) return

        val uri = command.getArgumentAt(0, String::class.java) ?: return
        val range = command.getArgumentAt(1, Range::class.java) ?: return
        val snippet = command.getArgumentAt(2, String::class.java) ?: return

        val project = e.project ?: return

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
                val endOffset = document.getLineStartOffset(range.end.line) + range.end.character

                // Replace the range with empty string to prepare for template insertion
                if (endOffset > startOffset) {
                    document.replaceString(startOffset, endOffset, "")
                }

                editor.caretModel.moveToOffset(startOffset)

                // Snippet format: ${1:void} ${2:methodName}(${3}) { ${0} }
                val matcher = Pattern.compile("\\$\\{(\\d+):([^}]+)\\}|\\$(\\d+)").matcher(snippet)
                val sb = StringBuilder()
                val vars = mutableListOf<Pair<String, String>>()
                var lastEnd = 0

                while (matcher.find()) {
                    sb.append(snippet.substring(lastEnd, matcher.start()))

                    val placeholderIndex = matcher.group(1) ?: matcher.group(3)
                    val defaultValue = matcher.group(2)

                    if (placeholderIndex == "0") {
                        sb.append("\$END$")
                    } else {
                        val varName = "VAR$placeholderIndex"
                        sb.append("\$$varName$")
                        vars.add(varName to (defaultValue ?: ""))
                    }

                    lastEnd = matcher.end()
                }
                sb.append(snippet.substring(lastEnd))

                val templateManager = TemplateManager.getInstance(project)
                val template = templateManager.createTemplate("", "", sb.toString())

                vars.forEach { (name, value) ->
                    template.addVariable(name, ConstantNode(value), ConstantNode(value), true)
                }

                template.isToReformat = true

                templateManager.startTemplate(editor, template)
            }
        } catch (_: Exception) {
            // Ignore errors
        }
    }
}
