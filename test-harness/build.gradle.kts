plugins {
    id("dev.isxander.modstitch.base")
}

val rootSibling = stonecutter.node.sibling("")!!
val rootSiblingProject = rootSibling.project

modstitch {
    minecraftVersion = stonecutter.current.version
    javaTarget = 17

    metadata {
        modId = "controlify-test-harness"
        modGroup = "dev.isxander"
        modVersion = "1.0.0"
        modName = "Controlify Test Harness"
    }

    loom {
        fabricLoaderVersion = "0.16.10"
    }

    moddevgradle {
        enable {
            rootProp("deps.neoForge") { neoForgeVersion = it }
            rootProp("deps.forge") { forgeVersion = it }
        }
    }

    parchment {
        rootProp("parchment.version") { mappingsVersion = it }
        rootProp("parchment.minecraft") { minecraftVersion = it }
    }
}

dependencies {
    implementation(project(path = rootSibling.path, configuration = "namedElements")) {
        isTransitive = true
    }
}

fun <T> prop(property: String, required: Boolean = false, ifNull: () -> String? = { null }, target: Project = project, block: (String) -> T?): T? {
    return ((System.getenv(property) ?: project.findProperty(property)?.toString())
        ?.takeUnless { it.isBlank() }
        ?: ifNull())
        .let { if (required && it == null) error("Property $property is required") else it }
        ?.let(block)
}
fun <T> rootProp(property: String, required: Boolean = false, ifNull: () -> String? = { null }, block: (String) -> T?): T? =
    prop(property, required, ifNull, rootSiblingProject, block)
