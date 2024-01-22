import java.net.URL
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

fun httpGet(url: String): String {
    return URL(url).readText().trim()
}

fun getLatestBattlecodeVersion(): String {
    return Regex("\\\"release_version_public\\\":\\\"([^\"]+)\\\"")
        .find(httpGet("https://api.battlecode.org/api/episode/e/bc24/?format=json"))!!
        .groups[1]!!
        .value
}

fun getLatestExamplefuncsplayer(): String {
    val sourceUrl =
        "https://github.com/battlecode/battlecode24-scaffold/blob/main/src/examplefuncsplayer/RobotPlayer.java"
    val rawUrl =
        "https://raw.githubusercontent.com/battlecode/battlecode24-scaffold/main/src/examplefuncsplayer/RobotPlayer.java"

    val examplefuncsplayer = httpGet(rawUrl)
        .replace("System.out.", "// System.out.")
        .replace("e.printStackTrace()", "// e.printStackTrace()") + "\n"

    return "// Source: $sourceUrl\n\n$examplefuncsplayer"
}

fun announce(message: String) {
    val topBottom = "+${"-".repeat(message.length + 2)}+"
    val block = """
        $topBottom
        | $message |
        $topBottom
    """.trimIndent()

    logger.quiet("\n$block")
}

val projectDirectory = layout.projectDirectory.asFile
val buildDirectory = layout.buildDirectory.asFile.get()

val clientType = with(System.getProperty("os.name").lowercase()) {
    when {
        startsWith("windows") -> "win"
        startsWith("mac") -> "mac"
        else -> "linux"
    }
}

val clientName = "battlecode24-client-$clientType-electron"

val battlecodeVersionFile = projectDirectory.resolve("version.txt")
val examplefuncsplayerFile = projectDirectory.resolve("src/examplefuncsplayer/RobotPlayer.java")

val currentBattlecodeVersion = battlecodeVersionFile.readText().trim()

plugins {
    java
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

sourceSets {
    main {
        java.srcDirs("src")
        java.destinationDirectory.set(buildDirectory.resolve("classes"))
    }

    test {
        java.srcDirs("test")
        java.destinationDirectory.set(buildDirectory.resolve("tests"))
    }
}

configurations {
    create("client")
    all {
        resolutionStrategy.cacheDynamicVersionsFor(60, TimeUnit.SECONDS)
    }
}

repositories {
    mavenCentral()
    maven("https://releases.battlecode.org/maven")
}

dependencies {
    implementation("org.battlecode:battlecode24:$currentBattlecodeVersion")
    implementation("org.battlecode:battlecode24:$currentBattlecodeVersion:javadoc")
    add("client", "org.battlecode:$clientName:$currentBattlecodeVersion")
}

task("checkForUpdates") {
    group = "battlecode"
    description = "Checks for Battlecode updates."

    doLast {
        val latestBattlecodeVersion = getLatestBattlecodeVersion()
        if (currentBattlecodeVersion != latestBattlecodeVersion) {
            announce("Battlecode update available ($currentBattlecodeVersion -> $latestBattlecodeVersion)")
        }

        if (examplefuncsplayerFile.readText() != getLatestExamplefuncsplayer()) {
            announce("examplefuncsplayer update available")
        }
    }
}

task("update") {
    group = "battlecode"
    description = "Updates to the latest Battlecode version."

    doLast {
        val latestBattlecodeVersion = getLatestBattlecodeVersion()
        if (currentBattlecodeVersion == latestBattlecodeVersion) {
            logger.quiet("Already using the latest Battlecode version ($currentBattlecodeVersion)")
        } else {
            battlecodeVersionFile.writeText(latestBattlecodeVersion + "\n")
            logger.quiet("Updated Battlecode from $currentBattlecodeVersion to $latestBattlecodeVersion, please reload the Gradle project")
        }

        val latestExamplefuncsplayer = getLatestExamplefuncsplayer()
        if (examplefuncsplayerFile.readText() == latestExamplefuncsplayer) {
            logger.quiet("Already using the latest examplefuncsplayer")
        } else {
            examplefuncsplayerFile.writeText(latestExamplefuncsplayer)
            logger.quiet("Updated examplefuncsplayer to the latest version")
        }
    }
}

task<Copy>("unpackClient") {
    group = "battlecode"
    description = "Unpacks the client."

    dependsOn(configurations["client"], "checkForUpdates")

    from(configurations["client"].map { zipTree(it) })
    into("client/")
}

task<JavaExec>("run") {
    group = "battlecode"
    description = "Runs a match without starting the client."

    dependsOn("build")

    val classLocation = sourceSets["main"].output.classesDirs.asPath
    val replayPath = project.property("replayPath").toString()
        .replace("%TEAM_A%", project.property("teamA").toString())
        .replace("%TEAM_B%", project.property("teamB").toString())
        .replace("%MAP%", project.property("maps").toString())

    mainClass.set("battlecode.server.Main")
    classpath = sourceSets["main"].runtimeClasspath
    args = listOf("-c=-")
    jvmArgs = listOf(
        "-Dbc.server.wait-for-client=${project.findProperty("waitForClient") ?: "false"}",
        "-Dbc.server.websocket=${project.findProperty("waitForClient") ?: "false"}",
        "-Dbc.server.port=${project.findProperty("serverPort") ?: "6175"}",
        "-Dbc.server.validate-maps=${project.findProperty("validateMaps") ?: "true"}",
        "-Dbc.server.alternate-order=${project.findProperty("alternateOrder") ?: "false"}",
        "-Dbc.server.mode=headless",
        "-Dbc.server.map-path=maps",
        "-Dbc.engine.robot-player-to-system-out=${project.property("outputVerbose") ?: "true"}",
        "-Dbc.server.debug=false",
        "-Dbc.engine.debug-methods=${project.property("debug") ?: "false"}",
        "-Dbc.engine.show-indicators=${project.property("showIndicators") ?: "true"}",
        "-Dbc.engine.enable-profiler=${project.property("enableProfiler")}",
        "-Dbc.game.team-a=${project.property("teamA")}",
        "-Dbc.game.team-b=${project.property("teamB")}",
        "-Dbc.game.team-a.url=$classLocation",
        "-Dbc.game.team-b.url=$classLocation",
        "-Dbc.game.team-a.package=${project.property("teamA")}",
        "-Dbc.game.team-b.package=${project.property("teamB")}",
        "-Dbc.game.maps=${project.property("maps")}",
        "-Dbc.server.save-file=${replayPath}"
    )
}

task("listMaps") {
    group = "battlecode"
    description = "Lists all available maps."

    doLast {
        val officialMapFiles =
            zipTree(sourceSets["main"].compileClasspath.first { it.toString().contains("battlecode24-") })
        val customMapFiles = fileTree(projectDirectory.resolve("maps"))

        val maps = (officialMapFiles + customMapFiles)
            .filter { it.name.endsWith(".map24") }
            .map { it.name.substringBeforeLast(".map24") }
            .distinct()
            .sortedBy { it.lowercase() }

        logger.quiet("Maps (${maps.size}):")
        for (map in maps) {
            logger.quiet(map)
        }
    }
}

task<Zip>("createSubmission") {
    group = "battlecode"
    description = "Creates a submission zip."

    dependsOn("build")

    from(file("src").absolutePath)
    include("camel_case/**/*")
    archiveBaseName.set(DateTimeFormatter.ofPattern("yyyy-MM-dd_kk-mm-ss").format(LocalDateTime.now()))
    destinationDirectory.set(buildDirectory.resolve("submissions"))
}

tasks.named("build") {
    group = "battlecode"

    dependsOn("unpackClient")
}
