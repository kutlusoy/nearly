package com.kutlusoy.nearly.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.PersistableBundle
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kutlusoy.nearly.R

private const val SENSITIVE = "android.content.extra.IS_SENSITIVE"

// Shows what is needed to get the account back. The secrets stay hidden until the person asks for them.
@Composable
fun BackupScreen(backup: AccountBackup?, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    var revealed by remember { mutableStateOf(false) }
    val context = LocalContext.current
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                Text(stringResource(R.string.backup_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(4.dp)) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer), modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp)) {
                    Text(
                        stringResource(R.string.backup_warning),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                if (backup == null) {
                    Text(stringResource(R.string.backup_unavailable), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(vertical = 16.dp))
                } else {
                    val hidden = stringResource(R.string.backup_hidden)
                    Header(R.string.backup_to_sign_in)
                    Field(stringResource(R.string.backup_username), backup.username, secret = false, onCopy = { copy(context, backup.username, false) })
                    Field(stringResource(R.string.backup_email), backup.email, secret = false, onCopy = { copy(context, backup.email, false) })
                    val words = backup.words
                    if (words == null) {
                        Field(stringResource(R.string.backup_words), stringResource(R.string.backup_words_missing), secret = false, onCopy = null)
                    } else {
                        Field(stringResource(R.string.backup_words), if (revealed) words else hidden, secret = true, onCopy = if (revealed) ({ copy(context, words, true) }) else null)
                    }
                    Divider()
                    Header(R.string.backup_npub)
                    Field(null, backup.npub, secret = false, mono = true, onCopy = { copy(context, backup.npub, false) })
                    Text(stringResource(R.string.backup_npub_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Divider()
                    Header(R.string.backup_nsec)
                    Field(null, if (revealed) backup.nsec else hidden, secret = true, mono = true, onCopy = if (revealed) ({ copy(context, backup.nsec, true) }) else null)
                    Text(stringResource(R.string.backup_nsec_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(Modifier.height(16.dp))
                    OutlinedButton(onClick = { revealed = !revealed }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(if (revealed) R.string.backup_hide else R.string.backup_show))
                    }
                    if (revealed) {
                        val all = listOf(
                            stringResource(R.string.backup_username) to backup.username,
                            stringResource(R.string.backup_email) to backup.email,
                            stringResource(R.string.backup_words) to (words ?: stringResource(R.string.backup_words_missing)),
                            stringResource(R.string.backup_npub) to backup.npub,
                            stringResource(R.string.backup_nsec) to backup.nsec,
                        ).joinToString("\n") { (label, value) -> "$label: $value" }
                        Button(onClick = { copy(context, all, true) }, modifier = Modifier.fillMaxWidth().padding(top = 8.dp)) {
                            Text(stringResource(R.string.backup_copy_all))
                        }
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }
        }
    }
}

private fun copy(context: Context, text: String, sensitive: Boolean) {
    val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    val clip = ClipData.newPlainText("Nearly", text)
    if (sensitive) clip.description.extras = PersistableBundle().apply { putBoolean(SENSITIVE, true) }
    clipboard.setPrimaryClip(clip)
}

@Composable
private fun Field(label: String?, value: String, secret: Boolean, mono: Boolean = false, onCopy: (() -> Unit)?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f).padding(vertical = 6.dp)) {
            if (label != null) Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                value,
                style = if (mono) MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace) else MaterialTheme.typography.bodyLarge,
                fontWeight = if (secret) FontWeight.SemiBold else FontWeight.Normal,
            )
        }
        if (onCopy != null) TextButton(onClick = onCopy) { Text(stringResource(R.string.reg_words_copy)) }
    }
}

@Composable
private fun Header(title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 12.dp, bottom = 2.dp))
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(8.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}
