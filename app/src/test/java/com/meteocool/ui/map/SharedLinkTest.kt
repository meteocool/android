package com.meteocool.ui.map

import com.google.gson.Gson
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

class SharedLinkTest {

    @Test
    fun `a shared link opens its query`() {
        assertEquals(
            "?layer=cells3d&cell=2026100612340000012345&shared=20261006T1234Z",
            SharedLink.search("https://app.meteocool.com/?layer=cells3d&cell=2026100612340000012345&shared=20261006T1234Z"),
        )
        assertEquals("?latLonZ=48.1,11.5,9", SharedLink.search("https://APP.meteocool.com/?latLonZ=48.1,11.5,9"))
        assertEquals("?layer=radar", SharedLink.search("https://next.meteocool.com/?layer=radar"))
        assertEquals("?layer=radar", SharedLink.search("https://demo.meteocool.com?layer=radar"))
        assertEquals("?q=%3Cscript%3E", SharedLink.search("https://app.meteocool.com/?q=%3Cscript%3E"))
    }

    @Test
    fun `anything else is not`() {
        listOf(
            null,
            "",
            "not a url",
            "https://app.meteocool.com/",
            "https://app.meteocool.com/?",
            "https://app.meteocool.com/android.html?layer=radar",
            "https://app.meteocool.com/post_location?x=1",
            "http://app.meteocool.com/?layer=radar",
            "https://app.meteocool.com:8443/?layer=radar",
            "https://meteocool.com/?layer=radar",
            "https://app.meteocool.com.evil.example/?layer=radar",
        ).forEach { assertNull(it, SharedLink.search(it)) }
    }

    @Test
    fun `the script carries the search as a string literal`() {
        val search = "?a=\"</script>"
        val script = SharedLink.openScript(search)
        val literal = script.substringAfterLast("})(").substringBeforeLast(");")
        assertEquals(search, Gson().fromJson(literal, String::class.java))
        assertFalse(script, script.contains("</script>"))
    }
}
