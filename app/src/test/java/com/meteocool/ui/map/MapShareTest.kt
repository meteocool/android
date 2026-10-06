package com.meteocool.ui.map

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class MapShareTest {

    private val host = "app.meteocool.com"
    private val link = "https://app.meteocool.com/?layer=cells3d&cell=2026100402050000012345&shared=20261006T1234Z"

    @Test
    fun `a share from a button keeps its link and title`() {
        val share = MapShare.parse("""{"url":"$link","title":"Strong storm · meteocool","x":10,"y":20.5,"width":44,"height":44}""", host)
        assertEquals(MapShare(link, "Strong storm · meteocool"), share)
    }

    @Test
    fun `an empty title falls back to the app's name`() {
        assertEquals("meteocool", MapShare.parse("""{"url":"$link","title":"  "}""", host)?.title)
        assertEquals("meteocool", MapShare.parse("""{"url":"$link"}""", host)?.title)
    }

    @Test
    fun `only links back to the map's own host are shared`() {
        assertNull(MapShare.parse("""{"url":"https://evil.example/?x","title":"t"}""", host))
        assertNull(MapShare.parse("""{"url":"https://app.meteocool.com.evil.example/","title":"t"}""", host))
        assertNull(MapShare.parse("""{"url":"$link"}""", null))
        assertNotNull(MapShare.parse("""{"url":"https://APP.meteocool.com/","title":"t"}""", host))
    }

    @Test
    fun `plain http is for a loopback test map only`() {
        assertNull(MapShare.parse("""{"url":"http://app.meteocool.com/","title":"t"}""", host))
        assertNotNull(MapShare.parse("""{"url":"http://127.0.0.1:18765/?layer=radar","title":"t"}""", "127.0.0.1"))
        assertNotNull(MapShare.parse("""{"url":"http://localhost:4173/?layer=radar","title":"t"}""", "localhost"))
        assertNull(MapShare.parse("""{"url":"http://10.0.2.2:4173/?layer=radar","title":"t"}""", "10.0.2.2"))
    }

    @Test
    fun `anything that is not a link is refused`() {
        assertNull(MapShare.parse("""{"url":"javascript:alert(1)","title":"t"}""", host))
        assertNull(MapShare.parse("""{"url":42,"title":"t"}""", host))
        assertNull(MapShare.parse("""{"url":"https://app.meteocool.com/a b","title":"t"}""", host))
        assertNull(MapShare.parse("null", host))
        assertNull(MapShare.parse("not json", host))
        assertNull(MapShare.parse("[]", host))
    }

    @Test
    fun `a long title is cut without splitting a character`() {
        assertEquals(MapShare.MAX_TITLE_LENGTH, MapShare.parse("""{"url":"$link","title":"${"a".repeat(500)}"}""", host)?.title?.length)
        val title = MapShare.parse("""{"url":"$link","title":"${"🌩".repeat(300)}"}""", host)?.title.orEmpty()
        assertEquals(MapShare.MAX_TITLE_LENGTH, title.codePointCount(0, title.length))
        assertEquals("🌩", title.takeLast(2))
    }

    @Test
    fun `the page's answer to shareLink arrives as a JSON string`() {
        val answer = "\"{\\\"url\\\":\\\"$link\\\",\\\"title\\\":\\\"meteocool\\\"}\""
        assertEquals(MapShare(link, "meteocool"), MapShare.fromScriptResult(answer, host))
        // Before the map is up, and from a page without window.shareLink.
        assertNull(MapShare.fromScriptResult("\"null\"", host))
        // The script threw.
        assertNull(MapShare.fromScriptResult("null", host))
        assertNull(MapShare.fromScriptResult(null, host))
    }
}
