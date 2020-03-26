package io.github.eirikh1996.periscopes.hooks

import io.github.eirikh1996.periscopes.Periscope
import io.github.eirikh1996.periscopes.Periscopes
import net.countercraft.movecraft.events.CraftRotateEvent
import net.countercraft.movecraft.events.CraftTranslateEvent
import net.countercraft.movecraft.utils.HitBox
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

object MovecraftHook : Listener {


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
        for (periscope in Periscopes.instance.periscopes) {
            if (!hitbox.containsAll(periscope.asHitBox().asSet())) {
                continue
            }
            periscopes.add(periscope);
        }
        return periscopes
    }
}