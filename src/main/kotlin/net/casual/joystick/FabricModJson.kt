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

    fun readModIdAndVersion(artifact: File): Pair<String, String>? {
        val json = readModJson(artifact) ?: return null
        val id = json.get("id")?.asString ?: return null
        val version = json.get("version")?.asString ?: return null
        return id to version
    }

    fun readBundledModIds(artifact: File): Set<String> {
        val ids = LinkedHashSet<String>()
        if (artifact.isDirectory) {
            val jars = artifact.resolve(NESTED_PREFIX).listFiles() ?: return ids
            for (jar in jars.sorted()) {
                if (jar.isFile && jar.extension == "jar") {
                    jar.inputStream().use { collectBundledModIds(it, ids) }
                }
            }
            return ids
        }
        if (!isArchive(artifact)) {
            return ids
        }
        ZipFile(artifact).use { zip ->
            for (entry in zip.entries()) {
                if (isNestedJar(entry.name)) {
                    zip.getInputStream(entry).use { collectBundledModIds(it, ids) }
                }
            }
        }
        return ids
    }

    private fun readModJson(artifact: File): JsonObject? {
        if (artifact.isDirectory) {
            val file = artifact.resolve(FILE_NAME)
            if (!file.isFile) {
                return null
            }
            return file.reader().use { JsonParser.parseReader(it).asJsonObject }
        }
        if (!isArchive(artifact)) {
            return null
        }
        ZipFile(artifact).use { zip ->
            val entry = zip.getEntry(FILE_NAME) ?: return null
            return zip.getInputStream(entry).reader().use { JsonParser.parseReader(it).asJsonObject }
        }
    }

    private fun isArchive(artifact: File): Boolean {
        return artifact.isFile && (artifact.extension == "jar" || artifact.extension == "zip")
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
