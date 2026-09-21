package net.casual.joystick

import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.provider.MapProperty
import org.gradle.api.provider.SetProperty
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.TaskAction
import org.gradle.work.DisableCachingByDefault

@DisableCachingByDefault(because = "Verification task without outputs")
public abstract class VerifyArcadeModulesTask: DefaultTask() {
    @get:Input
    public abstract val declared: SetProperty<String>

    @get:Input
    public abstract val compileClasspath: MapProperty<String, String>

    @get:Input
    public abstract val bundled: MapProperty<String, String>

    @TaskAction
    public fun verify() {
        val declared = this.declared.get()
        val bundled = this.bundled.get()
        val undeclared = this.compileClasspath.get().filterKeys { it !in declared }
        val (provided, missing) = undeclared.entries.sortedBy { it.key }.partition { it.key in bundled }

        for ((module, via) in provided) {
            this.logger.warn(
                "Arcade module $module is on the compile classpath via $via and is only available at runtime " +
                    "because ${bundled.getValue(module)} bundles it; declare it with arcade { modules(...) } " +
                    "to control the version"
            )
        }
        if (missing.isEmpty()) {
            return
        }
        val lines = missing.joinToString("\n") { (module, via) -> "  - $module (via $via)" }
        throw GradleException(
            "The following arcade modules are on the compile classpath but were not declared " +
                "with arcade { modules(...) }, so they would be missing at runtime:\n$lines"
        )
    }
}
