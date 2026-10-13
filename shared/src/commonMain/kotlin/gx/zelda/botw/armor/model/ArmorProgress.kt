package gx.zelda.botw.armor.model

import kotlinx.serialization.Serializable

@Serializable
enum class ArmorSort(val label: String) {
    ALPHABETICAL("A–Z"),
    BODY_PART("Body part"),
    SET("Set"),
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
    val sort: ArmorSort = ArmorSort.ALPHABETICAL,
)

/**
 * The active save file's pieces combined with the shared [ArmorPrefs] — what the screens and
 * calculator work from. Also the shape of the pre-save-file JSON blob, which is migrated on load.
 */
@Serializable
data class ArmorProgress(
    val pieces: Map<String, PieceProgress> = emptyMap(),
    val showDlc: Boolean = true,
    val showAmiibo: Boolean = true,
    val sort: ArmorSort = ArmorSort.ALPHABETICAL,
) {
    fun of(pieceId: String): PieceProgress = pieces[pieceId] ?: PieceProgress()

    fun isVisible(source: Source): Boolean = when {
        source.isDlc -> showDlc
        source == Source.AMIIBO -> showAmiibo
        else -> true
    }

    val prefs: ArmorPrefs get() = ArmorPrefs(showDlc, showAmiibo, sort)

    fun withPiece(pieceId: String, transform: (PieceProgress) -> PieceProgress): ArmorProgress =
        copy(pieces = pieces + (pieceId to transform(of(pieceId))))

    companion object {
        fun of(pieces: Map<String, PieceProgress>, prefs: ArmorPrefs) =
            ArmorProgress(pieces, prefs.showDlc, prefs.showAmiibo, prefs.sort)
    }
}
