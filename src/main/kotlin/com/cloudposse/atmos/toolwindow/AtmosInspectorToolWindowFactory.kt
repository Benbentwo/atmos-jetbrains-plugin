package com.cloudposse.atmos.toolwindow

import com.cloudposse.atmos.AtmosIcons
import com.cloudposse.atmos.services.AtmosProjectService
import com.cloudposse.atmos.settings.AtmosSettings
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.event.CaretEvent
import com.intellij.openapi.editor.event.CaretListener
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.FileEditorManagerListener
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import kotlinx.serialization.json.*
import org.jetbrains.yaml.psi.YAMLFile
import org.jetbrains.yaml.psi.YAMLKeyValue
import org.jetbrains.yaml.psi.YAMLMapping
import java.awt.BorderLayout
import java.util.concurrent.atomic.AtomicReference
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Factory for creating the Atmos Component Inspector tool window.
 * Displays real-time resolved component values based on cursor position.
 */
class AtmosInspectorToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = AtmosInspectorPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return AtmosProjectService.getInstance(project).isAtmosProject
    }
}

/**
 * Main panel for the Component Inspector.
 */
class AtmosInspectorPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val componentLabel = JLabel("No component selected")
    private val stackLabel = JLabel("")
    private val statusLabel = JLabel("")
    private val tree = Tree()
    private val lastComponent = AtomicReference<ComponentInfo?>(null)
    private var debounceTimer: Timer? = null

    init {
        createUI()
        setupListeners()
    }

    private fun createUI() {
        // Header panel
        val headerPanel = JPanel()
        headerPanel.layout = BoxLayout(headerPanel, BoxLayout.Y_AXIS)

        val componentPanel = JPanel(BorderLayout())
        componentPanel.add(JLabel("Component: "), BorderLayout.WEST)
        componentPanel.add(componentLabel, BorderLayout.CENTER)
        headerPanel.add(componentPanel)

        val stackPanel = JPanel(BorderLayout())
        stackPanel.add(JLabel("Stack: "), BorderLayout.WEST)
        stackPanel.add(stackLabel, BorderLayout.CENTER)
        headerPanel.add(stackPanel)

        val statusPanel = JPanel(BorderLayout())
        statusPanel.add(JLabel("Status: "), BorderLayout.WEST)
        statusPanel.add(statusLabel, BorderLayout.CENTER)
        headerPanel.add(statusPanel)

        add(headerPanel, BorderLayout.NORTH)

        // Tree view for resolved values
        tree.model = DefaultTreeModel(DefaultMutableTreeNode("Select a component"))
        tree.cellRenderer = InspectorTreeCellRenderer()
        add(JBScrollPane(tree), BorderLayout.CENTER)

        // Toolbar
        val toolbar = createToolbar()
        add(toolbar, BorderLayout.SOUTH)
    }

    private fun createToolbar(): JPanel {
        val toolbar = JPanel()
        toolbar.layout = BoxLayout(toolbar, BoxLayout.X_AXIS)

        val refreshButton = JButton(AtmosIcons.REFRESH)
        refreshButton.toolTipText = "Refresh"
        refreshButton.addActionListener { refreshComponent() }
        toolbar.add(refreshButton)

        val copyYamlButton = JButton("Copy YAML")
        copyYamlButton.toolTipText = "Copy as YAML"
        copyYamlButton.addActionListener { copyAsYaml() }
        toolbar.add(copyYamlButton)

        val copyJsonButton = JButton("Copy JSON")
        copyJsonButton.toolTipText = "Copy as JSON"
        copyJsonButton.addActionListener { copyAsJson() }
        toolbar.add(copyJsonButton)

        toolbar.add(Box.createHorizontalGlue())

        return toolbar
    }

    private fun setupListeners() {
        // Listen for caret changes
        val connection = project.messageBus.connect()

        connection.subscribe(FileEditorManagerListener.FILE_EDITOR_MANAGER, object : FileEditorManagerListener {
            override fun fileOpened(source: FileEditorManager, file: VirtualFile) {
                setupCaretListener()
            }

            override fun selectionChanged(event: com.intellij.openapi.fileEditor.FileEditorManagerEvent) {
                setupCaretListener()
            }
        })

        // Initial setup
        setupCaretListener()
    }

    private fun setupCaretListener() {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val file = FileEditorManager.getInstance(project).selectedFiles.firstOrNull() ?: return

        val projectService = AtmosProjectService.getInstance(project)
        if (!projectService.isStackFile(file) && !projectService.isStackTemplateFile(file)) {
            return
        }

        editor.caretModel.addCaretListener(object : CaretListener {
            override fun caretPositionChanged(event: CaretEvent) {
                // Debounce updates
                debounceTimer?.stop()
                debounceTimer = Timer(250) {
                    ApplicationManager.getApplication().invokeLater {
                        updateForCaretPosition(event.editor.caretModel.offset)
                    }
                }
                debounceTimer?.isRepeats = false
                debounceTimer?.start()
            }
        })
    }

    private fun updateForCaretPosition(offset: Int) {
        val editor = FileEditorManager.getInstance(project).selectedTextEditor ?: return
        val document = editor.document
        val psiFile = PsiDocumentManager.getInstance(project).getPsiFile(document) as? YAMLFile ?: return

        val element = psiFile.findElementAt(offset) ?: return

        // Find the component context
        val componentInfo = findComponentContext(element) ?: return

        // Only update if component changed
        if (lastComponent.get() == componentInfo) return
        lastComponent.set(componentInfo)

        componentLabel.text = componentInfo.componentName
        stackLabel.text = componentInfo.stackName
        statusLabel.text = "Loading..."

        // Load component data in background
        loadComponentData(componentInfo)
    }

    private fun findComponentContext(element: PsiElement): ComponentInfo? {
        var current: PsiElement? = element
        var componentName: String? = null
        var componentType: String? = null

        while (current != null) {
            if (current is YAMLKeyValue) {
                when (current.keyText) {
                    "terraform" -> componentType = "terraform"
                    "helmfile" -> componentType = "helmfile"
                    "components" -> break
                    else -> {
                        if (componentType != null && componentName == null) {
                            componentName = current.keyText
                        }
                    }
                }
            }
            current = current.parent
        }

        if (componentName == null || componentType == null) return null

        // Derive stack name from file path
        val file = element.containingFile?.virtualFile ?: return null
        val stackName = deriveStackName(file)

        return ComponentInfo(componentName, stackName, componentType)
    }

    private fun deriveStackName(file: VirtualFile): String {
        // Simple derivation - use filename without extension
        return file.nameWithoutExtension
    }

    private fun loadComponentData(componentInfo: ComponentInfo) {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val settings = AtmosSettings.getInstance()
                val atmosPath = settings.atmosExecutablePath.ifEmpty { "atmos" }

                val commandLine = GeneralCommandLine(
                    atmosPath,
                    "describe", "component", componentInfo.componentName,
                    "-s", componentInfo.stackName,
                    "--format", "json"
                )
                commandLine.setWorkDirectory(project.basePath)

                val output = ScriptRunnerUtil.getProcessOutput(
                    commandLine,
                    ScriptRunnerUtil.STDOUT_OUTPUT_KEY_FILTER,
                    30000
                )

                ApplicationManager.getApplication().invokeLater {
                    parseAndDisplayOutput(output)
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    statusLabel.text = "Error: ${e.message}"
                    val errorRoot = DefaultMutableTreeNode("Error loading component")
                    errorRoot.add(DefaultMutableTreeNode(e.message))
                    tree.model = DefaultTreeModel(errorRoot)
                }
            }
        }
    }

    private fun parseAndDisplayOutput(output: String) {
        try {
            val json = Json.parseToJsonElement(output)

            if (json is JsonObject) {
                statusLabel.text = "✓ Valid"
                statusLabel.icon = AtmosIcons.VALID

                val root = DefaultMutableTreeNode("Component")
                buildTreeFromJson(json, root)
                tree.model = DefaultTreeModel(root)

                // Expand first level
                expandFirstLevel()
            } else {
                statusLabel.text = "Invalid response"
            }
        } catch (e: Exception) {
            statusLabel.text = "Parse error"
            val errorRoot = DefaultMutableTreeNode("Parse error")
            errorRoot.add(DefaultMutableTreeNode(e.message))
            tree.model = DefaultTreeModel(errorRoot)
        }
    }

    private fun buildTreeFromJson(element: JsonElement, parentNode: DefaultMutableTreeNode) {
        when (element) {
            is JsonObject -> {
                element.entries.forEach { (key, value) ->
                    val node = DefaultMutableTreeNode(InspectorNodeData(key, value))
                    if (value is JsonObject || value is JsonArray) {
                        buildTreeFromJson(value, node)
                    }
                    parentNode.add(node)
                }
            }
            is JsonArray -> {
                element.forEachIndexed { index, value ->
                    val node = DefaultMutableTreeNode(InspectorNodeData("[$index]", value))
                    if (value is JsonObject || value is JsonArray) {
                        buildTreeFromJson(value, node)
                    }
                    parentNode.add(node)
                }
            }
            else -> {
                // Primitive values are handled by the node itself
            }
        }
    }

    private fun expandFirstLevel() {
        val root = tree.model.root as? DefaultMutableTreeNode ?: return
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val path = javax.swing.tree.TreePath(arrayOf(root, child))
            tree.expandPath(path)
        }
    }

    private fun refreshComponent() {
        val componentInfo = lastComponent.get() ?: return
        loadComponentData(componentInfo)
    }

    private fun copyAsYaml() {
        // Implementation would serialize the tree to YAML
        val componentInfo = lastComponent.get() ?: return
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val content = java.awt.datatransfer.StringSelection("# Component: ${componentInfo.componentName}\n# Stack: ${componentInfo.stackName}")
        clipboard.setContents(content, null)
    }

    private fun copyAsJson() {
        // Implementation would serialize the tree to JSON
        val componentInfo = lastComponent.get() ?: return
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val content = java.awt.datatransfer.StringSelection("{\"component\": \"${componentInfo.componentName}\", \"stack\": \"${componentInfo.stackName}\"}")
        clipboard.setContents(content, null)
    }

    data class ComponentInfo(
        val componentName: String,
        val stackName: String,
        val componentType: String
    )

    data class InspectorNodeData(
        val key: String,
        val value: JsonElement
    ) {
        override fun toString(): String {
            return when (value) {
                is JsonPrimitive -> "$key: ${value.content}"
                is JsonObject -> "$key (${value.size} items)"
                is JsonArray -> "$key [${value.size} items]"
                else -> key
            }
        }
    }
}

/**
 * Custom cell renderer for the Inspector tree.
 */
class InspectorTreeCellRenderer : javax.swing.tree.DefaultTreeCellRenderer() {

    override fun getTreeCellRendererComponent(
        tree: JTree?,
        value: Any?,
        sel: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ): java.awt.Component {
        super.getTreeCellRendererComponent(tree, value, sel, expanded, leaf, row, hasFocus)

        val node = value as? DefaultMutableTreeNode ?: return this
        val nodeData = node.userObject as? AtmosInspectorPanel.InspectorNodeData

        if (nodeData != null) {
            when (nodeData.key) {
                "vars" -> icon = AtmosIcons.VARIABLE
                "settings" -> icon = AtmosIcons.SETTINGS
                "metadata" -> icon = AtmosIcons.METADATA
                "env" -> icon = AtmosIcons.SETTINGS
                "backend" -> icon = AtmosIcons.TERRAFORM
                else -> {
                    when (nodeData.value) {
                        is JsonObject -> icon = AtmosIcons.STACK_FOLDER
                        is JsonArray -> icon = AtmosIcons.STACK_FOLDER
                        else -> icon = AtmosIcons.VARIABLE
                    }
                }
            }
        }

        return this
    }
}
