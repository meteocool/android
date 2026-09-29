package com.meteocool.network

import com.meteocool.network.ApiClient.Endpoint
import kotlinx.coroutines.test.runTest
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

/**
 * The v4 backend answers a payload it rejects with `200 {"success": false}`,
 * so the status alone says nothing.
 */
class ApiClientTest {

    private lateinit var server: MockWebServer

    @Before
    fun setUp() {
        server = MockWebServer()
        server.start()
    }

    @After
    fun tearDown() = server.shutdown()

    private val base get() = server.url("/").toString()

    @Test
    fun `success needs the body to say so`() {
        assertTrue(ApiClient.isSuccess(Endpoint.POST_LOCATION, 200, """{"success": true}"""))
        assertFalse(ApiClient.isSuccess(Endpoint.POST_LOCATION, 200, """{"success": false}"""))
        assertFalse(ApiClient.isSuccess(Endpoint.POST_LOCATION, 200, "OK"))
        assertFalse(ApiClient.isSuccess(Endpoint.POST_LOCATION, 200, null))
        assertFalse(ApiClient.isSuccess(Endpoint.POST_LOCATION, 500, """{"success": true}"""))
        assertFalse(ApiClient.isSuccess(Endpoint.POST_LOCATION, 200, """{"success": "true"}"""))
    }

    @Test
    fun `unregistering an unknown token counts as done`() {
        val body = """{"success": false, "message": "not registered"}"""
        assertTrue(ApiClient.isSuccess(Endpoint.UNREGISTER, 200, body))
        assertFalse(ApiClient.isSuccess(Endpoint.POST_LOCATION, 200, body))
    }

    @Test
    fun `a non-finite number is not encoded`() {
        assertNull(ApiClient.encode(mapOf("lat" to Double.NaN)))
        assertNull(ApiClient.encode(mapOf("pressure" to Float.POSITIVE_INFINITY)))
        assertEquals("""{"lat":1.5}""", ApiClient.encode(mapOf("lat" to 1.5)))
    }

    @Test
    fun `posts json to the endpoint below the base`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":true}"""))
        val ok = ApiClient().post(base, Endpoint.POST_LOCATION, mapOf("token" to "t", "ahead" to 15))
        assertTrue(ok)
        val request = server.takeRequest()
        assertEquals("POST", request.method)
        assertEquals("/post_location", request.path)
        assertTrue(request.getHeader("Content-Type")!!.startsWith("application/json"))
        assertEquals("""{"token":"t","ahead":15}""", request.body.readUtf8())
    }

    @Test
    fun `a rejected payload is a failure`() = runTest {
        server.enqueue(MockResponse().setBody("""{"success":false}"""))
        assertFalse(ApiClient().post(base, Endpoint.POST_LOCATION, mapOf("token" to "t")))
    }

    @Test
    fun `an http error is a failure`() = runTest {
        server.enqueue(MockResponse().setResponseCode(502).setBody("""{"success":true}"""))
        assertFalse(ApiClient().post(base, Endpoint.CLEAR_NOTIFICATION, mapOf("token" to "t")))
    }

    @Test
    fun `a NaN payload is never sent`() = runTest {
        assertFalse(ApiClient().post(base, Endpoint.POST_LOCATION, mapOf("lat" to Double.NaN)))
        assertEquals(0, server.requestCount)
    }
}
