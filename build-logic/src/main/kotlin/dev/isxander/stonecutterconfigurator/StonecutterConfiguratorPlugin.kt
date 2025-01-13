package dev.isxander.stonecutterconfigurator

import dev.kikugie.stonecutter.settings.StonecutterSettings
import org.gradle.api.Plugin
import org.gradle.api.initialization.Settings
import org.gradle.kotlin.dsl.*

class StonecutterConfiguratorPlugin : Plugin<Settings> {
    override fun apply(target: Settings) = with(target) {
        val ciSingleBuild = System.getenv("CI_SINGLE_BUILD")

        pluginManager.apply("dev.kikugie.stonecutter")
        pluginManager.withPlugin("dev.kikugie.stonecutter") {
            extensions.configure<StonecutterSettings> {
                kotlinController = true
                centralScript = "build.gradle.kts"

                create(rootProject) {
                    val registeredBuilds = getRegisteredBuilds(settingsDir.resolve("versions/builds.json"))

                    registeredBuilds.builds.forEach { (name, version) ->
                        if (ciSingleBuild != null && ciSingleBuild != name) return@forEach

                        vers(name, version)
                    }
                    if (ciSingleBuild == null) {
                        vcsVersion = registeredBuilds.vcsVersion
                    }

                    //branch("test-harness")
                }
            }
        }
    }
}
