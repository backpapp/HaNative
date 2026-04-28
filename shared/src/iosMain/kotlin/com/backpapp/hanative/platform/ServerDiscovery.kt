package com.backpapp.hanative.platform

import com.backpapp.hanative.domain.model.HaServerInfo
import kotlinx.coroutines.flow.Flow

actual interface ServerDiscovery {
    actual fun startDiscovery(): Flow<List<HaServerInfo>>
    actual fun stopDiscovery()
}
