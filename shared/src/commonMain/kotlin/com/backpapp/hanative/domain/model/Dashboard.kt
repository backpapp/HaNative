package com.backpapp.hanative.domain.model

data class Dashboard(
    val id: String,
    val name: String,
    val position: Int,
    val createdAt: Long,
    val cards: List<DashboardCard>,
)
