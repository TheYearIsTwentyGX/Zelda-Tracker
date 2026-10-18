package gx.zelda.checklist

import gx.zelda.core.Game
import gx.zelda.core.ReleaseDate
import gx.zelda.core.SaveFile
import gx.zelda.core.Tracker
import gx.zelda.core.TrackerKind

/** A game made only of checklists, as a new game would be: two files, an overview and two checklists. */
val ChecklistTestGame = Game(
    id = "test",
    title = "Test Game",
    released = ReleaseDate(2030, 1, 1),
    platform = "Test",
    saveFiles = listOf(SaveFile("file_1", "File 1"), SaveFile("file_2", "File 2")),
    trackers = listOf(
        Tracker(TrackerKind.OVERVIEW, "Progress Overview", ""),
        Tracker(TrackerKind.CHECKLIST, "Hearts", "", dataFile = "test/hearts.json"),
        Tracker(TrackerKind.CHECKLIST, "Echoes", "", dataFile = "test/echoes.json"),
    ),
)
