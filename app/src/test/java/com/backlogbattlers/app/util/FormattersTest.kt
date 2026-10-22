package com.backlogbattlers.app.util

import com.backlogbattlers.app.R
import com.backlogbattlers.app.domain.model.Platform
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class FormattersTest {

    @Test
    fun `the main platform is PC first, then the consoles, and other comes last`() {
        assertEquals(Platform.PC, primaryPlatform(listOf("OTHER", "PLAYSTATION", "PC", "XBOX")))
        assertEquals(Platform.PLAYSTATION, primaryPlatform(listOf("XBOX", "SWITCH", "PLAYSTATION")))
        assertEquals(Platform.XBOX, primaryPlatform(listOf("OTHER", "XBOX")))
        assertEquals(Platform.OTHER, primaryPlatform(listOf("OTHER")))
    }

    @Test
    fun `a game that lists no platforms has no main platform`() {
        assertNull(primaryPlatform(emptyList()))
    }

    @Test
    fun `platform names in another case, or ones we do not know, still rank`() {
        assertEquals(Platform.PC, primaryPlatform(listOf("Stadia", "pc")))
        assertEquals(Platform.OTHER, primaryPlatform(listOf("Stadia")))
    }

    @Test
    fun `the default platform from Settings wins whenever the game is on it`() {
        assertEquals(Platform.PLAYSTATION, primaryPlatform(listOf("OTHER", "PLAYSTATION", "PC"), preferred = Platform.PLAYSTATION))
        assertEquals(Platform.SWITCH, primaryPlatform(listOf("switch", "PC"), preferred = Platform.SWITCH))
    }

    @Test
    fun `a game not on the default platform falls back to its main one`() {
        assertEquals(Platform.PC, primaryPlatform(listOf("XBOX", "PC"), preferred = Platform.SWITCH))
        assertNull(primaryPlatform(emptyList(), preferred = Platform.XBOX))
    }

    @Test
    fun `each platform is labelled from strings xml, with PC shown as Steam`() {
        assertEquals(R.string.platform_steam, platformLabelRes("PC"))
        assertEquals(R.string.platform_playstation, platformLabelRes("playstation"))
        assertEquals(R.string.platform_xbox, platformLabelRes("XBOX"))
        assertEquals(R.string.platform_switch, platformLabelRes("SWITCH"))
        assertEquals(R.string.platform_other, platformLabelRes("Stadia"))
    }

    @Test
    fun `a game reads as its platform and first genre`() {
        assertEquals("Steam · Adventure", gameSubtitle("Steam", listOf("Adventure", "Indie")))
        assertEquals("PlayStation · Strategy", gameSubtitle("PlayStation", listOf("Strategy")))
    }

    @Test
    fun `a game with only a platform, or only a genre, reads as just that`() {
        assertEquals("Steam", gameSubtitle("Steam", emptyList()))
        assertEquals("Adventure", gameSubtitle(null, listOf("Adventure")))
        assertEquals("", gameSubtitle(null, emptyList()))
    }

    @Test
    fun `hours to beat round to the nearest hour, and are left out rather than shown as zero`() {
        assertEquals(25, hoursToBeat(24.6f))
        assertEquals(1, hoursToBeat(0.6f))
        assertNull(hoursToBeat(0f))
        assertNull(hoursToBeat(null))
    }
}
