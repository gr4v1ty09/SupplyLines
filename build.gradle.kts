plugins {
    id("net.minecraftforge.gradle") version "6.0.25"
    id("org.spongepowered.mixin") version "0.7-SNAPSHOT"
    id("com.diffplug.spotless") version "6.25.0"
}

val mcVersion: String by project.extra { providers.gradleProperty("minecraft_version").get() }
val forgeVersion: String by project.extra { providers.gradleProperty("forge_version").get() }
val modVersion: String by project.extra { providers.gradleProperty("supplylines_version").get() }

group = "com.gr4v1ty"
version = modVersion

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

minecraft {
    mappings("official", mcVersion)

    runs {
        create("client") {
            workingDirectory(project.file("run"))
            args("--username", "Dev")

            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")
            property("mixin.env.remapRefMap", "true")
            property("mixin.env.refMapRemappingFile", "${projectDir}/build/createSrgToMcp/output.srg")

            mods {
                create("supplylines") {
                    source(sourceSets.main.get())
                }
            }
        }

        create("server") {
            workingDirectory(project.file("run-server"))

            property("forge.logging.markers", "REGISTRIES")
            property("forge.logging.console.level", "debug")

            mods {
                create("supplylines") {
                    source(sourceSets.main.get())
                }
            }
        }
    }
}

mixin {
    add(sourceSets.main.get(), "supplylines.refmap.json")
    // Config is registered via IMixinConnector in META-INF/services
    // config("supplylines.mixins.json")
}

repositories {
    maven {
        name = "CurseMaven"
        url = uri("https://cursemaven.com")
        content { includeGroup("curse.maven") }
    }
    maven {
        name = "LDTTeam"
        url = uri("https://ldtteam.jfrog.io/ldtteam/modding/")
    }
    maven {
        name = "Registrate"
        url = uri("https://maven.tterrag.com/")
    }
}

val fg = project.extensions.getByType<net.minecraftforge.gradle.userdev.DependencyManagementExtension>()

dependencies {
    minecraft("net.minecraftforge:forge:${mcVersion}-${forgeVersion}")

    // MineColonies 1.20.1-1.1.1069-snapshot (from LDTTeam maven)
    compileOnly(fg.deobf("com.ldtteam:minecolonies:1.20.1-1.1.1069-snapshot"))
    runtimeOnly(fg.deobf("com.ldtteam:minecolonies:1.20.1-1.1.1069-snapshot"))

    // Structurize 1.20.1-1.0.784-snapshot (project 298744, file 7041348)
    compileOnly(fg.deobf("curse.maven:structurize-298744:7041348"))
    runtimeOnly(fg.deobf("curse.maven:structurize-298744:7041348"))

    // BlockUI 1.20.1-1.0.194 (project 522992, file 7041657)
    runtimeOnly(fg.deobf("curse.maven:blockui-522992:7041657"))

    // Domum Ornamentum 1.20.1-1.0.291-snapshot (project 527361, file 6870756)
    runtimeOnly(fg.deobf("curse.maven:domum_ornamentum-527361:6870756"))

    // Multi-Piston - download manually from CurseForge, not on LDTTeam maven for 1.20.1

    // Create 6.0.6 for 1.20.1 (project 328085, file 6641603)
    compileOnly(fg.deobf("curse.maven:create-328085:6641603"))
    runtimeOnly(fg.deobf("curse.maven:create-328085:6641603"))

    // Registrate (required by Create) - only compileOnly, Create bundles it at runtime
    compileOnly(fg.deobf("com.tterrag.registrate:Registrate:MC1.20-1.3.3"))

    // Mixin annotation processor
    annotationProcessor("org.spongepowered:mixin:0.8.5:processor")
}

// Merge resources into classes directory for ForgeGradle
sourceSets.main.get().output.setResourcesDir(sourceSets.main.get().java.classesDirectory)

tasks.processResources {
    filesMatching("META-INF/mods.toml") {
        expand(
            "version" to modVersion,
            "mcVersion" to mcVersion,
            "forgeVersion" to forgeVersion
        )
    }
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
}

tasks.jar {
    archiveBaseName.set("supplylines")

    manifest {
        attributes(
            "Specification-Title" to "SupplyLines",
            "Specification-Vendor" to "gr4v1ty",
            "Specification-Version" to "1",
            "Implementation-Title" to project.name,
            "Implementation-Version" to modVersion,
            "Implementation-Vendor" to "gr4v1ty",
            "MixinConfigs" to "supplylines.mixins.json"
        )
    }

    finalizedBy("reobfJar")
}

spotless {
    java {
        target("src/**/*.java")
        removeUnusedImports()
        eclipse().configFile(rootProject.file(".spotless/eclipse-formatter.xml"))
        trimTrailingWhitespace()
        endWithNewline()
    }
}
