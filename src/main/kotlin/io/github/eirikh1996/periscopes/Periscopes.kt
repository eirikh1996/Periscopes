package io.github.eirikh1996.periscopes


import com.shampaggon.crackshot.CSDirector
import io.github.eirikh1996.periscopes.hooks.CrackShotHook
import io.github.eirikh1996.periscopes.hooks.MovecraftHook
import io.github.eirikh1996.periscopes.hooks.QualityArmoryHook
import io.github.eirikh1996.periscopes.listener.MainListener
import me.zombie_striker.qg.QAMain
import net.countercraft.movecraft.Movecraft
import net.milkbowl.vault.economy.Economy
import org.bukkit.Bukkit
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.Sign
import org.bukkit.block.data.type.WallSign
import org.bukkit.command.Command
import org.bukkit.command.CommandSender
import org.bukkit.configuration.InvalidConfigurationException
import org.bukkit.configuration.file.YamlConfiguration
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
import org.bukkit.plugin.java.JavaPlugin
import org.bukkit.potion.PotionEffect
import org.bukkit.potion.PotionEffectType
import org.bukkit.scheduler.BukkitRunnable
import org.bukkit.util.Vector
import java.io.File
import java.io.IOException

class Periscopes : JavaPlugin(), Listener {
    companion object {
        lateinit var instance : Periscopes;

    }
    var movecraftPlugin : Movecraft? = null
    var economy : Economy? = null
    var QAplugin : QAMain? = null
    var csPlugin : CSDirector? = null
    val periscopes = HashSet<Periscope>()
    val PERISCOPES_PREFIX = "§2[§bPericopes§2] §3"
    val ERROR = "§4Error: "


    override fun onEnable() {
        val packageName = server.javaClass.`package`.name
        val version = packageName.substring(packageName.lastIndexOf(".") + 1)
        Settings.isLegacy = version.split("_")[1].toInt() <= 12
        saveDefaultConfig()
        updateConfig()
        readConfig()

        server.pluginManager.registerEvents(MainListener, this)
        val movecraft = server.pluginManager.getPlugin("Movecraft")
        if (movecraft is Movecraft) {
            logger.info("Movecraft detected. Hooking into it now")
            server.pluginManager.registerEvents(MovecraftHook, this)
            movecraftPlugin = movecraft
        }
        //QualityArmory
        val qa = server.pluginManager.getPlugin("QualityArmory")
        if (qa is QAMain) {
            logger.info("QualityArmory detected. Hooking into it now")
            server.pluginManager.registerEvents(QualityArmoryHook, this)
            QAplugin = qa
        }
        //CrackShot
        val cs = server.pluginManager.getPlugin("CrackShot")
        if (cs is CSDirector) {
            logger.info("CrackShot detected. Hooking into it now")
            server.pluginManager.registerEvents(CrackShotHook, this)
            csPlugin = cs
        }
        //Vault
        if (server.pluginManager.getPlugin("Vault") != null) {
            val rsp = server.servicesManager.getRegistration(Economy::class.java)
            if (rsp != null) {
                economy = rsp.provider
                logger.info("Vault and a compatible economy plugin detected. Hooking into it now")
            } else {
                logger.warning("Vault was detected, but no compatible economy plugin. Install an economy plugin that uses Vault to use economy")
            }
        }
        server.pluginManager.registerEvents(UpdateManager, this)
    }

    override fun onLoad() {
        instance = this
    }

    override fun onDisable() {
        for (periscope in periscopes) {
            periscope.demountPlayer()
        }
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
        Settings.periscopeSignText = config.getString("Periscope sign text", "§3Periscope")!!
        Settings.periscopeCreateCost = config.getDouble("Periscope create cost", 1000.0)
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

    fun updateConfig() {
        val cfg = YamlConfiguration()
        cfg.load(getResource(if (!Settings.isLegacy) "config.yml" else "legacyconfig.yml")!!.reader())
        try {
            if (File(getDataFolder().toString() + "/config.yml").exists()) {
                var changesMade = false
                val tmp = YamlConfiguration()
                tmp.load(getDataFolder().toString() + "/config.yml")
                for (str in cfg.getKeys(true)) {
                    if (!tmp.getKeys(true).contains(str)) {
                        tmp[str!!] = cfg.get(str)
                        changesMade = true
                    }
                }
                if (changesMade) tmp.save(getDataFolder().toString() + "/config.yml")
            }
        } catch (e: IOException) {
            e.printStackTrace()
        } catch (e: InvalidConfigurationException) {
            e.printStackTrace()
        }
    }
}