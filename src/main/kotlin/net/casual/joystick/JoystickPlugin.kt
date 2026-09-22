package net.casual.joystick

import org.gradle.api.InvalidUserDataException
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.artifacts.Configuration
import org.gradle.api.artifacts.component.ModuleComponentIdentifier
import org.gradle.api.attributes.Bundling
import org.gradle.api.attributes.Category
import org.gradle.api.attributes.LibraryElements
import org.gradle.api.attributes.Usage
import org.gradle.api.plugins.JavaPlugin
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.language.base.plugins.LifecycleBasePlugin
import org.gradle.language.jvm.tasks.ProcessResources

public class JoystickPlugin: Plugin<Project> {
    override fun apply(project: Project) {
        val extension = project.extensions.create("arcade", ArcadeExtension::class.java)
        extension.group.convention(ARCADE_GROUP)
        extension.include.convention(true)
        extension.declareDependencies.convention(true)
        extension.verify.convention(true)

        val catalogue = ArcadeModuleCatalogue(project.dependencies)

        val arcade = this.registerModules(
            project, extension, catalogue, extension.modules, ARCADE_CONFIGURATION,
            "Arcade modules declared with arcade { modules(...) }"
        )
        val arcadeDev = this.registerModules(
            project, extension, catalogue, extension.devModules, ARCADE_DEV_CONFIGURATION,
            "Development only arcade modules declared with arcade { devModules(...) }"
        )

        val arcadeClasspath = this.registerClasspath(
            project, arcade, ARCADE_CLASSPATH_CONFIGURATION,
            "Every arcade module reachable from the declared ones"
        )
        val arcadeDevClasspath = this.registerClasspath(
            project, arcadeDev, ARCADE_DEV_CLASSPATH_CONFIGURATION,
            "Every arcade module reachable from the declared development only ones"
        )

        val resolvedModules = this.resolveModules(extension, arcadeClasspath)
        val resolvedDevModules = this.resolveModules(extension, arcadeDevClasspath)

        this.configureInclude(project, extension, arcadeClasspath)
        this.configureRepository(project)

        project.configurations.named { it == LOCAL_RUNTIME_CONFIGURATION }.configureEach {
            extendsFrom(arcadeDev.get())
        }

        project.plugins.withType(JavaPlugin::class.java) {
            project.configurations.named(JavaPlugin.IMPLEMENTATION_CONFIGURATION_NAME) { extendsFrom(arcade.get()) }
            project.configurations.named(JavaPlugin.COMPILE_ONLY_CONFIGURATION_NAME) { extendsFrom(arcadeDev.get()) }
            configureModDependencies(project, extension, arcadeClasspath)
            configureVerification(project, extension, resolvedModules, resolvedDevModules)
        }
    }

    private fun registerModules(
        project: Project,
        extension: ArcadeExtension,
        catalogue: ArcadeModuleCatalogue,
        declared: Provider<Set<String>>,
        name: String,
        description: String
    ): Provider<Configuration> {
        return project.configurations.register(name) {
            this.description = description
            isCanBeConsumed = false
            isCanBeResolved = false
            dependencies.addAllLater(project.provider {
                val modules = declared.get()
                if (modules.isEmpty()) {
                    return@provider emptyList()
                }
                val group = extension.group.get()
                val version = extension.version.orNull
                    ?: throw InvalidUserDataException("arcade.version must be set when arcade modules are declared")
                catalogue.validate(group, version, modules)
                modules.map { project.dependencies.create("$group:$it:$version") }
            })
        }
    }

    private fun registerClasspath(
        project: Project,
        modules: Provider<Configuration>,
        name: String,
        description: String
    ): Provider<Configuration> {
        return project.configurations.register(name) {
            this.description = description
            isCanBeConsumed = false
            isCanBeResolved = true
            extendsFrom(modules.get())
            attributes {
                attribute(Usage.USAGE_ATTRIBUTE, project.objects.named(Usage::class.java, Usage.JAVA_RUNTIME))
                attribute(Category.CATEGORY_ATTRIBUTE, project.objects.named(Category::class.java, Category.LIBRARY))
                attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, project.objects.named(LibraryElements::class.java, LibraryElements.JAR))
                attribute(Bundling.BUNDLING_ATTRIBUTE, project.objects.named(Bundling::class.java, Bundling.EXTERNAL))
            }
        }
    }

    private fun resolveModules(
        extension: ArcadeExtension,
        classpath: Provider<Configuration>
    ): Provider<Map<String, ArcadeComponent>> {
        return classpath.flatMap { config ->
            config.incoming.resolutionResult.rootComponent.zip(extension.group) { root, group ->
                collectArcadeComponents(root, group)
            }
        }
    }

    private fun configureInclude(
        project: Project,
        extension: ArcadeExtension,
        arcadeClasspath: Provider<Configuration>
    ) {
        val nested: Provider<List<String>> = extension.include.flatMap fm@ { enabled ->
            if (!enabled) {
                return@fm project.provider { emptyList() }
            }
            arcadeClasspath.flatMap { config ->
                val group = extension.group.get()
                config.incoming.artifactView {
                    componentFilter { it is ModuleComponentIdentifier && it.group == group }
                }.artifacts.resolvedArtifacts.map { resolved ->
                    val bundled = resolved.flatMapTo(HashSet()) { FabricModJson.readBundledModIds(it.file) }
                    resolved.filter { (FabricModJson.readModIdAndVersion(it.file)?.first ?: "") !in bundled }
                        .map { it.id.componentIdentifier.displayName }
                        .sorted()
                }
            }
        }
        project.configurations.named { it == INCLUDE_CONFIGURATION }.configureEach {
            dependencies.addAllLater(nested.map { coordinates -> coordinates.map { project.dependencies.create(it) } })
        }
    }

    private fun configureRepository(project: Project) {
        for ((name, url, groups) in REPOSITORIES) {
            project.repositories.maven {
                this.name = name
                this.url = project.uri(url)
                content { groups.forEach(::includeGroup) }
            }
        }
    }

    private fun configureModDependencies(
        project: Project,
        extension: ArcadeExtension,
        arcadeClasspath: Provider<Configuration>
    ) {
        val dependencies: Provider<Map<String, String>> = extension.declareDependencies.flatMap fm@ { enabled ->
            if (!enabled) {
                return@fm project.provider { emptyMap() }
            }
            arcadeClasspath.flatMap { config ->
                val group = extension.group.get()
                val artifacts = config.incoming.artifactView {
                    componentFilter { it is ModuleComponentIdentifier && it.group == group }
                }.artifacts.resolvedArtifacts
                artifacts.map { resolved ->
                    resolved.mapNotNull { FabricModJson.readModIdAndVersion(it.file) }
                        .associate { (id, version) -> id to ">=$version" }
                        .toSortedMap()
                }
            }
        }

        project.tasks.named(JavaPlugin.PROCESS_RESOURCES_TASK_NAME, ProcessResources::class.java) {
            inputs.property("arcadeDependencies", dependencies)
            val file = destinationDir.resolve(FABRIC_MOD_JSON)
            doLast("addArcadeDependencies") {
                if (file.isFile) {
                    FabricModJson.addDependencies(file, dependencies.get())
                }
            }
        }
    }

    private fun configureVerification(
        project: Project,
        extension: ArcadeExtension,
        resolvedModules: Provider<Map<String, ArcadeComponent>>,
        resolvedDevModules: Provider<Map<String, ArcadeComponent>>
    ) {
        val sourceSets = project.extensions.getByType(SourceSetContainer::class.java)
        val compileClasspath = project.configurations.named(sourceSets.getByName("main").compileClasspathConfigurationName)

        val verify = project.tasks.register(VERIFY_TASK_NAME, VerifyArcadeModulesTask::class.java) {
            group = LifecycleBasePlugin.VERIFICATION_GROUP
            description = "Checks every arcade module on the compile classpath was declared with arcade { modules(...) }"
            onlyIf { extension.verify.get() }
            declared.set(resolvedModules.zip(resolvedDevModules) { modules, dev -> modules.keys + dev.keys })
            this.compileClasspath.set(compileClasspath.flatMap { config ->
                config.incoming.resolutionResult.rootComponent.zip(extension.group) { root, group ->
                    collectArcadeComponents(root, group).mapValues { (_, component) ->
                        component.path.lastOrNull() ?: "direct dependency"
                    }
                }
            })
            bundled.set(compileClasspath.flatMap { config ->
                val group = extension.group.get()
                config.incoming.artifactView {
                    componentFilter { it !is ModuleComponentIdentifier || it.group != group }
                }.artifacts.resolvedArtifacts.map { resolved ->
                    val bundled = LinkedHashMap<String, String>()
                    for (artifact in resolved) {
                        for (id in FabricModJson.readBundledModIds(artifact.file)) {
                            bundled.putIfAbsent(id, artifact.id.componentIdentifier.displayName)
                        }
                    }
                    bundled
                }
            })
        }
        project.tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME) { dependsOn(verify) }
    }

    private companion object {
        const val ARCADE_GROUP = "net.casualchampionships"
        const val ARCADE_CONFIGURATION = "arcade"
        const val ARCADE_CLASSPATH_CONFIGURATION = "arcadeClasspath"
        const val ARCADE_DEV_CONFIGURATION = "arcadeDev"
        const val ARCADE_DEV_CLASSPATH_CONFIGURATION = "arcadeDevClasspath"
        const val INCLUDE_CONFIGURATION = "include"
        const val LOCAL_RUNTIME_CONFIGURATION = "localRuntime"
        const val VERIFY_TASK_NAME = "verifyArcadeModules"
        const val FABRIC_MOD_JSON = "fabric.mod.json"

        val REPOSITORIES = listOf(
            Triple("Arcade", "https://maven.casualchampionships.net/snapshots", listOf(ARCADE_GROUP, "com.github.ReplayMod")),
            Triple("Nucleoid", "https://maven.nucleoid.xyz", listOf("xyz.nucleoid")),
        )
    }
}
