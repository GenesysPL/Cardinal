package io.github.gilbertodamim.kcore.config

import io.github.gilbertodamim.kcore.KCoreMain.disablePlugin
import io.github.gilbertodamim.kcore.KCoreMain.instance
import io.github.gilbertodamim.kcore.config.configs.DatabaseConfig
import io.github.gilbertodamim.kcore.config.configs.GeneralConfig.selectedLang
import io.github.gilbertodamim.kcore.config.configs.KitsConfig
import io.github.gilbertodamim.kcore.config.langs.GeneralLang
import io.github.gilbertodamim.kcore.config.langs.KitsLang
import io.github.gilbertodamim.kcore.config.langs.StartLang.*
import io.github.gilbertodamim.kcore.config.langs.TimeLang
import io.github.gilbertodamim.kcore.inventory.KitsInventory
import io.github.gilbertodamim.kcore.library.LibChecker
import io.github.gilbertodamim.kcore.management.ErrorClass
import io.github.gilbertodamim.kcore.management.Manager.consoleMessage
import io.github.gilbertodamim.kcore.management.Manager.pluginLangDir
import io.github.gilbertodamim.kcore.management.Manager.pluginPasteDir
import org.bukkit.configuration.file.YamlConfiguration
import java.io.File
import java.nio.file.*
import java.util.stream.Collectors

internal class ConfigMain {

    private var configList = ArrayList<YamlConfiguration>()

    private var langList = ArrayList<YamlConfiguration>()

    private lateinit var essentialsConfig: YamlConfiguration

    private lateinit var langConfig: YamlConfiguration

    fun start() {
        consoleMessage(startVerification.replace("%to%", "config"))
        essentialsConfig = kCoreConfig("KCoreMainConfig") ?: return
        reloadConfig(true)
        consoleMessage(completeVerification)
        consoleMessage(startVerification.replace("%to%", "lang"))
        reloadLang(true)
        consoleMessage(completeVerification)
    }

    private fun reloadHelper(list: List<YamlConfiguration>, dir: String) {
        for (to in list) {
            val check: YamlConfiguration
            try {
                check = YamlConfiguration.loadConfiguration(File(dir, "${to.name}.yml"))
            } catch (ex: Exception) {
                ErrorClass().sendException(ex)
                consoleMessage(problemReload.replace("%file%", to.name))
                to.save(to.currentPath)
                return
            }
            check.save(to.currentPath)
        }
    }

    private fun reloadLang(firstTime: Boolean = false) {
        if (firstTime) {
            val directoryStream: DirectoryStream<Path>? = Files.newDirectoryStream(
                FileSystems.newFileSystem(
                    Paths.get(instance.javaClass.protectionDomain.codeSource.location.toURI()),
                    instance.javaClass.classLoader
                ).getPath("/lang/")
            )
            if (directoryStream != null) {
                for (i in directoryStream) {
                    kCoreConfig(i.fileName.toString().replace(".yml", ""), true)
                }
            }
            try {
                val langSelected = File(pluginLangDir(), "$selectedLang.yml")
                langConfig = YamlConfiguration.loadConfiguration(langSelected)
                if (langSelected.exists()) {
                    consoleMessage(langSelectedMessage.replace("%lang%", selectedLang))
                } else {
                    consoleMessage(langError)
                }
            } catch (ex: Exception) {
                consoleMessage(langError)
                ErrorClass().sendException(ex)
            }
        } else {
            reloadHelper(langList, pluginLangDir())
        }
        try {
            LibChecker.reloadClass("Time", TimeLang().javaClass, langConfig, true)
            LibChecker.reloadClass("Kits", KitsLang().javaClass, langConfig, true)
            LibChecker.reloadClass("General", GeneralLang().javaClass, langConfig, true)
        } finally {
            if (KitsConfig.activated) {
                KitsInventory().editKitInventory()
            }
        }
    }

    private fun reloadConfig(firstTime: Boolean = false) {
        if (!firstTime) {
            reloadHelper(configList, pluginPasteDir())
        }
        LibChecker.reloadClass("Database", DatabaseConfig().javaClass, essentialsConfig, false)
        LibChecker.reloadClass("Kits", KitsConfig().javaClass, essentialsConfig, false)
    }

    private fun kCoreConfig(source: String, lang: Boolean = false): YamlConfiguration? {
        val location: String
        val dir: String
        val message: String
        fun putInList(to: YamlConfiguration) {
            if (lang) {
                langList.add(to)
                return
            }
            configList.add(to)
        }
        if (lang) {
            location = "/lang/$source.yml"
            dir = pluginLangDir()
            message = "lang"
        } else {
            location = "/$source.yml"
            dir = pluginPasteDir()
            message = "config"
        }
        try {
            val configFile = File(dir, "$source.yml")
            val resource = instance.javaClass.getResourceAsStream(location)
            if (configFile.exists()) {
                val configYaml = YamlConfiguration.loadConfiguration(configFile)
                if (resource != null) {
                    val checkFile = File(dir, "checker.yml")
                    if (checkFile.exists()) checkFile.delete()
                    Files.copy(resource, checkFile.toPath())
                    fun configHelper(toCheck: YamlConfiguration, checkerYaml: YamlConfiguration): Boolean {
                        for (fileKeys in checkerYaml.getKeys(true)) {
                            if (toCheck.get(fileKeys) == null) {
                                ConfigVersionChecker(configFile, checkFile)
                                return false
                            }
                        }
                        return true
                    }

                    val checkYaml = YamlConfiguration.loadConfiguration(checkFile)
                    if (configHelper(checkYaml, configYaml)) {
                        configHelper(configYaml, checkYaml)
                    }
                    checkFile.delete()
                    val newConfigYaml = YamlConfiguration.loadConfiguration(configFile)
                    putInList(newConfigYaml)
                    return newConfigYaml
                }
                return configYaml
            }
            File(dir).mkdirs()
            Files.copy(resource!!, configFile.toPath())
            consoleMessage(createMessage.replace("%to%", message).replace("%file%", configFile.name))
            val newConfigYaml = YamlConfiguration.loadConfiguration(configFile)
            putInList(newConfigYaml)
            return newConfigYaml
        } catch (ex: Exception) {
            consoleMessage(problemMessage.replace("%to%", message).replace("%file%", source))
            ErrorClass().sendException(ex)
            disablePlugin()
        }
        return null
    }

    fun getString(source: YamlConfiguration, path: String, color: Boolean = false): String {
        return if (color) {
            source.getString(path)!!.replace("&", "§")
        } else source.getString(path)!!
    }

    fun getStringList(source: YamlConfiguration, path: String, color: Boolean = false): List<String> {
        return if (color) {
            source.getStringList(path).stream().map { to -> to.replace("&", "§") }.collect(Collectors.toList())
        } else source.getStringList(path)
    }

    fun getInt(source: YamlConfiguration, path: String): Int = source.getInt(path)
    fun getBoolean(source: YamlConfiguration, path: String): Boolean = source.getBoolean(path)
}