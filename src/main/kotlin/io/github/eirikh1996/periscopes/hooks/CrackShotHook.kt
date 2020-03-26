package io.github.eirikh1996.periscopes.hooks

import com.shampaggon.crackshot.events.WeaponPreShootEvent
import io.github.eirikh1996.periscopes.Periscopes
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener

object CrackShotHook : Listener {

    @EventHandler
    fun onPreShoot(event : WeaponPreShootEvent) {
        for (periscope in Periscopes.instance.periscopes) {
            if (!event.player.equals(periscope.player))
                continue
            event.player.sendMessage(Periscopes.instance.PERISCOPES_PREFIX + Periscopes.instance.ERROR + "You cannot fire guns from a periscope")
            event.isCancelled = true
            break
        }
    }
}