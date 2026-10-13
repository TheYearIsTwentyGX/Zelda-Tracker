package gx.zelda.botw.armor.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How the armor list is ordered. */
@Serializable
enum class ArmorOrder(val label: String) {
    ALPHABETICAL("Alphabetical"),
    IN_GAME("In-Game"),
}

/** Whether the armor list is split under set headers. */
@Serializable
enum class ArmorGroupBy(val label: String) {
    NONE("None"),
    SET("Set"),
}

/** The single sort setting that [ArmorOrder] + [ArmorGroupBy] replaced; read only to convert old prefs. */
@Serializable
enum class LegacyArmorSort(val order: ArmorOrder, val group: ArmorGroupBy) {
    ALPHABETICAL(ArmorOrder.ALPHABETICAL, ArmorGroupBy.SET),
    BODY_PART(ArmorOrder.IN_GAME, ArmorGroupBy.NONE),
    SET(ArmorOrder.IN_GAME, ArmorGroupBy.SET),
}

@Serializable
enum class MaterialSort(val label: String) {
    MOST_NEEDED("Most needed"),
    ALPHABETICAL("A–Z"),
    IN_GAME("In-game"),
}

@Serializable
data class PieceProgress(
    val owned: Boolean = false,
    val level: Int = 0,
)

/** Viewing preferences, shared by every save file. */
@Serializable
data class ArmorPrefs(
    val showDlc: Boolean = true,
    val showAmiibo: Boolean = true,
    val order: ArmorOrder = ArmorOrder.ALPHABETICAL,
    val group: ArmorGroupBy = ArmorGroupBy.SET,
    val materialSort: MaterialSort = MaterialSort.MOST_NEEDED,
    @SerialName("sort") val legacySort: LegacyArmorSort? = null,
) {
    /** Converts a saved single "sort" value into [order] + [group]. */
    fun normalized(): ArmorPrefs =
        legacySort?.let { copy(order = it.order, group = it.group, legacySort = null) } ?: this
}

/**
 * The active save file's pieces combined with the shared [ArmorPrefs] — what the screens and
 * calculator work from. Also the shape of the pre-save-file JSON blob, which is migrated on load.
 */
@Serializable
data class ArmorProgress(
    val pieces: Map<String, PieceProgress> = emptyMap(),
    val showDlc: Boolean = true,
    val showAmiibo: Boolean = true,
    val order: ArmorOrder = ArmorOrder.ALPHABETICAL,
    val group: ArmorGroupBy = ArmorGroupBy.SET,
    val materialSort: MaterialSort = MaterialSort.MOST_NEEDED,
    @SerialName("sort") val legacySort: LegacyArmorSort? = null,
) {
    fun of(pieceId: String): PieceProgress = pieces[pieceId] ?: PieceProgress()

    fun isVisible(source: Source): Boolean = when {
        source.isDlc -> showDlc
        source == Source.AMIIBO -> showAmiibo
        else -> true
    }

    val prefs: ArmorPrefs get() = ArmorPrefs(showDlc, showAmiibo, order, group, materialSort, legacySort).normalized()

    fun withPiece(pieceId: String, transform: (PieceProgress) -> PieceProgress): ArmorProgress =
        copy(pieces = pieces + (pieceId to transform(of(pieceId))))

    companion object {
        fun of(pieces: Map<String, PieceProgress>, prefs: ArmorPrefs): ArmorProgress {
            val p = prefs.normalized()
            return ArmorProgress(pieces, p.showDlc, p.showAmiibo, p.order, p.group, p.materialSort)
        }
    }
}
