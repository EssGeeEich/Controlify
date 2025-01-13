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

    includeBuild("build-logic")
}

plugins {
    id("dev.isxander.stonecutter-configurator")
}

rootProject.name = "Controlify"

