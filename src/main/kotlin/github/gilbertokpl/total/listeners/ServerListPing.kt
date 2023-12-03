package github.gilbertokpl.total.listeners

import github.gilbertokpl.total.TotalEssentialsJava
import github.gilbertokpl.total.config.files.MainConfig
import github.gilbertokpl.total.util.MainUtil
import github.gilbertokpl.total.util.PlayerUtil

import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.server.ServerListPingEvent

class ServerListPing : Listener {
    @EventHandler
    fun event(e: ServerListPingEvent) {
        if (MainConfig.motdEnabled) {
            try {
                motd(e)
            } catch (e: Throwable) {
                e.printStackTrace()
            }
        }
    }

    private fun motd(e: ServerListPingEvent) {
        val motd = if (TotalEssentialsJava.instance.server.hasWhitelist()) {
            MainUtil.getRandom(MainConfig.motdListMotdWhitelist).replace(
                "%players_online%",
                PlayerUtil.getIntOnlinePlayers(false).toString()
            ).replace("\\n", "\n")
        } else {
            MainUtil.getRandom(MainConfig.motdListMotd).replace(
                "%players_online%",
                PlayerUtil.getIntOnlinePlayers(false).toString()
            ).replace("\\n", "\n")
        }.replace("&", "§")

        e.motd = motd
    }
}
