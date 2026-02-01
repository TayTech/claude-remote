package com.clauderemote.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.clauderemote.R

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: SettingsViewModel,
    onSettingsSaved: () -> Unit,
    onCancel: () -> Unit
) {
    val host by viewModel.host.collectAsState()
    val port by viewModel.port.collectAsState()
    val testState by viewModel.testState.collectAsState()
    val saveError by viewModel.saveError.collectAsState()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.settings_title)) },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = stringResource(R.string.settings_server_section),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.primary
            )

            OutlinedTextField(
                value = host,
                onValueChange = viewModel::updateHost,
                label = { Text(stringResource(R.string.settings_host_label)) },
                placeholder = { Text("mac.tailnet-xxx.ts.net") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                supportingText = { Text(stringResource(R.string.settings_host_hint)) }
            )

            OutlinedTextField(
                value = port,
                onValueChange = viewModel::updatePort,
                label = { Text(stringResource(R.string.settings_port_label)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number)
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Test connection button
            OutlinedButton(
                onClick = viewModel::testConnection,
                modifier = Modifier.fillMaxWidth(),
                enabled = testState !is TestConnectionState.Testing
            ) {
                when (testState) {
                    is TestConnectionState.Testing -> {
                        CircularProgressIndicator(
                            modifier = Modifier.height(20.dp),
                            strokeWidth = 2.dp
                        )
                    }
                    else -> {
                        Text(stringResource(R.string.settings_test_connection))
                    }
                }
            }

            // Test result
            when (val state = testState) {
                is TestConnectionState.Success -> {
                    Text(
                        text = stringResource(R.string.connection_success),
                        color = MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                is TestConnectionState.Error -> {
                    Text(
                        text = state.message,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
                else -> {}
            }

            Spacer(modifier = Modifier.weight(1f))

            // Save error
            saveError?.let { error ->
                Text(
                    text = error,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodyMedium
                )
            }

            // Buttons row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Cancel button
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.settings_cancel))
                }

                // Save button
                Button(
                    onClick = { viewModel.saveSettings(onSettingsSaved) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.settings_save))
                }
            }
        }
    }
}
