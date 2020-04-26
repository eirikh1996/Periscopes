package io.github.eirikh1996.periscopes.hooks

import io.github.eirikh1996.periscopes.Periscope
import io.github.eirikh1996.periscopes.Periscopes
import net.countercraft.movecraft.events.CraftRotateEvent
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
        val oldHitBox : HitBox
        try {
            val getOldHitBox = CraftRotateEvent::class.java.getDeclaredMethod("getOldHitBox")
            oldHitBox = getOldHitBox.invoke(event) as HitBox
        } catch (e : Exception) {
            return
        }
        for (periscope in getPeriscopesInHitbox(oldHitBox, event.craft.w)) {
            periscope.rotate(event.originPoint, event.rotation)
        }
    }

    @EventHandler
    fun onCraftTranslate(event: CraftTranslateEvent) {
        if (event.isCancelled) {
            return
        }
        val oldHitBox : HitBox
        val newHitBox : HitBox
        try {
            val getOldHitBox = CraftTranslateEvent::class.java.getDeclaredMethod("getOldHitBox")
            val getNewHitBox = CraftTranslateEvent::class.java.getDeclaredMethod("getNewHitBox")
            oldHitBox = getOldHitBox.invoke(event) as HitBox
            newHitBox = getNewHitBox.invoke(event) as HitBox
        } catch (e : Exception) {
            return
        }
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