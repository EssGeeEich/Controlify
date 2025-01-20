package dev.isxander.stonecutterconfigurator

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.gradle.api.Project
import org.gradle.kotlin.dsl.*
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
    val experimental: Boolean = false,
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

data class Build(
    val identifier: BuildIdentifier,
    val version: Semver,
    val experimental: Boolean,
)

data class RegisteredBuilds(
    val builds: List<Build>,
    val vcsVersion: BuildIdentifier,
)
fun parseBuilds(buildsFile: File): RegisteredBuilds {
    if (!buildsFile.exists()) error("Builds file does not exist!")

    val builds = Json.decodeFromString<BuildsFile>(buildsFile.readText())
    return RegisteredBuilds(
        builds.builds.flatMap { (mcVersion, buildConfig) ->
            buildConfig.platforms.map { platform ->
                Build(
                    "$mcVersion-${platform.name.lowercase()}",
                    buildConfig.semver ?: mcVersion,
                    buildConfig.experimental,
                )
            }
        },
        builds.vcsVersion,
    )
}

fun Project.getRegisteredBuilds(): RegisteredBuilds {
    return gradle.extra["registeredBuilds"] as RegisteredBuilds
}

val Project.isExperimental: Boolean
    get() {
        val experimentalCached = if (extra.has("scExperimental")) extra["scExperimental"] as Boolean else null
        if (experimentalCached != null) return experimentalCached
        val experimental = getRegisteredBuilds().builds.find { it.identifier == project.name }?.experimental == true
        project.extra["scExperimental"] = experimental
        return experimental
    }
