package org.linkki.lsp.intellij

import com.intellij.ide.structureView.StructureView
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerEvent
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiManager
import com.intellij.ui.content.ContentFactory
import com.redhat.devtools.lsp4ij.features.documentSymbol.LSPDocumentSymbolStructureViewFactory
import java.awt.BorderLayout
import javax.swing.JLabel
import javax.swing.JPanel
import javax.swing.SwingConstants

class LinkkiStructureToolWindowFactory : ToolWindowFactory {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val contentFactory = ContentFactory.getInstance()
        val panel = LinkkiStructurePanel(project)
        val content = contentFactory.createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    private class LinkkiStructurePanel(private val project: Project) : JPanel(BorderLayout()), Disposable {
        private var currentStructureView: StructureView? = null

        init {
            val messageBusConnection = project.messageBus.connect(this)
            messageBusConnection.subscribe(
                FileEditorManagerListener.FILE_EDITOR_MANAGER,
                object : FileEditorManagerListener {
                    override fun selectionChanged(event: FileEditorManagerEvent) {
                        updateStructureView(event.newFile)
                    }
                })

            val currentFile = FileEditorManager.getInstance(project).selectedFiles.firstOrNull()
            updateStructureView(currentFile)
        }

        private fun updateStructureView(file: VirtualFile?) {
            currentStructureView?.let {
                Disposer.dispose(it)
                currentStructureView = null
            }
            removeAll()

            if (file == null) {
                add(JLabel("No file selected", SwingConstants.CENTER), BorderLayout.CENTER)
                revalidate()
                repaint()
                return
            }

            val psiFile = PsiManager.getInstance(project).findFile(file)
            if (psiFile == null) {
                add(JLabel("Cannot analyze file", SwingConstants.CENTER), BorderLayout.CENTER)
                revalidate()
                repaint()
                return
            }

            val factory = LSPDocumentSymbolStructureViewFactory()
            val builder: StructureViewBuilder? = factory.getStructureViewBuilder(psiFile)

            if (builder == null) {
                add(JLabel("No structure available", SwingConstants.CENTER), BorderLayout.CENTER)
            } else {
                val fileEditor = FileEditorManager.getInstance(project).getSelectedEditor(file)
                if (fileEditor != null) {
                    val structureView = builder.createStructureView(fileEditor, project)
                    currentStructureView = structureView
                    add(structureView.component, BorderLayout.CENTER)
                }
            }

            revalidate()
            repaint()
        }

        override fun dispose() {
            currentStructureView?.let { Disposer.dispose(it) }
        }
    }
}