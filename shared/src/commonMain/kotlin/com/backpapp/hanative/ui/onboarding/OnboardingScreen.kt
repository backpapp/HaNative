package com.backpapp.hanative.ui.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun OnboardingScreen(
    onNavigateToAuth: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: OnboardingViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val discoveredServers by viewModel.discoveredServers.collectAsStateWithLifecycle()

    var urlInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is OnboardingUiState.Success) {
            onNavigateToAuth()
            viewModel.onNavigationConsumed()
        }
    }

    Column(modifier = modifier.fillMaxSize().padding(16.dp)) {
        TextField(
            value = urlInput,
            onValueChange = { urlInput = it },
            label = { Text("Home Assistant URL") },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState !is OnboardingUiState.Loading,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
            keyboardActions = KeyboardActions(
                onGo = { if (urlInput.isNotBlank()) viewModel.testUrl(urlInput) }
            ),
            singleLine = true,
        )

        if (uiState is OnboardingUiState.Error) {
            Text(
                text = (uiState as OnboardingUiState.Error).message,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(top = 4.dp),
            )
        }

        if (uiState is OnboardingUiState.Loading) {
            CircularProgressIndicator(modifier = Modifier.padding(top = 12.dp))
        } else {
            Button(
                onClick = { viewModel.testUrl(urlInput) },
                enabled = urlInput.isNotBlank() && uiState !is OnboardingUiState.Loading,
                modifier = Modifier.padding(top = 12.dp),
            ) {
                Text("Connect")
            }
        }

        if (discoveredServers.isNotEmpty()) {
            LazyColumn(modifier = Modifier.padding(top = 16.dp)) {
                items(discoveredServers) { server ->
                    ListItem(
                        headlineContent = { Text(server.name) },
                        supportingContent = { Text("${server.host}:${server.port}") },
                        modifier = Modifier.clickable {
                            val host = if (server.host.contains(':')) "[${server.host}]" else server.host
                            urlInput = "$host:${server.port}"
                        },
                    )
                }
            }
        }
    }
}
