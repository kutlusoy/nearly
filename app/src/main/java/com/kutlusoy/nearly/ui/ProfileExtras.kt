package com.kutlusoy.nearly.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.kutlusoy.nearly.R
import com.kutlusoy.nearly.nostr.CardExtras
import com.kutlusoy.nearly.nostr.CardPrompt

enum class Orientation { Straight, Gay, Lesbian, Bisexual, Asexual, Pansexual, Other }

fun Orientation.label(): Int = when (this) {
    Orientation.Straight -> R.string.orientation_straight
    Orientation.Gay -> R.string.orientation_gay
    Orientation.Lesbian -> R.string.orientation_lesbian
    Orientation.Bisexual -> R.string.orientation_bisexual
    Orientation.Asexual -> R.string.orientation_asexual
    Orientation.Pansexual -> R.string.orientation_pansexual
    Orientation.Other -> R.string.orientation_other
}

enum class FamilyPlans { Want, Dont, Have, Unsure }

fun FamilyPlans.label(): Int = when (this) {
    FamilyPlans.Want -> R.string.family_want
    FamilyPlans.Dont -> R.string.family_dont
    FamilyPlans.Have -> R.string.family_have
    FamilyPlans.Unsure -> R.string.family_unsure
}

enum class Zodiac { Aries, Taurus, Gemini, Cancer, Leo, Virgo, Libra, Scorpio, Sagittarius, Capricorn, Aquarius, Pisces }

fun Zodiac.label(): Int = when (this) {
    Zodiac.Aries -> R.string.zodiac_aries
    Zodiac.Taurus -> R.string.zodiac_taurus
    Zodiac.Gemini -> R.string.zodiac_gemini
    Zodiac.Cancer -> R.string.zodiac_cancer
    Zodiac.Leo -> R.string.zodiac_leo
    Zodiac.Virgo -> R.string.zodiac_virgo
    Zodiac.Libra -> R.string.zodiac_libra
    Zodiac.Scorpio -> R.string.zodiac_scorpio
    Zodiac.Sagittarius -> R.string.zodiac_sagittarius
    Zodiac.Capricorn -> R.string.zodiac_capricorn
    Zodiac.Aquarius -> R.string.zodiac_aquarius
    Zodiac.Pisces -> R.string.zodiac_pisces
}

enum class Diet { Omnivore, Vegetarian, Vegan, Pescatarian, Other }

fun Diet.label(): Int = when (this) {
    Diet.Omnivore -> R.string.diet_omnivore
    Diet.Vegetarian -> R.string.diet_vegetarian
    Diet.Vegan -> R.string.diet_vegan
    Diet.Pescatarian -> R.string.diet_pescatarian
    Diet.Other -> R.string.diet_other
}


// A question with an answer of the person, the question is one of a fixed list so it can be translated
data class ProfilePrompt(val q: Int, val a: String)

const val PROMPT_COUNT = 10
const val PROMPTS_MAX = 3
const val PROMPT_ANSWER_MAX = 140
const val PRONOUNS_MAX = 20
const val PETS_MAX = 40
const val LINK_MAX = 80

fun promptQuestion(q: Int): Int = when (q) {
    0 -> R.string.prompt_0
    1 -> R.string.prompt_1
    2 -> R.string.prompt_2
    3 -> R.string.prompt_3
    4 -> R.string.prompt_4
    5 -> R.string.prompt_5
    6 -> R.string.prompt_6
    7 -> R.string.prompt_7
    8 -> R.string.prompt_8
    9 -> R.string.prompt_9
    else -> R.string.prompt_0
}

// The fields a person may hide, in the order of the edit screen. A hidden field is not written to the card.
enum class ProfileField(val key: String) {
    Gender("gender"),
    Looking("looking"),
    Interests("interests"),
    Languages("languages"),
    Job("job"),
    School("school"),
    City("city"),
    Height("height"),
    Drinking("drinking"),
    Smoking("smoking"),
    Exercise("exercise"),
    Orientation("orientation"),
    Family("family"),
    Zodiac("zodiac"),
    Diet("diet"),
    Pets("pets"),
    Link("link"),
    Pronouns("pronouns"),
    Prompts("prompts");

    fun label(): Int = when (this) {
        Gender -> R.string.field_gender
        Looking -> R.string.field_looking
        Interests -> R.string.field_interests
        Languages -> R.string.field_languages
        Job -> R.string.field_job
        School -> R.string.field_school
        City -> R.string.field_city
        Height -> R.string.field_height
        Drinking -> R.string.field_drinking
        Smoking -> R.string.field_smoking
        Exercise -> R.string.field_exercise
        Orientation -> R.string.field_orientation
        Family -> R.string.field_family
        Zodiac -> R.string.field_zodiac
        Diet -> R.string.field_diet
        Pets -> R.string.field_pets
        Link -> R.string.field_link
        Pronouns -> R.string.field_pronouns
        Prompts -> R.string.field_prompts
    }
}


// What the other people see besides the basics: facts in a quiet block and the answers to the prompts
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtrasBlock(extras: CardExtras, modifier: Modifier = Modifier) {
    val facts = buildList<String> {
        extras.height?.let { add(stringResource(R.string.detail_height, it)) }
        if (extras.school.isNotBlank()) add(stringResource(R.string.detail_school, extras.school))
        extras.orientation?.let { code -> Orientation.values().firstOrNull { it.name.lowercase() == code }?.let { add(stringResource(it.label())) } }
        extras.family?.let { code -> FamilyPlans.values().firstOrNull { it.name.lowercase() == code }?.let { add(stringResource(it.label())) } }
        extras.zodiac?.let { code -> Zodiac.values().firstOrNull { it.name.lowercase() == code }?.let { add(stringResource(it.label())) } }
        extras.diet?.let { code -> Diet.values().firstOrNull { it.name.lowercase() == code }?.let { add(stringResource(it.label())) } }
        extras.drinking?.let { code -> Frequency.values().firstOrNull { it.name.lowercase() == code }?.let { add(stringResource(R.string.edit_drinking) + ": " + stringResource(it.freqLabel())) } }
        extras.smoking?.let { code -> Frequency.values().firstOrNull { it.name.lowercase() == code }?.let { add(stringResource(R.string.edit_smoking) + ": " + stringResource(it.freqLabel())) } }
        extras.exercise?.let { code -> Frequency.values().firstOrNull { it.name.lowercase() == code }?.let { add(stringResource(R.string.edit_exercise) + ": " + stringResource(it.freqLabel())) } }
        if (extras.pets.isNotBlank()) add(stringResource(R.string.detail_pets, extras.pets))
    }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        if (facts.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                facts.forEach { androidx.compose.material3.SuggestionChip(onClick = {}, label = { Text(it) }) }
            }
        }
        if (extras.link.isNotBlank()) Text(stringResource(R.string.detail_link, extras.link), style = MaterialTheme.typography.bodyMedium, modifier = Modifier.testTag("detail-link"))
        extras.prompts.forEach { prompt ->
            Column(Modifier.fillMaxWidth().padding(top = 4.dp)) {
                Text(stringResource(promptQuestion(prompt.q)), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                Text(prompt.a, style = MaterialTheme.typography.bodyLarge)
            }
        }
    }
}

fun Frequency.freqLabel(): Int = when (this) {
    Frequency.Never -> R.string.freq_never
    Frequency.Sometimes -> R.string.freq_sometimes
    Frequency.Often -> R.string.freq_often
}

private val CONTACT_PATTERN = Regex("[\\w.+-]+@[\\w-]+\\.[\\w.]+|\\+?\\d[\\d ()/-]{7,}\\d")

// A phone number or an e-mail address in the text is a sign of trouble: the screen warns once
fun hasContactData(text: String): Boolean = CONTACT_PATTERN.containsMatchIn(text)

// The fields that came with Tinder and Instagram: pronouns, orientation, family plans, sign, diet, pets, a link and prompts
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ExtrasSection(profile: MyProfile, onChange: (MyProfile) -> Unit) {
    ExtraHeader(R.string.edit_more)
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        OutlinedTextField(
            value = profile.pronouns, onValueChange = { onChange(profile.copy(pronouns = it.take(PRONOUNS_MAX))) },
            modifier = Modifier.fillMaxWidth().testTag("edit-pronouns"), label = { Text(stringResource(R.string.edit_pronouns)) }, singleLine = true,
        )
        OutlinedTextField(
            value = profile.pets, onValueChange = { onChange(profile.copy(pets = it.take(PETS_MAX))) },
            modifier = Modifier.fillMaxWidth().testTag("edit-pets"), label = { Text(stringResource(R.string.edit_pets)) }, singleLine = true,
        )
        OutlinedTextField(
            value = profile.link, onValueChange = { onChange(profile.withLink(it)) },
            modifier = Modifier.fillMaxWidth().testTag("edit-link"), label = { Text(stringResource(R.string.edit_link)) },
            supportingText = { Text(stringResource(R.string.edit_link_help)) }, singleLine = true,
        )
    }
    Choices(R.string.edit_orientation, Orientation.values().toList(), profile.orientation, { stringResource(it.label()) }) { onChange(profile.copy(orientation = it)) }
    Choices(R.string.edit_family, FamilyPlans.values().toList(), profile.family, { stringResource(it.label()) }) { onChange(profile.copy(family = it)) }
    Choices(R.string.edit_zodiac, Zodiac.values().toList(), profile.zodiac, { stringResource(it.label()) }) { onChange(profile.copy(zodiac = it)) }
    Choices(R.string.edit_diet, Diet.values().toList(), profile.diet, { stringResource(it.label()) }) { onChange(profile.copy(diet = it)) }

    ExtraHeader(R.string.edit_prompts)
    Text(stringResource(R.string.edit_prompts_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    profile.prompts.forEachIndexed { index, prompt ->
        Column(Modifier.fillMaxWidth().padding(top = 8.dp)) {
            Row(verticalAlignment = androidx.compose.ui.Alignment.CenterVertically) {
                TextButton(onClick = { onChange(profile.withNextQuestion(index)) }, modifier = Modifier.weight(1f).testTag("prompt-question-$index")) {
                    Text(stringResource(promptQuestion(prompt.q)), modifier = Modifier.fillMaxWidth())
                }
                TextButton(onClick = { onChange(profile.withoutPrompt(index)) }, modifier = Modifier.testTag("prompt-remove-$index")) { Text(stringResource(R.string.edit_prompt_remove)) }
            }
            OutlinedTextField(
                value = prompt.a, onValueChange = { onChange(profile.withAnswer(index, it)) },
                modifier = Modifier.fillMaxWidth().testTag("prompt-answer-$index"), minLines = 2, maxLines = 4,
                supportingText = { Text(stringResource(R.string.edit_counter, prompt.a.length, PROMPT_ANSWER_MAX)) },
            )
        }
    }
    if (profile.prompts.size < PROMPTS_MAX) {
        Button(onClick = { onChange(profile.withNewPrompt()) }, modifier = Modifier.padding(top = 8.dp).testTag("prompt-add")) { Text(stringResource(R.string.edit_prompt_add)) }
    }

    ExtraHeader(R.string.edit_hide)
    Text(stringResource(R.string.edit_hide_help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
        ProfileField.values().forEach { field ->
            ExtraChip(selected = field.key in profile.hidden, onClick = { onChange(profile.toggleHidden(field)) }) { Text(stringResource(R.string.hide_chip, stringResource(field.label()))) }
        }
    }
    if (ProfileField.Gender.key in profile.hidden || ProfileField.Looking.key in profile.hidden) {
        Text(stringResource(R.string.edit_hide_filter_note), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 4.dp))
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun <T> Choices(title: Int, options: List<T>, selected: T?, label: @Composable (T) -> String, onSelect: (T?) -> Unit) {
    Text(stringResource(title), style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp, bottom = 4.dp))
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEach { option ->
            ExtraChip(selected = selected == option, onClick = { onSelect(if (selected == option) null else option) }) { Text(label(option)) }
        }
    }
}

@Composable
private fun ExtraHeader(title: Int) {
    Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
}

@Composable
private fun ExtraChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    androidx.compose.material3.FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        colors = androidx.compose.material3.FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
}
