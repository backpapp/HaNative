package com.backpapp.hanative.ui.auth

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import org.koin.compose.viewmodel.koinViewModel

@Composable
fun AuthScreen(
    onNavigateToDashboard: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val viewModel: AuthViewModel = koinViewModel()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    var tokenInput by rememberSaveable { mutableStateOf("") }

    LaunchedEffect(uiState) {
        if (uiState is AuthUiState.Success) {
            onNavigateToDashboard()
            viewModel.onNavigationConsumed()
        }
    }

    Column(
        modifier = modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text(
            "Sign in to Home Assistant",
            style = MaterialTheme.typography.headlineSmall,
        )

        TextField(
            value = tokenInput,
            onValueChange = { tokenInput = it },
            label = { Text("Long-lived access token") },
            modifier = Modifier.fillMaxWidth(),
            enabled = uiState !is AuthUiState.Loading,
            singleLine = true,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                imeAction = ImeAction.Go,
                keyboardType = KeyboardType.Password,
            ),
            keyboardActions = KeyboardActions(
                onGo = { if (tokenInput.isNotBlank()) viewModel.submitLongLivedToken(tokenInput) },
            ),
        )

        if (uiState is AuthUiState.Error) {
            Text(
                text = (uiState as AuthUiState.Error).message,
                color = MaterialTheme.colorScheme.error,
            )
        }

        // Submit slot — never collapses, button replaced by spinner during Loading.
        Box(
            modifier = Modifier.fillMaxWidth().height(48.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (uiState is AuthUiState.Loading) {
                CircularProgressIndicator()
            } else {
                Button(
                    onClick = { viewModel.submitLongLivedToken(tokenInput) },
                    enabled = tokenInput.isNotBlank() && uiState !is AuthUiState.Loading,
                ) { Text("Sign in") }
            }
        }

        Spacer(Modifier.height(8.dp))
        HorizontalDivider()
        Text(
            "or",
            modifier = Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.labelMedium,
        )

        OutlinedButton(
            onClick = { viewModel.startOAuthFlow() },
            enabled = uiState !is AuthUiState.Loading,
            modifier = Modifier.fillMaxWidth(),
        ) {
            Text("Sign in with browser")
        }
    }
}
