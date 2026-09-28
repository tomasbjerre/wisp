package com.github.tomasbjerre.wisp.ui.home

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.github.tomasbjerre.wisp.data.UnitPreferences
import com.github.tomasbjerre.wisp.data.WeightPreferences
import com.github.tomasbjerre.wisp.ui.Formatting
import com.github.tomasbjerre.wisp.ui.TestTags

/** See specs/ui-flows.md#1a-weight and specs/calories.md#weight. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WeightScreen(
    weightPreferences: WeightPreferences,
    unitPreferences: UnitPreferences,
    onBack: () -> Unit,
) {
    val unit by unitPreferences.unit.collectAsStateWithLifecycle()
    // What is typed, kept as typed: reformatting the stored kilograms on every keystroke would
    // fight the user (and turn 70 lb into 69.9).
    var text by remember {
        mutableStateOf(weightPreferences.weightKg.value?.let { Formatting.weightForEditing(it, unit) } ?: "")
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Weight") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
            )
        },
    ) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 16.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { typed ->
                    text = typed
                    val value = typed.replace(',', '.').toDoubleOrNull()
                    when {
                        typed.isBlank() -> weightPreferences.setWeightKg(null)
                        value != null && value > 0 -> weightPreferences.setWeightKg(unit.displayToKilograms(value))
                    }
                },
                label = { Text("Weight (${unit.weightAbbreviation})") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                modifier = Modifier.fillMaxWidth().testTag(TestTags.WEIGHT_FIELD),
            )
            Text(
                "Used to calculate calories burned. Calories are not shown while this is empty. " +
                    "An activity keeps the weight it was started with, so changing this doesn't " +
                    "change the calories of earlier activities.",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 16.dp),
            )
        }
    }
}
