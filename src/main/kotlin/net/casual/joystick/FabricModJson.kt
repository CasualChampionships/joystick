package net.casual.joystick

import com.google.gson.GsonBuilder
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.util.zip.ZipFile
import java.util.zip.ZipInputStream

internal object FabricModJson {
    private const val FILE_NAME = "fabric.mod.json"
    private const val NESTED_PREFIX = "META-INF/jars/"

    private val gson = GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create()

    fun readModIdAndVersion(jar: File): Pair<String, String>? {
        ZipFile(jar).use { zip ->
            val entry = zip.getEntry(FILE_NAME) ?: return null
            val json = zip.getInputStream(entry).reader().use { JsonParser.parseReader(it).asJsonObject }
            val id = json.get("id")?.asString ?: return null
            val version = json.get("version")?.asString ?: return null
            return id to version
        }
    }

    fun readBundledModIds(jar: File): Set<String> {
        val ids = LinkedHashSet<String>()
        ZipFile(jar).use { zip ->
            for (entry in zip.entries()) {
                if (isNestedJar(entry.name)) {
                    zip.getInputStream(entry).use { collectBundledModIds(it, ids) }
                }
            }
        }
        return ids
    }

    private fun collectBundledModIds(jar: InputStream, ids: MutableSet<String>) {
        ZipInputStream(jar).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                if (entry.name == FILE_NAME) {
                    val json = JsonParser.parseReader(zip.reader()).asJsonObject
                    json.get("id")?.asString?.let(ids::add)
                } else if (isNestedJar(entry.name)) {
                    collectBundledModIds(ByteArrayInputStream(zip.readBytes()), ids)
                }
            }
        }
    }

    private fun isNestedJar(name: String): Boolean {
        return name.startsWith(NESTED_PREFIX) && name.endsWith(".jar") && !name.removePrefix(NESTED_PREFIX).contains('/')
    }

    fun addDependencies(file: File, dependencies: Map<String, String>) {
        if (dependencies.isEmpty()) {
            return
        }
        val json = file.reader().use { JsonParser.parseReader(it).asJsonObject }
        val depends = json.getAsJsonObject("depends") ?: JsonObject()
        json.add("depends", depends)
        for ((id, constraint) in dependencies) {
            if (!depends.has(id)) {
                depends.addProperty(id, constraint)
            }
        }
        file.writeText(this.gson.toJson(json) + "\n")
    }
}
