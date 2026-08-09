package ch.brielmayer.bulkmergerequest.provider

import java.io.IOException
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertTrue

class AccessProbeTest {

    @Test
    fun `tries https before plain http`() {
        val tried = mutableListOf<String>()

        AccessProbe.probe("git.example.com") { baseUrl ->
            tried += baseUrl
            AccessCheck.Granted("someone")
        }

        assertEquals(listOf("https://git.example.com"), tried)
    }

    @Test
    fun `falls back to plain http when https cannot be reached`() {
        val tried = mutableListOf<String>()

        val result = AccessProbe.probe("localhost:3000") { baseUrl ->
            tried += baseUrl
            if (baseUrl.startsWith("https")) throw IOException("no TLS here") else AccessCheck.Granted("tester")
        }

        assertEquals(listOf("https://localhost:3000", "http://localhost:3000"), tried)
        assertEquals("tester", assertIs<AccessCheck.Granted>(result).accountName)
    }

    @Test
    fun `reports the transport failure when no scheme works`() {
        val result = AccessProbe.probe("nowhere.invalid") { throw IOException("connection refused") }

        val denied = assertIs<AccessCheck.Denied>(result)
        assertTrue(denied.message.contains("nowhere.invalid"), denied.message)
        assertTrue(denied.message.contains("connection refused"), denied.message)
    }

    @Test
    fun `keeps the hosts own answer instead of retrying the other scheme`() {
        val tried = mutableListOf<String>()

        val result = AccessProbe.probe("gitlab.com") { baseUrl ->
            tried += baseUrl
            AccessCheck.Denied("401 Unauthorized")
        }

        // A denial means the host answered. Retrying over http would only replace a precise reason
        // with a vague connection error.
        assertEquals(1, tried.size)
        assertEquals("401 Unauthorized", assertIs<AccessCheck.Denied>(result).message)
    }
}
