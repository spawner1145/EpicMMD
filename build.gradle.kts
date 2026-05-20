plugins {
    id("java")
    id("net.neoforged.moddev.legacyforge") version "2.0.141"
}

val minecraft_version: String by project
val forge_version: String by project
val parchment_version: String by project
val mod_id: String by project
val mod_version: String by project
val mod_group_id: String by project
val mod_name: String by project
val mod_authors: String by project
val mod_description: String by project

group = mod_group_id
version = "$mod_version-mc$minecraft_version-forge"

base {
    archivesName.set("epicmmd")
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(17))
    withSourcesJar()
}

tasks.withType<JavaCompile>().configureEach {
    options.encoding = "UTF-8"
}

legacyForge {
    version = "$minecraft_version-$forge_version"

    parchment {
        minecraftVersion.set(minecraft_version)
        mappingsVersion.set(parchment_version)
    }

    runs {
        create("client") {
            client()
            devLogin.set(true)
            gameDirectory.set(file("run/client"))
        }
    }

    mods {
        create(mod_id) {
            sourceSet(sourceSets.main.get())
        }
    }
}

repositories {
    flatDir {
        dirs(
            "../epicfight/build/libs",
            "../MC-MMD-rust/forge/build/libs",
            ".."
        )
    }
    mavenCentral()
}

dependencies {
    compileOnly(files("../epicfight/build/libs/epic-fight-20.14.17-mc1.20.1-forge.jar"))
    compileOnly(files("../MC-MMD-rust/forge/build/libs/mmdskin-forge-1.0.5-1.20.1.jar"))

    runtimeOnly(files("../epicfight/build/libs/epic-fight-20.14.17-mc1.20.1-forge.jar"))
    runtimeOnly(files("../MC-MMD-rust/forge/build/libs/mmdskin-forge-1.0.5-1.20.1.jar"))
}

tasks.named<ProcessResources>("processResources").configure {
    val replaceProperties = mapOf(
        "mod_id" to mod_id,
        "mod_name" to mod_name,
        "mod_version" to mod_version,
        "mod_authors" to mod_authors,
        "mod_description" to mod_description,
        "minecraft_version" to minecraft_version,
        "forge_version" to forge_version
    )

    inputs.properties(replaceProperties)

    filesMatching(listOf("META-INF/mods.toml", "pack.mcmeta")) {
        expand(replaceProperties)
    }
}

tasks.named<Jar>("jar").configure {
    manifest {
        attributes("MixinConfigs" to "epicmmd.mixins.json")
    }
}
