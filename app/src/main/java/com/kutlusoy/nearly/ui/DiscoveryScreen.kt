package com.kutlusoy.nearly.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.kutlusoy.nearly.R

private fun Gender.label(): Int = when (this) {
    Gender.Woman -> R.string.gender_woman
    Gender.Man -> R.string.gender_man
    Gender.NonBinary -> R.string.gender_nonbinary
}

fun Gender.singular(): Int = when (this) {
    Gender.Woman -> R.string.gender_woman_single
    Gender.Man -> R.string.gender_man_single
    Gender.NonBinary -> R.string.gender_nonbinary_single
}

fun LookingFor.label(): Int = when (this) {
    LookingFor.Serious -> R.string.goal_serious
    LookingFor.Open -> R.string.goal_open
    LookingFor.Casual -> R.string.goal_casual
    LookingFor.Fun -> R.string.goal_fun
    LookingFor.Friends -> R.string.goal_friends
    LookingFor.Unsure -> R.string.goal_unsure
}

// The settings of Tinder: who, how far, how old, what for. They apply to Swipe and the Map, not to the Feed.
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun DiscoveryScreen(filter: DiscoveryFilter, onChange: (DiscoveryFilter) -> Unit, onBack: () -> Unit) {
    BackHandler(onBack = onBack)
    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(Modifier.fillMaxSize().safeDrawingPadding()) {
            Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.common_back))
                }
                Text(
                    stringResource(R.string.discovery_title),
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = { onChange(DiscoveryFilter()) }) { Text(stringResource(R.string.discovery_reset)) }
            }
            Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Text(
                    stringResource(R.string.discovery_note),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 8.dp),
                )

                Header(R.string.discovery_area)
                AreaRow(R.string.area_current, R.string.area_current_help, selected = true, enabled = true)
                AreaRow(R.string.area_other, R.string.area_other_help, selected = false, enabled = false)
                Divider()

                Header(R.string.discovery_show_me)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val everyone = filter.showMe.size == Gender.values().size
                    RoseChip(
                        selected = everyone,
                        onClick = { onChange(filter.copy(showMe = Gender.values().toSet())) },
                        label = { Text(stringResource(R.string.gender_everyone)) },
                    )
                    Gender.values().forEach { gender ->
                        RoseChip(
                            selected = !everyone && gender in filter.showMe,
                            onClick = {
                                val base = if (everyone) filter.copy(showMe = setOf(gender)) else filter.withShowMe(gender)
                                onChange(base)
                            },
                            label = { Text(stringResource(gender.label())) },
                        )
                    }
                }
                Divider()

                Header(R.string.discovery_looking_for)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RoseChip(
                        selected = filter.lookingFor.isEmpty(),
                        onClick = { onChange(filter.copy(lookingFor = emptySet())) },
                        label = { Text(stringResource(R.string.goal_any)) },
                    )
                    LookingFor.values().forEach { goal ->
                        RoseChip(
                            selected = goal in filter.lookingFor,
                            onClick = { onChange(filter.withLookingFor(goal)) },
                            label = { Text(stringResource(goal.label())) },
                        )
                    }
                }
                Divider()

                ValueHeader(R.string.discovery_distance, stringResource(R.string.distance_value, filter.maxDistanceKm))
                Slider(
                    value = filter.maxDistanceKm.toFloat(),
                    onValueChange = { onChange(filter.withDistance(it.toInt())) },
                    valueRange = DISTANCE_MIN_KM.toFloat()..DISTANCE_MAX_KM.toFloat(),
                )
                Option(R.string.discovery_further, R.string.discovery_further_help, filter.furtherAway) { onChange(filter.copy(furtherAway = it)) }
                Divider()

                ValueHeader(R.string.discovery_age, stringResource(R.string.age_value, filter.ageMin, filter.ageMax))
                RangeSlider(
                    value = filter.ageMin.toFloat()..filter.ageMax.toFloat(),
                    onValueChange = { onChange(filter.withAge(it.start.toInt(), it.endInclusive.toInt())) },
                    valueRange = AGE_MIN.toFloat()..AGE_MAX.toFloat(),
                )
                Option(R.string.discovery_age_slack, R.string.discovery_age_slack_help, filter.slightlyOutsideAge) { onChange(filter.copy(slightlyOutsideAge = it)) }
                Divider()

                Header(R.string.discovery_activity)
                Option(R.string.discovery_recent, null, filter.recentlyActiveOnly) { onChange(filter.copy(recentlyActiveOnly = it)) }
                Divider()

                Header(R.string.discovery_privacy)
                Option(R.string.discovery_visible, R.string.discovery_visible_help, filter.visible) { onChange(filter.copy(visible = it)) }
                Option(R.string.discovery_show_distance, null, filter.showDistance) { onChange(filter.copy(showDistance = it)) }
                Spacer(Modifier.height(24.dp))
            }
        }
    }
}

@Composable
private fun RoseChip(selected: Boolean, onClick: () -> Unit, label: @Composable () -> Unit) {
    FilterChip(
        selected = selected,
        onClick = onClick,
        label = label,
        colors = FilterChipDefaults.filterChipColors(
            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
        ),
    )
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

@Composable
private fun ValueHeader(title: Int, value: String) {
    Row(Modifier.fillMaxWidth().padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(stringResource(title), style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Option(title: Int, help: Int?, checked: Boolean, onChecked: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().toggleable(value = checked, role = Role.Switch, onValueChange = onChecked).padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f).padding(end = 12.dp)) {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            if (help != null) Text(stringResource(help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
private fun AreaRow(title: Int, help: Int, selected: Boolean, enabled: Boolean) {
    Row(
        Modifier.fillMaxWidth().selectable(selected = selected, enabled = enabled, onClick = {}, role = Role.RadioButton).padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = null, enabled = enabled, modifier = Modifier.padding(end = 12.dp))
        Column {
            Text(stringResource(title), style = MaterialTheme.typography.bodyLarge)
            Text(stringResource(help), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
