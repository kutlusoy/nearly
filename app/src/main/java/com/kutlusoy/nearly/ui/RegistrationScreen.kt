package com.kutlusoy.nearly.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kutlusoy.nearly.R
import com.kutlusoy.nearly.nostr.BirthDate
import com.kutlusoy.nearly.ui.registration.FieldError
import com.kutlusoy.nearly.ui.registration.RegistrationForm
import com.kutlusoy.nearly.ui.registration.RegistrationStep

@Composable
fun RegistrationScreen(
    form: RegistrationForm,
    today: BirthDate,
    busy: Boolean,
    error: AppError?,
    onChange: ((RegistrationForm) -> RegistrationForm) -> Unit,
    onBack: () -> Unit,
    onNext: () -> Unit,
    onSubmit: () -> Unit,
    onNewWords: () -> Unit,
) {
    BackHandler(onBack = onBack)
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Box(Modifier.fillMaxSize()) {
            Column(modifier = Modifier.fillMaxSize().safeDrawingPadding().imePadding()) {
                IconButton(onClick = onBack, modifier = Modifier.padding(start = 4.dp, top = 4.dp)) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                LinearProgressIndicator(
                    progress = { form.stepNumber / RegistrationForm.STEPS.toFloat() },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp),
                )
                Text(
                    stringResource(R.string.step_of, form.stepNumber, RegistrationForm.STEPS),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                Column(
                    modifier = Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    when (form.step) {
                        RegistrationStep.Username -> UsernameStep(form, onChange)
                        RegistrationStep.Email -> EmailStep(form, onChange)
                        RegistrationStep.Words -> WordsStep(form, onChange, onNewWords)
                        RegistrationStep.Birthdate -> BirthdateStep(form, today, onChange)
                    }
                    errorText(error)?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium) }
                }
                Box(Modifier.padding(24.dp)) {
                    PrimaryButton(
                        text = stringResource(if (form.isLastStep) R.string.reg_create else R.string.common_continue),
                        enabled = form.canContinue(today) && !busy,
                        onClick = if (form.isLastStep) onSubmit else onNext,
                    )
                }
            }
            if (busy) BusyOverlay(stringResource(R.string.reg_creating), stringResource(R.string.reg_creating_help))
        }
    }
}

@Composable
private fun StepTitle(text: String) = Text(text, style = MaterialTheme.typography.headlineMedium, modifier = Modifier.padding(top = 8.dp))

@Composable
private fun UsernameStep(form: RegistrationForm, onChange: ((RegistrationForm) -> RegistrationForm) -> Unit) {
    StepTitle(stringResource(R.string.reg_username_title))
    val error = form.usernameError()
    OutlinedTextField(
        value = form.username,
        onValueChange = { v -> onChange { it.copy(username = v) } },
        label = { Text(stringResource(R.string.reg_username_label)) },
        singleLine = true,
        isError = error != null,
        supportingText = { Text(error?.let { stringResource(fieldErrorText(it)) } ?: stringResource(R.string.reg_username_help)) },
        modifier = Modifier.fillMaxWidth(),
    )
}

@Composable
private fun EmailStep(form: RegistrationForm, onChange: ((RegistrationForm) -> RegistrationForm) -> Unit) {
    StepTitle(stringResource(R.string.reg_email_title))
    val error = form.emailError()
    OutlinedTextField(
        value = form.email,
        onValueChange = { v -> onChange { it.copy(email = v) } },
        label = { Text(stringResource(R.string.reg_email_label)) },
        singleLine = true,
        isError = error == FieldError.EmailInvalid,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        supportingText = { if (error == FieldError.EmailInvalid) Text(stringResource(fieldErrorText(error))) },
        modifier = Modifier.fillMaxWidth(),
    )
    OutlinedTextField(
        value = form.emailAgain,
        onValueChange = { v -> onChange { it.copy(emailAgain = v) } },
        label = { Text(stringResource(R.string.reg_email_again)) },
        singleLine = true,
        isError = error == FieldError.EmailMismatch,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
        supportingText = { if (error == FieldError.EmailMismatch) Text(stringResource(fieldErrorText(error))) },
        modifier = Modifier.fillMaxWidth(),
    )
    Text(stringResource(R.string.reg_email_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
private fun WordsStep(form: RegistrationForm, onChange: ((RegistrationForm) -> RegistrationForm) -> Unit, onNewWords: () -> Unit) {
    val context = LocalContext.current
    // The recovery words must not end up in a screenshot or in the recent apps preview
    DisposableEffect(Unit) {
        val window = context.findActivity()?.window
        window?.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        onDispose { window?.clearFlags(WindowManager.LayoutParams.FLAG_SECURE) }
    }
    StepTitle(stringResource(R.string.reg_words_title))
    Text(stringResource(R.string.reg_words_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            form.words.split(" ").filter { it.isNotEmpty() }.forEachIndexed { index, word ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("${index + 1}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                    Text(word, style = MaterialTheme.typography.headlineSmall)
                }
            }
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedButton(onClick = onNewWords) {
            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
            Spacer(Modifier.size(8.dp))
            Text(stringResource(R.string.reg_words_new))
        }
        OutlinedButton(onClick = {
            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("Nearly", form.words))
        }) { Text(stringResource(R.string.reg_words_copy)) }
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(stringResource(R.string.reg_words_warning), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
        }
    }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().toggleable(value = form.wordsSaved, role = Role.Checkbox) { v -> onChange { it.copy(wordsSaved = v) } },
    ) {
        Checkbox(checked = form.wordsSaved, onCheckedChange = null)
        Text(stringResource(R.string.reg_words_confirm), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(start = 12.dp, top = 12.dp, bottom = 12.dp))
    }
}

@Composable
private fun BirthdateStep(form: RegistrationForm, today: BirthDate, onChange: ((RegistrationForm) -> RegistrationForm) -> Unit) {
    StepTitle(stringResource(R.string.reg_birth_title))
    val error = form.birthdateError(today)
    Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = form.day, onValueChange = { v -> onChange { it.copy(day = v.filter(Char::isDigit).take(2)) } },
            label = { Text(stringResource(R.string.reg_birth_day)) }, singleLine = true, isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = form.month, onValueChange = { v -> onChange { it.copy(month = v.filter(Char::isDigit).take(2)) } },
            label = { Text(stringResource(R.string.reg_birth_month)) }, singleLine = true, isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f),
        )
        OutlinedTextField(
            value = form.year, onValueChange = { v -> onChange { it.copy(year = v.filter(Char::isDigit).take(4)) } },
            label = { Text(stringResource(R.string.reg_birth_year)) }, singleLine = true, isError = error != null,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1.4f),
        )
    }
    if (error != null) Text(stringResource(fieldErrorText(error)), color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
    Text(stringResource(R.string.reg_birth_help), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

fun fieldErrorText(error: FieldError): Int = when (error) {
    FieldError.UsernameShort -> R.string.err_username_short
    FieldError.UsernameLong -> R.string.err_username_long
    FieldError.UsernameCharacters -> R.string.err_username_chars
    FieldError.EmailInvalid -> R.string.err_email_invalid
    FieldError.EmailMismatch -> R.string.err_email_mismatch
    FieldError.DateInvalid -> R.string.err_date_invalid
    FieldError.UnderAge -> R.string.err_under_age
}
