package io.github.eirikh1996.periscopes.hooks

import io.github.eirikh1996.periscopes.Periscope
import io.github.eirikh1996.periscopes.Periscopes
import net.countercraft.movecraft.events.CraftRotateEvent
import net.countercraft.movecraft.events.CraftSinkEvent
import net.countercraft.movecraft.events.CraftTranslateEvent
import net.countercraft.movecraft.utils.HitBox
import org.bukkit.World
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

object MovecraftHook : Listener {


    @EventHandler
    fun onCraftRotate(event : CraftRotateEvent) {
        if (event.isCancelled) {
            return
        }
        val oldHitBox = event.oldHitBox
        for (periscope in getPeriscopesInHitbox(oldHitBox, event.craft.w)) {
            periscope.rotate(event.originPoint, event.rotation)
        }
    }

    @EventHandler
    fun onCraftTranslate(event: CraftTranslateEvent) {
        if (event.isCancelled || event.craft.sinking) {
            return
        }
        val oldHitBox = event.oldHitBox
        val newHitBox = event.newHitBox
        val displacement = newHitBox.midPoint.subtract(oldHitBox.midPoint)
        var world : World;
        try {
            world = CraftTranslateEvent::class.java.getDeclaredMethod("getWorld").invoke(event) as World
        } catch (e : Exception) {
            world = event.craft.w
        }

        for (periscope in getPeriscopesInHitbox(oldHitBox, world)) {
            periscope.translate(displacement, world)
        }
    }

    @EventHandler
    fun onCraftSink(event: CraftSinkEvent) {
        for (periscope in getPeriscopesInHitbox(event.craft.hitBox, event.craft.w)) {
            periscope.demountPlayer()
            periscope.remove()
        }
    }

    private fun getPeriscopesInHitbox(hitbox : HitBox, world : World) : Collection<Periscope> {
        val periscopes = HashSet<Periscope>()
        for (periscope in Periscopes.instance.periscopes) {
            if (!periscope.world.equals(world))
                continue
            if (!hitbox.containsAll(periscope.asHitBox().asSet())) {
                continue
            }
            periscopes.add(periscope);
        }
        return periscopes
    }
}