package net.casual.joystick

import org.gradle.api.GradleException
import org.gradle.api.artifacts.dsl.DependencyHandler
import org.gradle.api.artifacts.result.ResolvedArtifactResult
import org.gradle.maven.MavenModule
import org.gradle.maven.MavenPomArtifact
import org.w3c.dom.Element
import java.io.File
import java.util.concurrent.ConcurrentHashMap
import javax.xml.parsers.DocumentBuilderFactory

internal class ArcadeModuleCatalogue(
    private val dependencies: DependencyHandler
) {
    private val cache = ConcurrentHashMap<String, Set<String>>()

    fun validate(group: String, version: String, modules: Set<String>) {
        val known = this.modules(group, version)
        val unknown = modules - known
        if (unknown.isEmpty()) {
            return
        }
        throw GradleException(
            "Unknown arcade module(s) ${unknown.sorted().joinToString()} for arcade $version. " +
                "Available modules: ${known.sorted().joinToString()}"
        )
    }

    fun modules(group: String, version: String): Set<String> {
        return this.cache.computeIfAbsent("$group:$version") { this.lookup(group, version) }
    }

    private fun lookup(group: String, version: String): Set<String> {
        val result = this.dependencies.createArtifactResolutionQuery()
            .forModule(group, AGGREGATE, version)
            .withArtifacts(MavenModule::class.java, MavenPomArtifact::class.java)
            .execute()
        val pom = result.resolvedComponents
            .flatMap { it.getArtifacts(MavenPomArtifact::class.java) }
            .filterIsInstance<ResolvedArtifactResult>()
            .firstOrNull()
            ?.file ?: throw GradleException(
                "Could not resolve arcade $version: no '$group:$AGGREGATE:$version' pom was found " +
                    "in the configured repositories"
            )
        return parseModules(pom, group)
    }

    private fun parseModules(pom: File, group: String): Set<String> {
        val document = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(pom)
        val dependencies = document.getElementsByTagName("dependency")
        val modules = LinkedHashSet<String>()
        for (i in 0 until dependencies.length) {
            val element = dependencies.item(i) as Element
            val groupId = element.getElementsByTagName("groupId").item(0)?.textContent
            val artifactId = element.getElementsByTagName("artifactId").item(0)?.textContent
            if (groupId == group && artifactId != null) {
                modules.add(artifactId)
            }
        }
        return modules
    }

    private companion object {
        const val AGGREGATE = "arcade"
    }
}
