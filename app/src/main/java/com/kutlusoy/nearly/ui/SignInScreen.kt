package com.kutlusoy.nearly.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kutlusoy.nearly.R
import com.kutlusoy.nearly.ui.registration.Rules
import com.kutlusoy.nearly.ui.registration.SignInForm

@Composable
fun SignInScreen(
    form: SignInForm,
    busy: Boolean,
    error: AppError?,
    onChange: ((SignInForm) -> SignInForm) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Text(stringResource(R.string.signin_title), style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))
                    OutlinedTextField(
                        value = form.username, onValueChange = { v -> onChange { it.copy(username = v) } },
                        label = { Text(stringResource(R.string.reg_username_label)) }, singleLine = true,
                        isError = Rules.usernameError(form.username) != null, modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.email, onValueChange = { v -> onChange { it.copy(email = v) } },
                        label = { Text(stringResource(R.string.reg_email_label)) }, singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email), modifier = Modifier.fillMaxWidth(),
                    )
                    OutlinedTextField(
                        value = form.words, onValueChange = { v -> onChange { it.copy(words = v) } },
                        label = { Text(stringResource(R.string.signin_words_label)) },
                        minLines = 2,
                        supportingText = { Text(stringResource(R.string.signin_words_count, form.wordCount())) },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    errorText(error)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                    Text(stringResource(R.string.signin_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                Box(Modifier.padding(24.dp)) {
                    PrimaryButton(stringResource(R.string.signin_button), enabled = form.canSubmit && !busy, onClick = onSubmit)
                }
            }
            if (busy) BusyOverlay(stringResource(R.string.signin_busy), stringResource(R.string.reg_creating_help))
        }
    }
}
