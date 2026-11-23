package com.kutlusoy.nearly.ui

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import com.kutlusoy.nearly.R

// Both people liked each other. The chat of the pair is open from now on.
@Composable
fun MatchDialog(name: String, onMessage: () -> Unit, onLater: () -> Unit) {
    AlertDialog(
        onDismissRequest = onLater,
        title = { Text(stringResource(R.string.match_title)) },
        text = { Text(stringResource(R.string.match_text, name)) },
        confirmButton = { Button(onClick = onMessage, modifier = Modifier.testTag("match-message")) { Text(stringResource(R.string.match_message)) } },
        dismissButton = { TextButton(onClick = onLater, modifier = Modifier.testTag("match-later")) { Text(stringResource(R.string.match_later)) } },
        modifier = Modifier.testTag("match-dialog"),
    )
}
