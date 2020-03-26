package io.github.eirikh1996.periscopes.listener

import io.github.eirikh1996.periscopes.Periscope
import io.github.eirikh1996.periscopes.Periscopes
import io.github.eirikh1996.periscopes.Settings
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.block.data.type.WallSign
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.SignChangeEvent
import org.bukkit.event.entity.EntityExplodeEvent
import org.bukkit.event.player.PlayerInteractEvent
import org.bukkit.potion.PotionEffectType
import org.bukkit.util.Vector

object MainListener : Listener {

    val FENCES = HashSet<Material>()

    init {
        for (type in Material.values()) {
            if (type.name.startsWith("IRON"))
                continue
            if (!type.name.endsWith("_FENCE") && !type.name.endsWith("_WALL") && !type.name.equals("FENCE")) {
                continue
            }
            FENCES.add(type)
        }
    }

    @EventHandler
    fun onSignChange(event : SignChangeEvent) {
        val periscopes = Periscopes.instance.periscopes
        if (!event.getLine(0).equals("[Periscope]", true)) {
            return
        }
        if (!event.player.hasPermission("periscopes.create")) {
            event.player.sendMessage(Periscopes.instance.PERISCOPES_PREFIX + Periscopes.instance.ERROR +" You have no permission to create periscopes")
            event.isCancelled = true
            return
        }

        val sign = event.block.state as Sign
        if (!isWallSign(event.block)) {
            event.player.sendMessage(Periscopes.instance.PERISCOPES_PREFIX + Periscopes.instance.ERROR +" Periscope sign must be a wall sign")
            event.isCancelled = true
            return
        }
        val attachedBlock = event.block.getRelative(getAttachment(sign))
        val attachedLoc = attachedBlock.location
        val attached = attachedBlock.type
        if (!Settings.allowedPeriscopeBlocks.contains(attached)) {
            event.player.sendMessage(Periscopes.instance.PERISCOPES_PREFIX + Periscopes.instance.ERROR + "Invalid periscope block " + attached.name.toLowerCase() + ". Valid periscope blocks are " + Settings.allowedPeriscopeBlocks.toString())
            event.isCancelled = true
            return
        }
        for (test in periscopes) {
            if (!test.contains(attachedLoc)) {
                continue
            }
            event.player.sendMessage(Periscopes.instance.PERISCOPES_PREFIX + Periscopes.instance.ERROR + "Block is already part of a periscope")
            event.isCancelled = true
            return
        }
        var topLoc = attachedLoc
        do {
            topLoc = topLoc.add(Vector(0, 1, 0))
        } while (Settings.allowedPeriscopeBlocks.contains(topLoc.block.type))
        val below = topLoc.block.getRelative(BlockFace.DOWN).type
        topLoc.add(0.5, if (FENCES.contains(below)) 0.5 else 0.0, 0.5)
        val periscope = Periscope(topLoc, event.block.location)
        if (Settings.maxPeriscopeHeight > -1 && periscope.getHeight() > Settings.maxPeriscopeHeight) {
            event.player.sendMessage(Periscopes.instance.PERISCOPES_PREFIX + Periscopes.instance.ERROR + "Periscope is too tall. Max height is " + Settings.maxPeriscopeHeight)
            event.isCancelled = true
            return
        }
        if (Settings.minPeriscopeHeight > -1 && periscope.getHeight() < Settings.minPeriscopeHeight) {
            event.player.sendMessage(Periscopes.instance.PERISCOPES_PREFIX + Periscopes.instance.ERROR + "Periscope is too low. Min height is " + Settings.minPeriscopeHeight)
            event.isCancelled = true
            return
        }
        var msg = Periscopes.instance.PERISCOPES_PREFIX + "Periscope created! "
        if (Periscopes.instance.economy != null) {
            val eco = Periscopes.instance.economy!!
            if (Settings.periscopeCreateCost > -1.0 && !eco.has(event.player, Settings.periscopeCreateCost)) {
                event.player.sendMessage(Periscopes.instance.PERISCOPES_PREFIX + Periscopes.instance.ERROR + "You cannot afford to create a periscope, which costs " + Settings.periscopeCreateCost)
                event.isCancelled = true
                return
            }
            eco.withdrawPlayer(event.player, Settings.periscopeCreateCost)
            msg += "Withdrew " + Settings.periscopeCreateCost + "from your balance"
        }
        event.player.sendMessage(msg)
        event.setLine(0, Settings.periscopeSignText)
        periscopes.add(periscope)

    }

    @EventHandler(priority = EventPriority.NORMAL)
    fun onInteract(event: PlayerInteractEvent) {
        val periscopes = Periscopes.instance.periscopes
        if (event.action == Action.RIGHT_CLICK_BLOCK) {
            if (!(event.clickedBlock!!.state is Sign)) {
                return
            }
            val sign = event.clickedBlock!!.state as Sign
            if (!sign.getLine(0).equals(Settings.periscopeSignText)) {
                return
            }
            val user = event.player
            if (!user.hasPermission("periscopes.use")) {
                event.player.sendMessage(Periscopes.instance.PERISCOPES_PREFIX + Periscopes.instance.ERROR + "You have no permission to use periscopes")
                return
            }
            var periscope : Periscope? = null
            for (test in periscopes) {
                if (!test.signLoc.equals(sign.location)) {
                    continue
                }
                periscope = test
            }
            if (periscope != null) {
                return
            }
            if (!isWallSign(event.clickedBlock!!)) {
                return
            }
            val attachedBlock = event.clickedBlock!!.getRelative(getAttachment(sign))
            val attachedLoc = attachedBlock.location
            val attached = attachedBlock.type
            if (!Settings.allowedPeriscopeBlocks.contains(attached)) {
                event.player.sendMessage(Periscopes.instance.PERISCOPES_PREFIX + Periscopes.instance.ERROR + "Invalid periscope block " + attached.name.toLowerCase() + ". Valid periscope blocks are " + Settings.allowedPeriscopeBlocks.toString())
                event.isCancelled = true
                return
            }
            var topLoc = attachedLoc
            do {
                topLoc = topLoc.add(Vector(0, 1, 0))
            } while (Settings.allowedPeriscopeBlocks.contains(topLoc.block.type))
            val below = topLoc.block.getRelative(BlockFace.DOWN).type
            topLoc.add(0.5, if (FENCES.contains(below)) 0.5 else 0.0, 0.5)
            periscope = Periscope(topLoc, event.clickedBlock!!.location)
            if (Settings.maxPeriscopeHeight > -1 && periscope.getHeight() > Settings.maxPeriscopeHeight) {
                event.player.sendMessage(Periscopes.instance.PERISCOPES_PREFIX + Periscopes.instance.ERROR + "Periscope is too tall. Max height is " + Settings.maxPeriscopeHeight)
                event.isCancelled = true
                return
            }
            if (Settings.minPeriscopeHeight > -1 && periscope.getHeight() < Settings.minPeriscopeHeight) {
                event.player.sendMessage(Periscopes.instance.PERISCOPES_PREFIX + Periscopes.instance.ERROR + "Periscope is too low. Min height is " + Settings.minPeriscopeHeight)
                event.isCancelled = true
                return
            }
            periscopes.add(periscope)
            periscope.mount(event.player)


        }


    }


    @EventHandler
    fun onEntityExplode(event : EntityExplodeEvent) {
        for (block in event.blockList()) {
            val iter = Periscopes.instance.periscopes.iterator()
            while (iter.hasNext()) {
                val periscope = iter.next()
                if (!periscope.contains(block.location)) {
                    continue
                }
                periscope.demountPlayer()
                iter.remove()
            }
        }
    }

    @EventHandler
    fun onBlockExplode(event : BlockExplodeEvent) {
        for (block in event.blockList()) {
            val iter = Periscopes.instance.periscopes.iterator()
            while (iter.hasNext()) {
                val periscope = iter.next()
                if (!periscope.contains(block.location)) {
                    continue
                }
                periscope.demountPlayer()
                iter.remove()
            }
        }
    }

    @EventHandler
    fun onBlockBreak(event : BlockBreakEvent) {
        val iter = Periscopes.instance.periscopes.iterator()
        while (iter.hasNext()) {
            val periscope = iter.next()
            if (!periscope.contains(event.block.location)) {
                continue
            }
            periscope.demountPlayer()
            iter.remove()
        }
    }


    private fun getAttachment(sign : Sign): BlockFace {
        if (Settings.isLegacy) {
            val signData = sign.data as org.bukkit.material.Sign
            return signData.attachedFace
        }
        val signData = sign.blockData as WallSign
        return signData.facing.oppositeFace
    }

    private fun isWallSign(block : Block) : Boolean {
        if (Settings.isLegacy) {
            val data = block.state.data
            if (data !is org.bukkit.material.Sign) {
                return false
            }
            val sign = data
            return sign.isWallSign
        }
        val data = block.blockData
        return data is WallSign


    }
}