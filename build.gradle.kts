import java.awt.RenderingHints
import java.awt.image.BufferedImage
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.imageio.ImageIO
import org.gradle.internal.os.OperatingSystem

plugins {
    application
}

// Version de l'app : -PappVersion=1.2.3 (la CI de release la tire du tag v1.2.3). macOS refuse 0.x.
version = providers.gradleProperty("appVersion").getOrElse("1.0.0")

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.googlecode.lanterna:lanterna:3.1.5")
    // Lecture des musiques OGG Vorbis (Java pur, LGPL 2+, jars séparés : voir licenses/THIRD-PARTY.md).
    runtimeOnly("com.googlecode.soundlibs:vorbisspi:1.0.3.3")
    runtimeOnly("com.googlecode.soundlibs:jorbis:0.0.17.4")
    runtimeOnly("com.googlecode.soundlibs:tritonus-share:0.3.7.4")

    testImplementation(platform("org.junit:junit-bom:5.14.4"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
}

tasks.withType<JavaCompile>().configureEach {
    options.release = 21
    options.encoding = "UTF-8"
}

application {
    mainClass = "cardistry.Main"
}

// Nom de jar fixe (jpackage le désigne comme jar principal), quelle que soit la version.
tasks.jar {
    archiveFileName.set("cardistry.jar")
}

tasks.named<JavaExec>("run") {
    // Le jeu lit le clavier : on branche l'entrée standard du terminal.
    standardInput = System.`in`
}

tasks.test {
    useJUnitPlatform()
    // Rendu de la fenêtre testé dans des images, jamais à l'écran (CI sans affichage).
    systemProperty("java.awt.headless", "true")
    if (project.hasProperty("trace")) {
        systemProperty("trace", "true")
    }
    // Les tests ne jouent jamais de son et n'écrivent pas de debug.log.
    systemProperty("cardistry.audio", "off")
    systemProperty("cardistry.log", "off")
}

// ===================== Art (Aseprite) =====================
// Sources dans art/ : icônes (une frame taguée par icône, le tag = l'id) et sprites d'adversaires
// (art/enemies/<id>.aseprite et art/npc/<id>.aseprite, un tag par animation). Les PNG exportés sont commités dans
// src/main/resources/images/. Sans Aseprite (CI, autre machine), generateArt et exportArt sont
// ignorées et les PNG commités restent utilisés.
// ./gradlew art         : tout d'un coup = generateArt, exportArt, planche
// ./gradlew generateArt : ajoute aux .aseprite les ébauches MANQUANTES (art/tools/generate.lua) ;
//                         un tag déjà présent n'est jamais redessiné
// ./gradlew exportArt   : art/<fichier>.aseprite → images/<dossier>/<tag>.png ;
//                         art/enemies/<id>.aseprite → images/enemies/<id>.<animation>.<n>.png + <id>.png
// ./gradlew planche     : art/planche.png (icônes) et art/planche-ennemis.png (frames des adversaires)

/** Fichier source (sans extension) → dossier de sortie dans images/. */
val artExports = mapOf("map" to "map", "items" to "items", "trinkets" to "items", "cards" to "cards",
        "statuses" to "status")

/** Chemin d'Aseprite : variable ASEPRITE, sinon « aseprite » dans le PATH ; null s'il est absent. */
fun findAseprite(): String? {
    System.getenv("ASEPRITE")?.takeIf { File(it).canExecute() }?.let { return it }
    val names = if (OperatingSystem.current().isWindows) listOf("aseprite.exe", "Aseprite.exe") else listOf("aseprite")
    return (System.getenv("PATH") ?: "").split(File.pathSeparator)
            .flatMap { dir -> names.map { File(dir, it) } }
            .firstOrNull { it.isFile && it.canExecute() }?.absolutePath
}

fun runAseprite(vararg args: String) {
    val process = ProcessBuilder(*args).inheritIO().start()
    if (process.waitFor() != 0) {
        throw GradleException("Aseprite en échec : ${args.joinToString(" ")}")
    }
}

val asepritePath = findAseprite()
val artDir = layout.projectDirectory.dir("art").asFile
val imagesDir = layout.projectDirectory.dir("src/main/resources/images").asFile

fun Task.needsAseprite() {
    onlyIf {
        if (asepritePath == null) {
            logger.lifecycle("Aseprite introuvable : $name ignorée, les PNG commités restent utilisés.")
        }
        asepritePath != null
    }
}

val generateArt by tasks.registering {
    group = "art"
    description = "Ajoute aux sources Aseprite les ébauches manquantes, sans toucher aux dessins existants."
    needsAseprite()
    doLast {
        runAseprite(asepritePath!!, "-b", "--script-param", "root=${layout.projectDirectory.asFile.path}",
                "--script", File(artDir, "tools/generate.lua").path)
    }
}

val exportArt by tasks.registering {
    group = "art"
    description = "Exporte les sources Aseprite (art/) vers src/main/resources/images/."
    mustRunAfter(generateArt)
    needsAseprite()
    doLast {
        for ((source, folder) in artExports) {
            val file = File(artDir, "$source.aseprite")
            if (file.isFile) {
                val out = File(imagesDir, folder)
                out.mkdirs()
                runAseprite(asepritePath!!, "-b", file.path, "--save-as", File(out, "{tag}.png").path)
            }
        }
        // Sprites animés : adversaires, personnages non joueurs (marchand), personnages jouables et
        // familier (art/player/), un .aseprite par id.
        for (folder in listOf("enemies", "npc", "player")) {
            val out = File(imagesDir, folder)
            out.mkdirs()
            for (file in File(artDir, folder).listFiles { f -> f.name.endsWith(".aseprite") }.orEmpty().sorted()) {
                val id = file.nameWithoutExtension
                // Anciennes frames d'abord : une animation raccourcie ne laisse pas de frame fantôme.
                out.listFiles { f -> f.name.startsWith("$id.") && f.name.endsWith(".png") }.orEmpty()
                        .forEach { it.delete() }
                runAseprite(asepritePath!!, "-b", file.path, "--save-as",
                        File(out, "$id.{tag}.{tagframe1}.png").path)
                // Image de base (animation absente) : la première frame idle.
                val idle = File(out, "$id.idle.1.png")
                if (idle.isFile) {
                    idle.copyTo(File(out, "$id.png"), overwrite = true)
                }
            }
        }
    }
}

val planche by tasks.registering(JavaExec::class) {
    group = "art"
    description = "Dessine art/planche.png : toutes les icônes avec leur nom et leur id."
    // Classes de test (l'outil) + jeu, sans les ressources de test (elles contiennent de fausses images).
    classpath = sourceSets["test"].output.classesDirs + sourceSets["main"].runtimeClasspath
    mainClass = "cardistry.tools.ArtSheet"
    args("art/planche.png", "art/planche-ennemis.png")
    systemProperty("java.awt.headless", "true")
    if (project.hasProperty("trace")) {
        systemProperty("trace", "true")
    }
    mustRunAfter(exportArt)
}

// ./gradlew animationCheck : ouvre la vraie fenêtre, joue tout seul et trace les animations affichées.
val animationCheck by tasks.registering(JavaExec::class) {
    group = "art"
    description = "Joue des combats dans la fenêtre et trace les animations réellement affichées."
    classpath = sourceSets["test"].output.classesDirs + sourceSets["main"].runtimeClasspath
    mainClass = "cardistry.tools.AnimationCheck"
    systemProperty("cardistry.audio", "off")
    if (project.hasProperty("enemies")) {
        args((project.property("enemies") as String).split(","))
    }
}

// ./gradlew balanceRun [-Pseeds=N] [-Pheroes=id,id] : parties complètes jouées par un bot, chiffres d'équilibrage.
val balanceRun by tasks.registering(JavaExec::class) {
    group = "verification"
    description = "Joue des parties complètes avec chaque personnage et écrit les chiffres d'équilibrage."
    classpath = sourceSets["test"].output.classesDirs + sourceSets["main"].runtimeClasspath
    mainClass = "cardistry.tools.BalanceRun"
    dependsOn("testClasses")
    systemProperty("cardistry.audio", "off")
    systemProperty("cardistry.log", "off")
    systemProperty("java.awt.headless", "true")
    if (project.hasProperty("trace")) {
        systemProperty("trace", "true")
    }
    if (project.hasProperty("seeds")) {
        systemProperty("seeds", project.property("seeds") as String)
    }
    if (project.hasProperty("heroes")) {
        systemProperty("heroes", project.property("heroes") as String)
    }
}

val art by tasks.registering {
    group = "art"
    description = "Ébauches manquantes, export des PNG et planches, en une commande."
    dependsOn(generateArt, exportArt, planche)
}

// ===================== Packaging (jpackage) =====================
// ./gradlew jpackageImage : l'app autonome (Java embarqué) dans build/jpackage/out, pour tester.
// ./gradlew jpackage      : les installateurs de l'OS courant (macOS .dmg ; Windows .msi + .zip
//                           portable ; Linux .deb + .rpm). Pas de compilation croisée : un OS à la fois.

val appName = "Cardistry"
val appId = "io.github.megaray.cardistry"
val packagingDir = layout.buildDirectory.dir("jpackage")
val iconsDir = layout.buildDirectory.dir("icons")
val os: OperatingSystem = OperatingSystem.current()

/** Icône agrandie d'un facteur entier, sans lissage (pixel art). */
fun scaledIcon(base: BufferedImage, size: Int): BufferedImage {
    val out = BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB)
    val g = out.createGraphics()
    g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
            RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR)
    g.drawImage(base, 0, 0, size, size, null)
    g.dispose()
    return out
}

/** Fichier .ico (Windows) : une entrée PNG par taille. */
fun writeIco(base: BufferedImage, sizes: List<Int>, target: File) {
    val pngs = sizes.map { size ->
        ByteArrayOutputStream().also { ImageIO.write(scaledIcon(base, size), "png", it) }.toByteArray()
    }
    val header = ByteBuffer.allocate(6 + 16 * sizes.size).order(ByteOrder.LITTLE_ENDIAN)
    header.putShort(0).putShort(1).putShort(sizes.size.toShort())
    var offset = 6 + 16 * sizes.size
    sizes.forEachIndexed { i, size ->
        val dim = if (size >= 256) 0 else size
        header.put(dim.toByte()).put(dim.toByte()).put(0).put(0).putShort(1).putShort(32)
        header.putInt(pngs[i].size).putInt(offset)
        offset += pngs[i].size
    }
    target.outputStream().use { out ->
        out.write(header.array())
        pngs.forEach(out::write)
    }
}

val appIcons by tasks.registering {
    description = "Icônes de l'app (.png, .ico, iconset macOS) depuis l'icône pixel 16 px."
    val source = file("src/main/resources/icon/cardistry-16.png")
    inputs.file(source)
    outputs.dir(iconsDir)
    doLast {
        val dir = iconsDir.get().asFile
        dir.mkdirs()
        val base = ImageIO.read(source)
        ImageIO.write(scaledIcon(base, 512), "png", File(dir, "cardistry.png"))
        writeIco(base, listOf(16, 32, 48, 64, 128, 256), File(dir, "cardistry.ico"))
        val iconset = File(dir, "cardistry.iconset")
        iconset.mkdirs()
        for (size in listOf(16, 32, 128, 256, 512)) {
            ImageIO.write(scaledIcon(base, size), "png", File(iconset, "icon_${size}x$size.png"))
            ImageIO.write(scaledIcon(base, size * 2), "png", File(iconset, "icon_${size}x$size@2x.png"))
        }
        if (os.isMacOsX) {
            runCommand(listOf("iconutil", "-c", "icns", iconset.absolutePath, "-o",
                    File(dir, "cardistry.icns").absolutePath))
        }
    }
}

val jpackageInput by tasks.registering(Sync::class) {
    description = "Jars du jeu et licences, tels que jpackage les embarque."
    dependsOn("installDist")
    into(packagingDir.map { it.dir("input") })
    from(layout.buildDirectory.dir("install/cardistry/lib"))
    into("licenses") {
        from("licenses")
        from("src/main/resources/fonts") { include("OFL-*.txt") }
    }
}

/** Lance une commande en affichant sa sortie ; échoue si elle échoue. */
fun runCommand(args: List<String>) {
    val process = ProcessBuilder(args).inheritIO().start()
    val code = process.waitFor()
    if (code != 0) {
        throw GradleException("Échec (code $code) : ${args.joinToString(" ")}")
    }
}

fun jpackageExecutable(): String {
    val name = if (os.isWindows) "jpackage.exe" else "jpackage"
    return File(File(System.getProperty("java.home"), "bin"), name).absolutePath
}

/** Arguments communs à tous les types de paquets. */
fun jpackageArgs(type: String): List<String> {
    val icons = iconsDir.get().asFile
    val icon = when {
        os.isMacOsX -> File(icons, "cardistry.icns")
        os.isWindows -> File(icons, "cardistry.ico")
        else -> File(icons, "cardistry.png")
    }
    val args = mutableListOf(
        jpackageExecutable(),
        "--type", type,
        "--name", appName,
        "--app-version", version.toString(),
        "--vendor", "Cardistry",
        "--description", "Jeu de cartes roguelite au tour par tour, à l'esthétique terminal",
        "--input", packagingDir.get().dir("input").asFile.absolutePath,
        "--main-jar", "cardistry.jar",
        "--main-class", "cardistry.Main",
        "--add-modules", "java.base,java.desktop,jdk.charsets",
        "--jlink-options", "--strip-debug --no-man-pages --no-header-files --strip-native-commands",
        "--java-options", "-Dfile.encoding=UTF-8",
        "--icon", icon.absolutePath,
        "--dest", packagingDir.get().dir("out").asFile.absolutePath,
    )
    if (type != "app-image") {
        when {
            os.isMacOsX -> args += listOf("--mac-package-identifier", appId, "--mac-package-name", appName,
                    "--mac-app-category", "public.app-category.games")
            os.isWindows -> args += listOf("--win-menu", "--win-menu-group", appName, "--win-shortcut",
                    "--win-dir-chooser", "--win-per-user-install",
                    // Identifiant fixe : une nouvelle version remplace l'ancienne.
                    "--win-upgrade-uuid", "6f3c2a1e-6c64-4f6e-9a3b-2c1d5e8f7a90")
            else -> args += listOf("--linux-package-name", "cardistry", "--linux-shortcut",
                    "--linux-menu-group", "Game", "--linux-app-category", "games")
        }
    } else if (os.isMacOsX) {
        args += listOf("--mac-package-identifier", appId)
    }
    return args
}

/** Types d'installateurs de l'OS courant ; -PinstallerTypes=rpm pour n'en faire qu'un. */
fun installerTypes(): List<String> = providers.gradleProperty("installerTypes").orNull
        ?.split(",")?.map(String::trim)?.filter(String::isNotEmpty) ?: when {
    os.isMacOsX -> listOf("dmg")
    os.isWindows -> listOf("msi")
    else -> listOf("deb", "rpm")
}

val jpackageImage by tasks.registering {
    group = "distribution"
    description = "App autonome (Java embarqué), sans installateur : build/jpackage/out/Cardistry."
    dependsOn(jpackageInput, appIcons)
    doLast {
        val out = packagingDir.get().dir("out").asFile
        delete(File(out, if (os.isMacOsX) "$appName.app" else appName))
        runCommand(jpackageArgs("app-image"))
    }
}

val jpackage by tasks.registering {
    group = "distribution"
    description = "Installateurs de l'OS courant dans build/jpackage/out (dmg ; msi + zip ; deb + rpm)."
    dependsOn(jpackageInput, appIcons)
    doLast {
        for (type in installerTypes()) {
            runCommand(jpackageArgs(type))
        }
    }
}

val jpackageZip by tasks.registering(Zip::class) {
    group = "distribution"
    description = "Windows : l'app autonome en .zip portable (sans installation)."
    dependsOn(jpackageImage)
    from(packagingDir.map { it.dir("out/$appName") }) { into(appName) }
    archiveFileName.set("$appName-$version-windows-portable.zip")
    destinationDirectory.set(packagingDir.map { it.dir("out") })
}

if (os.isWindows) {
    jpackage.configure { finalizedBy(jpackageZip) }
}
