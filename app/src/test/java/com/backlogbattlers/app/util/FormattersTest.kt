package com.backlogbattlers.app.util

import com.backlogbattlers.app.domain.model.LibraryStatus
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
    fun `a game reads as its main platform and first genre`() {
        assertEquals("PC · Adventure", gameSubtitle(listOf("OTHER", "PLAYSTATION", "PC"), listOf("Adventure", "Indie")))
        assertEquals("PlayStation · Strategy", gameSubtitle(listOf("XBOX", "PLAYSTATION"), listOf("Strategy")))
    }

    @Test
    fun `a game with only a platform, or only a genre, reads as just that`() {
        assertEquals("PC", gameSubtitle(listOf("PC"), emptyList()))
        assertEquals("Adventure", gameSubtitle(emptyList(), listOf("Adventure")))
        assertEquals("", gameSubtitle(emptyList(), emptyList()))
    }

    @Test
    fun `a library status reads as a short phrase`() {
        assertEquals("Not started", libraryStatusLabel(LibraryStatus.BACKLOG))
        assertEquals("In progress", libraryStatusLabel(LibraryStatus.PLAYING))
        assertEquals("Completed", libraryStatusLabel(LibraryStatus.COMPLETED))
        assertEquals("Abandoned", libraryStatusLabel(LibraryStatus.ABANDONED))
    }

    @Test
    fun `the library caption reads platform, status, and hours to beat when the game has one`() {
        assertEquals(
            "PC · Not started · 25h to beat",
            libraryStatusCaption(Platform.PC, LibraryStatus.BACKLOG, avgCompletionHours = 24.6f),
        )
        assertEquals(
            "PlayStation · Completed",
            libraryStatusCaption(Platform.PLAYSTATION, LibraryStatus.COMPLETED, avgCompletionHours = null),
        )
    }

    @Test
    fun `hours to beat is left out rather than shown as zero`() {
        assertEquals(
            "Xbox · In progress",
            libraryStatusCaption(Platform.XBOX, LibraryStatus.PLAYING, avgCompletionHours = null),
        )
        assertEquals(
            "Xbox · In progress",
            libraryStatusCaption(Platform.XBOX, LibraryStatus.PLAYING, avgCompletionHours = 0f),
        )
    }
}
