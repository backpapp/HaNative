package com.backpapp.hanative.platform

import com.backpapp.hanative.domain.model.HaServerInfo
import kotlinx.coroutines.flow.Flow

expect interface ServerDiscovery {
    fun startDiscovery(): Flow<List<HaServerInfo>>
    fun stopDiscovery()
}
