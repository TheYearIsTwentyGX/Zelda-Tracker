package gx.zelda.botw.armor.model

import kotlinx.serialization.Serializable

@Serializable
enum class Slot(val label: String) {
    HEAD("Head"),
    BODY("Body"),
    LEGS("Legs"),
}

@Serializable
enum class Source(val label: String) {
    BASE("Base game"),
    DLC1("The Master Trials"),
    DLC2("The Champions' Ballad"),
    AMIIBO("amiibo"),
    ;

    val isDlc: Boolean get() = this == DLC1 || this == DLC2
}

@Serializable
data class Material(
    val id: String,
    val name: String,
    val category: String? = null,
)

@Serializable
data class MaterialCost(
    val material: String,
    val qty: Int,
)

@Serializable
data class ArmorSet(
    val id: String,
    val name: String,
    /** Set bonus granted when every piece is worn at ★2 or higher; null when the set has none. */
    val bonus: String? = null,
    val source: Source,
)

@Serializable
data class ArmorPiece(
    val id: String,
    val name: String,
    val setId: String? = null,
    val slot: Slot,
    val source: Source,
    val effect: String? = null,
    /** Defense at each level, index = star count. One entry for pieces that can't be upgraded. */
    val defense: List<Int>,
    /** Materials for each upgrade; `upgrades[0]` takes the piece from ★0 to ★1. */
    val upgrades: List<List<MaterialCost>> = emptyList(),
    /** Who upgrades this piece when it isn't a Great Fairy. */
    val upgradeNote: String? = null,
) {
    val maxLevel: Int get() = upgrades.size
    val isUpgradable: Boolean get() = upgrades.isNotEmpty()
}

@Serializable
data class ArmorCatalog(
    val materials: List<Material>,
    val sets: List<ArmorSet>,
    val pieces: List<ArmorPiece>,
) {
    private val materialsById by lazy { materials.associateBy { it.id } }
    private val setsById by lazy { sets.associateBy { it.id } }
    private val piecesById by lazy { pieces.associateBy { it.id } }
    private val piecesBySet by lazy { pieces.filter { it.setId != null }.groupBy { it.setId!! } }

    fun material(id: String): Material = materialsById[id] ?: Material(id, id)
    fun set(id: String?): ArmorSet? = id?.let { setsById[it] }
    fun piece(id: String): ArmorPiece? = piecesById[id]
    fun piecesIn(setId: String): List<ArmorPiece> = piecesBySet[setId].orEmpty()
}
