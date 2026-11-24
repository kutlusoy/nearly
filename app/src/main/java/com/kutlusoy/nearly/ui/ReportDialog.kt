package com.kutlusoy.nearly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kutlusoy.nearly.R
import com.kutlusoy.nearly.nostr.Report
import com.kutlusoy.nearly.nostr.ReportType

// Where a report goes. It is set by the app, a screen without it shows no report button.
val LocalReportPerson = compositionLocalOf<((String, ReportType, String) -> Unit)?> { null }

private fun ReportType.label(): Int = when (this) {
    ReportType.Spam -> R.string.report_type_spam
    ReportType.Impersonation -> R.string.report_type_impersonation
    ReportType.Nudity -> R.string.report_type_nudity
    ReportType.Illegal -> R.string.report_type_illegal
    ReportType.Underage -> R.string.report_type_underage
    ReportType.Abuse -> R.string.report_type_abuse
}

// The report is public and names the user, so it is sent only after the consent. The person is blocked too.
@Composable
fun ReportDialog(onDismiss: () -> Unit, onSend: (ReportType, String) -> Unit) {
    var type by remember { mutableStateOf<ReportType?>(null) }
    var note by remember { mutableStateOf("") }
    var consent by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.report_title)) },
        text = { ReportForm(type, { type = it }, note, { note = it }, consent, { consent = it }) },
        confirmButton = {
            TextButton(onClick = { type?.let { onSend(it, note) } }, enabled = type != null && consent, modifier = Modifier.testTag("report-send")) {
                Text(stringResource(R.string.report_send), color = MaterialTheme.colorScheme.error)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(R.string.newchat_cancel)) } },
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
internal fun ReportForm(type: ReportType?, onType: (ReportType) -> Unit, note: String, onNote: (String) -> Unit, consent: Boolean, onConsent: (Boolean) -> Unit) {
    Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text(stringResource(R.string.report_help), style = MaterialTheme.typography.bodyMedium)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ReportType.values().forEach { entry ->
                FilterChip(selected = type == entry, onClick = { onType(entry) }, label = { Text(stringResource(entry.label())) }, modifier = Modifier.testTag("report-type-${entry.name.lowercase()}"))
            }
        }
        OutlinedTextField(note, { onNote(it.take(Report.MAX_NOTE)) }, label = { Text(stringResource(R.string.report_note)) }, modifier = Modifier.fillMaxWidth().testTag("report-note"))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Checkbox(consent, onConsent, modifier = Modifier.testTag("report-consent"))
            Text(stringResource(R.string.report_consent), style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 4.dp))
        }
    }
}
