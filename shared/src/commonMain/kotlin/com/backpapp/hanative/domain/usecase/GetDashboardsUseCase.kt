package com.backpapp.hanative.domain.usecase

import com.backpapp.hanative.domain.model.Dashboard
import com.backpapp.hanative.domain.repository.DashboardRepository
import kotlinx.coroutines.flow.Flow

class GetDashboardsUseCase(private val repository: DashboardRepository) {
    operator fun invoke(): Flow<List<Dashboard>> = repository.getDashboards()
}
