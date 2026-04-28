package com.backpapp.hanative.platform

import com.backpapp.hanative.domain.model.HaServerInfo
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeServerDiscovery : ServerDiscovery {
    var stopped = false
    private val _flow = MutableSharedFlow<List<HaServerInfo>>(replay = 1)

    suspend fun emit(servers: List<HaServerInfo>) = _flow.emit(servers)

    override fun startDiscovery(): Flow<List<HaServerInfo>> = _flow
    override fun stopDiscovery() { stopped = true }
}

class ServerDiscoveryTest {
    @Test
    fun startDiscoveryEmitsDiscoveredServers() = runTest {
        val fake = FakeServerDiscovery()
        val servers = listOf(HaServerInfo("Home", "192.168.1.100", 8123))

        fake.emit(servers)
        val collected = fake.startDiscovery().first()

        assertEquals(servers, collected)
    }

    @Test
    fun startDiscoveryEmitsEmptyList() = runTest {
        val fake = FakeServerDiscovery()

        fake.emit(emptyList())
        val collected = fake.startDiscovery().first()

        assertEquals(emptyList(), collected)
    }

    @Test
    fun stopDiscoverySetsStoppedFlag() {
        val fake = FakeServerDiscovery()
        fake.stopDiscovery()
        assertTrue(fake.stopped)
    }
}
