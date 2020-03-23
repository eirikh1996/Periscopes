package io.github.eirikh1996.periscopes


import net.countercraft.movecraft.events.CraftRotateEvent
import net.countercraft.movecraft.events.CraftTranslateEvent
import net.countercraft.movecraft.utils.HitBox
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.block.data.type.WallSign
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.block.SignChangeEvent
import org.bukkit.event.entity.*
import org.bukkit.event.player.*
import org.bukkit.event.server.ServerEvent
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import org.yaml.snakeyaml.Yaml
import java.io.File
import java.io.FileInputStream

class Periscopes : JavaPlugin(), Listener {
    companion object {
        lateinit var instance : Periscopes;

    }
    val periscopes = HashSet<Periscope>()
    val PERISCOPES_PREFIX = "§2[§bPericopes§2] §3"
    val PERISCOPE_SIGN_TEXT = "§3Periscope"
    val ERROR = "§4Error: "
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

    override fun onEnable() {
        val packageName = server.javaClass.`package`.name
        val version = packageName.substring(packageName.lastIndexOf(".") + 1)
        Settings.isLegacy = version.split("_")[1].toInt() <= 12
        saveDefaultConfig()
        readConfig()
        server.pluginManager.registerEvents(this, this)
        server.scheduler.runTaskTimerAsynchronously(this,
            Runnable {
                for (periscope in periscopes) {
                    if (periscope.player == null) {
                        continue
                    }
                    server.scheduler.runTask(this,
                        Runnable {
                            val player = periscope.player!!
                            if (player.getPotionEffect(PotionEffectType.INVISIBILITY) != null)
                                player.removePotionEffect(PotionEffectType.INVISIBILITY)
                            player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 200, 1))
                        })

                }
            },
            0,
            60)
        server.pluginManager.registerEvents(UpdateManager, this)
        UpdateManager.start()
    }

    override fun onLoad() {
        instance = this
    }

    override fun onDisable() {
        for (periscope in periscopes) {
            if (periscope.player == null)
                continue
            periscope.player!!.teleport(periscope.originalLocation!!)
            for (player in server.onlinePlayers) {
                player.showPlayer(this, periscope.player!!)
            }
        }
    }

    @EventHandler
    fun onSignChange(event : SignChangeEvent) {
        if (!event.getLine(0).equals("[Periscope]", true)) {
            return
        }
        if (!event.player.hasPermission("periscopes.create")) {
            event.player.sendMessage(PERISCOPES_PREFIX + ERROR +" You have no permission to create periscopes")
            event.isCancelled = true
            return
        }
        val sign = event.block.state as Sign
        if (!isWallSign(event.block)) {
            event.player.sendMessage(PERISCOPES_PREFIX + ERROR +" Periscope sign must be a wall sign")
            event.isCancelled = true
            return
        }
        val attachedBlock = event.block.getRelative(getAttachment(sign))
        val attachedLoc = attachedBlock.location
        val attached = attachedBlock.type
        if (!Settings.allowedPeriscopeBlocks.contains(attached)) {
            event.player.sendMessage(PERISCOPES_PREFIX + ERROR + "Invalid periscope block " + attached.name.toLowerCase() + ". Valid periscope blocks are " + Settings.allowedPeriscopeBlocks.toString())
            event.isCancelled = true
            return
        }
        for (test in periscopes) {
            if (!test.contains(attachedLoc)) {
                continue
            }
            event.player.sendMessage(PERISCOPES_PREFIX + ERROR + "Block is already part of a periscope")
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
            event.player.sendMessage(PERISCOPES_PREFIX + ERROR + "Periscope is too tall. Max height is " + Settings.maxPeriscopeHeight)
            event.isCancelled = true
            return
        }
        if (Settings.minPeriscopeHeight > -1 && periscope.getHeight() < Settings.minPeriscopeHeight) {
            event.player.sendMessage(PERISCOPES_PREFIX + ERROR + "Periscope is too low. Min height is " + Settings.minPeriscopeHeight)
            event.isCancelled = true
            return
        }
        event.setLine(0, PERISCOPE_SIGN_TEXT)
        periscopes.add(periscope)

    }

    @EventHandler
    fun onInteract(event: PlayerInteractEvent) {
        if (event.action == Action.RIGHT_CLICK_BLOCK) {
            if (!(event.clickedBlock!!.state is Sign)) {
                return
            }
            val sign = event.clickedBlock!!.state as Sign
            if (!sign.getLine(0).equals(PERISCOPE_SIGN_TEXT)) {
                return
            }
            val user = event.player
            if (!user.hasPermission("periscopes.use")) {
                event.player.sendMessage(PERISCOPES_PREFIX + ERROR + "You have no permission to use periscopes")
                return
            }
            var periscope : Periscope? = null
            for (test in periscopes) {
                if (!test.signLoc.equals(sign.location)) {
                    continue
                }
                periscope = test
            }
            if (periscope == null) {
                if (!isWallSign(event.clickedBlock!!)) {
                    return
                }
                val attachedBlock = event.clickedBlock!!.getRelative(getAttachment(sign))
                val attachedLoc = attachedBlock.location
                val attached = attachedBlock.type
                if (!Settings.allowedPeriscopeBlocks.contains(attached)) {
                    event.player.sendMessage(PERISCOPES_PREFIX + ERROR + "Invalid periscope block " + attached.name.toLowerCase() + ". Valid periscope blocks are " + Settings.allowedPeriscopeBlocks.toString())
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
                    event.player.sendMessage(PERISCOPES_PREFIX + ERROR + "Periscope is too tall. Max height is " + Settings.maxPeriscopeHeight)
                    event.isCancelled = true
                    return
                }
                if (Settings.minPeriscopeHeight > -1 && periscope.getHeight() < Settings.minPeriscopeHeight) {
                    event.player.sendMessage(PERISCOPES_PREFIX + ERROR + "Periscope is too low. Min height is " + Settings.minPeriscopeHeight)
                    event.isCancelled = true
                    return
                }
                periscopes.add(periscope)
            }
            if (periscope.player != null && !periscope.player!!.equals(event.player)) {
                event.player.sendMessage(PERISCOPES_PREFIX + ERROR + "This periscope is occupied")
                return
            }
            sign.setLine(2, event.player.name)
            sign.update()
            event.player.addPotionEffect(PotionEffect(PotionEffectType.INVISIBILITY, 200, 1))
            periscope.player = event.player
            periscope.originalLocation = event.player.location
            periscope.lookoutPoint.yaw = event.player.location.yaw
            periscope.lookoutPoint.pitch = event.player.location.pitch
            event.player.teleport(periscope.lookoutPoint)
            for (player in server.onlinePlayers) {
                player.hidePlayer(this, event.player)
            }
        } else if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.LEFT_CLICK_AIR) {
            //Prevent players using periscopes from interacting.
            //This will also prevent abuse on servers with gun plugins (such as CrackShot or QualityArmory)
            var periscope : Periscope? = null
            for (test in periscopes) {
                if (test.player == null || !test.player!!.equals(event.player)) {
                    continue
                }
                periscope = test
                break
            }
            if (periscope == null)
                return
            event.isCancelled = true
        }


    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerMove(event : PlayerMoveEvent) {
        val periscope = getPeriscope(event.player)
        if (periscope == null) {
            return
        }
        val to = event.to!!
        val lookoutPoint = periscope.lookoutPoint
        if (to.x != lookoutPoint.x || to.y != lookoutPoint.y || to.z != lookoutPoint.z) {
            event.isCancelled = true
        }
    }

    @EventHandler
    fun onEntityExplode(event : EntityExplodeEvent) {
        for (block in event.blockList()) {
            val iter = periscopes.iterator()
            while (iter.hasNext()) {
                val periscope = iter.next()
                if (!periscope.contains(block.location)) {
                    continue
                }
                iter.remove()
            }
        }
    }

    @EventHandler
    fun onBlockExplode(event : BlockExplodeEvent) {
        for (block in event.blockList()) {
            val iter = periscopes.iterator()
            while (iter.hasNext()) {
                val periscope = iter.next()
                if (!periscope.contains(block.location)) {
                    continue
                }
                iter.remove()
            }
        }
    }

    @EventHandler
    fun onBlockBreak(event : BlockBreakEvent) {
        val iter = periscopes.iterator()
        while (iter.hasNext()) {
            val periscope = iter.next()
            if (!periscope.contains(event.block.location)) {
                continue
            }
            iter.remove()
        }
    }

    @EventHandler
    fun onPlayerSneak(event : PlayerToggleSneakEvent) {
        if (!event.isSneaking)
            return

        for (periscope in periscopes) {
            if (!event.player.equals(periscope.player)) {
                continue
            }
            event.player.teleport(periscope.originalLocation!!)
            event.player.removePotionEffect(PotionEffectType.INVISIBILITY)
            periscope.player = null
            val sign = periscope.signLoc.block.state as Sign
            sign.setLine(2, "")
            sign.update()
            for (player in server.onlinePlayers) {
                player.showPlayer(this, event.player)
            }
            break
        }
    }

    @EventHandler
    fun onPlayerJoin(event : PlayerJoinEvent) {
        for (periscope in periscopes) {
            if (periscope.player == null) {
                continue
            }
            event.player.hidePlayer(this, periscope.player!!)
        }
    }

    @EventHandler
    fun onCraftRotate(event : CraftRotateEvent) {
        if (event.isCancelled) {
            return
        }
        for (periscope in getPeriscopesInHitbox(event.oldHitBox)) {
            periscope.rotate(event.originPoint, event.rotation)
        }
    }

    @EventHandler
    fun onCraftTranslate(event: CraftTranslateEvent) {
        if (event.isCancelled) {
            return
        }
        val displacement = event.newHitBox.midPoint.subtract(event.oldHitBox.midPoint)
        for (periscope in getPeriscopesInHitbox(event.oldHitBox)) {
            periscope.translate(displacement)
        }
    }

    private fun getPeriscopesInHitbox(hitbox : HitBox) : Collection<Periscope> {
        val periscopes = HashSet<Periscope>()
        for (periscope in this.periscopes) {
            if (!hitbox.containsAll(periscope.asHitBox().asSet())) {
                continue
            }
            periscopes.add(periscope);
        }
        return periscopes
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerQuit(event : PlayerQuitEvent) {
        for (periscope in periscopes) {
            if (periscope.player == null) {
                continue
            }
            event.player.showPlayer(this, periscope.player!!)
        }
    }

    @EventHandler
    fun onBowShoot(event : EntityShootBowEvent) {
        if (event.entity !is Player)
            return

        val player = event.entity as Player
        for (test in periscopes) {
            if (!player.equals(test.player))
                continue

            event.isCancelled = true
            break
        }
    }

    @EventHandler
    fun onEntityDamage(event : EntityDamageEvent) {
        if (event is EntityDamageByBlockEvent) {
            event.isCancelled = true
        } else if (event is EntityDamageByEntityEvent) {
            for (periscope in periscopes) {
                if (event.damager.equals(periscope.player) || event.entity.equals(periscope.player)) {
                    event.isCancelled = true
                    break
                }
            }
        }
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

    private fun getAttachment(sign : Sign): BlockFace {
        if (Settings.isLegacy) {
            val signData = sign.data as org.bukkit.material.Sign
            return signData.attachedFace
        }
        val signData = sign.blockData as WallSign
        return signData.facing.oppositeFace
    }

    private fun getPeriscope(player : Player) : Periscope? {
        for (periscope in periscopes) {
            if (!player.equals(periscope.player))
                continue
            return periscope
        }
        return null
    }

    override fun saveDefaultConfig() {
        if (Settings.isLegacy) {
            val config = File(dataFolder, "config.yml")
            if (config.exists())
                return
            else if (!dataFolder.exists())
                dataFolder.mkdirs()
            saveResource("legacyconfig.yml", false)
            val legacyConfigFile = File(dataFolder, "legacyconfig.yml")
            legacyConfigFile.renameTo(config)
            return
        }
        super.saveDefaultConfig()
    }

    fun readConfig() {
        val periscopeMaterials = config.getStringList("Allowed periscope materials")
        for (str in periscopeMaterials) {
            val type = Material.getMaterial(str.toUpperCase())
            if (type == null) {
                logger.severe("Invalid type: " + str)
                continue
            }
            Settings.allowedPeriscopeBlocks.add(type)
        }
        Settings.maxPeriscopeHeight = config.getInt("Max periscope height", -1)
        Settings.minPeriscopeHeight = config.getInt("Min periscope height", -1)
    }

    override fun onCommand(sender: CommandSender, command: Command, label: String, args: Array<out String>): Boolean {
        if (!command.name.equals("periscopes", true)) {
            return false
        }
        if (args.size == 0) {
            sender.sendMessage("§2=============[§bPericopes§2]=============")
            sender.sendMessage("§3Author: " + description.authors)
            sender.sendMessage("§3Version: " + description.version)
            sender.sendMessage("§3Page: https://dev.bukkit.org/projects/periscopes")
            sender.sendMessage("§3Update: " + if (UpdateManager.update.length == 0) "You are up to date" else ("Update " + UpdateManager.update + " is available. You are on " + description.version))
        } else if (args[0].equals("reload")) {
            if (!sender.hasPermission("periscopes.reload")) {
                sender.sendMessage(PERISCOPES_PREFIX + ERROR + "You don't have permission to do this")
                return true
            }
            readConfig()
            sender.sendMessage(PERISCOPES_PREFIX + "Config reloaded successfully")
        }

        return true
    }
}