package nl.openschatkamer.core.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoryboardTest {

    private val vtt = """
        WEBVTT

        00:00:06.000 --> 00:00:09.000
        https://sk-aux.cdn.beeldengeluid.nl/a/storyboard.jpg#xywh=0,0,240,180

        00:00:09.000 --> 00:00:12.000
        https://sk-aux.cdn.beeldengeluid.nl/a/storyboard.jpg#xywh=240,0,240,180

        01:02:03.500 --> 01:02:06.500
        storyboard2.jpg#xywh=480,180,240,180
    """.trimIndent()

    @Test
    fun `tegels en relatieve adressen`() {
        val board = Storyboard.parse(vtt, "https://sk-aux.cdn.beeldengeluid.nl/a/storyboard.vtt")
        assertEquals(3, board.tiles.size)
        assertEquals(240, board.tileAt(10_000)!!.x)
        assertEquals("https://sk-aux.cdn.beeldengeluid.nl/a/storyboard2.jpg", board.tiles[2].url)
        assertEquals(3_723_500L, board.tiles[2].startMs)
    }

    @Test
    fun `positie voor de eerste tegel geeft de eerste`() {
        val board = Storyboard.parse(vtt)
        assertEquals(0, board.tileAt(0)!!.x)
        assertNull(Storyboard.parse("WEBVTT").tileAt(1000))
    }
}
