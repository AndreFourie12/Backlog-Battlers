package com.backlogbattlers.app.util

import com.backlogbattlers.app.domain.model.AppLanguage
import org.junit.Assert.assertEquals
import org.junit.Test

// how a phone or app locale maps onto the languages the app offers
class LanguageTest {

    @Test
    fun `each offered language is found by its own tag`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en"))
        assertEquals(AppLanguage.AFRIKAANS, AppLanguage.fromTag("af"))
        assertEquals(AppLanguage.ZULU, AppLanguage.fromTag("zu"))
    }

    @Test
    fun `a regional locale counts as its language`() {
        assertEquals(AppLanguage.AFRIKAANS, AppLanguage.fromTag("af-ZA"))
        assertEquals(AppLanguage.ZULU, AppLanguage.fromTag("ZU-za"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("en-GB"))
    }

    @Test
    fun `a language the app is not translated into shows English`() {
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag("xh"))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag(""))
        assertEquals(AppLanguage.ENGLISH, AppLanguage.fromTag(null))
    }

    @Test
    fun `the tags match the folders the translations go in`() {
        // values-af and values-zu, and the locales listed in res/xml/locales_config.xml
        assertEquals(listOf("en", "af", "zu"), AppLanguage.entries.map { it.tag })
    }
}
//------------------------------EOF------------------------------\\
