package gx.zelda.checklist.data

import gx.zelda.checklist.model.ChecklistCatalog
import gx.zelda.checklist.model.ChecklistItem
import gx.zelda.checklist.model.SameAs
import gx.zelda.checklist.model.linkTargetProblems
import gx.zelda.core.Game
import gx.zelda.core.Games

/**
 * `sameAs` links between a game's checklist files: one item declares it's the same real thing as an
 * item, or one tier of an item, in another file (Batreaux's 5-crystal reward is the Medium Wallet),
 * and the two share one progress. The rules are in docs/checklist-trackers.md, "Linked items".
 */
object ChecklistLinks {
    /**
     * The games with links between their checklists, whose old saves [ChecklistLinkMigration] merges.
     * `ChecklistLinksDatasetTest` checks this is exactly the games whose files have a link.
     */
    val GAMES: List<Game> = listOf(Games.SS)

    /** One linked item of [game]: [item] in [dataFile] is the same as what [link] names, in [targetFile]. */
    data class Alias(val dataFile: String, val item: ChecklistItem, val link: SameAs, val targetFile: String)

    /** Every link in [catalogs] (by `dataFile`, all [game]'s checklists), with the file it points into. */
    fun aliases(game: Game, catalogs: Map<String, ChecklistCatalog>): List<Alias> = catalogs.flatMap { (dataFile, catalog) ->
        catalog.items.mapNotNull { item ->
            val link = item.link ?: return@mapNotNull null
            val target = if (link.file == null) dataFile else fileOf(game, link.file) ?: return@mapNotNull null
            Alias(dataFile, item, link, target)
        }
    }

    /** The `dataFile` of [game]'s checklist a link calls [stem], or null when it has none. */
    fun fileOf(game: Game, stem: String): String? =
        ChecklistStorage.checklistFiles(game).firstOrNull { ChecklistStorage.stemOf(it) == stem }

    /**
     * Everything wrong with the links from one of [game]'s files into another, as readable lines;
     * [catalogs] are all its checklists by `dataFile`. Links within a file are checked by
     * [ChecklistCatalog.problems]. A link must name a file of the game other than its own, and an
     * item there; with a tier if, and only if, that item has tiers, and one it has; and never an
     * item that is itself linked, which rules out chains and loops. Between two items without tiers,
     * the one in the file the game lists later is linked to the earlier.
     */
    fun problems(game: Game, catalogs: Map<String, ChecklistCatalog>): List<String> = buildList {
        val order = ChecklistStorage.checklistFiles(game)
        for ((dataFile, catalog) in catalogs) {
            val stem = ChecklistStorage.stemOf(dataFile)
            for (item in catalog.items) {
                val link = item.link ?: continue
                val file = link.file ?: continue
                val at = "$stem: item '${item.id}'"
                if (file == stem) {
                    add("$at names its own file: write it as '${SameAs(null, link.itemId, link.tier)}'")
                    continue
                }
                val targetFile = fileOf(game, file)
                val target = targetFile?.let(catalogs::get)
                if (targetFile == null || target == null) {
                    add("$at is the same as '$link', but ${game.id} has no checklist '$file'")
                    continue
                }
                val targetItem = target.item(link.itemId)
                if (targetItem == null) {
                    add("$at is the same as unknown item '${link.itemId}' in $file")
                    continue
                }
                addAll(linkTargetProblems(at, link, targetItem))
                if (targetItem.tiers.isEmpty() && order.indexOf(targetFile) > order.indexOf(dataFile)) {
                    add("$at is the same as '$link', in a file listed after $stem: link the item in the later file to the earlier")
                }
            }
        }
    }
}
