package com.cloudposse.atmos

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

    // Inheritance icons (will use standard IntelliJ icons for now)
    @JvmField
    val INHERIT_UP: Icon = IconLoader.getIcon("/icons/atmos.svg", AtmosIcons::class.java)

    @JvmField
    val INHERIT_DOWN: Icon = IconLoader.getIcon("/icons/atmos.svg", AtmosIcons::class.java)
}
