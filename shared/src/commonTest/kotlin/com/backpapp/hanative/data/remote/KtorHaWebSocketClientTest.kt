package com.backpapp.hanative.data.remote

import io.ktor.client.HttpClient
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.ExperimentalTime

// These tests exercise `parseFrameUpdatingTimestamp` directly rather than feeding via
// `WebSocketSession.incoming`, intentionally. Setting up a fake `DefaultClientWebSocketSession`
// in commonTest is currently not feasible without pulling in a Ktor MockEngine fixture,
// and the helper is the single source of truth for the timestamp + quarantine contract —
// driving it directly gives us the same coverage with no flake surface.
@OptIn(ExperimentalTime::class)
class KtorHaWebSocketClientTest {

    private val httpClient = HttpClient()
    private val client = KtorHaWebSocketClient(httpClient)

    @AfterTest
    fun tearDown() {
        httpClient.close()
    }

    @Test
    fun lastMessageEpochMsUpdatesOnInboundEventFrame() {
        assertNull(client.lastMessageEpochMs.value, "starts null until first message")

        val before = Clock.System.now().toEpochMilliseconds()
        val parsed = client.parseFrameUpdatingTimestamp("""{"type":"event","id":1}""")
        val after = Clock.System.now().toEpochMilliseconds()

        assertNotNull(parsed)
        val ts = client.lastMessageEpochMs.value
        assertNotNull(ts, "timestamp set on event frame")
        assertTrue(ts in before..after, "timestamp matches Clock.System.now() within window")
    }

    @Test
    fun lastMessageEpochMsUpdatesOnResultFrame() {
        val parsed = client.parseFrameUpdatingTimestamp("""{"type":"result","id":2,"success":true}""")
        assertNotNull(parsed)
        assertNotNull(client.lastMessageEpochMs.value)
    }

    @Test
    fun lastMessageEpochMsDoesNotUpdateOnAuthFrames() {
        // auth_required / auth_ok / auth_invalid are protocol handshake frames, not
        // payload-bearing — they must NOT refresh the user-visible "Last updated" timer.
        val authRequired = client.parseFrameUpdatingTimestamp("""{"type":"auth_required"}""")
        assertNotNull(authRequired, "auth_required parses successfully")
        assertNull(client.lastMessageEpochMs.value, "auth_required must not update timestamp")

        val authOk = client.parseFrameUpdatingTimestamp("""{"type":"auth_ok"}""")
        assertNotNull(authOk)
        assertNull(client.lastMessageEpochMs.value, "auth_ok must not update timestamp")
    }

    @Test
    fun lastMessageEpochMsDoesNotUpdateOnUnknownType() {
        val parsed = client.parseFrameUpdatingTimestamp("""{"type":"pong"}""")
        assertNotNull(parsed)
        assertNull(client.lastMessageEpochMs.value)
    }

    @Test
    fun malformedFrameIsAbsorbedAndDoesNotUpdateTimestamp() {
        // Corrupted JSON: parse fails, timestamp must not advance, no exception propagates.
        val parsed = client.parseFrameUpdatingTimestamp("{not valid json")
        assertNull(parsed, "malformed frame returns null")
        assertNull(client.lastMessageEpochMs.value, "timestamp untouched on malformed frame")
    }

    @Test
    fun nonObjectFrameIsAbsorbed() {
        // Valid JSON but not an object — also drops cleanly.
        val parsed = client.parseFrameUpdatingTimestamp("[1,2,3]")
        assertNull(parsed)
        assertNull(client.lastMessageEpochMs.value)
    }

    @Test
    fun successfulEventFrameAfterMalformedFrameStillUpdatesTimestamp() {
        client.parseFrameUpdatingTimestamp("garbage")
        assertNull(client.lastMessageEpochMs.value)

        client.parseFrameUpdatingTimestamp("""{"type":"event","id":2}""")
        assertNotNull(client.lastMessageEpochMs.value)
    }

    @Test
    fun isConnectedStartsFalse() {
        assertEquals(false, client.isConnected.value)
    }
}
