package com.khaled.translatorapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LanguagesTest {

    @Test
    public void rtlLanguages() {
        for (String c : new String[]{"ar", "fa", "he", "iw", "ur", "ckb"}) {
            assertTrue(c, Languages.isRtl(c));
        }
        for (String c : new String[]{"en", "fr", "zh-CN", "auto", "ku"}) {
            assertFalse(c, Languages.isRtl(c));
        }
        assertFalse(Languages.isRtl(null));
    }

    @Test
    public void pushRecentMovesToFrontAndCaps() {
        List<String> list = new ArrayList<>(Arrays.asList("fr", "de", "es", "it", "ja"));
        assertEquals(Arrays.asList("de", "fr", "es", "it", "ja"), LanguagePrefs.pushRecent(list, "de", 5));
        assertEquals(Arrays.asList("ko", "fr", "de", "es", "it"), LanguagePrefs.pushRecent(list, "ko", 5));
        assertEquals(Arrays.asList("en"), LanguagePrefs.pushRecent(new ArrayList<>(), "en", 5));
    }

    @Test
    public void likeEscaping() {
        assertEquals("50\\% off\\_now\\\\", HistoryStore.escapeLike("50% off_now\\"));
    }
}
