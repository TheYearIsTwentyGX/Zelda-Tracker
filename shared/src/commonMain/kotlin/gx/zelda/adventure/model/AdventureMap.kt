package gx.zelda.adventure.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * One Hyrule Warriors: Definitive Edition Adventure Mode map: a grid of battle squares, each with
 * its battle, A-rank limits, rewards, Gold Skulltulas and item-card searches. Bundled as
 * `files/hwde/<map>.json`, built by tools/hwde/build_maps.py; docs/hwde.md has the format, the
 * sources and how the art lines up. [problems] checks a file.
 *
 * Every id in it (squares, rewards, Skulltulas, searches) is a storage key: the converter keeps
 * them in tools/hwde/ids/<map>.txt so none can change once shipped.
 */
@Serializable
data class AdventureMap(
    val id: String,
    val name: String,
    val cols: Int,
    val rows: Int,
    /**
     * Where the map starts, the one square open before any battle. The Rewards Map has none: its
     * squares open as Gold Skulltula illustrations are completed.
     */
    val start: String? = null,
    /** More squares open from the beginning, on a map with more than one start (Grand Travels). */
    val otherStarts: List<String> = emptyList(),
    /** Squares that open once other squares are cleared, rather than from a neighbour. */
    val gates: List<MapGate> = emptyList(),
    /** The Dark Ruler's square. */
    val finalBoss: String? = null,
    /** The map's pictures; null for a map nobody has art for, whose squares are drawn as plain tiles. */
    val art: MapArt? = null,
    /** The squares' difficulty colours, easiest first. */
    val regions: List<MapRegion> = emptyList(),
    /** The item cards this map uses or gives, as the game names them. */
    val cards: List<ItemCard> = emptyList(),
    /** The warriors any square names, for their display names. */
    val warriors: List<Warrior> = emptyList(),
    /** In reading order (row by row). Blank squares on a map aren't listed. */
    val squares: List<Square>,
) {
    private val byId: Map<String, Square> by lazy { squares.associateBy { it.id } }
    private val byCell: Map<Pair<Int, Int>, Square> by lazy { squares.associateBy { it.row to it.col } }
    private val cardsById: Map<String, ItemCard> by lazy { cards.associateBy { it.id } }
    private val warriorNames: Map<String, String> by lazy { warriors.associate { it.id to it.name } }

    fun square(id: String): Square? = byId[id]

    /** Every square open from the beginning. */
    val starts: List<String> get() = listOfNotNull(start) + otherStarts

    /** The square at [row] and [col] (both from 1), or null for a blank square or off the grid. */
    fun squareAt(row: Int, col: Int): Square? = byCell[row to col]

    fun card(id: String): ItemCard? = cardsById[id]

    fun warriorName(id: String): String = warriorNames[id] ?: id

    fun regionName(id: String): String = regions.firstOrNull { it.id == id }?.name ?: id

    /** A square's size in map pixels: its picture's, or the original game's screen for a map without art. */
    val squareWidth: Int get() = art?.squareWidth ?: 256
    val squareHeight: Int get() = art?.squareHeight ?: 176

    /** [square]'s neighbour across [side], or null at the edge or a blank square. */
    fun neighbour(square: Square, side: Side): Square? = squareAt(square.row + side.dRow, square.col + side.dCol)

    /**
     * Whether the wall on [side] of [square] is open: no blockade drawn on either side of it. The
     * converter makes blockades match from both sides; [problems] reports one that doesn't.
     */
    fun isOpen(square: Square, side: Side): Boolean {
        val other = neighbour(square, side) ?: return false
        return side !in square.blockades && side.opposite !in other.blockades
    }

    /** Squares that give [cardId] when cleared. */
    fun sourcesOf(cardId: String): List<Square> = squares.filter { cardId in it.givesCards }

    /** Every reward, Skulltula and search id, square by square: what the progress's `done` set can hold. */
    val checkIds: Set<String> by lazy {
        squares.flatMapTo(mutableSetOf()) { s -> s.rewards.map { it.id } + s.skulltulas.map { it.id } + s.searches.map { it.id } }
    }

    /**
     * Everything wrong with this map, as readable lines; empty when it's sound. The dataset tests
     * assert this is empty for every map HW:DE lists.
     */
    fun problems(): List<String> = buildList {
        if (cols !in 1..MAX_COLS || rows !in 1..MAX_ROWS) add("grid ${cols}x$rows is outside 1..${MAX_COLS}x1..$MAX_ROWS")
        if (squares.isEmpty()) add("no squares")
        val ids = squares.map { it.id } + squares.flatMap { s -> s.rewards.map { it.id } + s.skulltulas.map { it.id } + s.searches.map { it.id } }
        ids.groupBy { it }.filter { it.value.size > 1 }.keys.forEach { add("duplicate id '$it'") }
        ids.filterNot { ID.matches(it) }.forEach { add("badly formed id '$it'") }
        squares.groupBy { it.row to it.col }.filter { it.value.size > 1 }.keys.forEach { add("two squares at row ${it.first}, col ${it.second}") }
        val regionIds = regions.map { it.id }.toSet()
        val warriorIds = warriors.map { it.id }.toSet()
        for (s in squares) {
            if (s.id != "r${s.row}c${s.col}") add("${s.id}: id doesn't match row ${s.row}, col ${s.col}")
            if (s.row !in 1..rows || s.col !in 1..cols) add("${s.id}: outside the ${cols}x$rows grid")
            if (s.region !in regionIds) add("${s.id}: unknown region '${s.region}'")
            if (s.mission.isBlank()) add("${s.id}: no mission")
            s.rewards.filter { it.name.isBlank() }.forEach { add("${it.id}: no name") }
            (s.rewards.mapNotNull { it.warrior } + s.warriors.map { it.warrior }).filter { it !in warriorIds }
                .forEach { add("${s.id}: unknown warrior '$it'") }
            s.rewards.filterNot { it.id.startsWith("${s.id}.") }.forEach { add("${it.id}: not under ${s.id}") }
            s.skulltulas.filterNot { it.id.startsWith("${s.id}.") }.forEach { add("${it.id}: not under ${s.id}") }
            s.searches.filterNot { it.id.startsWith("${s.id}.") }.forEach { add("${it.id}: not under ${s.id}") }
            (s.givesCards + s.searches.map { it.card }).filter { card(it) == null }.forEach { add("${s.id}: unknown card '$it'") }
            for (search in s.searches) {
                if (search.wholeSquare) {
                    if (search.row != null || search.col != null) add("${search.id}: a whole-square search has no cell")
                } else if (search.row !in 0 until SEARCH_ROWS || search.col !in 0 until SEARCH_COLS) {
                    add("${search.id}: cell (${search.row}, ${search.col}) is outside the ${SEARCH_COLS}x$SEARCH_ROWS grid")
                }
                search.opens.filter { square(it) == null }.forEach { add("${search.id}: opens '$it', which isn't a square") }
                val card = card(search.card)
                if (sourcesOf(search.card).isEmpty() && card?.effects != listOf(CardEffect.COMPASS) && card?.anyOf.isNullOrEmpty()) {
                    add("${search.id}: no square on the map gives the ${search.card} card")
                }
            }
            for (side in s.blockades) {
                val other = neighbour(s, side)
                if (other != null && side.opposite !in other.blockades) add("${s.id}: blockade ${side.name.lowercase()} isn't on ${other.id} too")
            }
        }
        starts.filter { square(it) == null }.forEach { add("start '$it' isn't a square") }
        for (gate in gates) {
            (listOf(gate.square) + gate.afterCleared).filter { square(it) == null }.forEach { add("gate: '$it' isn't a square") }
        }
        cards.flatMap { c -> c.anyOf.filter { card(it) == null }.map { "card '${c.id}': '$it' isn't a card on this map" } }.forEach(::add)
        finalBoss?.let { if (square(it)?.finalBoss != true) add("final boss '$it' isn't a square marked finalBoss") }
        squares.filter { it.finalBoss && it.id != finalBoss }.forEach { add("${it.id}: marked finalBoss but the map's is '$finalBoss'") }
        cards.groupBy { it.id }.filter { it.value.size > 1 }.keys.forEach { add("duplicate card '$it'") }
        warriors.groupBy { it.id }.filter { it.value.size > 1 }.keys.forEach { add("duplicate warrior '$it'") }
        regions.filter { r -> squares.none { it.region == r.id } }.forEach { add("region '${it.id}' has no squares") }
        if (squareWidth <= 0 || squareHeight <= 0) add("art has no square size")
    }

    companion object {
        const val MAX_COLS = 16
        const val MAX_ROWS = 8

        /** The search screen's grid over one square: the original game's 16×11 tiles. */
        const val SEARCH_COLS = 16
        const val SEARCH_ROWS = 11

        private val ID = Regex("r\\d+c\\d+(\\.[a-z0-9_]+)?")
    }
}

/**
 * Where a map's pictures are, under `files/`: one image per square ([squares]`/<square id>.webp`,
 * [squareWidth]×[squareHeight]) for zoomed in, and the whole map at a smaller size ([preview]) for
 * zoomed out. Pointing these at other images (captures of the game's own map, say) changes the art
 * without touching code; docs/hwde.md says how they must line up.
 */
@Serializable
data class MapArt(
    val squares: String,
    val preview: String? = null,
    val squareWidth: Int = 256,
    val squareHeight: Int = 176,
) {
    fun squarePath(square: Square): String = "files/$squares/${square.id}.webp"

    val previewPath: String? get() = preview?.let { "files/$it" }
}

@Serializable
data class MapRegion(val id: String, val name: String)

/** [square] opens once every square in [afterCleared] is cleared (Grand Travels' Phantom Hourglass). */
@Serializable
data class MapGate(val square: String, val afterCleared: List<String>)

@Serializable
data class Warrior(val id: String, val name: String)

@Serializable
data class Square(
    /** "r<row>c<col>", both from 1, top left first. */
    val id: String,
    /** Prima's official guide and Zelda Wiki: row letter, column number ("A-6"). */
    val label: String,
    /** SBAllen's guide and the Helper: column letter, row number ("F-1"). */
    val altLabel: String,
    val row: Int,
    val col: Int,
    /** A [MapRegion] id: the square's difficulty colour. */
    val region: String,
    val battle: BattleType? = null,
    /** The victory condition, as the game words it. */
    val mission: String,
    /** A name for the square where the game gives one: the Rewards Map's are named by their illustrations. */
    val title: String? = null,
    /** A map rule this square plays under, e.g. "Master Quest Rule: No Guarding!". */
    val rule: String? = null,
    val finalBoss: Boolean = false,
    /** The warriors the battle allows (one of them must be picked); empty when anyone can go. */
    val warriors: List<WarriorPick> = emptyList(),
    val aRank: ARankLimits = ARankLimits(),
    /** Sides with a wavy wall: the square doesn't open its neighbour that way. */
    val blockades: List<Side> = emptyList(),
    val rewards: List<Reward> = emptyList(),
    /** Item cards a clear can give: these feed the inventory rather than being ticked. */
    val givesCards: List<String> = emptyList(),
    val skulltulas: List<Skulltula> = emptyList(),
    val searches: List<Search> = emptyList(),
    /** A quiz battle's answers, in order. */
    val quiz: List<String> = emptyList(),
    /** Where the sources disagree about this square, in our words. */
    val note: String? = null,
)

@Serializable
enum class BattleType(val label: String) {
    @SerialName("adventure") ADVENTURE("Adventure Battle"),
    @SerialName("challenge") CHALLENGE("Challenge Battle"),
}

@Serializable
data class WarriorPick(val warrior: String, val weapon: String? = null)

/**
 * The A-rank limits: at least [kos] KOs, within [minutes], and at most [damage] damage taken (on
 * the Switch, a percentage of the warrior's health). The game doesn't show them; they're the
 * guides' findings.
 */
@Serializable
data class ARankLimits(val kos: Int? = null, val minutes: Int? = null, val damage: Int? = null)

@Serializable
enum class Side(val dRow: Int, val dCol: Int) {
    @SerialName("north") NORTH(-1, 0),
    @SerialName("east") EAST(0, 1),
    @SerialName("south") SOUTH(1, 0),
    @SerialName("west") WEST(0, -1);

    val opposite: Side get() = entries[(ordinal + 2) % 4]
}

@Serializable
data class Reward(
    val id: String,
    val slot: RewardSlot,
    val type: RewardType,
    /** Whose it is: for most rewards, the warrior who has to earn it. */
    val warrior: String? = null,
    val weapon: String? = null,
    val level: String? = null,
    val name: String,
    /** A treasure's keep: capture it to open the chest or reach the pot. */
    val keep: String? = null,
    /** "pot" or "jar" when the treasure is in one rather than a chest. */
    val container: String? = null,
)

@Serializable
enum class RewardSlot(val label: String) {
    @SerialName("aRank") A_RANK("\"A\" Rank Victory"),
    @SerialName("clear") CLEAR("Battle Victory"),
    @SerialName("treasure") TREASURE("Treasure"),
}

@Serializable
enum class RewardType(val label: String, val plural: String) {
    @SerialName("heart_container") HEART_CONTAINER("Heart Container", "Heart Containers"),
    @SerialName("heart_piece") HEART_PIECE("Piece of Heart", "Pieces of Heart"),
    @SerialName("weapon") WEAPON("Weapon", "Weapons"),
    @SerialName("outfit") OUTFIT("Costume", "Costumes"),
    @SerialName("warrior") WARRIOR("Warrior", "Warriors"),
    @SerialName("material") MATERIAL("Material", "Materials"),
    @SerialName("fairy_food") FAIRY_FOOD("Fairy food", "Fairy food"),
    @SerialName("my_fairy") MY_FAIRY("Companion fairy", "Companion fairies"),
    @SerialName("fairy_clothing") FAIRY_CLOTHING("Fairy clothing", "Fairy clothing"),
    @SerialName("other") OTHER("Reward", "Rewards"),
}

@Serializable
data class Skulltula(
    val id: String,
    /** What makes it appear. */
    val condition: String,
    /** Where it is once it has. */
    val where: String? = null,
)

/**
 * Using [card] on one cell of the search screen ([row] 0–10, [col] 0–15 over the square's picture),
 * or on the whole square when [wholeSquare].
 */
@Serializable
data class Search(
    val id: String,
    val card: String,
    val row: Int? = null,
    val col: Int? = null,
    val wholeSquare: Boolean = false,
    /** Squares the search opens, when it opens somewhere other than the neighbours (Lorule's Fissures). */
    val opens: List<String> = emptyList(),
)

@Serializable
data class ItemCard(
    val id: String,
    val name: String,
    /** What using it does on this map. */
    val effects: List<CardEffect> = emptyList(),
    /** Zelda Wiki's description of its use on this map. */
    val text: String = "",
    /** Rupees in DE's Item Card Shop, once found. */
    val shopPrice: Int? = null,
    /** For a search any of several cards can do (any Instrument of the Sirens): those cards. */
    val anyOf: List<String> = emptyList(),
)

@Serializable
enum class CardEffect(val label: String) {
    @SerialName("reward") REWARD("Reveals a hidden reward"),
    @SerialName("enemies") ENEMIES("Reveals hidden enemies"),
    @SerialName("path") PATH("Opens a way to a neighbouring square"),
    @SerialName("rule") RULE("Lifts the square's rule"),
    @SerialName("compass") COMPASS("Shows where the other cards go"),
    @SerialName("other") OTHER("Other"),
}
