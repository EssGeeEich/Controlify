plugins {
    val modstitch = "0.4.0"
    id("dev.isxander.modstitch.base") version modstitch
    id("dev.isxander.modstitch.publishing") version modstitch

    id("dev.kikugie.j52j") version "1.0.3"
}

// version stuff
val mcVersion = property("mcVersion")!!.toString()
val mcSemverVersion = stonecutter.current.version

// loader stuff
val isFabric = modstitch.isLoom
val isNeoforge = modstitch.isModDevGradleRegular
val isForge = modstitch.isModDevGradleLegacy
val isForgeLike = modstitch.isModDevGradle
val loader = when {
    isFabric -> "fabric"
    isNeoforge -> "neoforge"
    isForge -> "forge"
    else -> error("Unknown loader")
}

val versionWithoutMC = property("modVersion")!!.toString()

modstitch {
    minecraftVersion = mcVersion
    javaTarget = 17

    parchment {
        prop("parchment.version") { mappingsVersion = it }
        prop("parchment.minecraft") { minecraftVersion = it }
    }

    metadata {
        fun prop(property: String, block: (String) -> Unit) {
            prop(property, ifNull = {""}) { block(it) }
        }

        modId = "controlify"
        modName = "Controlify"
        modVersion = "$versionWithoutMC+${stonecutter.current.project}"
        modGroup = "dev.isxander"
        modLicense = "LGPL-3.0-or-later"
        modAuthor = "isXander"
        prop("modDescription") { modDescription = it }

        prop("packFormat") { replacementProperties.put("pack_format", it) }
        prop("githubProject") { replacementProperties.put("github", it) }
        prop("meta.mcDep") { replacementProperties.put("mc", it) }
        prop("meta.loaderDep") { replacementProperties.put("loaderVersion", it) }
        prop("deps.fabricApi") { replacementProperties.put("fapi", it) }


        if (isNeoforge && stonecutter.eval(stonecutter.current.version, "<=1.20.4")) {
            modLoaderManifest = "META-INF/mods.toml" // neoforge used to use this
        }
    }

    loom {
        prop("deps.fabricLoader", required = true) { fabricLoaderVersion = it }

        configureLoom {
            if (stonecutter.current.isActive) { // only generate active project run config as the rest would be invalid
                runConfigs.all {
                    ideConfigGenerated(true)

                    // use a single run directory for all targets (targets are two folders deep from root)
                    runDir("../../run")

                    // Loom messes with LWJGL version. It's not the one that ships with MC and Sodium doesn't like it
                    vmArgs("-Dsodium.checks.issue2561=false")
                }
            }
        }
    }

    moddevgradle {
        enable {
            prop("deps.neoForge") { neoForgeVersion = it }
            prop("deps.forge") { forgeVersion = it }
        }

        defaultRuns(server = false)
    }

    mixin {
        addMixinsToModManifest = true

        configs.register("controlify")
        if (isPropDefined("deps.iris")) configs.register("controlify-compat.iris")
        if (isPropDefined("deps.sodium")) configs.register("controlify-compat.sodium")
        if (isPropDefined("deps.reesesSodiumOptions")) configs.register("controlify-compat.reeses-sodium-options")
        configs.register("controlify-compat.yacl")
        if (isPropDefined("deps.simpleVoiceChat")) configs.register("controlify-compat.simple-voice-chat")
        if (isFabric) configs.register("controlify-platform.fabric")
        if (isNeoforge) configs.register("controlify-platform.neoforge")
    }
}

stonecutter {
    consts(
        "fabric" to modstitch.isLoom,
        "neoforge" to modstitch.isModDevGradleRegular,
        "forge" to modstitch.isModDevGradleLegacy,
        "forgelike" to modstitch.isModDevGradle,
    )

    val sodiumSemver = findProperty("deps.sodiumSemver")?.toString() ?: "0.0.0"
    dependencies(
        "fapi" to (findProperty("deps.fabricApi")?.toString() ?: "0.0.0"),
        "sodium" to sodiumSemver
    )

    swaps["sodium-package"] = if (eval(sodiumSemver, ">=0.6"))
        "net.caffeinemc.mods.sodium" else "me.jellysquid.mods.sodium"
}

repositories {
    maven("https://maven.terraformersmc.com")
    maven("https://maven.isxander.dev/releases")
    maven("https://maven.isxander.dev/snapshots")
    maven("https://maven.quiltmc.org/repository/release")
}

dependencies {
    fun Dependency?.jij() = this?.also(::modstitchJiJ)

    prop("deps.mixinExtras") {
        if (isForgeLike) {
            modstitchCompileOnly(annotationProcessor("io.github.llamalad7:mixinextras-common:$it")!!)
            if (isNeoforge) {
                modstitchImplementation("io.github.llamalad7:mixinextras-neoforge:$it").jij()
            } else {
                modstitchImplementation("io.github.llamalad7:mixinextras-forge:$it").jij()
            }
        } else {
            modstitchImplementation(annotationProcessor("io.github.llamalad7:mixinextras-fabric:$it")!!).jij()
        }
    }

    fun modDependency(id: String, artifactGetter: (String) -> String, extra: (Boolean) -> Unit = {}) {
        prop("deps.$id") {
            val noRuntime = findProperty("deps.$id.noRuntime")?.toString()?.toBoolean() == true
            val configuration = if (noRuntime) "modstitchModCompileOnly" else "modstitchModImplementation"

            configuration(artifactGetter(it))

            extra(!noRuntime)
        }
    }

    if (isFabric) {
        modDependency("fabricApi", { "net.fabricmc.fabric-api:fabric-api:$it" })

        // mod menu compat
        modDependency("modMenu", { "com.terraformersmc:modmenu:$it" })
    }

    modstitchModApi("dev.isxander:yet-another-config-lib:${property("deps.yacl")}") {
        // was including old fapi version that broke things at runtime
        exclude(group = "net.fabricmc.fabric-api", module = "fabric-api")
        exclude(group = "thedarkcolour")
    }

    // bindings for SDL3
    modstitchApi("dev.isxander:libsdl4j:${property("deps.sdl3Target")}-${property("deps.sdl34jBuild")}")
        .jij()

    // steam deck bindings
    modstitchApi("dev.isxander:steamdeck4j:${property("deps.steamdeck4j")}")
        .jij()

    // used to identify controller PID/VID when SDL is not available
    modstitchApi("org.hid4java:hid4java:${property("deps.hid4java")}")
        .jij()

    // A json5 reader
    modstitchApi("org.quiltmc.parsers:json:${property("deps.quiltParsers")}").jij()

    // sodium compat
    modDependency("sodium", { "maven.modrinth:sodium:$it" })

    // RSO compat
    modDependency("reesesSodiumOptions", { "maven.modrinth:reeses-sodium-options:$it" })

    // iris compat
    modDependency("iris", { "maven.modrinth:iris:$it" }) { runtime ->
        if (runtime) {
            modstitchModLocalRuntime("org.anarres:jcpp:1.4.14")
            modstitchModLocalRuntime("io.github.douira:glsl-transformer:2.0.0-pre13")
        }
    }

    // immediately-fast compat
    modDependency("immediatelyFast", { "maven.modrinth:immediatelyfast:$it" }) { runtime ->
        if (runtime) {
            modstitchModLocalRuntime("net.lenni0451:Reflect:1.1.0")
        }
    }

    // simple-voice-chat compat
    modDependency("simpleVoiceChat", { "maven.modrinth:simple-voice-chat:$it" })

    // fancy menu compat
    modDependency("fancyMenu", { "maven.modrinth:fancymenu:$it" })
}

tasks {
    generateModMetadata {
        eachFile {
            // don't include photoshop files for the textures for development
            if (name.endsWith(".psd")) {
                exclude()
            }
        }
    }

    register("releaseModVersion") {
        group = "controlify"

        dependsOn("publishMods")

        if (!project.publishMods.dryRun.get()) {
            dependsOn("publish")
        }
    }
}

val offlineJar by tasks.registering(Jar::class) {
    group = "controlify/internal"

    // ensure the input jar is built
    val inputJar = when {
        modstitch.isLoom -> "remapJar"
        modstitch.isModDevGradleRegular -> "jar"
        modstitch.isModDevGradleLegacy -> "reobfJar"
        else -> error("Unknown loader")
    }.let { tasks.named<AbstractArchiveTask>(it) }
    dependsOn(inputJar)

    // ensure the natives are downloaded
    val downloadTask = rootProject.tasks["downloadOfflineNatives"]
    dependsOn(downloadTask)

    // include the contents of the input jar
    from(zipTree(inputJar.flatMap { it.archiveFile }))

    // add the natives
    from(downloadTask.outputs.files)

    // set the classifier
    archiveClassifier.set("offline")
}
tasks.build { dependsOn(offlineJar) }

msPublishing {
    mpp {
        from(rootProject.publishMods)
        dryRun.set(rootProject.publishMods.dryRun)

        additionalFiles.setFrom(offlineJar.map { it.archiveFile })

        displayName.set("$versionWithoutMC for $loader $mcVersion")

        fun versionList(prop: String) = findProperty(prop)?.toString()
            ?.split(',')
            ?.map { it.trim() }
            ?: emptyList()

        // modrinth and curseforge use different formats for snapshots. this can be expressed globally
        val stableMCVersions = versionList("pub.stableMC")

        val modrinthId: String by project
        if (modrinthId.isNotBlank() && hasProperty("modrinth.token")) {
            modrinth {
                projectId.set(modrinthId)
                accessToken.set(findProperty("modrinth.token")?.toString())
                minecraftVersions.addAll(stableMCVersions)
                minecraftVersions.addAll(versionList("pub.modrinthMC"))

                announcementTitle = "Download $mcVersion for ${loader.replaceFirstChar { it.uppercase() }} from Modrinth"

                requires { slug.set("yacl") }

                if (isFabric) {
                    requires { slug.set("fabric-api") }
                    optional { slug.set("modmenu") }
                }
            }
        }

        val curseforgeId: String by project
        if (curseforgeId.isNotBlank() && hasProperty("curseforge.token")) {
            curseforge {
                projectId = curseforgeId
                projectSlug = findProperty("curseforgeSlug")!!.toString()
                accessToken = findProperty("curseforge.token")?.toString()
                minecraftVersions.addAll(stableMCVersions)
                minecraftVersions.addAll(versionList("pub.curseMC"))

                announcementTitle = "Download $mcVersion for ${loader.replaceFirstChar { it.uppercase() }} from CurseForge"

                requires { slug.set("yacl") }

                if (isFabric) {
                    requires { slug.set("fabric-api") }
                    optional { slug.set("modmenu") }
                }
            }
        }

        val githubProject: String by project
        if (githubProject.isNotBlank() && hasProperty("github.token")) {
            github {
                accessToken = findProperty("github.token")?.toString()

                // will upload files to this parent task
                parent(rootProject.tasks.named("publishGithub"))
            }
        }
    }

    maven {
        repositories {
            val username = prop("XANDER_MAVEN_USER") { it }
            val password = prop("XANDER_MAVEN_PASS") { it }
            if (username != null && password != null) {
                maven(url = "https://maven.isxander.dev/releases") {
                    name = "XanderReleases"
                    credentials {
                        this.username = username
                        this.password = password
                    }
                }
            } else {
                logger.warn("Xander Maven credentials not satisfied.")
            }
        }
    }
}

fun <T> prop(property: String, required: Boolean = false, ifNull: () -> String? = { null }, block: (String) -> T?): T? {
    return ((System.getenv(property) ?: findProperty(property)?.toString())
        ?.takeUnless { it.isBlank() }
        ?: ifNull())
        .let { if (required && it == null) error("Property $property is required") else it }
        ?.let(block)
}

fun isPropDefined(property: String): Boolean {
    return (System.getenv(property) ?: findProperty(property)?.toString())
        ?.isNotBlank() == true
}
