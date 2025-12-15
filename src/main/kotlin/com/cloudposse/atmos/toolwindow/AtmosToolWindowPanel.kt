package com.cloudposse.atmos.toolwindow

import com.cloudposse.atmos.AtmosBundle
import com.cloudposse.atmos.AtmosIcons
import com.cloudposse.atmos.run.AtmosCommandType
import com.cloudposse.atmos.run.AtmosRunConfiguration
import com.cloudposse.atmos.run.AtmosRunConfigurationType
import com.cloudposse.atmos.services.AtmosCommandRunner
import com.cloudposse.atmos.services.AtmosConfigurationService
import com.cloudposse.atmos.services.AtmosProjectService
import com.cloudposse.atmos.settings.AtmosSettings
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.configurations.GeneralCommandLine
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.execution.process.ProcessHandlerFactory
import com.intellij.execution.process.ScriptRunnerUtil
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import kotlinx.serialization.json.*
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import java.util.concurrent.atomic.AtomicReference
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Main panel for the Atmos tool window.
 * Shows stacks and their associated components using atmos list instances.
 */
class AtmosToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val statusLabel = JLabel("Loading stacks...")
    private val tree = Tree()
    private val stackInstances = AtomicReference<List<StackInstance>?>(null)
    private val selectedComponent = AtomicReference<ComponentInfo?>(null)

    init {
        createUI()
        setupListeners()
    }
    
    private fun createUI() {
        // Header panel
        val headerPanel = JPanel(BorderLayout())
        headerPanel.add(JLabel("Status: "), BorderLayout.WEST)
        headerPanel.add(statusLabel, BorderLayout.CENTER)
        add(headerPanel, BorderLayout.NORTH)

        // Tree view for stacks and components
        tree.model = DefaultTreeModel(DefaultMutableTreeNode("Loading stacks..."))
        tree.cellRenderer = StackTreeCellRenderer()
        
        // Add click listeners
        tree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 1 && e.button == MouseEvent.BUTTON1) { // Left single click
                    handleComponentNavigation()
                }
            }
            
            override fun mousePressed(e: MouseEvent) {
                maybeShowContextMenu(e)
            }
            
            override fun mouseReleased(e: MouseEvent) {
                maybeShowContextMenu(e)
            }
        })
        
        add(JBScrollPane(tree), BorderLayout.CENTER)

        // Toolbar
        val toolbar = createToolbar()
        add(toolbar, BorderLayout.SOUTH)
    }
    
    private fun setupListeners() {
        // Load stack instances on initialization
        loadStackInstances()
    }
    
    private fun handleComponentNavigation() {
        val node = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        val nodeData = node.userObject as? ComponentInfo ?: return
        
        // Navigate to component definition
        navigateToComponentDefinition(nodeData)
    }
    
    private fun maybeShowContextMenu(e: MouseEvent) {
        if (e.isPopupTrigger) {
            val path = tree.getPathForLocation(e.x, e.y)
            if (path != null) {
                tree.selectionPath = path
                val node = path.lastPathComponent as? DefaultMutableTreeNode
                val nodeData = node?.userObject
                
                if (nodeData is ComponentInfo) {
                    val menu = createComponentContextMenu(nodeData)
                    menu.show(e.component, e.x, e.y)
                }
            }
        }
    }
    
    private fun createComponentContextMenu(componentInfo: ComponentInfo): javax.swing.JPopupMenu {
        val popupMenu = javax.swing.JPopupMenu()
        
        val showDetailsAction = javax.swing.JMenuItem("Show Component Details")
        showDetailsAction.addActionListener {
            selectedComponent.set(componentInfo)
            loadComponentData(componentInfo)
        }
        popupMenu.add(showDetailsAction)
        
        return popupMenu
    }
    
    private fun navigateToComponentDefinition(componentInfo: ComponentInfo) {
        // Get the stack file for this component instance using atmos describe
        statusLabel.text = "Loading component configuration..."
        
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
                    navigateToStackFile(output, componentInfo)
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    statusLabel.text = "Error getting component info: ${e.message}"
                }
            }
        }
    }
    
    private fun navigateToStackFile(output: String, componentInfo: ComponentInfo) {
        try {
            val json = Json.parseToJsonElement(output)
            
            if (json is JsonObject) {
                val stackFile = json.jsonObject["atmos_stack_file"]?.jsonPrimitive?.content
                
                if (stackFile != null) {
                    // Find the stack file in the project
                    val projectService = AtmosProjectService.getInstance(project)
                    val configService = AtmosConfigurationService.getInstance(project)
                    val stacksBasePath = configService.getStacksBasePath()
                    
                    if (stacksBasePath != null) {
                        val stacksDir = projectService.resolveProjectPath(stacksBasePath)
                        if (stacksDir != null) {
                            // Try different possible file extensions
                            val possibleFiles = listOf(
                                "$stackFile.yaml",
                                "$stackFile.yml"
                            )
                            
                            for (fileName in possibleFiles) {
                                val stackFilePath = stacksDir.findFileByRelativePath(fileName)
                                if (stackFilePath != null) {
                                    statusLabel.text = "Opening stack file: $fileName"
                                    navigateToComponentDefinitionInFile(stackFilePath, componentInfo)
                                    return
                                }
                            }
                            
                            statusLabel.text = "Stack file not found: $stackFile in $stacksBasePath"
                        } else {
                            statusLabel.text = "Stacks directory not found: $stacksBasePath"
                        }
                    } else {
                        statusLabel.text = "Stacks base path not configured"
                    }
                } else {
                    statusLabel.text = "Stack file not found in component description"
                }
            } else {
                statusLabel.text = "Invalid component description format"
            }
        } catch (e: Exception) {
            statusLabel.text = "Error parsing component description: ${e.message}"
        }
    }
    
    /**
     * Navigate to the specific line in the stack file where the component instance is defined.
     */
    private fun navigateToComponentDefinitionInFile(stackFile: VirtualFile, componentInfo: ComponentInfo) {
        try {
            // Read the stack file content to find the component instance line
            val fileContent = String(stackFile.contentsToByteArray())
            val lines = fileContent.split('\n')
            
            // Look for the component instance definition
            // Format: "    componentName:" under "components:" -> "terraform:" section
            var lineNumber = 0
            var inComponentsSection = false
            var inTerraformSection = false
            
            for (i in lines.indices) {
                val line = lines[i].trimStart()
                val originalLine = lines[i]
                
                // Check if we're entering the components section
                if (line.startsWith("components:")) {
                    inComponentsSection = true
                    continue
                }
                
                // Check if we're entering the terraform subsection within components
                if (inComponentsSection && line.startsWith("terraform:")) {
                    inTerraformSection = true
                    continue
                }
                
                // If we're in the right section, look for our component instance
                if (inComponentsSection && inTerraformSection) {
                    // Component instance definitions start with the instance name followed by ":"
                    val componentInstancePattern = "${componentInfo.componentName}:"
                    if (line.startsWith(componentInstancePattern)) {
                        lineNumber = i + 1 // Line numbers are 1-based
                        break
                    }
                    
                    // If we hit another top-level section, we're done
                    if (originalLine.isNotEmpty() && !originalLine.startsWith(" ") && !originalLine.startsWith("\t") && originalLine != "components:" && originalLine != "  terraform:") {
                        break
                    }
                }
            }
            
            // Open the file and navigate to the specific line
            if (lineNumber > 0) {
                val descriptor = OpenFileDescriptor(project, stackFile, lineNumber - 1, 0) // OpenFileDescriptor uses 0-based line numbers
                FileEditorManager.getInstance(project).openTextEditor(descriptor, true)
                statusLabel.text = "Navigated to ${componentInfo.componentName} at line $lineNumber in ${stackFile.name}"
            } else {
                // Fallback: just open the file without specific line navigation
                FileEditorManager.getInstance(project).openFile(stackFile, true)
                statusLabel.text = "Opened ${stackFile.name} (component definition not found in expected location)"
            }
        } catch (e: Exception) {
            // Fallback: just open the file
            FileEditorManager.getInstance(project).openFile(stackFile, true)
            statusLabel.text = "Opened ${stackFile.name} (error finding component line: ${e.message})"
        }
    }


    private fun createToolbar(): JPanel {
        val toolbar = JPanel()
        toolbar.layout = BoxLayout(toolbar, BoxLayout.X_AXIS)

        val refreshButton = JButton(AtmosIcons.REFRESH)
        refreshButton.toolTipText = "Refresh Stacks"
        refreshButton.addActionListener { loadStackInstances() }
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

    fun refreshTrees() {
        loadStackInstances()
    }

    private fun loadStackInstances() {
        ApplicationManager.getApplication().executeOnPooledThread {
            try {
                val settings = AtmosSettings.getInstance()
                val atmosPath = settings.atmosExecutablePath.ifEmpty { "atmos" }

                val commandLine = GeneralCommandLine(
                    atmosPath,
                    "list", "instances",
                    "--format", "json"
                )
                commandLine.setWorkDirectory(project.basePath)

                val output = ScriptRunnerUtil.getProcessOutput(
                    commandLine,
                    ScriptRunnerUtil.STDOUT_OUTPUT_KEY_FILTER,
                    30000
                )

                ApplicationManager.getApplication().invokeLater {
                    parseStackInstances(output)
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    statusLabel.text = "Error loading stacks: ${e.message}"
                    val errorRoot = DefaultMutableTreeNode("Error loading stacks")
                    errorRoot.add(DefaultMutableTreeNode(e.message))
                    tree.model = DefaultTreeModel(errorRoot)
                }
            }
        }
    }

    private fun parseStackInstances(output: String) {
        try {
            // Handle edge case of empty output
            if (output.trim().isEmpty()) {
                statusLabel.text = "No output from atmos command"
                return
            }
            
            // Parse JSON format from atmos list instances --format json
            val json = Json.parseToJsonElement(output.trim())
            if (json !is JsonArray) {
                statusLabel.text = "Invalid response format: expected JsonArray, got ${json::class.simpleName}"
                return
            }
            
            val instances = mutableListOf<StackInstance>()
            for (element in json) {
                if (element is JsonObject) {
                    val component = element["Component"]?.jsonPrimitive?.content
                    val stack = element["Stack"]?.jsonPrimitive?.content
                    
                    if (component != null && stack != null && component.isNotBlank() && stack.isNotBlank()) {
                        instances.add(StackInstance(component, stack))
                    }
                }
            }
            
            if (instances.isEmpty()) {
                statusLabel.text = "No stack instances found"
                return
            }
            
            stackInstances.set(instances)
            buildStackTree(instances)
            statusLabel.text = "${instances.size} components across ${instances.map { it.stack }.distinct().size} stacks"
        } catch (e: Exception) {
            statusLabel.text = "Parse error: ${e.message}"
            val errorRoot = DefaultMutableTreeNode("Parse error")
            errorRoot.add(DefaultMutableTreeNode(e.message))
            tree.model = DefaultTreeModel(errorRoot)
        }
    }

    private fun buildStackTree(instances: List<StackInstance>) {
        val root = DefaultMutableTreeNode("Stacks")
        
        // Group instances by stack
        val stackGroups = instances.groupBy { it.stack }
        
        for ((stackName, stackComponents) in stackGroups.toSortedMap()) {
            val stackNode = DefaultMutableTreeNode(StackInfo(stackName))
            
            // Sort components within each stack
            for (instance in stackComponents.sortedBy { it.component }) {
                val componentInfo = ComponentInfo(instance.component, stackName, "terraform")
                val componentNode = DefaultMutableTreeNode(componentInfo)
                stackNode.add(componentNode)
            }
            
            root.add(stackNode)
        }
        
        tree.model = DefaultTreeModel(root)
        
        // Expand all stack nodes
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val path = javax.swing.tree.TreePath(arrayOf(root, child))
            tree.expandPath(path)
        }
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
                    displayComponentDetails(output, componentInfo)
                }
            } catch (e: Exception) {
                ApplicationManager.getApplication().invokeLater {
                    statusLabel.text = "Error loading ${componentInfo.componentName}: ${e.message}"
                }
            }
        }
    }


    private fun displayComponentDetails(output: String, componentInfo: ComponentInfo) {
        try {
            val json = Json.parseToJsonElement(output)

            if (json is JsonObject) {
                statusLabel.text = "Showing details for ${componentInfo.componentName} in ${componentInfo.stackName}"
                statusLabel.icon = AtmosIcons.VALID

                // Create a new window or dialog to show component details
                val detailsFrame = JFrame("Component Details: ${componentInfo.componentName} (${componentInfo.stackName})")
                val detailsTree = Tree()
                val root = DefaultMutableTreeNode("${componentInfo.componentName}")
                buildTreeFromJson(json, root)
                detailsTree.model = DefaultTreeModel(root)
                detailsTree.cellRenderer = InspectorTreeCellRenderer()
                
                // Add a toolbar to the details window
                val toolbar = JPanel()
                toolbar.layout = BoxLayout(toolbar, BoxLayout.X_AXIS)
                
                val copyButton = JButton("Copy JSON")
                copyButton.addActionListener {
                    val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
                    val content = java.awt.datatransfer.StringSelection(output)
                    clipboard.setContents(content, null)
                }
                toolbar.add(copyButton)
                
                val refreshButton = JButton("Refresh")
                refreshButton.addActionListener {
                    detailsFrame.dispose()
                    loadComponentData(componentInfo)
                }
                toolbar.add(refreshButton)
                
                detailsFrame.layout = BorderLayout()
                detailsFrame.add(toolbar, BorderLayout.NORTH)
                detailsFrame.add(JBScrollPane(detailsTree), BorderLayout.CENTER)
                detailsFrame.setSize(800, 600)
                detailsFrame.setLocationRelativeTo(this)
                detailsFrame.isVisible = true
                
                // Expand first level
                expandFirstLevel(detailsTree)
            } else {
                statusLabel.text = "Invalid component response for ${componentInfo.componentName}"
            }
        } catch (e: Exception) {
            statusLabel.text = "Parse error for ${componentInfo.componentName}: ${e.message}"
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

    private fun expandFirstLevel(targetTree: Tree = tree) {
        val root = targetTree.model.root as? DefaultMutableTreeNode ?: return
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val path = javax.swing.tree.TreePath(arrayOf(root, child))
            targetTree.expandPath(path)
        }
    }
    
    private fun copyAsYaml() {
        val componentInfo = selectedComponent.get() ?: return
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val content = java.awt.datatransfer.StringSelection("# Component: ${componentInfo.componentName}\n# Stack: ${componentInfo.stackName}")
        clipboard.setContents(content, null)
    }

    private fun copyAsJson() {
        val componentInfo = selectedComponent.get() ?: return
        val clipboard = java.awt.Toolkit.getDefaultToolkit().systemClipboard
        val content = java.awt.datatransfer.StringSelection("{\"component\": \"${componentInfo.componentName}\", \"stack\": \"${componentInfo.stackName}\"}")
        clipboard.setContents(content, null)
    }

}

/**
 * Data class representing a stack instance from atmos list instances.
 */
data class StackInstance(
    val component: String,
    val stack: String
)

/**
 * Data class representing a stack in the tree.
 */
data class StackInfo(
    val stackName: String
) {
    override fun toString(): String = stackName
}

/**
 * Data class representing a component in the tree.
 */
data class ComponentInfo(
    val componentName: String,
    val stackName: String,
    val componentType: String
) {
    override fun toString(): String = componentName
}

/**
 * Data class for inspector tree nodes.
 */
data class InspectorNodeData(
    val key: String,
    val value: JsonElement
) {
    override fun toString(): String {
        return when (value) {
            is JsonPrimitive -> "$key: ${value.content}"
            is JsonObject -> "$key (${value.size} items)"
            is JsonArray -> "$key [${value.size} items]"
        }
    }
}

/**
 * Custom cell renderer for the Stack tree.
 */
class StackTreeCellRenderer : javax.swing.tree.DefaultTreeCellRenderer() {

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
        val nodeData = node.userObject

        when (nodeData) {
            is StackInfo -> {
                icon = AtmosIcons.STACK_FOLDER
                text = nodeData.stackName
            }
            is ComponentInfo -> {
                icon = AtmosIcons.COMPONENT
                text = nodeData.componentName
            }
            else -> {
                icon = AtmosIcons.STACK_FOLDER
                text = nodeData?.toString() ?: "Unknown"
            }
        }

        return this
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
        val nodeData = node.userObject as? InspectorNodeData

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

