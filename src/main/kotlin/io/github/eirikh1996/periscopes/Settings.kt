package io.github.eirikh1996.periscopes

import org.bukkit.Material

object Settings {
    var allowedPeriscopeBlocks = HashSet<Material>()
    var periscopePassthroughBlocks = HashMap<Material, Double>()
    val allowedCommandsOnPeriscopes = ArrayList<String>()
    var isLegacy = false
    var maxPeriscopeHeight = -1
    var minPeriscopeHeight = -1
    var periscopeSignText = "§3Periscope"
    var periscopeCreateCost = -1.0
}