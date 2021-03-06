package io.github.eirikh1996.periscopes

import com.google.gson.Gson
import org.bukkit.Bukkit
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.PlayerJoinEvent
import org.bukkit.scheduler.BukkitRunnable
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.URL

object UpdateManager : BukkitRunnable(), Listener {
    var update = ""

    init {
        runTaskTimerAsynchronously(Periscopes.instance, 0, 100000)
    }

    override fun run() {
        val newVersion = newUpdateAvailable()
        Periscopes.instance.getLogger().info("Checking for updates")
        object : BukkitRunnable() {
            override fun run() {
                if (newVersion != null) {
                    for (p in Bukkit.getOnlinePlayers()) {
                        if (!p.hasPermission("periscopes.update")) {
                            p.sendMessage(Periscopes.instance.PERISCOPES_PREFIX + "An update of Periscopes is now available. Download from https://dev.bukkit.org/projects/periscopes")
                        }
                    }
                    Periscopes.instance.getLogger().warning("An update of Periscopes is available")
                    update = newVersion
                    return
                }
                Periscopes.instance.getLogger().info("You are up to date")
            }
        }.runTaskLaterAsynchronously(Periscopes.instance, 100)
    }

    @EventHandler
    fun onJoin(event: PlayerJoinEvent) {
        if (!event.player.hasPermission("periscopes.update"))
            return
        object : BukkitRunnable() {
            override fun run() {
                val newVer: String = newUpdateAvailable() ?: return
                event.player
                    .sendMessage(Periscopes.instance.PERISCOPES_PREFIX + "An update of Periscopes is now available. Download from https://dev.bukkit.org/projects/periscopes")
            }
        }.runTaskLaterAsynchronously(Periscopes.instance, 60)
    }

    fun newUpdateAvailable(): String? {
        try {
            val url = URL("https://servermods.forgesvc.net/servermods/files?projectids=369203")
            val conn = url.openConnection()
            conn.readTimeout = 5000
            conn.addRequestProperty("User-Agent", "Periscopes Update Checker")
            conn.doOutput = true
            val reader = BufferedReader(InputStreamReader(conn.getInputStream()))
            val response = reader.readLine()
            val gson = Gson()
            val objList =
                gson.fromJson<List<*>>(response, MutableList::class.java)
            if (objList.size == 0) {
                Periscopes.instance.getLogger().warning("No files found, or Feed URL is bad.")
                return null
            }
            val data =
                objList[objList.size - 1] as Map<String, Any>
            val versionName = data["name"] as String?
            val currVersion = Periscopes.instance.getDescription().getVersion().replace("v", "")
            val newVersion = versionName!!.substring(versionName.lastIndexOf("v") + 1)
            val cv = currVersion.split(".")[0].toInt() * 1000 + currVersion.split(".")[1].toInt()
            val nv = newVersion.split(".")[0].toInt() * 1000 + newVersion.split(".")[1].toInt()
            if (nv > cv)
                return newVersion
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return null
    }

}