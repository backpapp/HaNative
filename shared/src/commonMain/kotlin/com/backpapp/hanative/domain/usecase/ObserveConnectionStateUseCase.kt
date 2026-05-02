package com.backpapp.hanative.domain.usecase

import com.backpapp.hanative.data.remote.ServerManager
import kotlinx.coroutines.flow.StateFlow

// Relocation seam — VM no longer reaches into data.remote directly. ServerManager.ConnectionState
// is intentionally retained as the leaked type (per Story 4.8 AC6); introducing a new domain enum
// would force re-mapping in 4 call sites with no semantic gain in V1.
class ObserveConnectionStateUseCase(
    private val source: StateFlow<ServerManager.ConnectionState>,
) {
    operator fun invoke(): StateFlow<ServerManager.ConnectionState> = source
}
