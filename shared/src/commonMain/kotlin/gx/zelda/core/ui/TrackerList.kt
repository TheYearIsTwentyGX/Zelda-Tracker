package gx.zelda.core.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.ZeroCornerSize
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.LocationOn
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconToggleButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import gx.zelda.core.DiscoveryState
import gx.zelda.theme.OrnamentDivider

/*
 * Building blocks shared by the region-grouped trackers (Shrines, Koroks, Side Quests, and The
 * Minish Cap's Heart Pieces and Kinstones): each region is a card made of a header and its rows,
 * and each row can be found, completed, or hidden as a spoiler.
 */

val CardInset = 12.dp
private val GroupGap = 12.dp

/** The top and bottom of a region's card: the theme's large corners (16dp in the neutral theme). */
private val CardTop: Shape
    @Composable get() = MaterialTheme.shapes.large.copy(bottomStart = ZeroCornerSize, bottomEnd = ZeroCornerSize)
private val CardBottom: Shape
    @Composable get() = MaterialTheme.shapes.large.copy(topStart = ZeroCornerSize, topEnd = ZeroCornerSize)

/** Places a row inside its region's card; the [last] row closes the card and leaves a gap below. */
@Composable
fun Modifier.groupRow(last: Boolean): Modifier = this
    .padding(bottom = if (last) GroupGap else 0.dp)
    .padding(horizontal = CardInset)
    .clip(if (last) CardBottom else RectangleShape)
    .background(MaterialTheme.colorScheme.surfaceContainer)

/**
 * The top of a region's card: "<region><suffix>", how many are found, and done / total.
 * [suffix] defaults to " Tower" for BotW's and TotK's tower regions; The Minish Cap's regions are
 * places, so it passes "". [foundLabel] names the middle state, e.g. "Started" for quests; a null
 * [found] leaves the count out, for a tracker whose entries are only ever done or not.
 * [leading] goes before the name, lined up with the rows' checkboxes: a [BulkCompleteCheckbox].
 */
@Composable
fun RegionHeader(
    region: String,
    found: Int?,
    done: Int,
    total: Int,
    foundLabel: String = "Found",
    suffix: String = " Tower",
    leading: (@Composable () -> Unit)? = null,
) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainerHighest,
        shape = CardTop,
        modifier = Modifier.fillMaxWidth().padding(horizontal = CardInset),
    ) {
        Column {
            val padding = if (leading == null) Modifier.padding(horizontal = 16.dp, vertical = 10.dp) else Modifier.padding(start = 4.dp, end = 16.dp)
            Row(padding, verticalAlignment = Alignment.CenterVertically) {
                leading?.invoke()
                Text(
                    region + suffix,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f),
                )
                if (found != null) {
                    Text(
                        "$foundLabel $found",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                }
                if (done == total) {
                    Tag(
                        "$done / $total",
                        container = MaterialTheme.colorScheme.primaryContainer,
                        content = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                } else {
                    Tag("$done / $total")
                }
            }
            // The game's ornament (none in the neutral theme) closes the header.
            OrnamentDivider(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp))
        }
    }
}

/** The divider between rows of a region card, indented past the checkbox. */
@Composable
fun RowDivider() {
    HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant)
}

/** What [FoundToggle] shows: a pin for places, or something else for e.g. a quest in progress. */
data class FoundToggleStyle(
    val on: ImageVector,
    val off: ImageVector,
    /** Read out when the entry is found. */
    val onLabel: String,
    /** Read out when it isn't: what tapping does. */
    val offLabel: String,
) {
    companion object {
        val Pin = FoundToggleStyle(Icons.Filled.LocationOn, Icons.Outlined.LocationOn, "Found", "Mark found")
    }
}

/**
 * Marks an entry found or undiscovered. A completed entry is always found, so the button is
 * disabled until it's unticked.
 */
@Composable
fun FoundToggle(state: DiscoveryState, onStateChange: (DiscoveryState) -> Unit, style: FoundToggleStyle = FoundToggleStyle.Pin) {
    val found = state != DiscoveryState.UNDISCOVERED
    IconToggleButton(
        checked = found,
        enabled = state != DiscoveryState.COMPLETED,
        onCheckedChange = { onStateChange(if (it) DiscoveryState.FOUND else DiscoveryState.UNDISCOVERED) },
    ) {
        Icon(
            if (found) style.on else style.off,
            contentDescription = if (found) style.onLabel else style.offLabel,
            tint = if (found) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** An undiscovered entry with spoilers hidden: tap to see it, or mark it found. */
@Composable
fun HiddenRow(
    title: String,
    onReveal: () -> Unit,
    onFound: () -> Unit,
    showDivider: Boolean,
    modifier: Modifier = Modifier,
    toggleStyle: FoundToggleStyle = FoundToggleStyle.Pin,
) {
    Column(modifier) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClickLabel = "Reveal", onClick = onReveal)
                .padding(start = 4.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
        ) {
            Icon(
                Icons.Filled.Lock,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(12.dp),
            )
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Tap to reveal", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            FoundToggle(DiscoveryState.UNDISCOVERED, { onFound() }, toggleStyle)
        }
        if (showDivider) RowDivider()
    }
}

/** The search box above a tracker's list; the keyboard's action key reads "Search". */
@Composable
fun SearchField(query: String, onQueryChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    OutlinedTextField(
        value = query,
        onValueChange = onQueryChange,
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
        trailingIcon = {
            if (query.isNotEmpty()) {
                IconButton(onClick = { onQueryChange("") }) {
                    Icon(Icons.Filled.Clear, contentDescription = "Clear search")
                }
            }
        },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
        modifier = modifier.fillMaxWidth(),
    )
}
