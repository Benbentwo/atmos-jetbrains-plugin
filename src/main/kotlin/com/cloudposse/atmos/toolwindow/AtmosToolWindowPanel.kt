package com.cloudposse.atmos.toolwindow

import com.cloudposse.atmos.AtmosBundle
import com.cloudposse.atmos.AtmosIcons
import com.cloudposse.atmos.run.AtmosCommandType
import com.cloudposse.atmos.run.AtmosRunConfiguration
import com.cloudposse.atmos.run.AtmosRunConfigurationType
import com.cloudposse.atmos.services.AtmosCommandRunner
import com.cloudposse.atmos.services.AtmosConfigurationService
import com.cloudposse.atmos.services.AtmosProjectService
import com.intellij.execution.ProgramRunnerUtil
import com.intellij.execution.RunManager
import com.intellij.execution.executors.DefaultRunExecutor
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.ui.ColoredTreeCellRenderer
import com.intellij.ui.JBSplitter
import com.intellij.ui.SimpleTextAttributes
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.JPanel
import javax.swing.JTree
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Main panel for the Atmos tool window.
 */
class AtmosToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val stacksTree: Tree
    private val componentsTree: Tree
    private val stacksRootNode = DefaultMutableTreeNode(AtmosBundle.message("toolwindow.stacks"))
    private val componentsRootNode = DefaultMutableTreeNode(AtmosBundle.message("toolwindow.components"))

    init {
        // Create trees
        stacksTree = Tree(DefaultTreeModel(stacksRootNode))
        componentsTree = Tree(DefaultTreeModel(componentsRootNode))

        // Configure tree renderers
        stacksTree.cellRenderer = AtmosTreeCellRenderer()
        componentsTree.cellRenderer = AtmosTreeCellRenderer()

        // Add double-click listeners
        stacksTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    handleStackDoubleClick()
                }
            }
        })

        componentsTree.addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(e: MouseEvent) {
                if (e.clickCount == 2) {
                    handleComponentDoubleClick()
                }
            }
        })

        // Add right-click context menu
        stacksTree.addMouseListener(TreeContextMenuListener(stacksTree) { node ->
            createStackContextMenu(node)
        })

        componentsTree.addMouseListener(TreeContextMenuListener(componentsTree) { node ->
            createComponentContextMenu(node)
        })

        // Create splitter with two trees
        val splitter = JBSplitter(true, 0.5f)
        splitter.firstComponent = createTreePanel(AtmosBundle.message("toolwindow.stacks"), stacksTree)
        splitter.secondComponent = createTreePanel(AtmosBundle.message("toolwindow.components"), componentsTree)

        add(splitter, BorderLayout.CENTER)

        // Create toolbar
        val toolbar = createToolbar()
        add(toolbar, BorderLayout.NORTH)

        // Load initial data
        refreshTrees()
    }

    private fun createTreePanel(title: String, tree: Tree): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(JBLabel(title), BorderLayout.NORTH)
        panel.add(JBScrollPane(tree), BorderLayout.CENTER)
        return panel
    }

    private fun createToolbar(): JPanel {
        val actionGroup = DefaultActionGroup()
        actionGroup.add(RefreshAction())

        val toolbar = ActionManager.getInstance().createActionToolbar("AtmosToolWindow", actionGroup, true)
        toolbar.targetComponent = this

        val panel = JPanel(BorderLayout())
        panel.add(toolbar.component, BorderLayout.WEST)
        return panel
    }

    fun refreshTrees() {
        ApplicationManager.getApplication().executeOnPooledThread {
            loadStacks()
            loadComponents()
        }
    }

    private fun loadStacks() {
        val projectService = AtmosProjectService.getInstance(project)
        val configService = AtmosConfigurationService.getInstance(project)

        val stacksBasePath = configService.getStacksBasePath() ?: return
        val stacksDir = projectService.resolveProjectPath(stacksBasePath) ?: return

        ApplicationManager.getApplication().invokeLater {
            stacksRootNode.removeAllChildren()
            loadFilesIntoTree(stacksDir, stacksRootNode, setOf("yaml", "yml"))
            (stacksTree.model as DefaultTreeModel).reload()
            expandAllNodes(stacksTree)
        }
    }

    private fun loadComponents() {
        val projectService = AtmosProjectService.getInstance(project)
        val configService = AtmosConfigurationService.getInstance(project)

        ApplicationManager.getApplication().invokeLater {
            componentsRootNode.removeAllChildren()

            // Load Terraform components
            val terraformPath = configService.getTerraformComponentsBasePath()
            if (terraformPath != null) {
                val terraformDir = projectService.resolveProjectPath(terraformPath)
                if (terraformDir != null) {
                    val terraformNode = DefaultMutableTreeNode(ComponentNode("terraform", null, ComponentType.TERRAFORM_ROOT))
                    loadComponentDirectories(terraformDir, terraformNode)
                    componentsRootNode.add(terraformNode)
                }
            }

            // Load Helmfile components
            val helmfilePath = configService.getHelmfileComponentsBasePath()
            if (helmfilePath != null) {
                val helmfileDir = projectService.resolveProjectPath(helmfilePath)
                if (helmfileDir != null) {
                    val helmfileNode = DefaultMutableTreeNode(ComponentNode("helmfile", null, ComponentType.HELMFILE_ROOT))
                    loadComponentDirectories(helmfileDir, helmfileNode)
                    componentsRootNode.add(helmfileNode)
                }
            }

            (componentsTree.model as DefaultTreeModel).reload()
            expandAllNodes(componentsTree)
        }
    }

    private fun loadFilesIntoTree(dir: VirtualFile, parentNode: DefaultMutableTreeNode, extensions: Set<String>) {
        val children = dir.children.sortedWith(compareBy({ !it.isDirectory }, { it.name }))

        for (child in children) {
            if (child.isDirectory) {
                val node = DefaultMutableTreeNode(StackNode(child.name, child, isDirectory = true))
                loadFilesIntoTree(child, node, extensions)
                if (node.childCount > 0) {
                    parentNode.add(node)
                }
            } else if (extensions.contains(child.extension?.lowercase())) {
                val node = DefaultMutableTreeNode(StackNode(child.nameWithoutExtension, child, isDirectory = false))
                parentNode.add(node)
            }
        }
    }

    private fun loadComponentDirectories(dir: VirtualFile, parentNode: DefaultMutableTreeNode) {
        val children = dir.children.filter { it.isDirectory }.sortedBy { it.name }

        for (child in children) {
            // Check if this is a valid component (has .tf files)
            val hasTfFiles = child.children.any { it.extension?.lowercase() == "tf" }
            val componentType = if (hasTfFiles) ComponentType.TERRAFORM else ComponentType.DIRECTORY

            val node = DefaultMutableTreeNode(ComponentNode(child.name, child, componentType))

            // Recursively load subdirectories for nested components
            loadComponentDirectories(child, node)

            parentNode.add(node)
        }
    }

    private fun expandAllNodes(tree: Tree) {
        var row = 0
        while (row < tree.rowCount) {
            tree.expandRow(row)
            row++
        }
    }

    private fun handleStackDoubleClick() {
        val path = stacksTree.selectionPath ?: return
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val stackNode = node.userObject as? StackNode ?: return

        if (!stackNode.isDirectory && stackNode.file != null) {
            FileEditorManager.getInstance(project).openFile(stackNode.file, true)
        }
    }

    private fun handleComponentDoubleClick() {
        val path = componentsTree.selectionPath ?: return
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val componentNode = node.userObject as? ComponentNode ?: return

        if (componentNode.file != null && componentNode.type == ComponentType.TERRAFORM) {
            // Open main.tf if it exists
            val mainTf = componentNode.file.findChild("main.tf")
            if (mainTf != null) {
                FileEditorManager.getInstance(project).openFile(mainTf, true)
            }
        }
    }

    private fun createStackContextMenu(node: DefaultMutableTreeNode): ActionGroup {
        val stackNode = node.userObject as? StackNode
        val group = DefaultActionGroup()

        if (stackNode != null && !stackNode.isDirectory) {
            group.add(object : AnAction(AtmosBundle.message("action.open.file")) {
                override fun actionPerformed(e: AnActionEvent) {
                    stackNode.file?.let { FileEditorManager.getInstance(project).openFile(it, true) }
                }
            })
            group.addSeparator()
            group.add(object : AnAction(AtmosBundle.message("action.describe.stack")) {
                override fun actionPerformed(e: AnActionEvent) {
                    runDescribeStacks()
                }
            })
        }

        return group
    }

    private fun createComponentContextMenu(node: DefaultMutableTreeNode): ActionGroup {
        val componentNode = node.userObject as? ComponentNode
        val group = DefaultActionGroup()

        if (componentNode != null && componentNode.type == ComponentType.TERRAFORM) {
            group.add(object : AnAction(AtmosBundle.message("action.open.component")) {
                override fun actionPerformed(e: AnActionEvent) {
                    componentNode.file?.findChild("main.tf")?.let {
                        FileEditorManager.getInstance(project).openFile(it, true)
                    }
                }
            })
            group.addSeparator()
            group.add(object : AnAction(AtmosBundle.message("action.terraform.plan")) {
                override fun actionPerformed(e: AnActionEvent) {
                    runTerraformPlan(componentNode.name)
                }
            })
            group.add(object : AnAction(AtmosBundle.message("action.terraform.apply")) {
                override fun actionPerformed(e: AnActionEvent) {
                    runTerraformApply(componentNode.name)
                }
            })
        }

        return group
    }

    private fun runDescribeStacks() {
        createAndRunConfiguration("Describe Stacks", AtmosCommandType.DESCRIBE_STACKS)
    }

    private fun runTerraformPlan(componentName: String) {
        // TODO: Prompt for stack selection
        createAndRunConfiguration("Plan $componentName", AtmosCommandType.TERRAFORM_PLAN, componentName)
    }

    private fun runTerraformApply(componentName: String) {
        // TODO: Prompt for stack selection
        createAndRunConfiguration("Apply $componentName", AtmosCommandType.TERRAFORM_APPLY, componentName)
    }

    private fun createAndRunConfiguration(
        name: String,
        commandType: AtmosCommandType,
        component: String = "",
        stack: String = ""
    ) {
        val runManager = RunManager.getInstance(project)
        val configType = AtmosRunConfigurationType.getInstance()
        val settings = runManager.createConfiguration(name, configType.configurationFactories[0])
        val config = settings.configuration as AtmosRunConfiguration
        config.commandType = commandType
        config.component = component
        config.stack = stack

        runManager.addConfiguration(settings)
        runManager.selectedConfiguration = settings
        ProgramRunnerUtil.executeConfiguration(settings, DefaultRunExecutor.getRunExecutorInstance())
    }

    private inner class RefreshAction : AnAction(
        AtmosBundle.message("action.refresh"),
        AtmosBundle.message("action.refresh.description"),
        AllIcons.Actions.Refresh
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            refreshTrees()
        }
    }
}

/**
 * Data class representing a stack file in the tree.
 */
data class StackNode(
    val name: String,
    val file: VirtualFile?,
    val isDirectory: Boolean
)

/**
 * Data class representing a component in the tree.
 */
data class ComponentNode(
    val name: String,
    val file: VirtualFile?,
    val type: ComponentType
)

enum class ComponentType {
    TERRAFORM_ROOT,
    HELMFILE_ROOT,
    TERRAFORM,
    HELMFILE,
    DIRECTORY
}

/**
 * Custom tree cell renderer for Atmos trees.
 */
class AtmosTreeCellRenderer : ColoredTreeCellRenderer() {
    override fun customizeCellRenderer(
        tree: JTree,
        value: Any?,
        selected: Boolean,
        expanded: Boolean,
        leaf: Boolean,
        row: Int,
        hasFocus: Boolean
    ) {
        val node = value as? DefaultMutableTreeNode ?: return

        when (val userObject = node.userObject) {
            is String -> {
                append(userObject, SimpleTextAttributes.REGULAR_BOLD_ATTRIBUTES)
                icon = AllIcons.Nodes.Folder
            }
            is StackNode -> {
                append(userObject.name)
                icon = if (userObject.isDirectory) AllIcons.Nodes.Folder else AtmosIcons.STACK
            }
            is ComponentNode -> {
                append(userObject.name)
                icon = when (userObject.type) {
                    ComponentType.TERRAFORM_ROOT -> AllIcons.Nodes.ModuleGroup
                    ComponentType.HELMFILE_ROOT -> AllIcons.Nodes.ModuleGroup
                    ComponentType.TERRAFORM -> AtmosIcons.COMPONENT
                    ComponentType.HELMFILE -> AtmosIcons.COMPONENT
                    ComponentType.DIRECTORY -> AllIcons.Nodes.Folder
                }
            }
        }
    }
}

/**
 * Mouse listener for tree context menus.
 */
class TreeContextMenuListener(
    private val tree: Tree,
    private val menuProvider: (DefaultMutableTreeNode) -> ActionGroup
) : MouseAdapter() {
    override fun mousePressed(e: MouseEvent) {
        maybeShowPopup(e)
    }

    override fun mouseReleased(e: MouseEvent) {
        maybeShowPopup(e)
    }

    private fun maybeShowPopup(e: MouseEvent) {
        if (e.isPopupTrigger) {
            val path = tree.getPathForLocation(e.x, e.y)
            if (path != null) {
                tree.selectionPath = path
                val node = path.lastPathComponent as? DefaultMutableTreeNode
                if (node != null) {
                    val menu = ActionManager.getInstance().createActionPopupMenu("AtmosTree", menuProvider(node))
                    menu.component.show(e.component, e.x, e.y)
                }
            }
        }
    }
}
