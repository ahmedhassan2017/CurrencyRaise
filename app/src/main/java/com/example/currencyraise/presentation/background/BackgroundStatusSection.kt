package com.example.currencyraise.presentation.background

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.currencyraise.R
import com.example.currencyraise.background.BackgroundStatus
import com.example.currencyraise.background.WorkSyncScheduler
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
internal class BackgroundStatusViewModel @Inject constructor(scheduler: WorkSyncScheduler) : ViewModel() {
    val status = scheduler.status
}

@Composable
internal fun BackgroundStatusRoute() {
    val model: BackgroundStatusViewModel = viewModel()
    val status by model.status.collectAsStateWithLifecycle()
    BackgroundStatusSection(status)
}

@Composable
internal fun BackgroundStatusSection(status: BackgroundStatus = BackgroundStatus.Starting) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.background_section), style = MaterialTheme.typography.titleSmall)
        Text(when (status) {
            is BackgroundStatus.Active -> pluralStringResource(R.plurals.scheduled_interval,
                status.interval.hours, status.interval.hours)
            BackgroundStatus.Starting -> stringResource(R.string.background_starting)
            BackgroundStatus.Disabled -> stringResource(R.string.automatic_disabled)
            BackgroundStatus.Error -> stringResource(R.string.background_schedule_error)
        }, style = MaterialTheme.typography.bodyMedium)
        Text(stringResource(R.string.interval_approximate), style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
