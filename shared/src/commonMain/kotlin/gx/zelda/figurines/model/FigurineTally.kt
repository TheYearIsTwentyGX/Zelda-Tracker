package gx.zelda.figurines.model

/** Owned / total for one category, whatever the list's filters show. */
data class FigurineCategoryTally(val category: FigurineCategory, val owned: Int, val total: Int)

/** Where one save file stands with the whole collection. */
data class FigurineTally(
    val owned: Int,
    val total: Int,
    /** What Carlov can give the save file now, owned ones included: the game's pool. */
    val available: Int,
    val categories: List<FigurineCategoryTally>,
) {
    val odds: FigurineOdds get() = FigurineOdds(available, owned)
}

fun FigurineCatalog.tally(progress: FigurineProgress): FigurineTally = FigurineTally(
    owned = figurines.count { progress.isOwned(it.number) },
    total = figurines.size,
    available = figurines.count { isAvailable(it, progress) },
    categories = categories.map { c ->
        val inCategory = figurines.filter { it.category == c.id }
        FigurineCategoryTally(c, inCategory.count { progress.isOwned(it.number) }, inCategory.size)
    },
)
