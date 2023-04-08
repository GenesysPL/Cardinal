package github.gilbertokpl.core.internal.inventory.serializator

import br.com.finalcraft.evernifecore.itemstack.FCItemFactory
import br.com.finalcraft.evernifecore.itemstack.itembuilder.FCBaseItemBuilder
import br.com.finalcraft.evernifecore.itemstack.itembuilder.FCItemBuilder
import github.gilbertokpl.core.internal.serializator.InternalBukkitObjectInputStream
import github.gilbertokpl.core.internal.serializator.InternalBukkitObjectOutputStream
import github.gilbertokpl.total.config.files.MainConfig
import org.bukkit.Material
import org.bukkit.inventory.ItemStack
import org.bukkit.util.io.BukkitObjectInputStream
import org.bukkit.util.io.BukkitObjectOutputStream
import org.yaml.snakeyaml.external.biz.base64Coder.Base64Coder
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream


class ItemSerializer {

    fun serialize(items: MutableList<ItemStack>): String {
        if (items.isEmpty()) return ""

        var toReturn = ""

        if (MainConfig.generalCustomItemStack) {
            try {
                for (i in items) {
                    val data = FCItemBuilder(i)

                    var itemConfig = ""

                    for (configs in data.toDataPart()) {
                        if (itemConfig == "") {
                            itemConfig = configs.replace(",", "+").replace("|", "-")
                        }
                        else {
                            itemConfig += "|" + configs.replace(",", "+").replace("|", "-")
                        }
                    }

                    if (toReturn == "") {
                        toReturn = itemConfig
                    }
                    else {
                        toReturn += ",$itemConfig"
                    }
                }
            } catch (e: Throwable) {
                e.printStackTrace()
            }
            return toReturn
        }

        try {
            val outputStream = ByteArrayOutputStream()
            val dataOutput = try {
                BukkitObjectOutputStream(outputStream)
            } catch (e: NoClassDefFoundError) {
                InternalBukkitObjectOutputStream(outputStream)
            }
            dataOutput.writeInt(items.size)
            for (i in items.indices) {
                dataOutput.writeObject(items[i])
            }
            dataOutput.close()
            toReturn = Base64Coder.encodeLines(outputStream.toByteArray())
        } catch (ignored: Throwable) {
        }
        return toReturn
    }

    fun deserialize(data: String): ArrayList<ItemStack> {
        if (data == "") return ArrayList()

        val toReturn: ArrayList<ItemStack> = ArrayList()

        if (MainConfig.generalCustomItemStack) {
            try {
                val dataValue = data.split(",")

                for (item in dataValue) {
                    try {
                        val itemData = item.split("|")
                        val newItemData = ArrayList<String>()
                        for (ni in itemData) {
                            newItemData.add(ni.replace("+", ",").replace("-", "|"))
                        }
                        toReturn.add(FCItemFactory.from(newItemData).build())
                    } catch (ignored: Throwable) {
                    }
                }

            } catch (ignored: Throwable) {
            }
            return toReturn
        }

        try {
            val inputStream = ByteArrayInputStream(Base64Coder.decodeLines(data))
            val dataInput = try {
                BukkitObjectInputStream(inputStream)
            } catch (e: NoClassDefFoundError) {
                InternalBukkitObjectInputStream(inputStream)
            }
            val items = arrayOfNulls<ItemStack>(dataInput.readInt())
            for (i in items.indices) {
                items[i] = dataInput.readObject() as ItemStack?
            }
            dataInput.close()
            for (i in items.filterNotNull()) {
                toReturn.add(i)
            }
        } catch (ignored: Throwable) {
        }
        return toReturn
    }
}