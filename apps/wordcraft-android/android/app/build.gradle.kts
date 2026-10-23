import java.net.URI
import java.security.MessageDigest
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import org.gradle.process.ExecOperations

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// The Rust half of the app: this Gradle project is the android/ folder of the `wordcraft-android`
// crate (apps/wordcraft-android), which sits in the WordCraft cargo workspace two folders up.
val cargoWorkspaceDir: File = rootDir.parentFile.parentFile.parentFile
val rustAbis: List<String> =
    providers.gradleProperty("wordcraft.abis").getOrElse("arm64-v8a,x86_64").split(',').map { it.trim() }.filter { it.isNotEmpty() }
val minApi = 29

// The app's version: gradle.properties' wordcraft.version, or the version tag's when the release
// workflow builds a release. The version code follows from it (0.0.1-beta → 101, 0.0.1-beta.2 →
// 102, 0.0.1 → 199, 0.1.0-beta → 10001), so a pre-release comes before its release and every
// release before the next version's pre-releases; times ten plus the ABI of a one-ABI build
// (armeabi-v7a 1, arm64-v8a 2, x86_64 3; 0 for several), so each APK of a release has its own code
// (F-Droid needs that).
val appVersion: String = providers.gradleProperty("wordcraft.version").get()
val appVersionCode: Int = run {
    val m = requireNotNull(Regex("""(\d+)\.(\d+)\.(\d+)(-[0-9A-Za-z-]+?(\.(\d+))?)?""").matchEntire(appVersion)) {
        "wordcraft.version $appVersion is not a version like 1.2.3, 1.2.3-beta or 1.2.3-beta.4"
    }
    val (major, minor, patch) = m.groupValues.subList(1, 4).map { it.toInt() }
    val pre = if (m.groupValues[4].isEmpty()) null else m.groupValues[6].toIntOrNull() ?: 1
    require(minor < 100 && patch < 100 && (pre ?: 1) in 1..98) {
        "wordcraft.version $appVersion: minor and patch must be under 100, and a pre-release's number 1 to 98"
    }
    val base = ((major * 100 + minor) * 100 + patch) * 100 + (pre ?: 99)
    providers.gradleProperty("wordcraft.versionCode").orNull?.let { listed ->
        require(listed.trim().toIntOrNull() == base) {
            "gradle.properties: wordcraft.versionCode=$listed, but version $appVersion's is $base (set both before tagging)"
        }
    }
    val abi = when (rustAbis.singleOrNull()) {
        "armeabi-v7a" -> 1
        "arm64-v8a" -> 2
        "x86_64" -> 3
        else -> 0
    }
    base * 10 + abi
}

// The release key, when the build is given one (the release workflow passes it from the repository's
// secrets). Without one a release build is signed with the debug key: it installs, but it can't
// update a published release; or, with -Pwordcraft.unsigned=true (F-Droid's build), not signed at
// all, so the published APK's signature can be checked against it.
val releaseKeystore: String? = providers.environmentVariable("WORDCRAFT_KEYSTORE").orNull
val unsignedRelease: Boolean = providers.gradleProperty("wordcraft.unsigned").orNull == "true"

/** rustup's `cargo` (Android Studio may not see rustup's PATH), else `cargo` from PATH; `CARGO` overrides. */
val cargoExecutable: String = run {
    val exe = if (System.getProperty("os.name").lowercase().startsWith("windows")) "cargo.exe" else "cargo"
    val rustup = File(System.getProperty("user.home"), ".cargo/bin/$exe")
    providers.environmentVariable("CARGO").orNull ?: if (rustup.isFile) rustup.absolutePath else exe
}

/** Builds `libwordcraft_android.so` (release profile) for each ABI with cargo-ndk. */
abstract class CargoNdkBuild : DefaultTask() {
    @get:Inject abstract val execOperations: ExecOperations
    @get:Input abstract val cargo: Property<String>
    @get:Input abstract val workspaceDir: Property<String>
    @get:Input abstract val ndkDir: Property<String>
    @get:Input abstract val abis: ListProperty<String>
    @get:Input abstract val apiLevel: Property<Int>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun build() {
        val targets = abis.get().flatMap { listOf("-t", it) }
        val out = outputDir.get().asFile.absolutePath
        // Reproducible: the library mustn't hold the paths it was built in (panic locations name
        // source files), so the same commit builds byte-identical here, on CI and on F-Droid's
        // build server. Encoded flags (0x1f-separated) survive spaces in the paths.
        val cargoHome = System.getenv("CARGO_HOME")?.takeIf { it.isNotBlank() } ?: File(System.getProperty("user.home"), ".cargo").absolutePath
        val remap = listOf(
            "--remap-path-prefix=${File(workspaceDir.get()).absolutePath}=/wordcraft",
            "--remap-path-prefix=$cargoHome=/cargo",
        )
        execOperations.exec {
            workingDir(workspaceDir.get())
            environment("ANDROID_NDK_HOME", ndkDir.get())
            environment("CARGO_ENCODED_RUSTFLAGS", remap.joinToString("\u001f"))
            commandLine(
                listOf(cargo.get(), "ndk") + targets +
                    listOf("--platform", apiLevel.get().toString(), "-o", out, "build", "--release", "-p", "wordcraft-android", "--lib"),
            )
        }
        // cargo-ndk copies every shared library cargo left behind (dylib builds of a few
        // dependencies); the app's library links them statically, so only it ships
        File(out).walk().filter { it.isFile && it.name != "libwordcraft_android.so" }.forEach { it.delete() }
    }
}

/** Generates the Kotlin bindings from the library cargo built (UniFFI library mode). */
abstract class UniffiBindgen : DefaultTask() {
    @get:Inject abstract val execOperations: ExecOperations
    @get:Input abstract val cargo: Property<String>
    @get:Input abstract val workspaceDir: Property<String>
    @get:InputFile @get:PathSensitive(PathSensitivity.NONE) abstract val library: RegularFileProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        out.mkdirs()
        execOperations.exec {
            workingDir(workspaceDir.get())
            commandLine(
                cargo.get(), "run", "--quiet", "-p", "wordcraft-android-bindgen", "--",
                "generate", library.get().asFile.absolutePath, "--language", "kotlin", "--out-dir", out.absolutePath, "--no-format",
            )
        }
    }
}

/**
 * Files from WordCraft's `assets/` the app ships, copied at build time rather than duplicated:
 * its app icon, its UI font Inter (OFL) and the font's licence. [targets] maps each file name to
 * its path in the generated resource (or asset) folder.
 */
abstract class WordcraftAssets : DefaultTask() {
    @get:InputFiles @get:PathSensitive(PathSensitivity.NAME_ONLY) abstract val files: ConfigurableFileCollection
    @get:Input abstract val targets: MapProperty<String, String>
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun copy() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        for (file in files) {
            val target = targets.get()[file.name] ?: continue
            file.copyTo(File(out, target), overwrite = true)
        }
    }
}

/**
 * The app's UI icons (Lucide, ISC: WordCraft's assets/icons, each with an attribution sidecar)
 * as Compose path data, generated at build time rather than copied: `LucideIcons.Scissors` and
 * so on, one per SVG. Lucide draws with strokes only, from paths, circles, ellipses, rects, lines
 * and polylines, which become SVG path strings here.
 */
abstract class LucideIconsTask : DefaultTask() {
    @get:InputDirectory @get:PathSensitive(PathSensitivity.RELATIVE) abstract val iconsDir: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    @TaskAction
    fun generate() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        val pkg = File(out, "io/github/artcraftandroid/wordcraft/ui").apply { mkdirs() }
        val attr = Regex("""([\w-]+)="([^"]*)"""")
        val element = Regex("""<(path|circle|ellipse|rect|line|polyline|polygon)\b([^>]*)/?>""")
        fun n(v: Double) = if (v == Math.floor(v)) v.toLong().toString() else v.toString()
        val sb = StringBuilder()
        sb.appendLine("// Generated by app/build.gradle.kts (LucideIconsTask) from assets/icons: do not edit.")
        sb.appendLine("package io.github.artcraftandroid.wordcraft.ui\n")
        sb.appendLine("import androidx.compose.ui.graphics.vector.ImageVector\n")
        sb.appendLine("/** Lucide icons (ISC, Lucide Contributors; assets/icons/LICENSE-lucide.txt). */")
        sb.appendLine("object LucideIcons {")
        val files = iconsDir.get().asFile.listFiles { f -> f.extension == "svg" }.orEmpty().sortedBy { it.name }
        for (f in files) {
            val paths = mutableListOf<String>()
            for (m in element.findAll(f.readText())) {
                val a = attr.findAll(m.groupValues[2]).associate { it.groupValues[1] to it.groupValues[2] }
                fun d(k: String) = a[k]?.toDoubleOrNull() ?: 0.0
                when (m.groupValues[1]) {
                    "path" -> a["d"]?.let(paths::add)
                    "circle", "ellipse" -> {
                        val rx = if (m.groupValues[1] == "circle") d("r") else d("rx")
                        val ry = if (m.groupValues[1] == "circle") d("r") else d("ry")
                        val cx = d("cx"); val cy = d("cy")
                        paths += "M${n(cx - rx)} ${n(cy)}a${n(rx)} ${n(ry)} 0 1 0 ${n(2 * rx)} 0a${n(rx)} ${n(ry)} 0 1 0 ${n(-2 * rx)} 0"
                    }
                    "rect" -> {
                        val x = d("x"); val y = d("y"); val w = d("width"); val h = d("height")
                        val rx = minOf(a["rx"]?.toDoubleOrNull() ?: a["ry"]?.toDoubleOrNull() ?: 0.0, w / 2)
                        val ry = minOf(a["ry"]?.toDoubleOrNull() ?: rx, h / 2)
                        paths += if (rx <= 0.0) "M${n(x)} ${n(y)}h${n(w)}v${n(h)}h${n(-w)}Z" else
                            "M${n(x + rx)} ${n(y)}H${n(x + w - rx)}A${n(rx)} ${n(ry)} 0 0 1 ${n(x + w)} ${n(y + ry)}V${n(y + h - ry)}" +
                                "A${n(rx)} ${n(ry)} 0 0 1 ${n(x + w - rx)} ${n(y + h)}H${n(x + rx)}A${n(rx)} ${n(ry)} 0 0 1 ${n(x)} ${n(y + h - ry)}" +
                                "V${n(y + ry)}A${n(rx)} ${n(ry)} 0 0 1 ${n(x + rx)} ${n(y)}Z"
                    }
                    "line" -> paths += "M${n(d("x1"))} ${n(d("y1"))}L${n(d("x2"))} ${n(d("y2"))}"
                    "polyline", "polygon" -> {
                        val pts = (a["points"] ?: "").trim().split(Regex("[\\s,]+")).filter { it.isNotEmpty() }
                        if (pts.size >= 4) {
                            val s = StringBuilder("M${pts[0]} ${pts[1]}")
                            for (i in 2 until pts.size - 1 step 2) s.append("L${pts[i]} ${pts[i + 1]}")
                            if (m.groupValues[1] == "polygon") s.append("Z")
                            paths += s.toString()
                        }
                    }
                }
            }
            if (paths.isEmpty()) continue
            val id = f.nameWithoutExtension.split('-').joinToString("") { p -> p.replaceFirstChar { it.uppercase() } }
            val list = paths.joinToString(", ") { "\"" + it.replace("\"", "") + "\"" }
            sb.appendLine("    val $id: ImageVector by lazy { lucide(\"${f.nameWithoutExtension}\", $list) }")
        }
        sb.appendLine("}")
        File(pkg, "LucideIcons.kt").writeText(sb.toString())
    }
}

/**
 * Open fonts with the same widths as the fonts Word documents ask for (Carlito for Calibri,
 * Caladea for Cambria, Liberation Sans/Serif/Mono for Arial, Times New Roman and Courier New), so
 * documents lay out as they were written. Fetched at build time from pinned sources, each checked
 * against its SHA-256 and kept in a cache; never stored in the repository (fonts belong in
 * craft-fonts). All SIL OFL 1.1; their licences ship in the app's licences.
 */
abstract class DocumentFontsTask : DefaultTask() {
    /** File name in the app → "url sha256". */
    @get:Input abstract val files: MapProperty<String, String>

    /** A .tar.gz ("url sha256") and the members to take from it (base names). */
    @get:Input abstract val archive: Property<String>
    @get:Input abstract val archiveMembers: ListProperty<String>
    @get:Internal abstract val cacheDir: DirectoryProperty
    @get:OutputDirectory abstract val outputDir: DirectoryProperty

    private fun sha256(f: File): String =
        MessageDigest.getInstance("SHA-256").digest(f.readBytes()).joinToString("") { "%02x".format(it) }

    /** The cached copy of [url], downloaded when missing or wrong. */
    private fun fetch(url: String, sha: String): File {
        val cache = cacheDir.get().asFile.apply { mkdirs() }
        val f = File(cache, sha)
        if (f.isFile && sha256(f) == sha) return f
        val tmp = File(cache, "$sha.part")
        URI(url).toURL().openStream().use { input -> tmp.outputStream().use { input.copyTo(it) } }
        val got = sha256(tmp)
        check(got == sha) { "$url: SHA-256 $got, expected $sha" }
        tmp.renameTo(f)
        return f
    }

    @TaskAction
    fun run() {
        val out = outputDir.get().asFile
        out.deleteRecursively()
        File(out, "docfonts").mkdirs()
        File(out, "licences").mkdirs()
        for ((name, spec) in files.get()) {
            val (url, sha) = spec.split(' ')
            val dest = if (name.endsWith(".txt")) File(out, "licences/$name") else File(out, "docfonts/$name")
            fetch(url, sha).copyTo(dest, overwrite = true)
        }
        // the tarball: 512-byte headers, each followed by its file padded to 512 bytes
        val (url, sha) = archive.get().split(' ')
        val wanted = archiveMembers.get().toSet()
        GZIPInputStream(fetch(url, sha).inputStream().buffered()).use { tar ->
            val header = ByteArray(512)
            while (tar.readNBytes(header, 0, 512) == 512 && header[0] != 0.toByte()) {
                val path = String(header, 0, 100).trimEnd('\u0000')
                val size = String(header, 124, 12).trim('\u0000', ' ').toLong(8)
                val body = tar.readNBytes(size.toInt())
                tar.skipNBytes((512 - size % 512) % 512)
                val base = path.substringAfterLast('/')
                if (base !in wanted) continue
                val dest = if (base == "LICENSE") File(out, "licences/OFL-Liberation.txt") else File(out, "docfonts/$base")
                dest.writeBytes(body)
            }
        }
        val missing = wanted.filter { m -> !File(out, "docfonts/$m").isFile && m != "LICENSE" }
        check(missing.isEmpty()) { "not in the archive: $missing" }
    }
}

android {
    namespace = "io.github.artcraftandroid.wordcraft"
    compileSdk = 37
    ndkVersion = "29.0.14206865"

    defaultConfig {
        applicationId = "io.github.artcraftandroid.wordcraft"
        minSdk = minApi
        targetSdk = 36
        versionCode = appVersionCode
        versionName = appVersion
        ndk { abiFilters.addAll(rustAbis) }
    }

    signingConfigs {
        if (releaseKeystore != null) {
            create("release") {
                storeFile = file(releaseKeystore)
                storePassword = providers.environmentVariable("WORDCRAFT_KEYSTORE_PASSWORD").orNull
                keyAlias = providers.environmentVariable("WORDCRAFT_KEY_ALIAS").orNull
                keyPassword = providers.environmentVariable("WORDCRAFT_KEY_PASSWORD").orNull
            }
        }
    }

    buildTypes {
        debug {
            applicationIdSuffix = ".debug"
        }
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            signingConfig = if (unsignedRelease) null else signingConfigs.findByName("release") ?: signingConfigs.getByName("debug")
            // reproducible builds: no record of the checkout in the APK
            vcsInfo { include = false }
        }
    }
    // reproducible builds: no dependency metadata signing block (F-Droid's check rejects it)
    dependenciesInfo {
        includeInApk = false
        includeInBundle = false
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
}

androidComponents {
    onVariants { variant ->
        val name = variant.name.replaceFirstChar { it.uppercase() }
        val cargoBuild = tasks.register<CargoNdkBuild>("cargoBuild$name") {
            group = "rust"
            description = "Builds libwordcraft_android.so for $rustAbis"
            cargo.set(cargoExecutable)
            workspaceDir.set(cargoWorkspaceDir.absolutePath)
            ndkDir.set(androidComponents.sdkComponents.ndkDirectory.map { it.asFile.absolutePath })
            abis.set(rustAbis)
            apiLevel.set(minApi)
            // cargo knows what changed
            outputs.upToDateWhen { false }
        }
        val bindgen = tasks.register<UniffiBindgen>("uniffiBindgen$name") {
            group = "rust"
            description = "Generates the Kotlin bindings of libwordcraft_android.so"
            cargo.set(cargoExecutable)
            workspaceDir.set(cargoWorkspaceDir.absolutePath)
            library.set(cargoBuild.flatMap { it.outputDir.file("${rustAbis.first()}/libwordcraft_android.so") })
        }
        val assetsDir = File(cargoWorkspaceDir, "assets")
        val res = tasks.register<WordcraftAssets>("wordcraftResources$name") {
            val map = mapOf(
                "app-icon/hicolor/512x512/apps/ai.storyteller.wordcraft.png" to "drawable-nodpi/ic_launcher_tile.png",
                "fonts/Inter-Regular.ttf" to "font/inter_regular.ttf",
                "fonts/Inter-Medium.ttf" to "font/inter_medium.ttf",
                "fonts/Inter-SemiBold.ttf" to "font/inter_semibold.ttf",
            )
            files.from(map.keys.map { File(assetsDir, it) })
            targets.set(map.mapKeys { (k, _) -> k.substringAfterLast('/') })
        }
        val licences = tasks.register<WordcraftAssets>("wordcraftLicences$name") {
            files.from(File(assetsDir, "fonts/OFL-Inter.txt"), File(assetsDir, "icons/LICENSE-lucide.txt"))
            targets.set(mapOf("OFL-Inter.txt" to "licences/OFL-Inter.txt", "LICENSE-lucide.txt" to "licences/LICENSE-lucide.txt"))
        }
        val icons = tasks.register<LucideIconsTask>("lucideIcons$name") {
            iconsDir.set(File(assetsDir, "icons"))
        }
        val docFonts = tasks.register<DocumentFontsTask>("documentFonts$name") {
            val gf = "https://cdn.jsdelivr.net/gh/google/fonts@47d54ca1ebb14fd864695f1d39dc734f39017c33/ofl"
            files.set(
                mapOf(
                    "Carlito-Regular.ttf" to "$gf/carlito/Carlito-Regular.ttf f6418f708baede9789daef5d458c0f53d2a888af9820e8062934e504fedc6595",
                    "Carlito-Bold.ttf" to "$gf/carlito/Carlito-Bold.ttf bb5d20f79b82599ec72983597437373a80f2d2085fa91fc144fd74e876a594db",
                    "Carlito-Italic.ttf" to "$gf/carlito/Carlito-Italic.ttf 0b019225e58d702bfedcbd35c21696769f8ee115cb6343f84c2f240312450d1c",
                    "Carlito-BoldItalic.ttf" to "$gf/carlito/Carlito-BoldItalic.ttf b32928186c119599e03ca6a1ffc680fdcb7fac95772f4b95d989cf6cd3861517",
                    "OFL-Carlito.txt" to "$gf/carlito/OFL.txt 58402f82a7c332a700294988fe7554fbb0a63a8d27ccc1ee3bbc640311990a00",
                    "Caladea-Regular.ttf" to "$gf/caladea/Caladea-Regular.ttf f1e899278b7b4491aba5b6a8253c4b04c050cc59b21865be5c37559a775153cd",
                    "Caladea-Bold.ttf" to "$gf/caladea/Caladea-Bold.ttf ae3cb2dcbc925809dd29d2a44e9802211cab66be541bacbfc9c08c74b27c3742",
                    "Caladea-Italic.ttf" to "$gf/caladea/Caladea-Italic.ttf 4359a8e24f748b6447b1ff6d7a174febe70961d29f8bb8634b56dacd740a3deb",
                    "Caladea-BoldItalic.ttf" to "$gf/caladea/Caladea-BoldItalic.ttf ccabaa7b7e2fdf253d2b1a5fa699dd8a3df8d835a9eb285ad82631a677eb76c0",
                    "OFL-Caladea.txt" to "$gf/caladea/OFL.txt ccdab61d371d8c8683a128a92cd7d498dbdb1d37689f7cb21f1bf6b16658d213",
                ),
            )
            archive.set("https://github.com/liberationfonts/liberation-fonts/files/7261482/liberation-fonts-ttf-2.1.5.tar.gz 7191c669bf38899f73a2094ed00f7b800553364f90e2637010a69c0e268f25d0")
            archiveMembers.set(
                listOf("Sans", "Serif", "Mono").flatMap { f -> listOf("Regular", "Bold", "Italic", "BoldItalic").map { s -> "Liberation$f-$s.ttf" } } + "LICENSE",
            )
            cacheDir.set(File(rootDir, ".gradle/wordcraft-fonts"))
        }
        variant.sources.assets?.addGeneratedSourceDirectory(docFonts, DocumentFontsTask::outputDir)
        variant.sources.java?.addGeneratedSourceDirectory(icons, LucideIconsTask::outputDir)
        variant.sources.jniLibs?.addGeneratedSourceDirectory(cargoBuild, CargoNdkBuild::outputDir)
        variant.sources.java?.addGeneratedSourceDirectory(bindgen, UniffiBindgen::outputDir)
        variant.sources.res?.addGeneratedSourceDirectory(res, WordcraftAssets::outputDir)
        variant.sources.assets?.addGeneratedSourceDirectory(licences, WordcraftAssets::outputDir)
    }
}

// reproducible builds: no baseline profile in the APK (its bytes vary from one build to the next)
tasks.configureEach {
    if (name.contains("ArtProfile")) enabled = false
}

dependencies {
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.lifecycle.runtime.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.material3)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.androidx.exifinterface)
    implementation(libs.jna) { artifact { type = "aar" } }
    testImplementation(libs.junit)
}
