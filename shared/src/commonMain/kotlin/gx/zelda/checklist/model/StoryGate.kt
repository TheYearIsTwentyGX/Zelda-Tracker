package gx.zelda.checklist.model

/**
 * What a game's story has unlocked on one save file, for [ChecklistItem.unlockedBy]: the Main Quest
 * steps done there, and every step's name. With [enabled] off (the Settings toggle) nothing is
 * locked, but the names still give an item's page its "After …" line.
 */
data class StoryGate(
    val enabled: Boolean = false,
    /** Ids of the story file's steps done on the save file, with every step those require. */
    val done: Set<String> = emptySet(),
    /** Every step of the story file, id to name. An id not in here gates nothing. */
    val stepNames: Map<String, String> = emptyMap(),
) {
    /**
     * True when [item] is hidden: gating is on, one of its [ChecklistItem.unlockedBy] steps isn't
     * done, and nothing on it is. Anything ticked, even ahead of the story, always shows.
     */
    fun isLocked(item: ChecklistItem, progress: ChecklistProgress): Boolean =
        enabled && !progress.isStarted(item) && item.unlockedBy.any { it in stepNames && it !in done }

    /** The names of [item]'s known [ChecklistItem.unlockedBy] steps, for its page. */
    fun stepsFor(item: ChecklistItem): List<String> = item.unlockedBy.mapNotNull(stepNames::get)

    companion object {
        /** No story file, or the story file itself: nothing is gated. */
        val OFF = StoryGate()

        /**
         * [story]'s steps done on [progress], gated when [enabled]. A ticked step counts every step it
         * [ChecklistItem.requires], however far back, as done too: a save that ticked a temple has
         * reached what opens it, even if that row was never ticked. This is for gating only; the
         * Main Quest's own rows and counts still show just what was ticked.
         */
        fun of(enabled: Boolean, story: ChecklistCatalog, progress: ChecklistProgress): StoryGate {
            val done = mutableSetOf<String>()
            fun reach(step: ChecklistItem) {
                if (done.add(step.id)) step.requires.mapNotNull(story::item).forEach(::reach)
            }
            story.items.filter(progress::isDone).forEach(::reach)
            return StoryGate(enabled = enabled, done = done, stepNames = story.items.associate { it.id to it.name })
        }
    }
}

/**
 * Each step of a story file by how far into the story it comes: 0 for a step that requires nothing,
 * otherwise one more than the furthest step it [ChecklistItem.requires]. Dataset tests use it to
 * check an item never unlocks before something it requires.
 */
fun storyOrder(story: ChecklistCatalog): Map<String, Int> {
    val depth = mutableMapOf<String, Int>()
    fun visit(item: ChecklistItem, path: Set<String>): Int = depth[item.id] ?: run {
        // problems() rejects loops; guard anyway so a bad file can't recurse forever.
        val before = item.requires.filter { it !in path }.mapNotNull(story::item)
        (before.maxOfOrNull { visit(it, path + item.id) + 1 } ?: 0).also { depth[item.id] = it }
    }
    story.items.forEach { visit(it, emptySet()) }
    return depth
}
