package com.github.tomasbjerre.wisp.ui.common

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.github.tomasbjerre.wisp.data.ActivityType
import com.github.tomasbjerre.wisp.ui.TestTags

/** See specs/calories.md#activity-type: what is being recorded, so calories can be estimated. */
@Composable
fun ActivityTypeChoice(
    selected: ActivityType?,
    onSelect: (ActivityType) -> Unit,
    modifier: Modifier = Modifier,
) {
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        ActivityType.entries.forEachIndexed { index, option ->
            SegmentedButton(
                selected = selected == option,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ActivityType.entries.size),
                modifier = Modifier.testTag(TestTags.activityTypeOption(option.id)),
            ) {
                Text(option.label)
            }
        }
    }
}
