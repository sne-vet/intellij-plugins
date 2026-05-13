import groovy.json.JsonSlurper
import java.io.File
import java.util.ArrayDeque
import java.util.Locale
import java.util.zip.ZipFile

plugins {
    id("java")
    kotlin("jvm")
    id("org.jetbrains.intellij.platform")
}

repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
    }
}

dependencies {
    intellijPlatform {
        intellijIdeaCommunity("2024.2")
        bundledPlugins("Git4Idea")
    }
}

intellijPlatform {
    pluginConfiguration {
        ideaVersion {
            sinceBuild = "242"
        }
    }
    buildSearchableOptions = false
}

data class LocalJetBrainsIde(
    val productInfo: File,
    val home: File,
    val name: String,
    val version: String,
    val buildNumber: String,
    val dataDirectoryName: String,
    val pluginsDir: File,
)

fun localJetBrainsConfigRoot(): File {
    requireMacOsLocalInstall()
    return File(System.getProperty("user.home"), "Library/Application Support/JetBrains")
}

fun requireMacOsLocalInstall() {
    val osName = System.getProperty("os.name")
    if (!osName.lowercase(Locale.US).contains("mac")) {
        throw GradleException("Local JetBrains IDE install is only supported on macOS. Current OS: $osName")
    }
}

fun productInfoHome(productInfo: File): File {
    val macSuffix = "${File.separator}Contents${File.separator}Resources${File.separator}product-info.json"
    val path = productInfo.absolutePath

    return if (path.endsWith(macSuffix) && path.removeSuffix(macSuffix).endsWith(".app")) {
        File(path.removeSuffix(macSuffix))
    } else {
        productInfo.parentFile
    }
}

@Suppress("UNCHECKED_CAST")
fun jetBrainsIdeFromProductInfo(productInfo: File): LocalJetBrainsIde? {
    if (!productInfo.isFile) {
        return null
    }

    val data = JsonSlurper().parse(productInfo) as? Map<String, Any?> ?: return null
    val dataDirectoryName = data["dataDirectoryName"]?.toString()?.takeIf { it.isNotBlank() } ?: return null
    val home = productInfoHome(productInfo)
    val name = data["name"]?.toString()?.takeIf { it.isNotBlank() } ?: home.name.removeSuffix(".app")

    return LocalJetBrainsIde(
        productInfo = productInfo,
        home = home,
        name = name,
        version = data["version"]?.toString().orEmpty(),
        buildNumber = data["buildNumber"]?.toString().orEmpty(),
        dataDirectoryName = dataDirectoryName,
        pluginsDir = File(localJetBrainsConfigRoot(), "$dataDirectoryName/plugins"),
    )
}

fun findMacAppBundles(root: File): Sequence<File> = sequence {
    if (!root.isDirectory) {
        return@sequence
    }

    val pending = ArrayDeque<File>()
    pending.add(root)

    while (!pending.isEmpty()) {
        val directory = pending.removeFirst()
        directory.listFiles()
            ?.filter { it.isDirectory }
            ?.forEach { child ->
                if (child.name.endsWith(".app", ignoreCase = true)) {
                    yield(child)
                } else {
                    pending.add(child)
                }
            }
    }
}

fun findProductInfoFiles(root: File): Sequence<File> =
    if (!root.isDirectory) {
        emptySequence()
    } else {
        root.walkTopDown().filter { it.isFile && it.name == "product-info.json" }
    }

fun discoverLocalJetBrainsIdes(): List<LocalJetBrainsIde> {
    requireMacOsLocalInstall()
    val userHome = File(System.getProperty("user.home"))
    val productInfos = sequenceOf(
        File("/Applications"),
        File(userHome, "Applications"),
        File(userHome, "Library/Application Support/JetBrains/Toolbox/apps"),
    )
        .flatMap(::findMacAppBundles)
        .map { it.resolve("Contents/Resources/product-info.json") }

    return productInfos
        .mapNotNull(::jetBrainsIdeFromProductInfo)
        .distinctBy { it.productInfo.canonicalPath }
        .sortedWith(compareBy<LocalJetBrainsIde> { it.name }.thenByDescending { it.version })
        .toList()
}

fun localJetBrainsIdeFromPath(path: String): LocalJetBrainsIde {
    val idePath = File(path).absoluteFile
    val productInfo = when {
        idePath.resolve("Contents/Resources/product-info.json").isFile ->
            idePath.resolve("Contents/Resources/product-info.json")
        idePath.resolve("product-info.json").isFile ->
            idePath.resolve("product-info.json")
        idePath.isDirectory -> {
            val matches = findProductInfoFiles(idePath).toList()
            when (matches.size) {
                1 -> matches.single()
                0 -> null
                else -> throw GradleException(
                    buildString {
                        appendLine("Found multiple JetBrains product-info.json files under: $idePath")
                        matches.sortedBy { it.absolutePath }.forEach { appendLine(" - $it") }
                        append("Provide a more specific -PlocalIdePath, or point directly to the IDE installation.")
                    },
                )
            }
        }
        else -> null
    } ?: throw GradleException("Could not find a JetBrains product-info.json under: $idePath")

    return jetBrainsIdeFromProductInfo(productInfo)
        ?: throw GradleException("Could not read JetBrains product info from: $productInfo")
}

fun LocalJetBrainsIde.displayName(): String = buildString {
    append(name)
    if (version.isNotBlank()) {
        append(" ")
        append(version)
    }
    if (buildNumber.isNotBlank()) {
        append(" (")
        append(buildNumber)
        append(")")
    }
}

fun printLocalJetBrainsIdes(ides: List<LocalJetBrainsIde>) {
    if (ides.isEmpty()) {
        println("No JetBrains IDE installations found.")
        return
    }

    println("Discovered JetBrains IDEs:")
    ides.forEachIndexed { index, ide ->
        println("%2d) %s".format(index + 1, ide.displayName()))
        println("    app:     ${ide.home}")
        println("    plugins: ${ide.pluginsDir}")
    }
}

fun configuredLocalJetBrainsIdes(): List<LocalJetBrainsIde> {
    val configuredPath = providers.gradleProperty("localIdePath").orNull
    return if (configuredPath != null) {
        listOf(localJetBrainsIdeFromPath(configuredPath))
    } else {
        discoverLocalJetBrainsIdes()
    }
}

fun selectLocalJetBrainsIde(ides: List<LocalJetBrainsIde>): LocalJetBrainsIde {
    if (ides.isEmpty()) {
        throw GradleException("No JetBrains IDE installations found. Re-run with -PlocalIdePath=/path/to/ide if it is installed somewhere custom.")
    }

    printLocalJetBrainsIdes(ides)

    val configuredIndex = providers.gradleProperty("localIdeIndex").orNull
    if (configuredIndex != null) {
        val index = configuredIndex.toIntOrNull()
            ?: throw GradleException("localIdeIndex must be a number from 1 to ${ides.size}")

        return ides.getOrNull(index - 1)
            ?: throw GradleException("localIdeIndex must be a number from 1 to ${ides.size}")
    }

    if (ides.size == 1) {
        println()
        println("Using the only discovered IDE: ${ides.single().displayName()}")
        return ides.single()
    }

    while (true) {
        print("Install into which IDE? [1-${ides.size}] ")
        val choice = readlnOrNull()?.trim()
            ?: throw GradleException("Multiple IDEs found, but no interactive input is available. Re-run with -PlocalIdeIndex=1 or -PlocalIdePath=/path/to/ide.")

        val index = choice.toIntOrNull()
        if (index != null && index in 1..ides.size) {
            return ides[index - 1]
        }

        println("Choose a number from 1 to ${ides.size}.")
    }
}

fun latestPluginDistributionZip(): File {
    val distributionsDir = layout.buildDirectory.dir("distributions").get().asFile

    return distributionsDir
        .walkTopDown()
        .filter { it.isFile && it.extension == "zip" }
        .maxByOrNull { it.lastModified() }
        ?: throw GradleException("No plugin ZIP found in $distributionsDir")
}

fun pluginRootDirectory(pluginZip: File): String =
    ZipFile(pluginZip).use { zipFile ->
        zipFile.entries().asSequence()
            .map { it.name.substringBefore("/") }
            .firstOrNull { it.isNotBlank() }
    } ?: throw GradleException("Could not determine plugin root directory in ZIP: $pluginZip")

tasks.register("listLocalJetBrainsIdes") {
    group = "intellij"
    description = "Lists locally installed JetBrains IDEs that can receive this plugin."

    doLast {
        printLocalJetBrainsIdes(configuredLocalJetBrainsIdes())
    }
}

tasks.register("install") {
    group = "intellij"
    description = "Builds the plugin and installs the newest distribution ZIP into a local JetBrains IDE."

    dependsOn(tasks.named("buildPlugin"))

    doLast {
        val selectedIde = selectLocalJetBrainsIde(configuredLocalJetBrainsIdes())
        val pluginZip = latestPluginDistributionZip()
        val pluginRoot = pluginRootDirectory(pluginZip)
        val targetPluginDir = selectedIde.pluginsDir.resolve(pluginRoot)

        println()
        println("Installing ${pluginZip.name}")
        println("Target IDE: ${selectedIde.displayName()}")
        println("Plugins directory: ${selectedIde.pluginsDir}")

        if (targetPluginDir.exists()) {
            println("Removing previous install: $targetPluginDir")
            val removed = targetPluginDir.deleteRecursively()
            if (!removed && targetPluginDir.exists()) {
                throw GradleException("Failed to remove previous install: $targetPluginDir")
            }
        }

        selectedIde.pluginsDir.mkdirs()

        copy {
            from(zipTree(pluginZip))
            into(selectedIde.pluginsDir)
        }

        println()
        println("Installed to: $targetPluginDir")
        println("Restart the IDE to load the updated plugin.")
    }
}
