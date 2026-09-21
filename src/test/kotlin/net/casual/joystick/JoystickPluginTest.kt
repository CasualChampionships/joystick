package net.casual.joystick

import org.gradle.testkit.runner.GradleRunner
import org.gradle.testkit.runner.TaskOutcome
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.io.TempDir
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class JoystickPluginTest {
    @TempDir
    lateinit var projectDir: File

    @BeforeEach
    fun setUp() {
        projectDir.resolve("settings.gradle.kts").writeText("rootProject.name = \"consumer\"\n")
        projectDir.resolve("src/main/resources").mkdirs()
        projectDir.resolve("src/main/resources/fabric.mod.json").writeText(
            """
            {
              "schemaVersion": 1,
              "id": "consumer",
              "version": "1.0.0",
              "depends": {
                "fabricloader": ">=0.19.5",
                "arcade-utils": ">=0.1.0"
              }
            }
            """.trimIndent()
        )
    }

    @Test
    fun `include receives the transitive closure of the declared modules`() {
        writeBuild(modules = "\"nametags\"")

        val result = run("printInclude")

        val included = result.output.lines().filter { it.startsWith("include: ") }.map { it.removePrefix("include: ") }
        assertEquals(
            listOf(
                "arcade-event-registry",
                "arcade-events-server",
                "arcade-extensions",
                "arcade-nametags",
                "arcade-observers",
                "arcade-utils",
                "arcade-virtual-entities",
            ),
            included.sorted()
        )
        assertTrue(included.all { it.isNotEmpty() })
    }

    @Test
    fun `the aggregate is nested alone because it bundles every module itself`() {
        writeBuild(modules = "\"arcade\"")

        val result = run("printInclude")

        val included = result.output.lines().filter { it.startsWith("include: ") }.map { it.removePrefix("include: ") }
        assertEquals(listOf("arcade"), included)
    }

    @Test
    fun `modules outside the aggregate are accepted when published`() {
        writeBuild(modules = "\"datagen\"")

        val result = run("printInclude")

        val included = result.output.lines().filter { it.startsWith("include: ") }.map { it.removePrefix("include: ") }
        assertTrue("arcade-datagen" in included, result.output)
        assertTrue("arcade-resource-pack-generation" in included, result.output)
    }

    @Test
    fun `include is empty when nesting is disabled`() {
        writeBuild(modules = "\"nametags\"", arcadeExtra = "include = false")

        val result = run("printInclude")

        assertTrue(result.output.lines().none { it.startsWith("include: ") })
    }

    @Test
    fun `unknown modules fail with the available module list`() {
        writeBuild(modules = "\"nametags\", \"nonsense\"")

        val result = run("printInclude", expectFailure = true)

        assertTrue("Unknown arcade module(s) arcade-nonsense for arcade $ARCADE" in result.output)
        assertTrue("Available modules: " in result.output)
        assertTrue("arcade-nametags" in result.output.substringAfter("Available modules: "))
    }

    @Test
    fun `fabric mod json declares every nested module`() {
        writeBuild(modules = "\"nametags\"")

        val result = run("processResources")

        assertEquals(TaskOutcome.SUCCESS, result.task(":processResources")?.outcome)
        val json = projectDir.resolve("build/resources/main/fabric.mod.json").readText()
        assertTrue("\"arcade-nametags\": \">=$ARCADE_MOD_VERSION\"" in json, json)
        assertTrue("\"arcade-virtual-entities\": \">=$ARCADE_MOD_VERSION\"" in json, json)
        assertTrue("\"arcade-utils\": \">=0.1.0\"" in json, json)
        assertTrue("\"fabricloader\": \">=0.19.5\"" in json, json)
    }

    @Test
    fun `verification fails for arcade modules that bypass the extension`() {
        writeBuild(modules = "\"nametags\"", extra = """
            dependencies {
                implementation("net.casualchampionships:arcade-commands:$ARCADE")
            }
        """.trimIndent())

        val result = run("verifyArcadeModules", expectFailure = true)

        assertTrue("arcade-commands (via direct dependency)" in result.output, result.output)
        assertTrue("arcade-nametags" !in result.output.substringAfter("would be missing at runtime"), result.output)
    }

    @Test
    fun `verification warns for modules another dependency bundles`() {
        writeBundlingMod(bundles = true)
        writeBuild(modules = "\"nametags\"", extra = DEPEND_ON_X)

        val result = run("verifyArcadeModules")

        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyArcadeModules")?.outcome)
        assertTrue(
            "Arcade module arcade-commands is on the compile classpath via com.example:x:1.0 and is only " +
                "available at runtime because com.example:x:1.0 bundles it" in result.output,
            result.output
        )
    }

    @Test
    fun `verification fails for modules another dependency exposes without bundling`() {
        writeBundlingMod(bundles = false)
        writeBuild(modules = "\"nametags\"", extra = DEPEND_ON_X)

        val result = run("verifyArcadeModules", expectFailure = true)

        assertTrue("arcade-commands (via com.example:x:1.0)" in result.output, result.output)
    }

    @Test
    fun `verification passes when everything is declared`() {
        writeBuild(modules = "\"nametags\", \"commands\"")

        val result = run("verifyArcadeModules")

        assertEquals(TaskOutcome.SUCCESS, result.task(":verifyArcadeModules")?.outcome)
    }

    private fun writeBuild(modules: String, arcadeExtra: String = "", extra: String = "") {
        projectDir.resolve("build.gradle.kts").writeText(
            """
            plugins {
                java
                id("net.casualchampionships.joystick")
            }

            repositories {
                mavenCentral()
                maven("https://maven.fabricmc.net/")
                maven("https://jitpack.io")
            }

            // Stands in for the configuration fabric-loom registers.
            val include: Configuration by configurations.creating

            // The plugin's own toolchain drives the TestKit daemon, which is older than
            // the jvm arcade targets.
            java {
                disableAutoTargetJvm()
            }

            arcade {
                version = "$ARCADE"
                modules($modules)
                $arcadeExtra
            }

            $extra

            tasks.register("printInclude") {
                doLast {
                    include.dependencies.forEach { println("include: " + it.name) }
                }
            }
            """.trimIndent()
        )
    }

    private fun writeBundlingMod(bundles: Boolean) {
        val dir = projectDir.resolve("repo/com/example/x/1.0").apply { mkdirs() }
        dir.resolve("x-1.0.pom").writeText(
            """
            <project>
              <modelVersion>4.0.0</modelVersion>
              <groupId>com.example</groupId>
              <artifactId>x</artifactId>
              <version>1.0</version>
              <dependencies>
                <dependency>
                  <groupId>net.casualchampionships</groupId>
                  <artifactId>arcade-commands</artifactId>
                  <version>$ARCADE</version>
                  <scope>compile</scope>
                </dependency>
              </dependencies>
            </project>
            """.trimIndent()
        )
        val nested = zip("fabric.mod.json" to """{"id": "arcade-commands", "version": "$ARCADE_MOD_VERSION"}""".toByteArray())
        val entries = mutableListOf("fabric.mod.json" to """{"id": "x", "version": "1.0"}""".toByteArray())
        if (bundles) {
            entries.add("META-INF/jars/arcade-commands-$ARCADE.jar" to nested)
        }
        dir.resolve("x-1.0.jar").writeBytes(zip(*entries.toTypedArray()))
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val bytes = ByteArrayOutputStream()
        ZipOutputStream(bytes).use { zip ->
            for ((name, content) in entries) {
                zip.putNextEntry(ZipEntry(name))
                zip.write(content)
                zip.closeEntry()
            }
        }
        return bytes.toByteArray()
    }

    private fun run(vararg tasks: String, expectFailure: Boolean = false) = GradleRunner.create()
        .withProjectDir(projectDir)
        .withPluginClasspath()
        .withArguments(*tasks, "--stacktrace")
        .let { if (expectFailure) it.buildAndFail() else it.build() }

    private companion object {
        const val ARCADE_MOD_VERSION = "0.14.0-beta.3"
        const val ARCADE = "$ARCADE_MOD_VERSION+26.3"
        val DEPEND_ON_X = """
            repositories {
                maven(uri("repo"))
            }

            dependencies {
                implementation("com.example:x:1.0")
            }
        """.trimIndent()
    }
}
