package com.xiaogou.serverseyes.core.network

import com.xiaogou.serverseyes.core.model.LogTargetType
import com.xiaogou.serverseyes.core.model.MetricSource
import com.xiaogou.serverseyes.core.parser.AgentAuthException
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class AgentApiClientTest {

    private lateinit var mockServer: MockWebServer
    private lateinit var client: AgentApiClient

    @Before
    fun setUp() {
        mockServer = MockWebServer()
        mockServer.start()
        client = AgentApiClient()
    }

    @After
    fun tearDown() {
        mockServer.shutdown()
    }

    @Test
    fun `fetchSnapshot and fetchHistory parse valid agent responses with bearer token`() = runTest {
        mockServer.enqueue(
            MockResponse()
                .setResponseCode(200)
                .setBody(
                    """
                    {
                      "timestampMs": 1700000000000,
                      "hostname": "db-node-01",
                      "osName": "Debian 12",
                      "kernel": "Linux 6.1",
                      "uptimeSeconds": 86400,
                      "cpuCores": 8,
                      "cpuPercent": 34.5,
                      "load1": 1.1,
                      "load5": 0.9,
                      "load15": 0.8,
                      "memTotalBytes": 10000,
                      "memUsedBytes": 4500,
                      "disks": [],
                      "netRxBytesPerSec": 1200,
                      "netTxBytesPerSec": 800
                    }
                    """.trimIndent()
                )
        )
        val baseUrl = mockServer.url("/").toString().trimEnd('/')
        val snapRes = client.fetchSnapshot("s1", baseUrl, "secret-token")
        assertTrue(snapRes.isSuccess)
        val snap = snapRes.getOrThrow()
        assertEquals(MetricSource.AGENT, snap.sourceUsed)
        assertEquals("db-node-01", snap.hostname)
        assertEquals(34.5, snap.cpuPercent, 0.01)

        val recorded = mockServer.takeRequest()
        assertEquals("Bearer secret-token", recorded.getHeader("Authorization"))
        assertEquals("/api/v1/snapshot", recorded.path)
    }

    @Test
    fun `unauthorized response returns AgentAuthException`() = runTest {
        mockServer.enqueue(MockResponse().setResponseCode(401))
        val baseUrl = mockServer.url("/").toString().trimEnd('/')
        val res = client.fetchLogs(baseUrl, "wrong", LogTargetType.DOCKER_CONTAINER, "web", 50)
        assertTrue(res.isFailure)
        assertTrue(res.exceptionOrNull() is AgentAuthException)
    }
}
