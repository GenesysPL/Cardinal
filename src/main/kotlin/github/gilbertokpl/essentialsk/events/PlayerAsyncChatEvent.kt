package github.gilbertokpl.essentialsk.events

import github.gilbertokpl.essentialsk.configs.GeneralLang
import github.gilbertokpl.essentialsk.configs.MainConfig
import github.gilbertokpl.essentialsk.data.Dao
import github.gilbertokpl.essentialsk.data.KitData
import github.gilbertokpl.essentialsk.util.FileLoggerUtil
import github.gilbertokpl.essentialsk.util.PermissionUtil
import github.gilbertokpl.essentialsk.util.PluginUtil
import github.gilbertokpl.essentialsk.util.TimeUtil
import org.apache.commons.lang3.exception.ExceptionUtils
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.player.AsyncPlayerChatEvent

class PlayerAsyncChatEvent : Listener {
    @EventHandler
    fun event(e: AsyncPlayerChatEvent) {
        if (MainConfig.getInstance().kitsActivated) {
            try {
                if (editKitChatEvent(e)) return
            } catch (e: Exception) {
                FileLoggerUtil.getInstance().logError(ExceptionUtils.getStackTrace(e))
            }
        }
        if (MainConfig.getInstance().addonsColorInChat) {
            try {
                colorChat(e)
            } catch (e: Exception) {
                FileLoggerUtil.getInstance().logError(ExceptionUtils.getStackTrace(e))
            }
        }
    }

    private fun editKitChatEvent(e: AsyncPlayerChatEvent): Boolean {
        Dao.getInstance().editKitChat[e.player].also {
            if (it == null) return false
            Dao.getInstance().editKitChat.remove(e.player)
            val split = it.split("-")

            val dataInstance = KitData(split[1])

            //time
            if (split[0] == "time") {
                e.isCancelled = true
                val time = TimeUtil.getInstance().convertStringToMillis(e.message)
                e.player.sendMessage(GeneralLang.getInstance().generalSendingInfoToDb)
                if (dataInstance.setTime(time)) {
                    e.player.sendMessage(
                        GeneralLang.getInstance().kitsEditKitTime.replace(
                            "%time%",
                            TimeUtil.getInstance()
                                .convertMillisToString(time, MainConfig.getInstance().kitsUseShortTime)
                        )
                    )
                    e.player.sendMessage(GeneralLang.getInstance().kitsEditKitSuccess.replace("%kit%", split[1]))
                }
                return true
            }

            //name
            if (split[0] == "name") {
                e.isCancelled = true
                //check message length
                if (e.message.length > 16) {
                    e.player.sendMessage(GeneralLang.getInstance().kitsNameLength)
                    return true
                }

                e.player.sendMessage(GeneralLang.getInstance().generalSendingInfoToDb)
                if (dataInstance.setFakeName(e.message.replace("&", "§"))) {
                    e.player.sendMessage(GeneralLang.getInstance().kitsEditKitSuccess.replace("%kit%", split[1]))
                }
            }

            return true
        }
    }

    private fun colorChat(e: AsyncPlayerChatEvent) {
        e.message = PermissionUtil.getInstance().colorPermission(e.player, e.message)
    }
}