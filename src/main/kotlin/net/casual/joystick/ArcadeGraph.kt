package net.casual.joystick

import org.gradle.api.GradleException
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.artifacts.result.ResolvedComponentResult
import org.gradle.api.artifacts.result.ResolvedDependencyResult
import org.gradle.api.artifacts.result.UnresolvedDependencyResult

internal data class ArcadeComponent(val name: String, val version: String, val path: List<String>) {
    val coordinate: String
        get() = "$name:$version"
}

internal fun collectArcadeComponents(
    root: ResolvedComponentResult,
    group: String
): Map<String, ArcadeComponent> {
    val found = LinkedHashMap<String, ArcadeComponent>()
    val visited = HashSet<ResolvedComponentResult>()
    val queue = ArrayDeque<Pair<ResolvedComponentResult, List<String>>>()
    queue.add(root to emptyList())
    visited.add(root)

    while (queue.isNotEmpty()) {
        val (component, path) = queue.removeFirst()
        for (dependency in component.dependencies) {
            if (dependency is UnresolvedDependencyResult) {
                throw GradleException("Could not resolve ${dependency.attempted.displayName}", dependency.failure)
            }
            if (dependency !is ResolvedDependencyResult) {
                continue
            }
            val selected = dependency.selected
            if (!visited.add(selected)) {
                continue
            }
            val id = selected.id
            val next = path + selected.id.displayName
            if (id is ModuleComponentIdentifier && id.group == group) {
                found.putIfAbsent(id.module, ArcadeComponent(id.module, id.version, path))
            }
            queue.add(selected to next)
        }
    }
    return found
}
