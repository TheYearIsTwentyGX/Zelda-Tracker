package gx.zelda.core.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** One choice in a [SortMenu] section. */
class SortOption(val label: String, val selected: Boolean, val onSelect: () -> Unit)

/** A titled group of mutually exclusive choices; [title] null for a menu with one plain list. */
class SortSection(val title: String?, val options: List<SortOption>)

/**
 * "Sort: [summary] ▾" text button opening a menu of [sections]. With several sections the menu
 * stays open after a pick, so each section can be set in one visit.
 */
@Composable
fun SortMenu(summary: String, sections: List<SortSection>) {
    var open by remember { mutableStateOf(false) }
    val closeOnSelect = sections.size == 1
    Box {
        TextButton(onClick = { open = true }) {
            Text("Sort: $summary")
            Icon(Icons.Filled.ArrowDropDown, contentDescription = null)
        }
        DropdownMenu(expanded = open, onDismissRequest = { open = false }) {
            sections.forEachIndexed { i, section ->
                if (i > 0) HorizontalDivider(Modifier.padding(vertical = 4.dp))
                section.title?.let {
                    Text(
                        it,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    )
                }
                section.options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.label) },
                        onClick = {
                            option.onSelect()
                            if (closeOnSelect) open = false
                        },
                        trailingIcon = { if (option.selected) Icon(Icons.Filled.Check, contentDescription = "Selected") },
                    )
                }
            }
        }
    }
}

/** Single-list convenience: "Sort: X ▾" over [options], closing once one is picked. */
@Composable
fun <T> SortMenu(current: T, options: List<T>, label: (T) -> String, onSelect: (T) -> Unit) =
    SortMenu(
        summary = label(current),
        sections = listOf(SortSection(null, options.map { SortOption(label(it), it == current) { onSelect(it) } })),
    )
