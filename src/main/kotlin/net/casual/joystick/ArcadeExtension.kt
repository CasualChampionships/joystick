package net.casual.joystick

import org.gradle.api.provider.Property
import org.gradle.api.provider.SetProperty

/**
 * Configures the arcade modules a mod depends on.
 *
 * ```kotlin
 * arcade {
 *     version = "0.14.0+26.3"
 *     modules("nametags", "commands")
 * }
 * ```
 *
 * Every module reachable from the declared ones is put on the compile and runtime
 * classpaths, nested in the built jar, and added to `depends` in `fabric.mod.json`.
 */
public abstract class ArcadeExtension {
    /**
     * The arcade version to depend on.
     */
    public abstract val version: Property<String>

    /**
     * The maven group arcade modules are published under.
     */
    public abstract val group: Property<String>

    /**
     * Whether every resolved arcade module is nested in the built jar.
     */
    public abstract val include: Property<Boolean>

    /**
     * Whether every resolved arcade module is added to `depends` in `fabric.mod.json`.
     */
    public abstract val declareDependencies: Property<Boolean>

    /**
     * Whether `check` fails when an arcade module reaches the compile classpath
     * without being declared here.
     */
    public abstract val verify: Property<Boolean>

    /**
     * The declared modules, by artifact name, e.g. `arcade-nametags`.
     * `arcade` is the aggregate that bundles every server-side module.
     */
    public abstract val modules: SetProperty<String>

    /**
     * The declared development only modules, by artifact name, e.g. `arcade-datagen`.
     * These are on the compile and development runtime classpaths, but are never
     * nested in the built jar or added to `depends` in `fabric.mod.json`.
     */
    public abstract val devModules: SetProperty<String>

    /**
     * The declared test only modules, by artifact name, e.g. `arcade-gametest`.
     * These are on the classpaths of the [testSourceSets], but are never nested
     * in the built jar or added to `depends` in `fabric.mod.json`.
     */
    public abstract val testModules: SetProperty<String>

    /**
     * The names of the source sets that receive the [testModules],
     * `gametest` by default.
     */
    public abstract val testSourceSets: SetProperty<String>
    /**
     * Declares arcade modules to depend on.
     *
     * @param names Module names, with or without the `arcade-` prefix.
     */
    public fun modules(vararg names: String) {
        this.modules.addAll(names.map(::normalise))
    }

    /**
     * Declares an arcade module to depend on.
     *
     * @param name The module name, with or without the `arcade-` prefix.
     */
    public fun module(name: String) {
        this.modules.add(normalise(name))
    }

    /**
     * Declares arcade modules that are only needed during development,
     * for example `datagen`.
     *
     * Any module that is only reachable from these is also treated as
     * development only, so it will not be verified as missing at runtime.
     *
     * @param names Module names, with or without the `arcade-` prefix.
     */
    public fun devModules(vararg names: String) {
        this.devModules.addAll(names.map(::normalise))
    }

    /**
     * Declares an arcade module that is only needed during development.
     *
     * @param name The module name, with or without the `arcade-` prefix.
     * @see devModules
     */
    public fun devModule(name: String) {
        this.devModules.add(normalise(name))
    }

    /**
     * Declares arcade modules that are only needed by tests,
     * for example `gametest`.
     *
     * @param names Module names, with or without the `arcade-` prefix.
     * @see testModules
     */
    public fun testModules(vararg names: String) {
        this.testModules.addAll(names.map(::normalise))
    }

    /**
     * Declares an arcade module that is only needed by tests.
     *
     * @param name The module name, with or without the `arcade-` prefix.
     * @see testModules
     */
    public fun testModule(name: String) {
        this.testModules.add(normalise(name))
    }

    /**
     * Sets the source sets that receive the [testModules], replacing the defaults.
     *
     * @param names The source set names, e.g. `testmod`.
     */
    public fun testSourceSets(vararg names: String) {
        this.testSourceSets.set(names.toSet())
    }

    private fun normalise(name: String): String {
        return if (name == AGGREGATE || name.startsWith(ARCADE_PREFIX)) name else ARCADE_PREFIX + name
    }

    private companion object {
        const val AGGREGATE = "arcade"
        const val ARCADE_PREFIX = "arcade-"
    }
}
