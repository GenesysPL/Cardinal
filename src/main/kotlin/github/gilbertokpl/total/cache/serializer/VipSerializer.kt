package github.gilbertokpl.total.cache.serializer

import github.gilbertokpl.core.external.cache.convert.SerializerBase

class VipSerializer : SerializerBase<HashMap<String, Long>, String> {
    override fun convertToDatabase(hash: HashMap<String, Long>): String {
        return hash.entries.joinToString("|") { (key, value) ->
            "$key,$value"
        }
    }

    override fun convertToCache(value: String): HashMap<String, Long> {
        val hash = HashMap<String, Long>()
        for (entryString in value.split("|")) {
            val split = entryString.split(",")
            if (split.size < 2) continue
            hash[split[0]] = split[1].toLong()
        }
        return hash
    }
}