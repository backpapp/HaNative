package com.backpapp.hanative.domain.usecase

import com.backpapp.hanative.domain.repository.EntityRepository

class CallServiceUseCase(private val repository: EntityRepository) {
    suspend operator fun invoke(
        domain: String,
        service: String,
        entityId: String? = null,
        serviceData: Map<String, Any?> = emptyMap(),
    ): Result<Unit> = repository.callService(domain, service, entityId, serviceData)
}
