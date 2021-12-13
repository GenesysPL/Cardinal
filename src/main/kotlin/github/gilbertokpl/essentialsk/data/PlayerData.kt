package github.gilbertokpl.essentialsk.data

import github.gilbertokpl.essentialsk.EssentialsK
import github.gilbertokpl.essentialsk.configs.MainConfig
import github.gilbertokpl.essentialsk.tables.PlayerDataSQL
import github.gilbertokpl.essentialsk.tables.PlayerDataSQL.FakeNick
import github.gilbertokpl.essentialsk.tables.PlayerDataSQL.KitsTime
import github.gilbertokpl.essentialsk.tables.PlayerDataSQL.PlayerInfo
import github.gilbertokpl.essentialsk.tables.PlayerDataSQL.SavedHomes
import github.gilbertokpl.essentialsk.util.*
import org.apache.commons.lang3.exception.ExceptionUtils
import org.bukkit.GameMode
import org.bukkit.Location
import org.jetbrains.exposed.sql.Column
import org.jetbrains.exposed.sql.insert
import org.jetbrains.exposed.sql.select
import org.jetbrains.exposed.sql.transactions.transaction
import org.jetbrains.exposed.sql.update
import java.util.concurrent.CompletableFuture


class PlayerData(player: String) {

    private val p = EssentialsK.instance.server.getPlayer(player)

    private val online = p != null

    private val uuid = if (p != null) {
        PluginUtil.getInstance().getPlayerUUID(p)
    } else {
        CompletableFuture.supplyAsync({
            PluginUtil.getInstance().getPlayerUUID(EssentialsK.instance.server.getOfflinePlayer(player))
        }, TaskUtil.getInstance().getExecutor()).get()
    }

    fun unloadCache() {
        Dao.getInstance().playerCache.remove(uuid)
    }

    fun loadCache() {
        TaskUtil.getInstance().asyncExecutor {
            //values
            val cacheKits = HashMap<String, Long>(40)
            val cacheHomes = HashMap<String, Location>(40)
            val limitHome: Int =
                PluginUtil.getInstance().getNumberPermission(
                    p!!,
                    "essentialsk.commands.sethome.",
                    MainConfig.getInstance().homesDefaultLimitHomes
                )
            var fakeNick = ""
            var gameMode = 0
            var vanish = false

            //internal
            lateinit var homesList: String
            lateinit var timeKits: String
            var emptyQuery = false

            transaction(SqlUtil.getInstance().sql) {
                PlayerDataSQL.select { PlayerInfo eq uuid }.also { query ->
                    emptyQuery = query.empty()
                    if (emptyQuery) {
                        PlayerDataSQL.insert {
                            it[PlayerInfo] = this@PlayerData.uuid
                        }
                        return@transaction
                    }
                    timeKits = query.single()[KitsTime]
                    fakeNick = query.single()[FakeNick]
                    homesList = query.single()[SavedHomes]
                    gameMode = query.single()[PlayerDataSQL.GameMode]
                    vanish = query.single()[PlayerDataSQL.Vanish]
                }
            }

            if (emptyQuery) {
                Dao.getInstance().playerCache[uuid] = InternalPlayerData(
                    uuid,
                    HashMap(40),
                    HashMap(40),
                    limitHome,
                    fakeNick,
                    0,
                    false
                )
                return@asyncExecutor
            }

            var replace = ""
            var update = false

            for (kits in timeKits.split("|")) {
                try {
                    if (kits == "") continue
                    val split = kits.split(".")
                    val timeKit = split[1].toLong()
                    val nameKit = split[0]

                    val kitsCache = KitData(nameKit)

                    if (kitsCache.checkCache()) {
                        update = true
                        continue
                    }

                    val timeAll = KitData(nameKit).getCache(true)

                    if (timeKit != 0L && timeAll != null && (timeAll.time + timeKit) > System.currentTimeMillis()) {
                        replace += if (replace == "") {
                            "$nameKit,$timeKit"
                        } else {
                            "|$nameKit,$timeKit"
                        }
                        cacheKits[nameKit] = timeKit
                        continue
                    }
                } catch (ignored: Exception) {
                    update = true
                }
            }

            //nick
            if (fakeNick != "") {
                p.setDisplayName(fakeNick)
            }

            //gamemode
            val gameModeName: GameMode = when (gameMode) {
                0 -> GameMode.SURVIVAL
                1 -> GameMode.CREATIVE
                2 -> try {
                    GameMode.ADVENTURE
                } catch (e: Exception) {
                    GameMode.SURVIVAL
                }
                3 -> try {
                    GameMode.SPECTATOR
                } catch (e: Exception) {
                    GameMode.SURVIVAL
                }
                else -> GameMode.SURVIVAL
            }
            if (p.gameMode != gameModeName) {
                p.gameMode = gameModeName
            }

            //home
            for (h in homesList.split("|")) {
                if (h == "") continue
                val split = h.split(",")
                val locationHome = LocationUtil.getInstance().locationSerializer(split[1])
                val nameHome = split[0]
                cacheHomes[nameHome] = locationHome
            }

            //vanish
            if (vanish) {
                ReflectUtil.getInstance().getPlayers().forEach {
                    it.hidePlayer(p)
                }
            }

            //cache is in final to improve protection to load caches
            Dao.getInstance().playerCache[uuid] = InternalPlayerData(
                uuid,
                cacheKits,
                cacheHomes,
                limitHome,
                fakeNick,
                gameMode,
                vanish
            )

            if (update) {
                transaction(SqlUtil.getInstance().sql) {
                    PlayerDataSQL.update {
                        it[KitsTime] = replace
                    }
                }
            }
        }
    }

    fun checkSql(): Boolean {
        return if (online) {
            true
        } else {
            CompletableFuture.supplyAsync({
                try {
                    var check = false
                    transaction(SqlUtil.getInstance().sql) {
                        check = PlayerDataSQL.select { PlayerInfo eq uuid }.empty()
                    }
                    return@supplyAsync !check
                } catch (ex: Exception) {
                    FileLoggerUtil.getInstance().logError(ExceptionUtils.getStackTrace(ex))
                }
                return@supplyAsync false
            }, TaskUtil.getInstance().getExecutor()).get()
        }
    }

    //only online
    fun setGamemode(gm: GameMode, value: Int) {
        if (online) {
            p!!.gameMode = gm
            val cache = getCache() ?: return
            cache.Gamemode = value

            //sql
            helperUpdater(PlayerDataSQL.GameMode, value)
        }
    }

    fun checkVanish() : Boolean {
        if (online) {
            val cache = getCache() ?: return false

            return cache.Vanish
        }
        return false
    }

    //only online
    fun switchVanish() : Boolean {
        if (online) {
            val cache = getCache() ?: return false

            val newValue = when (cache.Vanish) {
                false -> true
                true -> false
            }

            cache.Vanish = newValue

            //sql
            helperUpdater(PlayerDataSQL.Vanish , newValue)

            return if (newValue) {
                ReflectUtil.getInstance().getPlayers().forEach {
                    it.hidePlayer(p!!)
                }
                true
            } else {
                ReflectUtil.getInstance().getPlayers().forEach {
                    it.showPlayer(p!!)
                }
                false
            }
        }
        return false
    }

    //only online
    fun delKitTime(kit: String) {
        if (online) {
            val cache = getCache()?.kitsCache ?: return
            cache.remove(kit)
            getCache()?.let { it.kitsCache = cache } ?: return
        }
    }

    fun setKitTime(kit: String, time: Long) {
        //cache
        if (online) {
            val cache = getCache()?.kitsCache ?: return
            cache.remove(kit)
            cache[kit] = time
            getCache()?.let { it.kitsCache = cache } ?: return
        }

        //sql
        TaskUtil.getInstance().asyncExecutor {

            var query = false
            lateinit var kitTime: String

            transaction(SqlUtil.getInstance().sql) {
                PlayerDataSQL.select { PlayerInfo eq uuid }.also {
                    query = it.empty()
                    kitTime = it.single()[KitsTime]
                }
            }

            if (query || kitTime == "") {
                transaction(SqlUtil.getInstance().sql) {
                    PlayerDataSQL.update({ PlayerInfo eq uuid }) {
                        it[KitsTime] = "$kit,$time"
                    }
                }
                return@asyncExecutor
            }
            val check = kitTime.split("|")
            var newPlace = ""
            for (i in check) {
                if (i.split(",")[0] != kit) {
                    if (newPlace == "") {
                        newPlace += i
                        continue
                    }
                    newPlace += "|$i"
                    continue
                }
            }
            newPlace += if (newPlace == "") {
                "$kit,${System.currentTimeMillis()}"
            } else {
                "-$kit,$time"
            }
            transaction(SqlUtil.getInstance().sql) {
                PlayerDataSQL.update({ PlayerInfo eq uuid }) {
                    it[KitsTime] = newPlace
                }
            }
        }
    }

    fun createHome(name: String, loc: Location) {
        //cache
        if (online) {
            val cache = getCache()?.homeCache ?: return
            cache[name] = loc
            getCache()?.let { it.homeCache = cache } ?: return
        }

        //sql
        TaskUtil.getInstance().asyncExecutor {
            lateinit var homes: String
            val serializedHome = LocationUtil.getInstance().locationSerializer(loc)
            var emptyQuery = false
            transaction(SqlUtil.getInstance().sql) {
                PlayerDataSQL.select { PlayerInfo eq uuid }.also { query ->
                    emptyQuery = query.empty()
                    if (emptyQuery) {
                        PlayerDataSQL.update({ PlayerInfo eq uuid }) {
                            it[SavedHomes] = "$name,$serializedHome"
                        }
                        return@transaction
                    }
                    homes = query.single()[SavedHomes]
                }
            }
            if (emptyQuery) return@asyncExecutor

            var newHome = "$name,$serializedHome"

            for (h in homes.split("|")) {
                if (h == "") continue
                newHome += "|$h"
            }

            transaction(SqlUtil.getInstance().sql) {
                PlayerDataSQL.update({ PlayerInfo eq uuid }) {
                    it[SavedHomes] = newHome
                }
            }
        }
    }

    fun delHome(name: String) {
        //cache
        if (online) {
            val cache = getCache()?.homeCache ?: return
            cache.remove(name)
            getCache()?.let { it.homeCache = cache } ?: return
        }

        //sql
        TaskUtil.getInstance().asyncExecutor {
            lateinit var homes: String
            transaction(SqlUtil.getInstance().sql) {
                PlayerDataSQL.select { PlayerInfo eq uuid }.also { query ->
                    homes = query.single()[SavedHomes]
                }
            }

            var newHome = ""

            for (h in homes.split("|")) {
                if (h.split(",")[0] == name) continue
                if (newHome == "") {
                    newHome += h
                    continue
                }
                newHome += "|$h"
            }

            transaction(SqlUtil.getInstance().sql) {
                PlayerDataSQL.update({ PlayerInfo eq uuid }) {
                    it[SavedHomes] = newHome
                }
            }
        }
    }

    fun getHomeList(): List<String> {
        return if (online) {
            getCache()?.let { get -> get.homeCache.map { it.key } } ?: return emptyList()
        } else {
            CompletableFuture.supplyAsync({
                lateinit var homesList: String
                val cacheHomes = ArrayList<String>()
                var bol = false
                transaction(SqlUtil.getInstance().sql) {
                    PlayerDataSQL.select { PlayerInfo eq uuid }.also { query ->
                        if (query.empty()) {
                            bol = true
                            return@transaction
                        }
                        homesList = query.single()[SavedHomes]
                    }
                }
                if (bol) {
                    return@supplyAsync emptyList()
                }
                for (h in homesList.split("|")) {
                    if (h == "") continue
                    val split = h.split(",")
                    val nameHome = split[0]
                    cacheHomes.add(nameHome)
                }
                return@supplyAsync cacheHomes
            }, TaskUtil.getInstance().getExecutor()).get()
        }
    }

    fun getLocationOfHome(home: String): Location? {
        return if (online) {
            val cache = getCache() ?: return null
            cache.homeCache[home.lowercase()]
        } else {
            CompletableFuture.supplyAsync({
                try {
                    lateinit var homesList: String
                    transaction(SqlUtil.getInstance().sql) {
                        PlayerDataSQL.select { PlayerInfo eq uuid }.also { query ->
                            homesList = query.single()[SavedHomes]
                        }
                    }
                    for (h in homesList.split("|")) {
                        if (h == "") continue
                        val split = h.split(",")
                        if (home.lowercase() == split[0]) {
                            return@supplyAsync LocationUtil.getInstance().locationSerializer(split[1])
                        }
                    }
                } catch (ex: Exception) {
                    FileLoggerUtil.getInstance().logError(ExceptionUtils.getStackTrace(ex))
                }
                return@supplyAsync null
            }, TaskUtil.getInstance().getExecutor()).get()
        }
    }

    fun setNick(newNick: String, other: Boolean = false): CompletableFuture<Boolean> {
        return CompletableFuture.supplyAsync({
            try {
                if (online && !other) {
                    var exist = false
                    transaction(SqlUtil.getInstance().sql) {
                        exist = PlayerDataSQL.select { FakeNick eq newNick }.empty()
                    }
                    if (!MainConfig.getInstance().nicksCanPlayerHaveSameNick) {
                        if (!exist) {
                            return@supplyAsync true
                        }
                    }
                }
                if (online && other) {
                    p!!.setDisplayName(newNick)
                    getCache()?.let { it.FakeNick = newNick } ?: return@supplyAsync true
                }
                transaction(SqlUtil.getInstance().sql) {
                    PlayerDataSQL.update({ PlayerInfo eq uuid }) {
                        it[FakeNick] = newNick
                    }
                }
            } catch (ex: Exception) {
                FileLoggerUtil.getInstance().logError(ExceptionUtils.getStackTrace(ex))
            }
            return@supplyAsync false
        }, TaskUtil.getInstance().getExecutor())
    }

    fun removeNick() {
        //cache
        if (online) {
            getCache()?.let { it.FakeNick = "" } ?: return
            p!!.setDisplayName(p.name)
        }

        //sql
        helperUpdater(FakeNick, "")
    }

    private fun <T> helperUpdater(col : Column<T>, value : T) {
        TaskUtil.getInstance().asyncExecutor {
            transaction(SqlUtil.getInstance().sql) {
                PlayerDataSQL.update({ PlayerInfo eq uuid }) {
                    it[col] = value
                }
            }
        }
    }


    fun getCache(): InternalPlayerData? {
        Dao.getInstance().playerCache[uuid].also {
            return it
        }
    }

    data class InternalPlayerData(
        val playerUUID: String,
        var kitsCache: HashMap<String, Long>,
        var homeCache: HashMap<String, Location>,
        var homeLimit: Int,
        var FakeNick: String,
        var Gamemode: Int,
        var Vanish: Boolean
    )
}