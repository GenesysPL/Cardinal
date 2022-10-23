package github.gilbertokpl.total.commands

import github.gilbertokpl.base.external.command.CommandTarget
import github.gilbertokpl.base.external.command.annotations.CommandPattern
import github.gilbertokpl.total.config.files.LangConfig
import github.gilbertokpl.total.config.files.MainConfig
import github.gilbertokpl.total.cache.PlayerData
import github.gilbertokpl.total.util.MainUtil
import github.gilbertokpl.total.util.PermissionUtil
import org.bukkit.command.CommandSender
import org.bukkit.entity.Player

class CommandSetHome : github.gilbertokpl.base.external.command.CommandCreator("sethome") {

    override fun commandPattern(): CommandPattern {
        return CommandPattern(
            aliases = listOf("setarhome"),
            active = MainConfig.homesActivated,
            target = CommandTarget.PLAYER,
            countdown = 0,
            permission = "essentialsk.commands.sethome",
            minimumSize = 1,
            maximumSize = 1,
            usage = listOf(
                "/sethome <homeName>",
                "essentialsk.commands.sethome.other_/sethome <playername>:<homeName>"
            )
        )
    }

    override fun funCommand(s: CommandSender, label: String, args: Array<out String>): Boolean {

        val nameHome = args[0].lowercase()

        //admin
        if (args[0].contains(":") && s.hasPermission("essentialsk.commands.sethome.other")) {
            val split = args[0].split(":")

            val pName = split[0]

            if (!PlayerData.checkIfPlayerExist(pName)) {
                s.sendMessage(LangConfig.generalPlayerNotExist)
                return false
            }

            val homes = PlayerData.homeCache[pName]!!

            if (split.size < 2) {
                s.sendMessage(
                    LangConfig.homesOtherList.replace("%player%", pName)
                        .replace("%list%", homes.map { it.key }.toString())
                )
                return false
            }

            if (homes.contains(split[1])) {
                s.sendMessage(LangConfig.homesNameAlreadyExist)
                return false
            }

            homes[split[1]] = (s as Player).location

            s.sendMessage(
                LangConfig.homesOtherCreated.replace("%player%", pName)
                    .replace("%home%", split[1])
            )

            return false
        }

        //check if home name do not contain . or - to not bug
        if (MainUtil.checkSpecialCaracteres(nameHome)) {
            s.sendMessage(LangConfig.generalSpecialCaracteresDisabled)
            return false
        }

        //check length of home name
        if (nameHome.length > 16) {
            s.sendMessage(LangConfig.homesNameLength)
            return false
        }

        val homes = PlayerData.homeCache[s as Player]!!

        //check if already exist
        if (homes.contains(nameHome)) {
            s.sendMessage(LangConfig.homesNameAlreadyExist)
            return false
        }

        //update limit
        if (!s.hasPermission("essentialsk.commands.sethome." + PlayerData.homeLimitCache[s])) {
            PlayerData.homeLimitCache[s] = PermissionUtil.getNumberPermission(
                s,
                "essentialsk.commands.sethome.",
                MainConfig.homesDefaultLimitHomes
            )
        }

        //check limit of homes
        if (PlayerData.homeCache[s]!!.size >= PlayerData.homeLimitCache[s]!! &&
            !s.hasPermission("essentialsk.bypass.homelimit")
        ) {
            s.sendMessage(
                LangConfig.homesLimitMessage.replace(
                    "%limit%",
                    PlayerData.homeLimitCache[s].toString()
                )
            )
            return false
        }

        //check if world is blocked
        if (MainConfig.homesBlockWorlds.contains(s.world.name.lowercase()) && !s.hasPermission(
                "essentialsk.bypass.homeblockedworlds"
            )
        ) {
            s.sendMessage(LangConfig.homesBlockedWorld)
            return false
        }

        PlayerData.homeCache[s] = hashMapOf(nameHome to s.location)
        s.sendMessage(LangConfig.homesCreated.replace("%home%", nameHome))

        return false
    }
}
