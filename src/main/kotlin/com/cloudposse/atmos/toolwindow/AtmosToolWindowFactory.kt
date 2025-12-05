package com.cloudposse.atmos.toolwindow

import com.cloudposse.atmos.AtmosBundle
import com.cloudposse.atmos.AtmosIcons
import com.cloudposse.atmos.run.AtmosCommandRunner
import com.cloudposse.atmos.services.AtmosConfigurationService
import com.cloudposse.atmos.services.AtmosProjectService
import com.intellij.execution.filters.TextConsoleBuilderFactory
import com.intellij.execution.ui.ConsoleView
import com.intellij.icons.AllIcons
import com.intellij.openapi.actionSystem.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.diagnostic.Logger
import com.intellij.openapi.project.DumbAware
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.SimpleToolWindowPanel
import com.intellij.openapi.wm.ToolWindow
import com.intellij.openapi.wm.ToolWindowFactory
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTabbedPane
import com.intellij.ui.content.ContentFactory
import com.intellij.ui.treeStructure.Tree
import com.intellij.util.ui.JBUI
import java.awt.BorderLayout
import javax.swing.JPanel
import javax.swing.tree.DefaultMutableTreeNode
import javax.swing.tree.DefaultTreeModel

/**
 * Factory for creating the Atmos tool window.
 *
 * The tool window displays:
 * - Stacks tree: Hierarchical view of all stacks and their components
 * - Components tree: All available components with their types
 * - Workflows: Available workflows from workflows.base_path
 * - Console: Output from recent Atmos commands
 */
class AtmosToolWindowFactory : ToolWindowFactory, DumbAware {

    override fun createToolWindowContent(project: Project, toolWindow: ToolWindow) {
        val toolWindowPanel = AtmosToolWindowPanel(project)
        val contentFactory = ContentFactory.getInstance()
        val content = contentFactory.createContent(toolWindowPanel, "", false)
        toolWindow.contentManager.addContent(content)
    }

    override fun shouldBeAvailable(project: Project): Boolean {
        return AtmosProjectService.getInstance(project).isAtmosProject
    }
}

/**
 * Main panel for the Atmos tool window.
 */
class AtmosToolWindowPanel(private val project: Project) : SimpleToolWindowPanel(true, true) {

    private val LOG = Logger.getInstance(AtmosToolWindowPanel::class.java)

    private val tabbedPane = JBTabbedPane()
    private val stacksTree = Tree()
    private val componentsTree = Tree()
    private val workflowsTree = Tree()
    private val consoleView: ConsoleView

    private val stacksRootNode = DefaultMutableTreeNode("Stacks")
    private val componentsRootNode = DefaultMutableTreeNode("Components")
    private val workflowsRootNode = DefaultMutableTreeNode("Workflows")

    init {
        // Create console view
        consoleView = TextConsoleBuilderFactory.getInstance()
            .createBuilder(project)
            .console

        // Initialize trees
        initializeTrees()

        // Set up tabs
        tabbedPane.addTab(AtmosBundle.message("toolwindow.tab.stacks"), AtmosIcons.STACK, createStacksPanel())
        tabbedPane.addTab(AtmosBundle.message("toolwindow.tab.components"), AtmosIcons.COMPONENT, createComponentsPanel())
        tabbedPane.addTab(AtmosBundle.message("toolwindow.tab.workflows"), AllIcons.Actions.Execute, createWorkflowsPanel())
        tabbedPane.addTab(AtmosBundle.message("toolwindow.tab.console"), AllIcons.Debugger.Console, consoleView.component)

        setContent(tabbedPane)

        // Set up toolbar
        val actionGroup = DefaultActionGroup().apply {
            add(RefreshAction())
            addSeparator()
            add(DescribeConfigAction())
            add(ValidateStacksAction())
        }

        val toolbar = ActionManager.getInstance().createActionToolbar("AtmosToolWindow", actionGroup, true)
        toolbar.targetComponent = this
        setToolbar(toolbar.component)

        // Load initial data
        refreshData()
    }

    private fun initializeTrees() {
        stacksTree.model = DefaultTreeModel(stacksRootNode)
        stacksTree.isRootVisible = true

        componentsTree.model = DefaultTreeModel(componentsRootNode)
        componentsTree.isRootVisible = true

        workflowsTree.model = DefaultTreeModel(workflowsRootNode)
        workflowsTree.isRootVisible = true

        // Add double-click listener for stacks/components
        stacksTree.addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                if (e.clickCount == 2) {
                    handleStackTreeDoubleClick()
                }
            }
        })
    }

    private fun createStacksPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(JBScrollPane(stacksTree), BorderLayout.CENTER)

        val statusLabel = JBLabel(AtmosBundle.message("toolwindow.stacks.hint"))
        statusLabel.border = JBUI.Borders.empty(4)
        panel.add(statusLabel, BorderLayout.SOUTH)

        return panel
    }

    private fun createComponentsPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(JBScrollPane(componentsTree), BorderLayout.CENTER)

        val statusLabel = JBLabel(AtmosBundle.message("toolwindow.components.hint"))
        statusLabel.border = JBUI.Borders.empty(4)
        panel.add(statusLabel, BorderLayout.SOUTH)

        return panel
    }

    private fun createWorkflowsPanel(): JPanel {
        val panel = JPanel(BorderLayout())
        panel.add(JBScrollPane(workflowsTree), BorderLayout.CENTER)

        val statusLabel = JBLabel(AtmosBundle.message("toolwindow.workflows.hint"))
        statusLabel.border = JBUI.Borders.empty(4)
        panel.add(statusLabel, BorderLayout.SOUTH)

        return panel
    }

    private fun handleStackTreeDoubleClick() {
        val path = stacksTree.selectionPath ?: return
        val node = path.lastPathComponent as? DefaultMutableTreeNode ?: return
        val userObject = node.userObject

        // If it's a component node (has parent that's a stack)
        if (node.parent is DefaultMutableTreeNode) {
            val parent = node.parent as DefaultMutableTreeNode
            if (parent.parent == stacksRootNode) {
                // Parent is a stack, this is a component
                val stackName = parent.userObject as? String ?: return
                val componentName = userObject as? String ?: return

                // Run describe component
                ApplicationManager.getApplication().executeOnPooledThread {
                    val result = AtmosCommandRunner.describeComponent(project, componentName, stackName)
                    ApplicationManager.getApplication().invokeLater {
                        tabbedPane.selectedIndex = 3 // Switch to console tab
                        consoleView.clear()
                        if (result.success) {
                            consoleView.print(result.stdout, com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT)
                        } else {
                            consoleView.print(result.stderr, com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT)
                        }
                    }
                }
            }
        }
    }

    fun refreshData() {
        ApplicationManager.getApplication().executeOnPooledThread {
            loadStacks()
            loadComponents()
            loadWorkflows()
        }
    }

    private fun loadStacks() {
        val result = AtmosCommandRunner.listStacks(project)

        ApplicationManager.getApplication().invokeLater {
            stacksRootNode.removeAllChildren()

            if (result.success) {
                val stacks = result.stdout.lines()
                    .filter { it.isNotBlank() }
                    .sorted()

                for (stack in stacks) {
                    val stackNode = DefaultMutableTreeNode(stack)
                    stacksRootNode.add(stackNode)
                }

                if (stacks.isEmpty()) {
                    stacksRootNode.add(DefaultMutableTreeNode(AtmosBundle.message("toolwindow.no.stacks.found")))
                }
            } else {
                stacksRootNode.add(DefaultMutableTreeNode(AtmosBundle.message("toolwindow.error.loading.stacks")))
                LOG.warn("Failed to load stacks: ${result.stderr}")
            }

            (stacksTree.model as DefaultTreeModel).reload()
        }
    }

    private fun loadComponents() {
        val result = AtmosCommandRunner.listComponents(project)

        ApplicationManager.getApplication().invokeLater {
            componentsRootNode.removeAllChildren()

            if (result.success) {
                val components = result.stdout.lines()
                    .filter { it.isNotBlank() }
                    .sorted()

                // Group by terraform/helmfile if possible
                val terraformNode = DefaultMutableTreeNode("terraform")
                val helmfileNode = DefaultMutableTreeNode("helmfile")

                for (component in components) {
                    // Just add to terraform for now; could parse component type later
                    terraformNode.add(DefaultMutableTreeNode(component))
                }

                if (terraformNode.childCount > 0) {
                    componentsRootNode.add(terraformNode)
                }
                if (helmfileNode.childCount > 0) {
                    componentsRootNode.add(helmfileNode)
                }

                if (components.isEmpty()) {
                    componentsRootNode.add(DefaultMutableTreeNode(AtmosBundle.message("toolwindow.no.components.found")))
                }
            } else {
                componentsRootNode.add(DefaultMutableTreeNode(AtmosBundle.message("toolwindow.error.loading.components")))
                LOG.warn("Failed to load components: ${result.stderr}")
            }

            (componentsTree.model as DefaultTreeModel).reload()
        }
    }

    private fun loadWorkflows() {
        val configService = AtmosConfigurationService.getInstance(project)
        val projectService = AtmosProjectService.getInstance(project)
        val workflowsBasePath = configService.getWorkflowsBasePath()

        ApplicationManager.getApplication().invokeLater {
            workflowsRootNode.removeAllChildren()

            if (workflowsBasePath != null) {
                val workflowsDir = projectService.resolveProjectPath(workflowsBasePath)

                if (workflowsDir != null && workflowsDir.exists() && workflowsDir.isDirectory) {
                    val workflowFiles = workflowsDir.children
                        .filter { it.extension == "yaml" || it.extension == "yml" }
                        .sortedBy { it.name }

                    for (file in workflowFiles) {
                        workflowsRootNode.add(DefaultMutableTreeNode(file.nameWithoutExtension))
                    }

                    if (workflowFiles.isEmpty()) {
                        workflowsRootNode.add(DefaultMutableTreeNode(AtmosBundle.message("toolwindow.no.workflows.found")))
                    }
                } else {
                    workflowsRootNode.add(DefaultMutableTreeNode(AtmosBundle.message("toolwindow.no.workflows.found")))
                }
            } else {
                workflowsRootNode.add(DefaultMutableTreeNode(AtmosBundle.message("toolwindow.no.workflows.found")))
            }

            (workflowsTree.model as DefaultTreeModel).reload()
        }
    }

    // ---- Actions ----

    private inner class RefreshAction : AnAction(
        AtmosBundle.message("toolwindow.action.refresh"),
        AtmosBundle.message("toolwindow.action.refresh.description"),
        AllIcons.Actions.Refresh
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            refreshData()
        }
    }

    private inner class DescribeConfigAction : AnAction(
        AtmosBundle.message("toolwindow.action.describe.config"),
        AtmosBundle.message("toolwindow.action.describe.config.description"),
        AllIcons.Actions.Preview
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            ApplicationManager.getApplication().executeOnPooledThread {
                val result = AtmosCommandRunner.describeConfig(project)
                ApplicationManager.getApplication().invokeLater {
                    tabbedPane.selectedIndex = 3 // Console tab
                    consoleView.clear()
                    if (result.success) {
                        consoleView.print(result.stdout, com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT)
                    } else {
                        consoleView.print(result.stderr, com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT)
                    }
                }
            }
        }
    }

    private inner class ValidateStacksAction : AnAction(
        AtmosBundle.message("toolwindow.action.validate.stacks"),
        AtmosBundle.message("toolwindow.action.validate.stacks.description"),
        AllIcons.Actions.Checked
    ) {
        override fun actionPerformed(e: AnActionEvent) {
            ApplicationManager.getApplication().executeOnPooledThread {
                val result = AtmosCommandRunner.validateStacks(project)
                ApplicationManager.getApplication().invokeLater {
                    tabbedPane.selectedIndex = 3 // Console tab
                    consoleView.clear()
                    if (result.success) {
                        consoleView.print("✓ All stacks are valid\n\n", com.intellij.execution.ui.ConsoleViewContentType.SYSTEM_OUTPUT)
                        consoleView.print(result.stdout, com.intellij.execution.ui.ConsoleViewContentType.NORMAL_OUTPUT)
                    } else {
                        consoleView.print("✗ Validation failed\n\n", com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT)
                        consoleView.print(result.stderr, com.intellij.execution.ui.ConsoleViewContentType.ERROR_OUTPUT)
                    }
                }
            }
        }
    }
}
