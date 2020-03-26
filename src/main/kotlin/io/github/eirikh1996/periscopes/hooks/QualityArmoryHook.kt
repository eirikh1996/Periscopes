package io.github.eirikh1996.periscopes.hooks

import io.github.eirikh1996.periscopes.Periscopes
import me.zombie_striker.qg.api.QAWeaponPrepareShootEvent
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

object QualityArmoryHook : Listener {

    @EventHandler
    fun onPreShoot(event : QAWeaponPrepareShootEvent) {
        for (periscope in Periscopes.instance.periscopes) {
            if (!event.player.equals(periscope.player))
                continue
            event.player.sendMessage(Periscopes.instance.PERISCOPES_PREFIX + Periscopes.instance.ERROR + "You cannot fire guns from a periscope")
            event.isCanceled = true
            break
        }
    }

}