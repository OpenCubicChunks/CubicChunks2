@file:Suppress("INACCESSIBLE_TYPE", "UnstableApiUsage")

import io.github.opencubicchunks.gradle.GeneratePackageInfo
import org.gradle.internal.os.OperatingSystem
import java.util.*

buildscript {
    dependencies {
        classpath("com.google.code.gson:gson:2.8.5")
    }
}
plugins {
    id("fabric-loom")
    id("maven-publish")
    id("checkstyle")
    id("io.github.juuxel.loom-quiltflower").version("1.7.2")
    id("io.github.opencubicchunks.javaheaders").version("1.2.5")
    id("io.github.opencubicchunks.gradle.mcGitVersion")
    id("io.github.opencubicchunks.gradle.mixingen")
    id("io.github.opencubicchunks.gradle.dasm")
    id("io.github.opencubicchunks.stirrin").version("1.3.1")
}

stirrin {
    setAcceptedJars(".*minecraft.*")
    setConfigs(setOf(
            "cubicchunks.mixins.access.json",
            "cubicchunks.mixins.asm.json",
            "cubicchunks.mixins.asmfixes.json",
            "cubicchunks.mixins.core.json",
            "cubicchunks.mixins.debug.json",
            "cubicchunks.mixins.levelgen.json",
            "cubicchunks.mixins.optifine.json"
    ))
}

val minecraftVersion: String by project
val loaderVersion: String by project
val fabricVersion: String by project
val lwjglVersion: String by project
val lwjglNatives: String by project
val modId: String by project
val debugArtifactTransforms: String by project

javaHeaders {
    setAcceptedJars(".*CubicChunksCore.*")
    setConfig(file("javaHeaders.json"))
    setDebug(debugArtifactTransforms.toBoolean())
}

mcGitVersion {
    isSnapshot = true
    mcVersion = minecraftVersion
    setCommitVersion("570c0cbf0cdc15b8348a862a519d3399a943af9", "0.0")
}

val generatePackageInfo: Task by tasks.creating
generatePackageInfo.apply {
    group = "filegen"
    doFirst {
        GeneratePackageInfo.generateFiles(project.sourceSets["main"])
    }
}

val genAll: Task by tasks.creating {
    group = "filegen"
    dependsOn(generatePackageInfo, "generateMixinConfigs")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

mixinGen {
    filePattern = "cubicchunks.mixins.%s.json"
    defaultRefmap = "CubicChunks-refmap.json"
    defaultPackagePrefix = "io.github.opencubicchunks.cubicchunks.mixin"
    defaultCompatibilityLevel = "JAVA_17"
    defaultMinVersion = "0.8"

    config("core") {
        required = true
        conformVisibility = true
        injectorsDefaultRequire = 1
        configurationPlugin = "io.github.opencubicchunks.cubicchunks.mixin.AnnotationConfigPlugin"
    }

    config("levelgen") {
        required = true
        conformVisibility = true
        injectorsDefaultRequire = 1
    }

    config("access") {
        required = true
        conformVisibility = true
    }

    config("asm") {
        required = true
        configurationPlugin = "io.github.opencubicchunks.cubicchunks.mixin.ASMConfigPlugin"
    }

    config("asmfixes") {
        required = true
        injectorsDefaultRequire = 1
    }

    config("optifine") {
        required = true
        configurationPlugin = "io.github.opencubicchunks.cubicchunks.mixin.OptiFineMixinConfig"
        injectorsDefaultRequire = 1
    }

    config("debug") {
        required = false
        conformVisibility = true
        injectorsDefaultRequire = 0
        configurationPlugin = "io.github.opencubicchunks.cubicchunks.mixin.DebugMixinConfig"
    }
}

group = "io.github.opencubicchunks" // http://maven.apache.org/guides/mini/guide-naming-conventions.html
base {
    archivesName.set("CubicChunks")
}

val debugCompile: Configuration by configurations.creating
val debugRuntime: Configuration by configurations.creating {
    extendsFrom(debugCompile)
}
val extraTests: Configuration by configurations.creating
val shade: Configuration by configurations.creating

sourceSets {
    create("debug") {
        compileClasspath += debugCompile
        compileClasspath += configurations.compileClasspath.get()
        compileClasspath += sourceSets.main.get().output
        runtimeClasspath += debugRuntime
        runtimeClasspath += configurations.runtimeClasspath.get()
        runtimeClasspath += sourceSets.main.get().output
    }
}

repositories {
    mavenCentral()
    maven {
        setUrl("https://oss.sonatype.org/content/repositories/snapshots/")
    }
    maven {
        setUrl("https://repo.spongepowered.org/maven/")
    }
    maven {
        name = "ParchmentMC"
        setUrl("https://maven.parchmentmc.net/")
    }
    maven {
        name = "JitPack"
        setUrl("https://jitpack.io")
    }
    maven {
        name = "Gegy"
        setUrl("https://maven.gegy.dev")
    }
}

loom {
    accessWidenerPath.set(file("src/main/resources/cubicchunks.accesswidener"))
    // intermediaryUrl = { "http://localhost:9000/intermediary-20w49a-v2.jar" }

    val dependency = project.configurations.detachedConfiguration(project.dependencies.create("net.fabricmc:sponge-mixin:0.11.2+mixin.0.8.5"))
    dependency.isTransitive = false
    val mixinFile = dependency.resolve().iterator().next().toString()

    val args = listOf(
            "-XX:+UnlockExperimentalVMOptions",
            "-XX:+AllowEnhancedClassRedefinition",
            "-XX:-OmitStackTraceInFastThrow",
            "-XX:+UseG1GC",
            "-XX:G1NewSizePercent=20",
            "-XX:G1ReservePercent=20",
            "-XX:MaxGCPauseMillis=50",
            "-XX:G1HeapRegionSize=32M",
            "-javaagent:" + mixinFile,
            "-Dmixin.debug.verbose=true",
            "-Dmixin.debug.export=true",
            "-Dmixin.checks.interfaces=true",
            "-Dcubicchunks.debug=false",
            "-Dcubicchunks.debug.loadorder=false",
            "-Dcubicchunks.debug.window=false",
            "-Dcubicchunks.debug.statusrenderer=false",
            "-Dcubicchunks.debug.heightmaprenderer=false",
            "-Dcubicchunks.debug.heightmaprenderer.server=false",
            "-Dcubicchunks.debug.heightmaprenderer.render_lightmap=false",
            "-Dcubicchunks.debug.heightmaprenderer.radius=2",
            "-Dcubicchunks.debug.heightmapverification=false",
            "-Dcubicchunks.debug.heightmapverification.frequency=1",
            "-Dcubicchunks.debug.biomes=false",
            "-ea"
    )

    runConfigs {
        get("client").apply {
            client()
            vmArgs("-Xmx2G")
        }
        get("server").apply {
            server()
            vmArgs("-Xmx2G")
        }

        create("client4g").apply {
            client()
            vmArgs("-Xmx4G")
        }
        create("server4g").apply {
            server()
            vmArgs("-Xmx4G")
        }
    }
    runConfigs.configureEach {
        isIdeConfigGenerated = true
        vmArgs(args)
    }
}

// for vulkan
project.ext["lwjglVersion"] = "3.2.2"

when (OperatingSystem.current()) {
    OperatingSystem.LINUX -> {
        @Suppress("UnstableApiUsage") val osArch = System.getProperty("os.arch")
        project.ext["lwjglNatives"] = if (osArch.startsWith("arm") || osArch.startsWith("aarch64"))
            "natives-linux-${if (osArch.contains("64") || osArch.startsWith("armv8")) "arm64" else "arm32"}"
        else "natives-linux"
    }

    OperatingSystem.WINDOWS -> {
        project.ext["lwjglNatives"] = if (System.getProperty("os.arch").contains("64")) "natives-windows" else "natives-windows-x86"
    }

    OperatingSystem.MAC_OS -> {
        project.ext["lwjglNatives"] = "natives-macos"
        project.ext["lwjglVersion"] = "3.2.1"
    }
}

dependencies {
    stirrin.addDependency("net.fabricmc:sponge-mixin:0.11.4+mixin.0.8.5")

    minecraft("com.mojang:minecraft:${minecraftVersion}")
    mappings(loom.layered {
        officialMojangMappings {
            nameSyntheticMembers = true
        }
        parchment("org.parchmentmc.data:parchment-1.18.2:2022.05.22@zip")
    })
    modImplementation("net.fabricmc:fabric-loader:${loaderVersion}")

    // Add each module as a dependency
    listOf("fabric-api-base", "fabric-command-api-v1", "fabric-networking-v0", "fabric-lifecycle-events-v1", "fabric-resource-loader-v0").forEach {
        modImplementation(fabricApi.module(it, fabricVersion))
    }

    // modImplementation "net.fabricmc.fabric-api:fabric-api:${project.fabricVersion}"
//
//    modRuntime ("supercoder79:databreaker:0.2.9") {
//        exclude module: "fabric-loader"
//    }

    stirrin.addDependency(project(":CubicChunksCore"))
    // we shade the core classes directly into CC, so it gets remapped
    shade(implementation(project(":CubicChunksCore")) {
        attributes {
            attribute(LibraryElements.LIBRARY_ELEMENTS_ATTRIBUTE, objects.named(LibraryElements::class, LibraryElements.JAR))
        }
        isTransitive = false
    })

    debugCompile("org.lwjgl:lwjgl-vulkan:$lwjglVersion")
    debugRuntime("org.lwjgl:lwjgl::$lwjglNatives")

    include(implementation("com.github.OpenCubicChunks:dasm:2895c9ccc8")!!)
    include(implementation("io.github.opencubicchunks:regionlib:0.63.0-SNAPSHOT")!!)
    include(implementation("org.spongepowered:noise:2.0.0-SNAPSHOT")!!)

    include(implementation("com.electronwill.night-config:core:3.6.0")!!)
    include(implementation("com.electronwill.night-config:toml:3.6.0")!!)

    extraTests(project(":CubicChunksCore")) {
        targetConfiguration = "testArchivesOutput"
    }

    compileOnly("com.google.code.findbugs:jsr305:3.0.1")
    testCompileOnly("com.google.code.findbugs:jsr305:3.0.1")

    testImplementation("org.junit.jupiter:junit-jupiter-api:5.9.0")
    testImplementation("org.junit.jupiter:junit-jupiter-params:5.9.0")
    testRuntimeOnly("org.junit.jupiter:junit-jupiter-engine:5.9.0")

    testImplementation("org.hamcrest:hamcrest-junit:2.0.0.0")
    testImplementation("org.hamcrest:hamcrest:2.2")
}

val jar: Jar by tasks
jar.apply {
    dependsOn(configurations["shade"])
    doFirst {
        from({
            project.configurations["shade"].map { if (it.isDirectory) it else zipTree(it) }.toList()
        })
    }
}

if (project.tasks.findByName("ideaSyncTask") != null) {
    project.tasks.findByName("ideaSyncTask")!!.dependsOn("CubicChunksCore:assemble")
}

// unzipping subproject (CubicChunksCore) tests
val unzipTests by tasks.creating(Copy::class) {
    outputs.upToDateWhen {
        false
    }
    dependsOn(configurations["extraTests"])
    from(configurations["extraTests"])
    doFirst {
        val testsFile = configurations["extraTests"].resolve().iterator().next()
        from(zipTree(testsFile))
        exclude(testsFile.name)
    }
    into(sourceSets.test.get().output.classesDirs.asPath)
}

// add explicit dependency to silence gradle warnings when building
tasks["checkstyleTest"].dependsOn(unzipTests)

val test: Test by tasks
test.apply {
    dependsOn(unzipTests)
    useJUnitPlatform()

    // Always run tests, even when nothing changed.
    dependsOn("cleanTest")

    // Show test results.
    testLogging {
        events("passed", "skipped", "failed")
    }
}

val processResources: ProcessResources by tasks
processResources.apply {
    inputs.property("version", project.version)

    filesMatching("fabric.mod.json") {
        expand("version" to project.version)
    }
}

// ensure that the encoding is set to UTF-8, no matter what the system default is
// this fixes some edge cases with special characters not displaying correctly
// see http://yodaconditions.net/blog/fix-for-java-file-encoding-problems-with-gradle.html
tasks.withType(JavaCompile::class) {
    options.encoding = "UTF-8"
}

// Loom will automatically attach sourcesJar to a RemapSourcesJar task and to the "build" task
// if it is present.
// If you remove this task, sources will not be generated.
val sourcesJar by tasks.creating(Jar::class) {
    dependsOn("classes")
    archiveClassifier.set("sources")
    from(sourceSets.main.get().allSource)
}

jar.apply {
    from("LICENSE")
    manifest {
        attributes(
                "Specification-Title" to modId,
                "Specification-Vendor" to "cubicchunks",
                "Specification-Version" to "1", // We are version 1 of ourselves
                "Implementation-Title" to project.name,
                "Implementation-Version" to archiveVersion.get(),
                "Implementation-Vendor" to "cubicchunks",
                "Implementation-Timestamp" to Date().toInstant().toString(),
                "MixinConnector" to "io.github.opencubicchunks.cubicchunks.mixin.CCMixinConnector",
                "accessWidener" to "cubicchunks.accesswidener"
        )
    }
}

// configure the maven publication
publishing {
    publications {
        val mavenJava by creating(MavenPublication::class)
        mavenJava.apply {
            // add all the jars that should be included when publishing to maven
            artifact("remapJar") {
                builtBy("remapJar")
            }
            artifact(sourcesJar) {
                builtBy("remapSourcesJar")
            }
        }
    }

    // select the repositories you want to publish to
    repositories {
        maven {
            setUrl("file:///${project.projectDir}/mcmodsrepo")
        }
    }
}


