package gx.zelda.art

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toPath
import okio.buffer
import okio.openZip
import okio.use
import kotlin.random.Random

/** An art pack that's been imported: its folder under the store's root and what it holds. */
@Serializable
data class InstalledPack(
    val dir: String,
    val id: String,
    val name: String,
    val version: String = "",
    val bytes: Long = 0,
    /** Asset ids it supplies, so coverage needs no second read of its manifest. */
    val assets: List<String> = emptyList(),
)

/** What an import kept and what it left out, with reasons. */
data class ImportResult(val pack: InstalledPack, val ignored: List<String>, val rejected: List<String>)

/** An import that failed as a whole; [message] is for the user. */
class ArtPackException(message: String) : Exception(message)

/** Every installed pack's files by asset id, the newest pack's winning where two supply one. */
class ArtIndex(
    val icons: Map<String, Path> = emptyMap(),
    val tilesets: Map<String, Located<ArtManifest.Tileset>> = emptyMap(),
    val squaresets: Map<String, Located<ArtManifest.Squareset>> = emptyMap(),
    val data: Map<String, Path> = emptyMap(),
) {
    /** [value] read from the pack folder [dir]. */
    class Located<T>(val dir: Path, val value: T)

    fun has(id: String): Boolean = id in icons || id in tilesets || id in squaresets || id in data

    companion object {
        val EMPTY = ArtIndex()
    }
}

/**
 * Art packs in app-private storage, one folder each under [root], listed newest first in
 * `index.json`. An import is checked against the rules in docs/art-packs.md and unpacked into a
 * temporary folder, which is renamed into place only once it's complete, so a failed or
 * interrupted import leaves nothing behind. Not thread-safe: [GameArt] serialises calls.
 */
class ArtPackStore(val root: Path, private val fs: FileSystem = FileSystem.SYSTEM) {
    private val indexFile = root / "index.json"
    private val listSerializer = ListSerializer(InstalledPack.serializer())
    private val json = Json { ignoreUnknownKeys = true }

    fun installed(): List<InstalledPack> =
        if (!fs.exists(indexFile)) emptyList()
        else runCatching { json.decodeFromString(listSerializer, fs.read(indexFile) { readUtf8() }) }.getOrDefault(emptyList())

    /** The kept part of [pack]'s manifest. */
    fun manifest(pack: InstalledPack): ArtManifest = ArtManifest.parse(fs.read(root / pack.dir / MANIFEST) { readUtf8() })

    fun index(): ArtIndex {
        val icons = mutableMapOf<String, Path>()
        val tilesets = mutableMapOf<String, ArtIndex.Located<ArtManifest.Tileset>>()
        val squaresets = mutableMapOf<String, ArtIndex.Located<ArtManifest.Squareset>>()
        val data = mutableMapOf<String, Path>()
        // Oldest first, so a newer pack's entry replaces an older one's.
        for (pack in installed().asReversed()) {
            val dir = root / pack.dir
            val m = runCatching { manifest(pack) }.getOrNull() ?: continue
            m.icons.forEach { (id, file) -> icons[id] = dir / file }
            m.tilesets.forEach { (id, t) -> tilesets[id] = ArtIndex.Located(dir, t) }
            m.squaresets.forEach { (id, s) -> squaresets[id] = ArtIndex.Located(dir, s) }
            m.data.forEach { (id, file) -> data[id] = dir / file }
        }
        return ArtIndex(icons, tilesets, squaresets, data)
    }

    fun remove(dir: String) {
        val packs = installed()
        if (packs.none { it.dir == dir }) return
        writeIndex(packs.filter { it.dir != dir })
        fs.deleteRecursively(root / dir)
    }

    /**
     * Checks and unpacks the zip at [zip]. Throws [ArtPackException] if the pack can't be used at
     * all; an asset that breaks a rule is left out and listed in the result. [onProgress] hears
     * the share of files done.
     */
    fun import(zip: Path, onProgress: (Float) -> Unit = {}): ImportResult {
        val zipFs = runCatching { fs.openZip(zip) }.getOrElse { throw ArtPackException("That file isn't a zip archive.") }
        val entries = zipFs.listRecursively(ZIP_ROOT).count()
        if (entries > MAX_ENTRIES) throw ArtPackException("The pack has more than $MAX_ENTRIES files.")
        val manifest = readManifest(zipFs)
        fs.createDirectories(root)
        val dir = "pack_" + Random.nextLong().toULong().toString(16)
        val tmp = root / "$dir.tmp"
        fs.deleteRecursively(tmp)
        fs.createDirectories(tmp)
        try {
            val unpacker = Unpacker(zipFs, tmp, manifest, onProgress)
            val kept = unpacker.run()
            if (kept.icons.isEmpty() && kept.tilesets.isEmpty() && kept.squaresets.isEmpty() && kept.data.isEmpty()) {
                throw ArtPackException("Nothing in the pack could be used." + unpacker.rejected.firstOrNull()?.let { " $it" }.orEmpty())
            }
            fs.write(tmp / MANIFEST) { writeUtf8(ArtManifest.json.encodeToString(ArtManifest.serializer(), kept)) }
            fs.atomicMove(tmp, root / dir)
            val pack = InstalledPack(
                dir = dir,
                id = manifest.pack.id,
                name = manifest.pack.name,
                version = manifest.pack.version,
                bytes = unpacker.written,
                assets = (kept.icons.keys + kept.tilesets.keys + kept.squaresets.keys + kept.data.keys).sorted(),
            )
            writeIndex(listOf(pack) + installed())
            return ImportResult(pack, unpacker.ignored, unpacker.rejected)
        } catch (e: Throwable) {
            fs.deleteRecursively(tmp)
            throw e
        }
    }

    private fun readManifest(zipFs: FileSystem): ArtManifest {
        val path = ZIP_ROOT / MANIFEST
        val meta = zipFs.metadataOrNull(path) ?: throw ArtPackException("The pack has no manifest.json at its top level.")
        if ((meta.size ?: 0) > MAX_MANIFEST) throw ArtPackException("The pack's manifest.json is too large.")
        val manifest = runCatching { ArtManifest.parse(zipFs.read(path) { readUtf8() }) }
            .getOrElse { throw ArtPackException("The pack's manifest.json can't be read.") }
        if (manifest.format > ArtManifest.FORMAT) throw ArtPackException("This pack was made for a newer version of the app.")
        if (manifest.format < 1) throw ArtPackException("The pack's manifest.json has no valid format number.")
        return manifest
    }

    private fun writeIndex(packs: List<InstalledPack>) {
        fs.createDirectories(root)
        val tmp = root / "index.json.tmp"
        fs.write(tmp) { writeUtf8(json.encodeToString(listSerializer, packs)) }
        fs.atomicMove(tmp, indexFile)
    }

    /** Copies the assets [manifest] names out of [zipFs] into [dest], checking each one. */
    private inner class Unpacker(
        private val zipFs: FileSystem,
        private val dest: Path,
        private val manifest: ArtManifest,
        private val onProgress: (Float) -> Unit,
    ) {
        val ignored = mutableListOf<String>()
        val rejected = mutableListOf<String>()
        var written = 0L
            private set
        private var done = 0
        private val total = manifest.icons.size + manifest.data.size +
            manifest.tilesets.values.sumOf { t -> runCatching { t.geometry.tiles().size }.getOrDefault(0) } +
            manifest.squaresets.values.sumOf { it.squares.size + 1 }

        fun run(): ArtManifest {
            val icons = manifest.icons.filter { (id, file) -> icon(id, file) }
            val tilesets = manifest.tilesets.filter { (id, t) -> tileset(id, t) }
            val squaresets = manifest.squaresets.filter { (id, s) -> squareset(id, s) }
            val data = manifest.data.filter { (id, file) -> data(id, file) }
            return manifest.copy(icons = icons, tilesets = tilesets, squaresets = squaresets, data = data)
        }

        private fun icon(id: String, file: String): Boolean {
            if (!ArtIds.isKnownGame(id) || ArtIds.kind(id) !in ArtIds.ICON_KINDS) return ignore("$id: not an asset this app uses")
            return group(id) {
                val ext = file.substringAfterLast('.', "").lowercase()
                if (ext !in ICON_EXTENSIONS) fail("$file isn't PNG, WebP or a vector drawable")
                copy(file, MAX_ICON) { bytes ->
                    if (ext == "xml") checkVector(file, bytes) else checkImage(file, bytes, maxSide = MAX_ICON_SIDE)
                }
            }
        }

        private fun tileset(id: String, t: ArtManifest.Tileset): Boolean {
            val expected = ArtIds.TILESETS[id] ?: return ignore("$id: not a map this app draws")
            if (t.geometry != expected) {
                return reject("$id: the tiles don't fit the map (expected ${expected.levels} levels of ${expected.tileSize} px tiles, ${expected.width}×${expected.height})")
            }
            return group(id) {
                for (tile in expected.tiles()) {
                    copy(t.file(tile), MAX_TILE) { checkImage(t.file(tile), it, exact = expected.tileSize to expected.tileSize) }
                }
            }
        }

        private fun squareset(id: String, s: ArtManifest.Squareset): Boolean {
            if (!ArtIds.isKnownGame(id) || ArtIds.kind(id) != "map" || ArtIds.game(id) != "hwde") return ignore("$id: not a map this app draws")
            return group(id) {
                if (s.width !in 1..MAX_ICON_SIDE || s.height !in 1..MAX_ICON_SIDE) fail("squares must be at most $MAX_ICON_SIDE px a side")
                if (s.squares.isEmpty() || s.squares.any { !SQUARE_ID.matches(it) }) fail("square ids must look like r1c1")
                for (square in s.squares) copy(s.file(square), MAX_TILE) { checkImage(s.file(square), it, exact = s.width to s.height) }
                s.preview?.let { p -> copy(p, MAX_PREVIEW) { checkImage(p, it, maxSide = MAX_PREVIEW_SIDE) } }
            }
        }

        private fun data(id: String, file: String): Boolean {
            if (!ArtIds.isKnownGame(id) || ArtIds.kind(id) != "data") return ignore("$id: not data this app uses")
            return group(id) {
                if (!file.endsWith(".json")) fail("$file isn't JSON")
                copy(file, MAX_DATA) { bytes ->
                    runCatching { Json.parseToJsonElement(bytes.decodeToString()) }.onFailure { fail("$file isn't valid JSON") }
                }
            }
        }

        private fun ignore(reason: String): Boolean = false.also { ignored += reason }

        private fun reject(reason: String): Boolean = false.also { rejected += reason }

        /** Runs [body] for one asset; if any of its files fails, all of them are removed and the asset is rejected. */
        private fun group(id: String, body: Group.() -> Unit): Boolean {
            val g = Group()
            return try {
                g.body()
                true
            } catch (e: Rejected) {
                g.files.forEach { fs.delete(it, mustExist = false) }
                reject("$id: ${e.message}")
            }
        }

        private inner class Group {
            val files = mutableListOf<Path>()

            fun fail(reason: String): Nothing = throw Rejected(reason)

            /** Reads [file] from the zip, at most [limit] bytes, runs [check] on it and writes it out. */
            fun copy(file: String, limit: Long, check: (ByteArray) -> Unit) {
                if (!isSafePath(file)) fail("$file isn't a plain relative path")
                val src = ZIP_ROOT / file
                val meta = zipFs.metadataOrNull(src)
                if (meta == null || !meta.isRegularFile) fail("$file is missing from the zip")
                val bytes = zipFs.source(src).buffer().use { source ->
                    if (source.request(limit + 1)) fail("$file is larger than ${limit / 1024} KB")
                    source.readByteArray()
                }
                if (written + bytes.size > MAX_TOTAL) throw ArtPackException("The pack is larger than ${MAX_TOTAL / (1024 * 1024)} MB unpacked.")
                check(bytes)
                val out = dest / file
                out.parent?.let { fs.createDirectories(it) }
                fs.write(out) { write(bytes) }
                files += out
                written += bytes.size
                done++
                if (total > 0 && done % 50 == 0) onProgress(done.toFloat() / total)
            }

            fun checkImage(file: String, bytes: ByteArray, maxSide: Int = Int.MAX_VALUE, exact: Pair<Int, Int>? = null) {
                val header = ImageHeader.read(bytes) ?: fail("$file isn't a PNG, WebP or JPEG picture")
                if (exact != null && (header.width != exact.first || header.height != exact.second)) {
                    fail("$file is ${header.width}×${header.height}, not ${exact.first}×${exact.second}")
                }
                if (header.width > maxSide || header.height > maxSide) fail("$file is larger than $maxSide px a side")
            }

            fun checkVector(file: String, bytes: ByteArray) {
                val text = bytes.decodeToString()
                if (!text.contains("<vector")) fail("$file isn't a vector drawable")
                // Resource references (@color/…, @drawable/…) and external entities can't resolve outside an APK.
                if (Regex("""="@|<!ENTITY|<!DOCTYPE""").containsMatchIn(text)) fail("$file refers to other resources")
            }
        }
    }

    private class Rejected(message: String) : Exception(message)

    companion object {
        const val MANIFEST = "manifest.json"
        private val ZIP_ROOT = "/".toPath()
        private val ICON_EXTENSIONS = setOf("png", "webp", "xml")
        private val SQUARE_ID = Regex("r[0-9]{1,2}c[0-9]{1,2}")
        private val SAFE_SEGMENT = Regex("[A-Za-z0-9._-]+")

        const val MAX_ENTRIES = 20_000
        const val MAX_MANIFEST = 2L * 1024 * 1024
        const val MAX_ICON = 512L * 1024
        const val MAX_ICON_SIDE = 1024
        const val MAX_TILE = 128L * 1024
        const val MAX_PREVIEW = 2L * 1024 * 1024
        const val MAX_PREVIEW_SIDE = 4096
        const val MAX_DATA = 4L * 1024 * 1024
        const val MAX_TOTAL = 300L * 1024 * 1024

        /** Relative, forward slashes, no `.` or `..` segments, plain characters only. */
        fun isSafePath(path: String): Boolean =
            path.length in 1..200 && path.split('/').all { it.isNotEmpty() && it != "." && it != ".." && SAFE_SEGMENT.matches(it) }
    }
}
