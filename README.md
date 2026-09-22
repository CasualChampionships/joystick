# Joystick

A Gradle plugin for Fabric mods that depend on [arcade](https://github.com/CasualChampionships/arcade).

## Motivation

Arcade has a lot of different modules, and is designed to so that developers can depend on
individual modules and JiJ them without shipping the entirety of arcade. The problem with
this is that many of the more complex arcade modules are built on top of many other modules.

But fabric's JiJ is designed to be not transitive, to prevent accidentally JiJ-ing dependencies
that were never wanted. This means that developers would need to JiJ not only the module they 
depend on but also all of its dependencies and so on which can become messy quickly. Not only
this but if a developer forgot to include a dependency everything would work fine in their
dev environment since `implementation` is transitive.

The main goal of Joystick is that it handles all of this for you, and derives what dependencies
your mod actually needs to bundle.

## Usage

The plugin is published to the Gradle plugin portal so you can simply write in your `build.gradle.kts`:

```kts
plugins {
    id("net.casualchampionships.joystick") version "1.0.4"
}

arcade {
    // Whatever version arcade you are targetting
    // The latest version can be found here: 
    // https://maven.casualchampionships.net/snapshots/net/casualchampionships/arcade/maven-metadata.xml
    version = "<arcade-version>"
    modules("nametags", "commands")
}
```

In this above example, joystick will add every module that is reachable from `arcade-nametags`
and `arcade-commands` to `implemenation`, `include` (JiJ), and your dependencies in `fabric.mod.json`.

In addition to this, `check` runs `verifyArcadeModules` which checks if an arcade module somehow
reached the compiled classpath without being registered via `arcade { ... }`, e.g. another mod
depends on an arcade module. 

## Options

```kts
arcade {
    version = libs.versions.arcade // The version of arcade you're depending on
    modules("nametags")            // The modules you want to depend on, can be prefixed with arcade-
    devModules("datagen")          // Modules only needed during development

    include = false                // Whether to JiJ the arcade modules, true by default
    declareDependencies = false    // Whether to update fabric.mod.json dependencies, true by default
    verify = false                 // Whether to run `verifyArcadeModules`, true by default
}
```
