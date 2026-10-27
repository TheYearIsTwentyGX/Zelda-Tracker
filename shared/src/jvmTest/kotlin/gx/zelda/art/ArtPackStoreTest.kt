package gx.zelda.art

import kotlinx.coroutines.test.runTest
import okio.FileSystem
import okio.Path
import okio.Path.Companion.toOkioPath
import java.io.ByteArrayOutputStream
import java.io.File
import java.nio.file.Files
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertContentEquals
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertIs
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

/** Importing, checking, resolving and removing art packs, with zips built here. */
class ArtPackStoreTest {
    private val tmp: File = Files.createTempDirectory("artpacks").toFile()
    private val root: Path = File(tmp, "store").toOkioPath()
    private val store = ArtPackStore(root)

    @AfterTest
    fun cleanUp() {
        GameArt.detach()
        tmp.deleteRecursively()
    }

    @Test
    fun anIconIsImportedAndWinsOverTheBundledOne() = runTest {
        val icon = png(64, 64)
        val zip = zip(manifest(icons = """"botw/armor/hylian_hood": "a/hood.png""""), "a/hood.png" to icon)
        GameArt.attach(store)
        val result = GameArt.import(zip)

        assertEquals(listOf("botw/armor/hylian_hood"), result.pack.assets)
        assertTrue(result.rejected.isEmpty() && result.ignored.isEmpty(), "$result")
        val ref = assertIs<ArtRef.File>(GameArt.icon("botw/armor/hylian_hood"))
        assertContentEquals(icon, File(ref.path.toString()).readBytes())
        // Pieces the pack leaves out still come from the app.
        assertIs<ArtRef.Bundled>(GameArt.icon("botw/armor/hylian_tunic"))
    }

    @Test
    fun theNewestPackWins() = runTest {
        GameArt.attach(store)
        GameArt.import(zip(manifest(id = "old", icons = """"ss/thrill/bomb": "b.png""""), "b.png" to png(10, 10)))
        GameArt.import(zip(manifest(id = "new", icons = """"ss/thrill/bomb": "b.png""""), "b.png" to png(20, 20)))
        val ref = assertIs<ArtRef.File>(GameArt.icon("ss/thrill/bomb"))
        assertEquals(20, ImageHeader.read(File(ref.path.toString()).readBytes())!!.width)

        GameArt.remove(GameArt.packs.value.first { it.id == "new" })
        val older = assertIs<ArtRef.File>(GameArt.icon("ss/thrill/bomb"))
        assertEquals(10, ImageHeader.read(File(older.path.toString()).readBytes())!!.width)
    }

    @Test
    fun removingAPackDeletesItsFiles() = runTest {
        GameArt.attach(store)
        val pack = GameArt.import(zip(manifest(icons = """"ss/thrill/bomb": "b.png""""), "b.png" to png(10, 10))).pack
        assertTrue(FileSystem.SYSTEM.exists(root / pack.dir))
        GameArt.remove(pack)
        assertFalse(FileSystem.SYSTEM.exists(root / pack.dir))
        assertTrue(store.installed().isEmpty())
        assertIs<ArtRef.Bundled>(GameArt.icon("ss/thrill/bomb"))
    }

    @Test
    fun unsafePathsAreRejectedAndNothingLeavesThePackFolder() {
        val zip = zip(
            manifest(icons = """"ss/thrill/bomb": "../escape.png", "ss/thrill/gold": "/abs.png", "ss/thrill/red": "a\\b.png", "ss/thrill/blue": "ok.png""""),
            "../escape.png" to png(8, 8), "abs.png" to png(8, 8), "ok.png" to png(8, 8),
        )
        val result = store.import(zip)
        assertEquals(listOf("ss/thrill/blue"), result.pack.assets)
        assertEquals(3, result.rejected.size, "${result.rejected}")
        assertFalse(File(tmp, "store/escape.png").exists())
        assertFalse(File(tmp, "escape.png").exists())
    }

    @Test
    fun unknownIdsAreIgnored() {
        val result = store.import(
            zip(
                manifest(icons = """"zz/armor/x": "a.png", "botw/hat/x": "a.png", "Bad Id": "a.png", "totk/armor/x": "a.png""""),
                "a.png" to png(8, 8),
            ),
        )
        assertEquals(listOf("totk/armor/x"), result.pack.assets)
        assertEquals(3, result.ignored.size)
    }

    @Test
    fun aPackForANewerAppIsRefused() {
        val e = assertFailsWith<ArtPackException> { store.import(zip(manifest(format = 2, icons = """"ss/thrill/bomb": "b.png""""), "b.png" to png(8, 8))) }
        assertTrue("newer version" in e.message!!)
        assertTrue(store.installed().isEmpty())
    }

    @Test
    fun aZipWithoutAManifestOrAnythingUsableIsRefused() {
        assertFailsWith<ArtPackException> { store.import(zip(null, "b.png" to png(8, 8))) }
        assertFailsWith<ArtPackException> { store.import(zip(manifest(icons = """"ss/thrill/bomb": "missing.png""""))) }
        assertFailsWith<ArtPackException> { store.import(File(tmp, "not-a-zip.zip").apply { writeText("hello") }.toOkioPath()) }
        assertTrue(store.installed().isEmpty())
        // No half-unpacked folders are left behind.
        assertEquals(emptyList(), root.toFile().listFiles().orEmpty().filter { it.isDirectory }.map { it.name })
    }

    @Test
    fun picturesAreCheckedByHeaderAndSize() {
        val result = store.import(
            zip(
                manifest(icons = """"ss/thrill/bomb": "big.png", "ss/thrill/gold": "huge.png", "ss/thrill/red": "text.png", "ss/thrill/blue": "v.xml", "ss/thrill/green": "ref.xml""""),
                "big.png" to png(2048, 16),
                "huge.png" to png(8, 8) + ByteArray(600 * 1024),
                "text.png" to "not a picture".encodeToByteArray(),
                "v.xml" to """<vector android:width="24dp"><path android:pathData="M0,0h1"/></vector>""".encodeToByteArray(),
                "ref.xml" to """<vector><path android:fillColor="@color/x"/></vector>""".encodeToByteArray(),
            ),
        )
        assertEquals(listOf("ss/thrill/blue"), result.pack.assets)
        assertEquals(4, result.rejected.size, "${result.rejected}")
        assertTrue((GameArt.icon("ss/thrill/blue", store.index()) as ArtRef.File).vector)
    }

    @Test
    fun aTilesetMustMatchTheMapExactly() = runTest {
        val geometry = ArtIds.TILESETS.getValue(ArtIds.BOTW_WORLD)
        val tiles = geometry.tiles()
        assertEquals(1700, tiles.size)
        val tile = webp(256, 256)
        val files = tiles.map { "t/$it.webp" to tile }
        val good = """"botw/map/world": {"path": "t/{level}/{row}/{col}.webp", "levels": 4, "tileSize": 256, "width": ${geometry.width}, "height": ${geometry.height}}"""
        val wrong = """"botw/map/world": {"path": "t/{level}/{row}/{col}.webp", "levels": 3, "tileSize": 256, "width": ${geometry.width}, "height": ${geometry.height}}"""

        val rejected = store.import(zip(manifest(tilesets = wrong, icons = """"ss/thrill/bomb": "b.png""""), "b.png" to png(8, 8), *files.toTypedArray()))
        assertEquals(listOf("ss/thrill/bomb"), rejected.pack.assets)
        assertTrue(rejected.rejected.single().startsWith("botw/map/world"))

        GameArt.attach(store)
        GameArt.import(zip(manifest(tilesets = good), *files.toTypedArray()))
        assertContentEquals(tile, GameArt.tile(ArtIds.BOTW_WORLD, 3, 31, 39, bundledPath = null))
    }

    @Test
    fun oneBadTileRejectsTheWholeTilesetAndItsFiles() {
        val geometry = ArtIds.TILESETS.getValue(ArtIds.BOTW_WORLD)
        val files = geometry.tiles().map { "t/$it.webp" to if (it == "2/3/4") webp(255, 256) else webp(256, 256) }
        val tileset = """"botw/map/world": {"path": "t/{level}/{row}/{col}.webp", "levels": 4, "tileSize": 256, "width": ${geometry.width}, "height": ${geometry.height}}"""
        val result = store.import(zip(manifest(tilesets = tileset, icons = """"ss/thrill/bomb": "b.png""""), "b.png" to png(8, 8), *files.toTypedArray()))
        assertTrue("2/3/4" in result.rejected.single(), "${result.rejected}")
        assertFalse(File(tmp, "store/${result.pack.dir}/t").walk().any { it.isFile }, "the tileset's files are removed")
    }

    @Test
    fun adventureMapSquaresAndPreview() = runTest {
        val squares = """"hwde/map/adventure_map": {"path": "s/{id}.webp", "squares": ["r1c1", "r1c2"], "preview": "s/preview.webp", "width": 256, "height": 176}"""
        val sq = webp(256, 176)
        GameArt.attach(store)
        GameArt.import(zip(manifest(squaresets = squares), "s/r1c1.webp" to sq, "s/r1c2.webp" to sq, "s/preview.webp" to webp(1280, 440)))
        assertContentEquals(sq, GameArt.square("hwde/map/adventure_map", "r1c2", bundledPath = null))
        assertEquals(1280, ImageHeader.read(GameArt.square("hwde/map/adventure_map", null, bundledPath = null)!!)!!.width)
        // A square the pack doesn't list isn't taken from the app's art either: the pack owns the map.
        assertNull(GameArt.square("hwde/map/adventure_map", "r5c5", bundledPath = "files/hwde/adventure_map/r5c5.webp"))
    }

    @Test
    fun dataMustBeJson() {
        val result = store.import(
            zip(
                manifest(data = """"hwde/data/adventure_map": "d/a.json", "hwde/data/lorule_map": "d/b.json""""),
                "d/a.json" to """{"id": "adventure_map"}""".encodeToByteArray(),
                "d/b.json" to "{not json".encodeToByteArray(),
            ),
        )
        assertEquals(listOf("hwde/data/adventure_map"), result.pack.assets)
        assertNotNull(store.index().data["hwde/data/adventure_map"])
    }

    @Test
    fun theBundledArtCanBeSwitchedOff() = runTest {
        GameArt.attach(store)
        assertNotNull(GameArt.icon("ss/thrill/bomb"))
        assertNotNull(GameArt.tile(ArtIds.BOTW_WORLD, 0, 0, 0, "files/botw/map/0/0/0.webp"))
        GameArt.bundled = false
        assertNull(GameArt.icon("ss/thrill/bomb"))
        assertNull(GameArt.tile(ArtIds.BOTW_WORLD, 0, 0, 0, "files/botw/map/0/0/0.webp"))
    }

    // ------------------------------------------------------------------ helpers

    private fun manifest(
        format: Int = 1,
        id: String = "test",
        icons: String = "",
        tilesets: String = "",
        squaresets: String = "",
        data: String = "",
    ) = """{"format": $format, "pack": {"id": "$id", "name": "Test $id", "version": "1"},
        "icons": {$icons}, "tilesets": {$tilesets}, "squaresets": {$squaresets}, "data": {$data}}"""

    private var zips = 0

    private fun zip(manifest: String?, vararg files: Pair<String, ByteArray>): Path {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            if (manifest != null) {
                z.putNextEntry(ZipEntry("manifest.json"))
                z.write(manifest.encodeToByteArray())
            }
            for ((name, bytes) in files) {
                z.putNextEntry(ZipEntry(name))
                z.write(bytes)
            }
        }
        return File(tmp, "pack${zips++}.zip").apply { writeBytes(out.toByteArray()) }.toOkioPath()
    }

    /** Just enough of a PNG for its header to be read. */
    private fun png(w: Int, h: Int): ByteArray =
        byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 13) + "IHDR".encodeToByteArray() +
            be32(w) + be32(h) + byteArrayOf(8, 6, 0, 0, 0, 0, 0, 0, 0)

    /** Just enough of a lossless WebP for its header to be read. */
    private fun webp(w: Int, h: Int): ByteArray {
        val bits = (w - 1) or ((h - 1) shl 14)
        return "RIFF".encodeToByteArray() + le32(30) + "WEBPVP8L".encodeToByteArray() + le32(10) + byteArrayOf(0x2F) + le32(bits) + ByteArray(6)
    }

    private fun be32(v: Int) = byteArrayOf((v ushr 24).toByte(), (v ushr 16).toByte(), (v ushr 8).toByte(), v.toByte())

    private fun le32(v: Int) = be32(v).reversedArray()
}
