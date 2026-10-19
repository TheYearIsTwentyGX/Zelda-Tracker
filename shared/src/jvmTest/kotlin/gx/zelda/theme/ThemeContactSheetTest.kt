package gx.zelda.theme

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.ImageComposeScene
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import gx.zelda.core.AppSettings
import gx.zelda.core.Games
import gx.zelda.core.ThemeMode
import gx.zelda.core.ui.HomeScreen
import gx.zelda.core.ui.AppTheme
import gx.zelda.core.ui.RegionHeader
import gx.zelda.core.ui.Stars
import gx.zelda.core.ui.Tag
import gx.zelda.core.ui.ZeldaTheme
import org.jetbrains.skia.EncodedImageFormat
import java.io.File
import kotlin.test.Test
import kotlin.test.assertTrue

/**
 * Renders a sample tracker screen in every theme, light beside dark, plus the real home screen with
 * each game's accent, to PNGs under `build/reports/theme-sheets/` for a person to look at. A smoke
 * test only: it checks they render, not what they look like.
 */
class ThemeContactSheetTest {
    private val out = File("build/reports/theme-sheets").apply { mkdirs() }

    @Test
    fun everyThemeRenders() {
        for (theme in GameThemes.byId.values + GameThemes.Neutral) {
            val file = File(out, "${theme.id}.png")
            render(file, width = 2 * PANEL, height = 720) {
                Row(Modifier.fillMaxSize()) {
                    for (dark in listOf(false, true)) {
                        CompositionLocalProvider(LocalAppearance provides Appearance(dark, gameThemes = true)) {
                            ZeldaTheme(theme) { SampleTracker(Modifier.width(PANEL.dp).fillMaxHeight()) }
                        }
                    }
                }
            }
            assertTrue(file.length() > 0, "$file is empty")
        }
    }

    @Test
    fun homeRowsRender() {
        val file = File(out, "home.png")
        render(file, width = 2 * PANEL, height = 1000) {
            Row(Modifier.fillMaxSize()) {
                for (mode in listOf(ThemeMode.LIGHT, ThemeMode.DARK)) {
                    AppTheme(AppSettings(themeMode = mode)) { HomeRows(Modifier.width(PANEL.dp).fillMaxHeight()) }
                }
            }
        }
        assertTrue(file.length() > 0)
    }

    private fun render(file: File, width: Int, height: Int, content: @Composable () -> Unit) {
        val scene = ImageComposeScene(width * SCALE, height * SCALE, Density(SCALE.toFloat()), content = content)
        try {
            // A second frame, in case a font finished loading after the first.
            scene.render(0)
            val image = scene.render(1_000_000_000)
            file.writeBytes(image.encodeToData(EncodedImageFormat.PNG)!!.bytes)
        } finally {
            scene.close()
        }
    }

    @OptIn(ExperimentalMaterial3Api::class)
    @Composable
    private fun SampleTracker(modifier: Modifier) {
        Surface(modifier, color = MaterialTheme.colorScheme.background) {
            Column {
                TopAppBar(
                    title = { Text("Items & Equipment") },
                    navigationIcon = { IconButton(onClick = {}) { Icon(Icons.Filled.Menu, null) } },
                    actions = {
                        IconButton(onClick = {}) { Icon(Icons.Filled.Search, null) }
                        IconButton(onClick = {}) { Icon(Icons.Filled.MoreVert, null) }
                    },
                )
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp),
                ) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("412 / 900", style = MaterialTheme.typography.titleLarge)
                        GameProgressBar(progress = { 412 / 900f })
                        Text("Next: the rock circle west of the Lost Woods", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.primary)
                    }
                }
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = true, onClick = {}, label = { Text("Incomplete") })
                    FilterChip(selected = false, onClick = {}, label = { Text("DLC") })
                }
                RegionHeader("Lanayru", found = 12, done = 9, total = 21)
                Surface(color = MaterialTheme.colorScheme.surfaceContainerLow, modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
                    Column {
                        for ((name, done) in listOf("Lift the rock" to true, "Shoot the balloon" to false, "Race to the stump" to false)) {
                            ListItem(
                                leadingContent = { Checkbox(checked = done, onCheckedChange = {}) },
                                headlineContent = { Text(name) },
                                supportingContent = { Text("Near the Zora's Domain bridge") },
                                trailingContent = { Tag("Puzzle") },
                                colors = androidx.compose.material3.ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceContainerLow),
                            )
                        }
                    }
                }
                Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                    Stars(level = 2, max = 4)
                    Button(onClick = {}) { Text("Mark all complete") }
                }
            }
        }
    }

    @Composable
    private fun HomeRows(modifier: Modifier) {
        Box(modifier) {
            HomeScreen(games = Games.all, onOpenTracker = { _, _ -> }, onOpenMenu = {}, onOpenSettings = {})
        }
    }

    private companion object {
        const val PANEL = 360
        const val SCALE = 2
    }
}
