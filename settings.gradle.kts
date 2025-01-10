import dev.kikugie.stonecutter.AnyVersion
import dev.kikugie.stonecutter.Identifier

pluginManagement {
    repositories {
        mavenCentral()
        gradlePluginPortal()
        maven("https://maven.fabricmc.net")
        maven("https://maven.quiltmc.org/repository/release")
        maven("https://maven.architectury.dev")
        maven("https://maven.neoforged.net/releases/")
        maven("https://maven.minecraftforge.net/")
        maven("https://maven.kikugie.dev/releases")
        maven("https://maven.isxander.dev/releases")
    }
}

plugins {
    id("dev.kikugie.stonecutter") version "0.5"
}

val ciSingleBuild: String? = System.getenv("CI_SINGLE_BUILD")

stonecutter {
    kotlinController = true
    centralScript = "build.gradle.kts"

    create(rootProject) treeBuilder@{
        val versionCandidates = mutableListOf<Pair<Identifier, AnyVersion>>()

        fun mc(mcVersion: String, name: String = mcVersion, loaders: Iterable<String>) {
            for (loader in loaders) {
                versionCandidates += "$name-$loader" to mcVersion
            }
        }

        val fabric = "fabric"
        val neoforge = "neoforge"
        val forge = "forge"

        mc("1.21.4", loaders = listOf(fabric, neoforge))
        mc("1.21.3", loaders = listOf(fabric, neoforge))
        mc("1.21", loaders = listOf(fabric, neoforge))
        mc("1.20.6", loaders = listOf(fabric, neoforge))
        mc("1.20.4", loaders = listOf(fabric, neoforge))
        mc("1.20.1", loaders = listOf(fabric))

        val vcsVersion = "1.21.4-fabric"

        // if CI_SINGLE_BUILD is set, only register that version to prevent
        // configuration of other projects
        var atLeastOneBuildAdded = false
        for ((id, version) in versionCandidates) {
            if (ciSingleBuild == null || ciSingleBuild == id) {
                vers(id, version)
                atLeastOneBuildAdded = true

                if (id == vcsVersion) {
                    this@treeBuilder.vcsVersion = id
                }
            }
        }
        if (!atLeastOneBuildAdded) {
            logger.warn("No build added, CI_SINGLE_BUILD did not match any build")
        }
    }
}

rootProject.name = "Controlify"

