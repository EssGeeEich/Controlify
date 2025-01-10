import de.undercouch.gradle.tasks.download.Download
import dev.kikugie.stonecutter.ChiseledTask

plugins {
    id("me.modmuss50.mod-publish-plugin") version "0.6.1+"
    id("org.ajoberstar.grgit") version "5.0.+"
    id("dev.kikugie.stonecutter")
    id("de.undercouch.download") version "5.6.0"
}

val ciSingleBuild: String? = System.getenv("CI_SINGLE_BUILD")
if (ciSingleBuild != null) {
    stonecutter active ciSingleBuild
} else {
stonecutter active "1.21.4-fabric" /* [SC] DO NOT EDIT */
}

fun registerChiseled(task: String, action: ChiseledTask.() -> Unit = {}): TaskProvider<ChiseledTask> {
    return tasks.register(
        "chiseled" + task.replaceFirstChar { it.uppercase() },
        stonecutter.chiseled.kotlin
    ) {
        group = "controlify"
        ofTask(task)
        action(this)

    }.also { stonecutter registerChiseled it }
}

val chiseledBuildAndCollect = registerChiseled("buildAndCollect")
val chiseledReleaseModVersion = registerChiseled("releaseModVersion") {
    mustRunAfter(chiseledBuildAndCollect)
}

val releaseMod by tasks.registering {
    group = "controlify"
    dependsOn(chiseledBuildAndCollect)
    dependsOn(chiseledReleaseModVersion)
    dependsOn("publishMods")
}

stonecutter.parameters {
    fun String.propDefined() = node!!.findProperty(this)?.toString()?.isNotBlank() ?: false
    consts(
        listOf(
            "immediately-fast" to "deps.immediatelyFast".propDefined(),
            "iris" to "deps.iris".propDefined(),
            "mod-menu" to "deps.modMenu".propDefined(),
            "sodium" to "deps.sodium".propDefined(),
            "simple-voice-chat" to "deps.simpleVoiceChat".propDefined(),
            "reeses-sodium-options" to "deps.reesesSodiumOptions".propDefined(),
            "fancy-menu" to "deps.fancyMenu".propDefined(),
        )
    )
}

val sdl3Target = property("deps.sdl3Target")!!.toString()

data class NativesDownload(
    val mavenSuffix: String,
    val extension: String,
    val jnaCanonicalPrefix: String,
    val taskName: String
)

listOf(
    NativesDownload("windows-x86_64", "dll", "win32-x86-64", "WinX86_64"),
    NativesDownload("windows-x86", "dll", "win32-x86", "WinX86"),
    NativesDownload("linux-x86_64", "so", "linux-x86-64", "LinuxX86_64"),
    NativesDownload("linux-aarch64", "so", "linux-aarch64", "LinuxAarch64"),
    NativesDownload("macos-universal", "dylib", "darwin-aarch64", "MacArm"),
    NativesDownload("macos-universal", "dylib", "darwin-x86-64", "MacIntel"),
).map {
    tasks.register("download${it.taskName}", Download::class) {
        group = "controlify/natives"

        src("https://maven.isxander.dev/releases/dev/isxander/libsdl4j-natives/$sdl3Target/libsdl4j-natives-$sdl3Target-${it.mavenSuffix}.${it.extension}")
        dest("${layout.buildDirectory.get()}/sdl-natives/${sdl3Target}/${it.jnaCanonicalPrefix}/libSDL3.${it.extension}")
        overwrite(false)
    }
}.let { downloadTasks ->
    tasks.register("downloadOfflineNatives") {
        group = "controlify/natives"

        downloadTasks.forEach(::dependsOn)

        outputs.dir("${layout.buildDirectory.get()}/sdl-natives/${sdl3Target}")
    }
}

// download the most up to date controller database for SDL2
val downloadHidDb by tasks.registering(Download::class) {
    finalizedBy("convertHidDBToSDL3")

    group = "controlify"

    src("https://raw.githubusercontent.com/gabomdq/SDL_GameControllerDB/master/gamecontrollerdb.txt")
    dest("src/main/resources/assets/controlify/controllers/gamecontrollerdb-sdl2.txt")
}

// SDL3 renamed `Mac OS X` -> `macOS` and this change carried over to mappings
val convertHidDBToSDL3 by tasks.registering(Copy::class) {
    mustRunAfter(downloadHidDb)
    dependsOn(downloadHidDb)

    group = "controlify/internal"

    val file = downloadHidDb.get().outputs.files.singleFile
    from(file)
    into(file.parent)

    rename { "gamecontrollerdb-sdl3.txt" }
    filter { it.replace("Mac OS X", "macOS") }
}

val modVersion: String by project
version = modVersion

val versionProjects = stonecutter.versions.map { findProject(it.project)!! }
publishMods {
    val modChangelog =
        rootProject.file("changelog.md")
            .takeIf { it.exists() }
            ?.readText()
            ?.replace("{version}", modVersion)
            ?.replace("{targets}", stonecutter.versions
                .map { it.project }
                .joinToString(separator = "\n") { "- $it" })
            ?: "No changelog provided."
    changelog.set(modChangelog)

    type.set(
        when {
            "alpha" in modVersion -> ALPHA
            "beta" in modVersion -> BETA
            else -> STABLE
        }
    )

    if (hasProperty("discord.publish-webhook")) {
        discord {
            webhookUrl = findProperty("discord.publish-webhook")!!.toString()
            dryRunWebhookUrl.set(webhookUrl)

            username = "Controlify Updates"
            avatarUrl = "https://raw.githubusercontent.com/isXander/Controlify/1.20.x/dev/src/main/resources/icon.png"

            var discordChangelog = changelog.get()
            val controlifyPing = "\n\n<@&1146064258652712960>" // <@Controlify Ping>
            if ((discordChangelog.length + controlifyPing.length) > 2000) {
                println("Changelog is too long for Discord, trimming.")
                discordChangelog = discordChangelog.substring(0, 2000 - controlifyPing.length - 3) + "..."
            }

            content = "$discordChangelog\n\n<@&1146064258652712960>" // <@Controlify Ping>

//            publishResults.from(
//                *versionProjects
//                    .map { it to it.extensions.findByType<ModPublishExtension>()!!.platforms }
//                    .flatMap { (project, platforms) ->
//                        platforms.map { platform ->
//                            project.tasks.named<PublishModTask>(platform.taskName)
//                                .map { it.result }
//                        }
//                    }
//                    .toTypedArray()
//            )
        }
    }

    val githubProject: String by project
    if (githubProject.isNotBlank() && hasProperty("github.token")) {
        github {
            displayName.set("Controlify $modVersion")

            repository.set(githubProject)
            accessToken.set(findProperty("github.token")?.toString())
            commitish.set(grgit.branch.current().name)
            tagName = modVersion

            allowEmptyFiles = true

            announcementTitle = "Download from GitHub"
        }
    }
}
