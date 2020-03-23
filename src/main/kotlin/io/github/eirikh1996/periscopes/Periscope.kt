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

class Periscope constructor(var lookoutPoint : Location, var signLoc : Location) {
    val id = UUID.randomUUID()
    var player : Player? = null
    var originalLocation : Location? = null
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

    fun getHeight() : Int {
        return lookoutPoint.blockY - signLoc.blockY
    }

    override fun hashCode(): Int {
        return id.hashCode()
    }

    override fun equals(other: Any?): Boolean {
        if (!(other is Periscope)) {
            return false
        }
        return other.id.equals(id)

    }
}