package io.github.eirikh1996.periscopes

import net.countercraft.movecraft.MovecraftLocation
import net.countercraft.movecraft.Rotation
import net.countercraft.movecraft.utils.HashHitBox
import net.countercraft.movecraft.utils.HitBox
import net.countercraft.movecraft.utils.MathUtils
import org.bukkit.Location
import org.bukkit.entity.Player
import org.bukkit.util.Vector
import java.util.*

import kotlinx.coroutines.*
import org.bukkit.Bukkit
import org.bukkit.OfflinePlayer
import org.bukkit.block.Sign
import org.bukkit.event.EventHandler
import org.bukkit.event.EventPriority
import org.bukkit.event.HandlerList
import org.bukkit.event.Listener
import org.bukkit.event.block.Action
import org.bukkit.event.block.BlockBreakEvent
import org.bukkit.event.block.BlockExplodeEvent
import org.bukkit.event.entity.*
import org.bukkit.event.player.*
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType

class Periscope constructor(var lookoutPoint : Location, var signLoc : Location) : Listener {
    val id = UUID.randomUUID()
    var player : Player? = null
    var originalLocation : Location? = null
    var originalWalkSpeed : Float = 0f

    init {
        Bukkit.getPluginManager().registerEvents(this, Periscopes.instance)
    }
    fun translate(displacement : MovecraftLocation) {
        lookoutPoint = lookoutPoint.add(movecraftLocationToVector(displacement))
        signLoc = signLoc.add(movecraftLocationToVector(displacement))
        if (originalLocation == null)
            return
        originalLocation!!.add(movecraftLocationToVector(displacement))
    }

    fun rotate(originPoint : MovecraftLocation, rotation: Rotation) {

        lookoutPoint = MathUtils.rotateVec(rotation, MathUtils.bukkit2MovecraftLoc(lookoutPoint).subtract(originPoint)).add(originPoint).toBukkit(lookoutPoint.world).add(0.5, 0.5, 0.5)
        signLoc = MathUtils.rotateVec(rotation, MathUtils.bukkit2MovecraftLoc(signLoc).subtract(originPoint)).add(originPoint).toBukkit(signLoc.world)
        if (originalLocation == null)
            return
        originalLocation = MathUtils.rotateVec(rotation, MathUtils.bukkit2MovecraftLoc(originalLocation!!).subtract(originPoint)).add(originPoint).toBukkit(originalLocation!!.world).add(0.5, 0.5, 0.5)
    }

    private fun movecraftLocationToVector(movecraftLocation: MovecraftLocation) : Vector {
        return Vector(movecraftLocation.x, movecraftLocation.y, movecraftLocation.z)
    }

    fun contains(loc : Location) : Boolean {
        val attachedLoc = Location(loc.world, lookoutPoint.x, signLoc.y, lookoutPoint.z)
        return signLoc.equals(loc) || loc.blockX == lookoutPoint.blockX && loc.blockZ == lookoutPoint.blockZ &&
                (loc.blockY >= attachedLoc.blockY && loc.blockY <= lookoutPoint.blockY)
    }

    fun asHitBox() : HitBox {
        val hitBox = HashHitBox()
        val minY = signLoc.blockY;
        val maxY = lookoutPoint.blockY;
        for (y in minY..maxY-1) {
            hitBox.add(MovecraftLocation(lookoutPoint.blockX, y, lookoutPoint.blockZ))
        }
        hitBox.add(MathUtils.bukkit2MovecraftLoc(signLoc))
        return hitBox
    }

    fun mount(player: Player) {
        val sign = signLoc.block.state as Sign
        sign.setLine(2, player.name)
        sign.update()
        this.player = player
        this.originalLocation = player.location
        lookoutPoint.yaw = player.location.yaw
        lookoutPoint.pitch = player.location.pitch
        originalWalkSpeed = player.walkSpeed
        player.walkSpeed = 0F
        player.teleport(lookoutPoint)
        for (op in Bukkit.getOnlinePlayers()) {
            op.hidePlayer(Periscopes.instance, player)
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onSneak(event : PlayerToggleSneakEvent) {
        if (!event.isSneaking)
            return
        if (!event.player.equals(player))
            return
        demountPlayer()
    }

    @EventHandler(priority = EventPriority.MONITOR)
    fun onPlayerMove(event : PlayerMoveEvent) {
        if (!event.player.equals(player))
            return
        val to = event.to!!
        if (to.x != lookoutPoint.x || to.y != lookoutPoint.y || to.z != lookoutPoint.z) {
            event.isCancelled = true
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    fun onPlayerJoin(event : PlayerJoinEvent) {
        if (player == null)
            return
        event.player.hidePlayer(Periscopes.instance, player!!)
    }

    @EventHandler(priority = EventPriority.HIGH)
    fun onPlayerInteract(event : PlayerInteractEvent) {
        val cb = event.clickedBlock
        if (event.action == Action.RIGHT_CLICK_BLOCK) {
            if (cb!!.state !is Sign) {
                return
            }
            val sign = cb.state as Sign
            if (sign.getLine(0).equals(Settings.periscopeSignText)) {
                return
            }
            val main = Periscopes.instance
            if (player != null && !player!!.equals(event.player)) {
                event.player.sendMessage(main.PERISCOPES_PREFIX + main.ERROR + "This periscope is occupied")
                return
            }
            mount(event.player)
        } else if (event.action == Action.RIGHT_CLICK_AIR || event.action == Action.LEFT_CLICK_AIR) {
            //Prevent players using periscopes from interacting.
            //This will also prevent abuse on servers with gun plugins (such as CrackShot or QualityArmory)
            if (!event.player.equals(player)) {
                return
            }
            event.isCancelled = true
        }

    }


    @EventHandler
    fun onBowShoot(event : EntityShootBowEvent) {
        if (event.entity !is Player)
            return

        val player = event.entity as Player
        for (test in Periscopes.instance.periscopes) {
            if (!player.equals(test.player))
                continue

            event.isCancelled = true
            break
        }
    }

    @EventHandler
    fun onEntityDamage(event : EntityDamageEvent) {
        if (event is EntityDamageByBlockEvent && event.entity.equals(player)) {
            event.isCancelled = true
        } else if (event is EntityDamageByEntityEvent) {
                if (event.damager.equals(player) || event.entity.equals(player)) {
                    event.isCancelled = true

                }
        }
    }

    @EventHandler
    fun onQuit(event : PlayerQuitEvent) {
        if (!event.player.equals(player))
            return
        event.player.walkSpeed = originalWalkSpeed
        demountPlayer()
    }

    @EventHandler
    fun onEntityExplode(event : EntityExplodeEvent) {
        for (block in event.blockList()) {
            if (!contains(block.location)) {
                continue
            }
            demountPlayer()
            remove()
        }
    }

    @EventHandler
    fun onBlockExplode(event : BlockExplodeEvent) {
        for (block in event.blockList()) {
            if (!contains(block.location)) {
                continue
            }
            demountPlayer()
            remove()
        }
    }

    @EventHandler
    fun onBlockBreak(event : BlockBreakEvent) {
        if (!contains(event.block.location))
            return
        demountPlayer()
        remove()

    }

    fun demountPlayer() {
        if (player == null)
            return
        player!!.teleport(originalLocation!!)
        this.originalLocation = null
        for (op in Bukkit.getOnlinePlayers()) {
            op.showPlayer(Periscopes.instance, player!!)
        }
        val sign = signLoc.block.state as Sign
        sign.setLine(2, "")
        sign.update()
        player!!.walkSpeed = originalWalkSpeed
        player = null
    }

    fun getHeight() : Int {
        return lookoutPoint.blockY - signLoc.blockY
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    private fun remove() {
        HandlerList.unregisterAll(this)
        Periscopes.instance.periscopes.remove(this)
    }

    override fun equals(other: Any?): Boolean {
        if (!(other is Periscope)) {
            return false
        }
        return other.id.equals(id)

    }
}