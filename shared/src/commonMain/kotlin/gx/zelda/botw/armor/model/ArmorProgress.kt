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

/** Everything the user has entered for the BotW armor tracker. Persisted as one JSON blob. */
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

    fun withPiece(pieceId: String, transform: (PieceProgress) -> PieceProgress): ArmorProgress =
        copy(pieces = pieces + (pieceId to transform(of(pieceId))))
}
