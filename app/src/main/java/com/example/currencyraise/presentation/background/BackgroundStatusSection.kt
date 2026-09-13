package com.example.currencyraise.presentation.background

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.currencyraise.R
import com.example.currencyraise.background.BackgroundStatus
import com.example.currencyraise.background.WorkSyncScheduler
import com.example.currencyraise.presentation.components.SectionLabel
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class BackgroundStatusViewModel @Inject constructor(scheduler: WorkSyncScheduler) : ViewModel() {
    val status = scheduler.status
}

@Composable
internal fun BackgroundStatusRoute(showTitle: Boolean = true) {
    val model: BackgroundStatusViewModel = viewModel()
    val status by model.status.collectAsStateWithLifecycle()
    BackgroundStatusSection(status, showTitle)
}

@Composable
internal fun BackgroundStatusSection(
    status: BackgroundStatus = BackgroundStatus.Starting,
    showTitle: Boolean = true,
) {
    val statusColor = when (status) {
        is BackgroundStatus.Active -> MaterialTheme.colorScheme.tertiary
        BackgroundStatus.Starting -> MaterialTheme.colorScheme.secondary
        BackgroundStatus.Disabled -> MaterialTheme.colorScheme.onSurfaceVariant
        BackgroundStatus.Error -> MaterialTheme.colorScheme.error
    }
    Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
        if (showTitle) SectionLabel(stringResource(R.string.background_section))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(8.dp).clip(CircleShape).background(statusColor))
            Spacer(Modifier.width(9.dp))
            Text(
                when (status) {
                    is BackgroundStatus.Active -> pluralStringResource(
                        R.plurals.scheduled_interval,
                        status.interval.hours,
                        status.interval.hours,
                    )
                    BackgroundStatus.Starting -> stringResource(R.string.background_starting)
                    BackgroundStatus.Disabled -> stringResource(R.string.automatic_disabled)
                    BackgroundStatus.Error -> stringResource(R.string.background_schedule_error)
                },
                style = MaterialTheme.typography.bodyMedium,
            )
        }
        Text(
            stringResource(R.string.interval_approximate),
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
