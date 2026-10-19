package gx.zelda.kinstones.model

import kotlinx.serialization.Serializable

/** One save file's Kinstone fusions: the ids of those done. */
@Serializable
data class KinstoneProgress(val fused: Set<String> = emptySet()) {
    fun isFused(fusionId: String): Boolean = fusionId in fused

    fun withFused(fusionId: String, done: Boolean): KinstoneProgress =
        copy(fused = if (done) fused + fusionId else fused - fusionId)

    /** Every one of [fusionIds] done, or none of them. */
    fun withAllFused(fusionIds: Collection<String>, done: Boolean): KinstoneProgress =
        copy(fused = if (done) fused + fusionIds else fused - fusionIds.toSet())
}

/** Viewing preferences, shared by every save file. */
@Serializable
data class KinstonePrefs(
    /**
     * Spoiler-safe mode: a fusion not done yet shows who to fuse with and the piece, but not what
     * it does, until it's done or tapped to reveal.
     */
    val hideResults: Boolean = false,
)
