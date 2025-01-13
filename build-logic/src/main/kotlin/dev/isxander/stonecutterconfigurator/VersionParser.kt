package dev.isxander.stonecutterconfigurator

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class BuildsFile(
    val vcsVersion: String,
    val builds: Builds,
)

typealias Builds = Map<MinecraftVersion, BuildConfiguration>

@Serializable
data class BuildConfiguration(
    val platforms: List<Platform>,
    val semver: Semver? = null,
)

@Serializable
enum class Platform {
    @SerialName("fabric") Fabric,
    @SerialName("neoforge") Neoforge,
    @SerialName("forge") Forge,
}

typealias MinecraftVersion = String
typealias BuildIdentifier = String
typealias Semver = String

data class RegisteredBuilds(
    val builds: Map<BuildIdentifier, Semver>,
    val vcsVersion: BuildIdentifier,
)
fun getRegisteredBuilds(buildsFile: File): RegisteredBuilds {
    if (!buildsFile.exists()) error("Builds file does not exist!")

    val builds = Json.decodeFromString<BuildsFile>(buildsFile.readText())
    return RegisteredBuilds(
        builds.builds.flatMap { (mcVersion, buildConfig) ->
            buildConfig.platforms.map { platform ->
                "$mcVersion-${platform.name.lowercase()}" to (buildConfig.semver ?: mcVersion)
            }
        }.toMap(),
        builds.vcsVersion,
    )
}
