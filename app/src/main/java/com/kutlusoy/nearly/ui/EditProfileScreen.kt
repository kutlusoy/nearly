package com.kutlusoy.nearly.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.kutlusoy.nearly.R
import com.kutlusoy.nearly.nostr.PhotoRef

private fun Frequency.label(): Int = when (this) {
    Frequency.Never -> R.string.freq_never
    Frequency.Sometimes -> R.string.freq_sometimes
    Frequency.Often -> R.string.freq_often
}

// Everything a person can tell about themselves, saved with every change
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun EditProfileScreen(
    profile: MyProfile,
    onChange: (MyProfile) -> Unit,
    onBack: () -> Unit,
    photoBusy: Boolean = false,
    photoNote: PhotoNote? = null,
    onAddPhoto: (open: () -> java.io.InputStream) -> Unit = {},
    onRemovePhoto: (Int) -> Unit = {},
    onMakeFirst: (Int) -> Unit = {},
) {
    BackHandler(onBack = onBack)
    var custom by remember { mutableStateOf("") }
    var heightText by remember { mutableStateOf(profile.heightCm?.toString().orEmpty()) }
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                Text(stringResource(R.string.edit_title), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Text(
                    stringResource(R.string.edit_public_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )

                Header(R.string.edit_photos)
                PhotoSection(profile.photos, photoBusy, photoNote, onAddPhoto, onRemovePhoto, onMakeFirst)
                Divider()

                Header(R.string.edit_about)
                OutlinedTextField(
                    value = profile.about,
                    onValueChange = { onChange(profile.withAbout(it)) },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = { Text(stringResource(R.string.edit_about_hint)) },
                    supportingText = { Text(stringResource(R.string.edit_counter, profile.about.length, ABOUT_MAX)) },
                    minLines = 3,
                    maxLines = 6,
                )
                Divider()

                Header(R.string.edit_i_am)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Gender.values().forEach { gender ->
                        ChoiceChip(
                            selected = profile.gender == gender,
                            onClick = { onChange(profile.copy(gender = if (profile.gender == gender) null else gender)) },
                        ) { Text(stringResource(gender.singular())) }
                    }
                }
                Divider()

                Header(R.string.edit_looking_for)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LookingFor.values().forEach { goal ->
                        ChoiceChip(
                            selected = profile.lookingFor == goal,
                            onClick = { onChange(profile.copy(lookingFor = if (profile.lookingFor == goal) null else goal)) },
                        ) { Text(stringResource(goal.label())) }
                    }
                }
                Divider()

                Header(R.string.edit_interests)
                val full = profile.interests.size >= INTERESTS_MAX
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val suggestions = INTEREST_SUGGESTIONS.filter { s -> profile.interests.none { it.equals(s, ignoreCase = true) } }
                    profile.interests.forEach { item ->
                        ChoiceChip(selected = true, onClick = { onChange(profile.withoutInterest(item)) }) { Text(item) }
                    }
                    suggestions.forEach { item ->
                        ChoiceChip(selected = false, enabled = !full, onClick = { onChange(profile.withInterest(item)) }) { Text(item) }
                    }
                }
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val add = {
                        onChange(profile.withInterest(custom))
                        custom = ""
                    }
                    OutlinedTextField(
                        value = custom,
                        onValueChange = { custom = it.take(INTEREST_LENGTH) },
                        modifier = Modifier.weight(1f),
                        label = { Text(stringResource(R.string.edit_interests_add)) },
                        singleLine = true,
                        enabled = !full,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                        keyboardActions = KeyboardActions(onDone = { add() }),
                    )
                    Button(onClick = add, enabled = !full && custom.isNotBlank()) { Text(stringResource(R.string.edit_interests_add_button)) }
                }
                if (full) {
                    Text(
                        stringResource(R.string.edit_interests_max, INTERESTS_MAX),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Divider()

                Header(R.string.edit_languages)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    LANGUAGES.forEach { language ->
                        ChoiceChip(selected = language in profile.languages, onClick = { onChange(profile.toggleLanguage(language)) }) { Text(language) }
                    }
                }
                Divider()

                Header(R.string.edit_basics)
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = profile.job,
                        onValueChange = { onChange(profile.copy(job = it.take(60))) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.edit_job)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = profile.school,
                        onValueChange = { onChange(profile.copy(school = it.take(60))) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.edit_school)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = profile.city,
                        onValueChange = { onChange(profile.copy(city = it.take(60))) },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.edit_city)) },
                        singleLine = true,
                    )
                    OutlinedTextField(
                        value = heightText,
                        onValueChange = {
                            heightText = it.filter { c -> c.isDigit() }.take(3)
                            onChange(profile.withHeight(heightText))
                        },
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.edit_height)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    )
                }
                Divider()

                Header(R.string.edit_lifestyle)
                Frequencies(R.string.edit_drinking, profile.drinking) { onChange(profile.copy(drinking = it)) }
                Frequencies(R.string.edit_smoking, profile.smoking) { onChange(profile.copy(smoking = it)) }
                Frequencies(R.string.edit_exercise, profile.exercise) { onChange(profile.copy(exercise = it)) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun ChoiceChip(selected: Boolean, onClick: () -> Unit, enabled: Boolean = true, label: @Composable () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = label,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun Frequencies(title: Int, value: Frequency?, onChange: (Frequency?) -> Unit) {
    Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 8.dp, bottom = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Frequency.values().forEach { option ->
            ChoiceChip(selected = value == option, onClick = { onChange(if (value == option) null else option) }) { Text(stringResource(option.label())) }
        }
    }
}

@Composable
private fun Divider() {
    Spacer(Modifier.height(12.dp))
    HorizontalDivider(color = MaterialTheme.colorScheme.surfaceVariant)
}

@Composable
private fun Header(title: Int) {
    Text(
        stringResource(title),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
    )
}

// Up to six photos in a grid, the first one is the profile picture. A photo is added with the system photo picker.
@Composable
private fun PhotoSection(
    photos: List<PhotoRef>,
    busy: Boolean,
    note: PhotoNote?,
    onAdd: (open: () -> java.io.InputStream) -> Unit,
    onRemove: (Int) -> Unit,
    onMakeFirst: (Int) -> Unit,
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val picker = androidx.activity.compose.rememberLauncherForActivityResult(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) onAdd { context.contentResolver.openInputStream(uri) ?: throw java.io.IOException("the photo cannot be opened") }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        val cells: List<PhotoRef?> = (photos + listOf<PhotoRef?>(null)).take(MAX_PHOTOS)
        (cells + List(MAX_PHOTOS - cells.size) { null })
            .withIndex().toList().chunked(3).forEach { row ->
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    row.forEach { (index, photo) ->
                        val isAddCell = photo == null && index == photos.size
                        Box(
                            Modifier.weight(1f).aspectRatio(0.8f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(12.dp))
                                .clip(RoundedCornerShape(12.dp)),
                            contentAlignment = Alignment.Center,
                        ) {
                            if (photo != null) {
                                RemotePhoto(photo, Modifier.fillMaxSize().testTag("photo-$index"))
                                IconButton(
                                    onClick = { onRemove(index) },
                                    modifier = Modifier.align(Alignment.TopEnd).testTag("photo-remove-$index"),
                                ) {
                                    Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.photo_remove), tint = Color.White, modifier = Modifier.background(Color.Black.copy(alpha = 0.5f), androidx.compose.foundation.shape.CircleShape))
                                }
                                if (index == 0) {
                                    Text(stringResource(R.string.photo_main), style = MaterialTheme.typography.labelSmall, color = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(6.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(horizontal = 6.dp, vertical = 2.dp))
                                } else {
                                    TextButton(onClick = { onMakeFirst(index) }, modifier = Modifier.align(Alignment.BottomStart).testTag("photo-first-$index")) {
                                        Text(stringResource(R.string.photo_make_main), style = MaterialTheme.typography.labelSmall, color = Color.White)
                                    }
                                }
                            } else if (isAddCell) {
                                if (busy) {
                                    CircularProgressIndicator(Modifier.size(28.dp).testTag("photo-busy"))
                                } else {
                                    IconButton(onClick = { picker.launch(androidx.activity.result.PickVisualMediaRequest(androidx.activity.result.contract.ActivityResultContracts.PickVisualMedia.ImageOnly)) }, modifier = Modifier.fillMaxSize().testTag("photo-add")) {
                                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.edit_add_photo), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                            }
                        }
                    }
                }
            }
    }
    note?.let {
        Text(
            stringResource(
                when (it) {
                    PhotoNote.TooMany -> R.string.photo_too_many
                    PhotoNote.UploadFailed -> R.string.photo_failed
                    PhotoNote.Removed -> R.string.photo_removed
                    PhotoNote.ServerMayKeep -> R.string.photo_server_may_keep
                },
            ),
            style = MaterialTheme.typography.bodySmall,
            color = if (it == PhotoNote.UploadFailed || it == PhotoNote.TooMany) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 6.dp).testTag("photo-note"),
        )
    }
}
