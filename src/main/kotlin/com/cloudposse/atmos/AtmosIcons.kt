package com.cloudposse.atmos

import com.intellij.icons.AllIcons
import com.intellij.openapi.util.IconLoader
import javax.swing.Icon

object AtmosIcons {
    @JvmField
    val ATMOS: Icon = IconLoader.getIcon("/icons/atmos.svg", AtmosIcons::class.java)

    @JvmField
    val TOOL_WINDOW: Icon = IconLoader.getIcon("/icons/atmos.svg", AtmosIcons::class.java)

    @JvmField
    val STACK: Icon = IconLoader.getIcon("/icons/atmos.svg", AtmosIcons::class.java)

    @JvmField
    val COMPONENT: Icon = IconLoader.getIcon("/icons/atmos.svg", AtmosIcons::class.java)

    // Inheritance icons - using standard IntelliJ icons
    @JvmField
    val INHERIT_UP: Icon = AllIcons.Gutter.OverridingMethod

    @JvmField
    val INHERIT_DOWN: Icon = AllIcons.Gutter.OverridenMethod

    // Additional icons for completion and navigation
    @JvmField
    val STACK_FILE: Icon = AllIcons.FileTypes.Yaml

    @JvmField
    val STACK_FOLDER: Icon = AllIcons.Nodes.Folder

    @JvmField
    val METADATA: Icon = AllIcons.Nodes.Tag

    @JvmField
    val SETTINGS: Icon = AllIcons.General.Settings

    @JvmField
    val IMPORT: Icon = AllIcons.ToolbarDecorator.Import

    @JvmField
    val VARIABLE: Icon = AllIcons.Nodes.Variable

    @JvmField
    val WORKFLOW: Icon = AllIcons.Actions.Execute

    @JvmField
    val TERRAFORM: Icon = AllIcons.Nodes.Module

    @JvmField
    val ABSTRACT_COMPONENT: Icon = AllIcons.Nodes.AbstractClass

    @JvmField
    val REAL_COMPONENT: Icon = AllIcons.Nodes.Class

    @JvmField
    val WARNING: Icon = AllIcons.General.Warning

    @JvmField
    val ERROR: Icon = AllIcons.General.Error

    @JvmField
    val VALID: Icon = AllIcons.General.InspectionsOK

    @JvmField
    val REFRESH: Icon = AllIcons.Actions.Refresh
}
