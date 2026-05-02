package com.backpapp.hanative.domain.usecase

import kotlinx.coroutines.flow.StateFlow

class ObserveLastWebSocketMessageUseCase(
    private val source: StateFlow<Long?>,
) {
    operator fun invoke(): StateFlow<Long?> = source
}
