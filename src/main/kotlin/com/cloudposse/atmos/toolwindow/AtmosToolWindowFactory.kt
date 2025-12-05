package com.cloudposse.atmos.toolwindow

import com.cloudposse.atmos.AtmosIcons
import com.cloudposse.atmos.services.AtmosConfigurationService
import com.cloudposse.atmos.services.AtmosProjectService
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import java.awt.BorderLayout
import javax.swing.*
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Factory for creating the Atmos tool window.
 * Provides a hierarchical view of stacks and components.
 */
class AtmosToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val panel = AtmosToolWindowPanel(project)
        val content = ContentFactory.getInstance().createContent(panel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return AtmosProjectService.getInstance(project).isAtmosProject
    }
}

/**
 * Main panel for the Atmos tool window.
 */
class AtmosToolWindowPanel(private val project: Project) : JPanel(BorderLayout()) {

    private val tabbedPane = JBTabbedPane()
    private val stacksTree = Tree()
    private val componentsTree = Tree()
    private val workflowsTree = Tree()

    init {
        createUI()
        loadData()
    }

    private fun createUI() {
        // Create tabs
        tabbedPane.addTab("Stacks", AtmosIcons.STACK, JBScrollPane(stacksTree))
        tabbedPane.addTab("Components", AtmosIcons.COMPONENT, JBScrollPane(componentsTree))
        tabbedPane.addTab("Workflows", AtmosIcons.WORKFLOW, JBScrollPane(workflowsTree))

        add(tabbedPane, BorderLayout.CENTER)

        // Add toolbar
        val toolbar = createToolbar()
        add(toolbar, BorderLayout.NORTH)

        // Set up tree renderers
        stacksTree.cellRenderer = AtmosTreeCellRenderer()
        componentsTree.cellRenderer = AtmosTreeCellRenderer()
        workflowsTree.cellRenderer = AtmosTreeCellRenderer()

        // Set up double-click navigation
        stacksTree.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    navigateToSelectedNode(stacksTree)
                }
            }
        })

        componentsTree.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    navigateToSelectedNode(componentsTree)
                }
            }
        })

        workflowsTree.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    navigateToSelectedNode(workflowsTree)
                }
            }
        })
    }

    private fun createToolbar(): JPanel {
        val toolbar = JPanel()
        toolbar.layout = BoxLayout(toolbar, BoxLayout.X_AXIS)

        val refreshButton = JButton(AtmosIcons.REFRESH)
        refreshButton.toolTipText = "Refresh"
        refreshButton.addActionListener { loadData() }
        toolbar.add(refreshButton)

        toolbar.add(Box.createHorizontalGlue())

        return toolbar
    }

    private fun loadData() {
        val projectService = AtmosProjectService.getInstance(project)
        val configService = AtmosConfigurationService.getInstance(project)

        // Load stacks
        val stacksRoot = DefaultMutableTreeNode("Stacks")
        val stacksBasePath = configService.getStacksBasePath()
        if (stacksBasePath != null) {
            val stacksDir = projectService.resolveProjectPath(stacksBasePath)
            if (stacksDir != null) {
                loadStackFiles(stacksDir, stacksRoot)
            }
        }
        stacksTree.model = DefaultTreeModel(stacksRoot)

        // Load components
        val componentsRoot = DefaultMutableTreeNode("Components")

        val terraformNode = DefaultMutableTreeNode("Terraform")
        val terraformBasePath = configService.getTerraformComponentsBasePath()
        if (terraformBasePath != null) {
            val terraformDir = projectService.resolveProjectPath(terraformBasePath)
            if (terraformDir != null) {
                loadComponents(terraformDir, terraformNode, ComponentType.TERRAFORM)
            }
        }
        componentsRoot.add(terraformNode)

        val helmfileNode = DefaultMutableTreeNode("Helmfile")
        val helmfileBasePath = configService.getHelmfileComponentsBasePath()
        if (helmfileBasePath != null) {
            val helmfileDir = projectService.resolveProjectPath(helmfileBasePath)
            if (helmfileDir != null) {
                loadComponents(helmfileDir, helmfileNode, ComponentType.HELMFILE)
            }
        }
        componentsRoot.add(helmfileNode)

        componentsTree.model = DefaultTreeModel(componentsRoot)

        // Load workflows
        val workflowsRoot = DefaultMutableTreeNode("Workflows")
        val workflowsBasePath = configService.getWorkflowsBasePath()
        if (workflowsBasePath != null) {
            val workflowsDir = projectService.resolveProjectPath(workflowsBasePath)
            if (workflowsDir != null) {
                loadWorkflows(workflowsDir, workflowsRoot)
            }
        }
        workflowsTree.model = DefaultTreeModel(workflowsRoot)

        // Expand root nodes
        expandFirstLevel(stacksTree)
        expandFirstLevel(componentsTree)
        expandFirstLevel(workflowsTree)
    }

    private fun loadStackFiles(dir: VirtualFile, parentNode: DefaultMutableTreeNode) {
        dir.children.sortedBy { it.name }.forEach { child ->
            if (child.isDirectory) {
                val dirNode = DefaultMutableTreeNode(AtmosTreeNodeData(child.name, child, NodeType.FOLDER))
                loadStackFiles(child, dirNode)
                if (dirNode.childCount > 0) {
                    parentNode.add(dirNode)
                }
            } else if (isStackFile(child)) {
                val fileNode = DefaultMutableTreeNode(
                    AtmosTreeNodeData(child.nameWithoutExtension, child, NodeType.STACK_FILE)
                )
                parentNode.add(fileNode)
            }
        }
    }

    private fun loadComponents(dir: VirtualFile, parentNode: DefaultMutableTreeNode, type: ComponentType) {
        dir.children.sortedBy { it.name }.forEach { child ->
            if (child.isDirectory) {
                val isComponent = when (type) {
                    ComponentType.TERRAFORM -> child.findChild("main.tf") != null ||
                            child.findChild("variables.tf") != null
                    ComponentType.HELMFILE -> child.findChild("helmfile.yaml") != null
                }

                if (isComponent) {
                    val componentNode = DefaultMutableTreeNode(
                        AtmosTreeNodeData(child.name, child, NodeType.COMPONENT)
                    )
                    parentNode.add(componentNode)
                } else {
                    // Check if any subdirectories are components
                    val dirNode = DefaultMutableTreeNode(AtmosTreeNodeData(child.name, child, NodeType.FOLDER))
                    loadComponents(child, dirNode, type)
                    if (dirNode.childCount > 0) {
                        parentNode.add(dirNode)
                    }
                }
            }
        }
    }

    private fun loadWorkflows(dir: VirtualFile, parentNode: DefaultMutableTreeNode) {
        dir.children.sortedBy { it.name }.forEach { child ->
            if (child.isDirectory) {
                val dirNode = DefaultMutableTreeNode(AtmosTreeNodeData(child.name, child, NodeType.FOLDER))
                loadWorkflows(child, dirNode)
                if (dirNode.childCount > 0) {
                    parentNode.add(dirNode)
                }
            } else if (child.extension == "yaml" || child.extension == "yml") {
                val workflowNode = DefaultMutableTreeNode(
                    AtmosTreeNodeData(child.nameWithoutExtension, child, NodeType.WORKFLOW)
                )
                parentNode.add(workflowNode)
            }
        }
    }

    private fun isStackFile(file: VirtualFile): Boolean {
        val name = file.name
        return name.endsWith(".yaml") || name.endsWith(".yml") ||
                name.endsWith(".yaml.tmpl") || name.endsWith(".yml.tmpl")
    }

    private fun expandFirstLevel(tree: Tree) {
        val root = tree.model.root as? DefaultMutableTreeNode ?: return
        for (i in 0 until root.childCount) {
            val child = root.getChildAt(i) as? DefaultMutableTreeNode ?: continue
            val path = javax.swing.tree.TreePath(arrayOf(root, child))
            tree.expandPath(path)
        }
    }

    private fun navigateToSelectedNode(tree: Tree) {
        val selectedNode = tree.lastSelectedPathComponent as? DefaultMutableTreeNode ?: return
        val nodeData = selectedNode.userObject as? AtmosTreeNodeData ?: return
        val file = nodeData.file

        if (file.isDirectory) {
            // For components, open the main file
            val mainFile = file.findChild("main.tf")
                ?: file.findChild("variables.tf")
                ?: file.findChild("helmfile.yaml")
                ?: return

            com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(mainFile, true)
        } else {
            com.intellij.openapi.fileEditor.FileEditorManager.getInstance(project).openFile(file, true)
        }
    }

    enum class ComponentType {
        TERRAFORM, HELMFILE
    }

    enum class NodeType {
        FOLDER, STACK_FILE, COMPONENT, WORKFLOW
    }

    data class AtmosTreeNodeData(
        val name: String,
        val file: VirtualFile,
        val type: NodeType
    ) {
        override fun toString(): String = name
    }
}

/**
 * Custom cell renderer for the Atmos tree.
 */
class AtmosTreeCellRenderer : javax.swing.tree.DefaultTreeCellRenderer() {

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
        val nodeData = node.userObject as? AtmosToolWindowPanel.AtmosTreeNodeData

        if (nodeData != null) {
            icon = when (nodeData.type) {
                AtmosToolWindowPanel.NodeType.FOLDER -> AtmosIcons.STACK_FOLDER
                AtmosToolWindowPanel.NodeType.STACK_FILE -> AtmosIcons.STACK_FILE
                AtmosToolWindowPanel.NodeType.COMPONENT -> AtmosIcons.COMPONENT
                AtmosToolWindowPanel.NodeType.WORKFLOW -> AtmosIcons.WORKFLOW
            }
        }

        return this
    }
}
