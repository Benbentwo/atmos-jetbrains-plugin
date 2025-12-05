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
    val STACK_FILE: Icon = AllIcons.FileTypes.Yaml

    @JvmField
    val STACK_FOLDER: Icon = AllIcons.Nodes.Folder

    @JvmField
    val COMPONENT: Icon = IconLoader.getIcon("/icons/atmos.svg", AtmosIcons::class.java)

    @JvmField
    val ABSTRACT_COMPONENT: Icon = AllIcons.Nodes.AbstractClass

    @JvmField
    val REAL_COMPONENT: Icon = AllIcons.Nodes.Class

    // Inheritance icons (will use standard IntelliJ icons for now)
    @JvmField
    val INHERIT_UP: Icon = AllIcons.Gutter.OverridingMethod

    @JvmField
    val INHERIT_DOWN: Icon = AllIcons.Gutter.OverridenMethod

    @JvmField
    val IMPORT: Icon = AllIcons.Nodes.Include

    // Run configuration icons - using standard IntelliJ icons with fallback to Atmos icon
    @JvmField
    val RUN: Icon = AllIcons.Actions.Execute

    @JvmField
    val RUN_CONFIGURATION: Icon = IconLoader.getIcon("/icons/atmos.svg", AtmosIcons::class.java)

    @JvmField
    val TERRAFORM: Icon = IconLoader.getIcon("/icons/atmos.svg", AtmosIcons::class.java)

    @JvmField
    val WORKFLOW: Icon = AllIcons.Actions.Execute

    @JvmField
    val REFRESH: Icon = AllIcons.Actions.Refresh

    @JvmField
    val VALID: Icon = AllIcons.General.InspectionsOK

    @JvmField
    val VARIABLE: Icon = AllIcons.Nodes.Variable

    @JvmField
    val SETTINGS: Icon = AllIcons.General.Settings

    @JvmField
    val METADATA: Icon = AllIcons.Nodes.Tag
}
