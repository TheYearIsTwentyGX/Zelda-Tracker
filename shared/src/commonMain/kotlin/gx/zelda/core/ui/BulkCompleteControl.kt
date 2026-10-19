package gx.zelda.core.ui

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.selection.triStateToggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TriStateCheckbox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.state.ToggleableState
import androidx.compose.ui.unit.dp
import gx.zelda.core.BulkAction
import gx.zelda.core.BulkChange
import gx.zelda.core.BulkScope

/*
 * "Mark everything complete" for any tracker: a tri-state box per header, a labelled row per tab,
 * a confirm dialog and an Undo snackbar. The tracker supplies a [BulkScope] for each, writes the
 * change in one storage edit, and keeps a [BulkChange] for the undo. docs/checklist-trackers.md
 * walks through plugging a tracker in; the checklist screen is the worked example.
 */

val BulkScope.toggleState: ToggleableState
    get() = when (checked) {
        true -> ToggleableState.On
        false -> ToggleableState.Off
        null -> ToggleableState.Indeterminate
    }

/**
 * The tri-state box for a region or section header: ticked when everything in [scope] is complete,
 * a dash when some is. Tapping it asks [state] to mark all, or clear all when they already are.
 * A checkbox with a click handler is at least 48dp to tap.
 */
@Composable
fun BulkCompleteCheckbox(scope: BulkScope, state: BulkCompleteState, onApply: (BulkAction) -> Unit, modifier: Modifier = Modifier) {
    TriStateCheckbox(
        state = scope.toggleState,
        onClick = { state.request(scope, onApply) },
        modifier = modifier.semantics { contentDescription = scope.contentDescription },
    )
}

/**
 * The same toggle as a full-width row that says what it will do ("Mark all complete", "Clear all",
 * "Mark 3 shown complete") above where and how far along: for the top of a tab.
 */
@Composable
fun BulkCompleteRow(scope: BulkScope, state: BulkCompleteState, onApply: (BulkAction) -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp)
            .triStateToggleable(state = scope.toggleState, role = Role.Checkbox, onClick = { state.request(scope, onApply) })
            .clearAndSetSemantics { contentDescription = scope.contentDescription },
    ) {
        TriStateCheckbox(state = scope.toggleState, onClick = null, modifier = Modifier.padding(12.dp))
        Column(Modifier.weight(1f)) {
            Text(scope.actionLabel, style = MaterialTheme.typography.labelLarge)
            Text(
                "${scope.label} · ${scope.done} of ${scope.total} complete",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Which bulk change is waiting on its confirm dialog. One per screen, from [rememberBulkCompleteState];
 * show its dialog with [BulkConfirmDialog].
 */
@Stable
class BulkCompleteState {
    var pending: Pair<BulkScope, (BulkAction) -> Unit>? by mutableStateOf(null)
        private set

    /**
     * A tap on a toggle: applies [scope]'s action straight away when it's a small mark, otherwise
     * holds it for the confirm dialog. A clear always waits for the dialog.
     */
    fun request(scope: BulkScope, onApply: (BulkAction) -> Unit) {
        if (scope.changes == 0) return
        if (scope.needsConfirm) pending = scope to onApply else onApply(scope.action)
    }

    fun confirm() {
        val (scope, onApply) = pending ?: return
        pending = null
        onApply(scope.action)
    }

    fun dismiss() {
        pending = null
    }
}

@Composable
fun rememberBulkCompleteState(): BulkCompleteState = remember { BulkCompleteState() }

/**
 * The confirm dialog for [state]'s pending change, naming how many items, where and on which save
 * file ([saveFile] is its label), but never which items, so a spoiler-hidden row stays hidden.
 */
@Composable
fun BulkConfirmDialog(state: BulkCompleteState, saveFile: String) {
    val scope = state.pending?.first ?: return
    AlertDialog(
        onDismissRequest = state::dismiss,
        title = { Text(scope.confirmTitle) },
        text = { Text(scope.confirmText(saveFile)) },
        confirmButton = {
            TextButton(onClick = state::confirm) { Text(if (scope.action == BulkAction.CLEAR) "Clear" else "Mark complete") }
        },
        dismissButton = { TextButton(onClick = state::dismiss) { Text("Cancel") } },
    )
}

/**
 * Shows [change]'s snackbar with Undo on [snackbar] each time a new change arrives. Undo calls
 * [onUndo], which puts back the change's snapshot; the snackbar timing out or being swiped away
 * calls [onDismiss], so the tracker can drop the snapshot.
 */
@Composable
fun BulkUndoSnackbar(
    change: BulkChange<*>?,
    snackbar: SnackbarHostState,
    onUndo: (BulkChange<*>) -> Unit,
    onDismiss: (BulkChange<*>) -> Unit,
) {
    val undo by rememberUpdatedState(onUndo)
    val dismiss by rememberUpdatedState(onDismiss)
    LaunchedEffect(change) {
        change ?: return@LaunchedEffect
        val result = snackbar.showSnackbar(change.message, actionLabel = "Undo", withDismissAction = true, duration = SnackbarDuration.Long)
        if (result == SnackbarResult.ActionPerformed) undo(change) else dismiss(change)
    }
}
