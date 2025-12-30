rootProject.name = "supplylines"

pluginManagement {
    repositories {
        gradlePluginPortal()
        maven("https://maven.minecraftforge.net")
        maven("https://repo.spongepowered.org/maven")
        mavenCentral()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.PREFER_PROJECT)
    repositories {
        mavenCentral()
        maven("https://maven.minecraftforge.net")
        maven("https://maven.tterrag.com")           // Create/Flywheel
        maven("https://mvn.devos.one/snapshots")     // Create compat
        maven("https://cursemaven.com")              // CurseForge dependencies
    }
}
