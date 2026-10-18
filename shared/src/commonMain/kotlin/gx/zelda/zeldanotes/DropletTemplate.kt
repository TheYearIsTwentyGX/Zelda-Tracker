package gx.zelda.zeldanotes

import gx.zelda.core.Game
import gx.zelda.resources.Res
import gx.zelda.shrines.scan.MapImage
import gx.zelda.shrines.scan.decodeMapImage
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The Korok droplet as Zelda Notes draws it, built from a crop of one on the reference phone
 * (`files/<game>/notes_korok.png`): its tan fill, the fill closed over the dark inner drop, and that
 * shape grown by the outline. At work scale the template is [WORK_WIDTH] px wide; everything else in
 * the pipeline is measured in those work pixels.
 */
class DropletTemplate private constructor(
    /** The fill's bounding box on the reference phone, in source px, and how much of it is filled. */
    val fillWidth: Int,
    val fillHeight: Int,
    val fillDensity: Double,
    /** Reference source px to work px. */
    val workFactor: Double,
    val width: Int,
    val height: Int,
    /** Offsets (x + y·[width]) of the pixels matched, their zero-mean grey and its norm. */
    internal val nccX: IntArray,
    internal val nccY: IntArray,
    internal val nccT: FloatArray,
    internal val nccNorm: Float,
    /** The tan fill's pixels, and the whole drawn droplet's (fill, inner drop and outline). */
    internal val fillX: IntArray,
    internal val fillY: IntArray,
    internal val shapeX: IntArray,
    internal val shapeY: IntArray,
    /** The template's centre: a droplet "at" a point has this pixel there. */
    val cx: Int,
    val cy: Int,
    /** Two pixels deep inside the fill: a droplet whose template is placed right has tan at both. */
    internal val probes: IntArray,
) {
    companion object {
        const val WORK_WIDTH = 30

        fun path(game: Game) = "files/${game.id}/notes_korok.png"

        suspend fun load(game: Game): DropletTemplate =
            of(decodeMapImage(Res.readBytes(path(game))) ?: error("can't decode ${path(game)}"))

        fun of(crop: MapImage): DropletTemplate {
            val w = crop.width
            val h = crop.height
            val tan = BooleanArray(w * h) { NotesImage.colourOf(crop.argb[it]) == NotesImage.TAN }
            val fillBlob = Blobs.of(tan, w, h).maxBy { it.area }
            // Keep only the droplet's own fill, not a neighbour's edge.
            val fill = BooleanArray(w * h) { i ->
                tan[i] && i % w in fillBlob.minX..fillBlob.maxX && i / w in fillBlob.minY..fillBlob.maxY
            }
            val shape = erode(dilate(fill, w, h, 12), w, h, 12)
            val drawn = dilate(shape, w, h, 4)
            var x0 = w
            var y0 = h
            var x1 = -1
            var y1 = -1
            for (i in drawn.indices) if (drawn[i]) {
                x0 = min(x0, i % w); x1 = max(x1, i % w); y0 = min(y0, i / w); y1 = max(y1, i / w)
            }
            val f = WORK_WIDTH.toDouble() / (x1 - x0 + 1)
            val tw = ((x1 - x0 + 1) * f).roundToInt()
            val th = ((y1 - y0 + 1) * f).roundToInt()
            val grey = FloatArray(tw * th)
            val fillW = FloatArray(tw * th)
            val drawnW = FloatArray(tw * th)
            for (ty in 0 until th) for (tx in 0 until tw) {
                val sx0 = x0 + (tx / f).toInt()
                val sx1 = max(sx0 + 1, min(x1 + 1, x0 + ((tx + 1) / f).toInt()))
                val sy0 = y0 + (ty / f).toInt()
                val sy1 = max(sy0 + 1, min(y1 + 1, y0 + ((ty + 1) / f).toInt()))
                var g = 0f
                var a = 0
                var b = 0
                for (y in sy0 until sy1) for (x in sx0 until sx1) {
                    val i = y * w + x
                    g += NotesImage.luma(crop.argb[i])
                    if (fill[i]) a++
                    if (drawn[i]) b++
                }
                val n = (sy1 - sy0) * (sx1 - sx0)
                grey[ty * tw + tx] = g / n
                fillW[ty * tw + tx] = a.toFloat() / n
                drawnW[ty * tw + tx] = b.toFloat() / n
            }
            val ncc = (0 until tw * th).filter { drawnW[it] >= 0.5f }
            val mean = ncc.sumOf { grey[it].toDouble() }.toFloat() / ncc.size
            val t = FloatArray(ncc.size) { grey[ncc[it]] - mean }
            val fills = (0 until tw * th).filter { fillW[it] >= 0.5f }
            val inside = fills.filter { i ->
                val x = i % tw
                val y = i / tw
                (-1..1).all { dy -> (-1..1).all { dx -> (x + dx) in 0 until tw && (y + dy) in 0 until th && fillW[(y + dy) * tw + x + dx] >= 0.5f } }
            }
            val mx = inside.sumOf { it % tw }.toDouble() / inside.size
            val my = inside.sumOf { it / tw }.toDouble() / inside.size
            fun d2(i: Int) = (i % tw - mx) * (i % tw - mx) + (i / tw - my) * (i / tw - my)
            val probes = intArrayOf(inside.minBy(::d2), inside.maxBy(::d2))
            return DropletTemplate(
                fillWidth = fillBlob.width,
                fillHeight = fillBlob.height,
                fillDensity = fillBlob.area.toDouble() / (fillBlob.width * fillBlob.height),
                workFactor = f,
                width = tw,
                height = th,
                nccX = IntArray(ncc.size) { ncc[it] % tw },
                nccY = IntArray(ncc.size) { ncc[it] / tw },
                nccT = t,
                nccNorm = sqrt(t.sumOf { it.toDouble() * it }).toFloat(),
                fillX = IntArray(fills.size) { fills[it] % tw },
                fillY = IntArray(fills.size) { fills[it] / tw },
                shapeX = IntArray(ncc.size) { ncc[it] % tw },
                shapeY = IntArray(ncc.size) { ncc[it] / tw },
                cx = tw / 2,
                cy = th / 2,
                probes = probes,
            )
        }

        private fun dilate(m: BooleanArray, w: Int, h: Int, r: Int): BooleanArray = morph(m, w, h, r, true)

        private fun erode(m: BooleanArray, w: Int, h: Int, r: Int): BooleanArray = morph(m, w, h, r, false)

        /** A square max (dilate) or min (erode) filter of radius [r], separable; outside never changes the result. */
        private fun morph(m: BooleanArray, w: Int, h: Int, r: Int, grow: Boolean): BooleanArray {
            val rows = BooleanArray(m.size)
            for (y in 0 until h) for (x in 0 until w) {
                var v = !grow
                for (xx in x - r..x + r) {
                    val s = if (xx in 0 until w) m[y * w + xx] else !grow
                    if (grow && s) { v = true; break }
                    if (!grow && !s) { v = false; break }
                }
                rows[y * w + x] = v
            }
            val out = BooleanArray(m.size)
            for (y in 0 until h) for (x in 0 until w) {
                var v = !grow
                for (yy in y - r..y + r) {
                    val s = if (yy in 0 until h) rows[yy * w + x] else !grow
                    if (grow && s) { v = true; break }
                    if (!grow && !s) { v = false; break }
                }
                out[y * w + x] = v
            }
            return out
        }
    }
}
