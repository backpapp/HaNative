package com.backpapp.hanative.domain.model

data class DashboardCard(
    val id: String,
    val dashboardId: String,
    val entityId: String,
    val position: Int,
    val config: String,
)
