package com.khaled.translatorapp;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.List;

/** Runs against a real (in-memory) SQLite database on the device. */
@RunWith(AndroidJUnit4.class)
public class HistoryStoreTest {

    private HistoryStore store;

    @Before
    public void setUp() {
        store = new HistoryStore(InstrumentationRegistry.getInstrumentation().getTargetContext(), null);
    }

    @After
    public void tearDown() {
        store.close();
    }

    @Test
    public void addAndQueryNewestFirst() throws Exception {
        store.add("one", "واحد", "auto", "ar", "en");
        Thread.sleep(5);
        store.add("two", "اثنان", "en", "ar", "");
        List<HistoryEntry> all = store.query(null);
        assertEquals(2, all.size());
        assertEquals("two", all.get(0).sourceText);
        assertEquals("en", all.get(1).effectiveSourceLang());
    }

    @Test
    public void sameTextAndTargetUpdatesInsteadOfDuplicating() {
        store.add("hello", "مرحبا", "auto", "ar", "en");
        long id = store.query(null).get(0).id;
        store.setStarred(id, true);
        store.add("hello", "أهلا", "auto", "ar", "en");

        List<HistoryEntry> all = store.query(null);
        assertEquals(1, all.size());
        assertEquals("أهلا", all.get(0).translatedText);
        assertTrue("star survives the refresh", all.get(0).starred);

        store.add("hello", "Bonjour", "auto", "fr", "en");
        assertEquals("different target is a new entry", 2, store.query(null).size());
    }

    @Test
    public void keepsOnlyNewestUnstarredButAllStarred() {
        store.add("starred", "x", "en", "ar", "");
        store.setStarred(store.query(null).get(0).id, true);
        for (int i = 0; i < HistoryStore.MAX_UNSTARRED + 20; i++) {
            store.add("text " + i, "x", "en", "ar", "");
        }
        List<HistoryEntry> all = store.query(null);
        assertEquals(HistoryStore.MAX_UNSTARRED + 1, all.size());
        boolean hasStarred = false;
        for (HistoryEntry e : all) hasStarred |= e.sourceText.equals("starred");
        assertTrue(hasStarred);
    }

    @Test
    public void searchMatchesEitherSideAndEscapesWildcards() {
        store.add("50% off", "خصم", "en", "ar", "");
        store.add("500 items", "عناصر", "en", "ar", "");
        assertEquals(1, store.query("50%").size());
        assertEquals(1, store.query("خصم").size());
        assertEquals(0, store.query("_0").size());
    }

    @Test
    public void deleteRestoreAndClearUnstarred() {
        store.add("keep", "x", "en", "ar", "");
        store.setStarred(store.query(null).get(0).id, true);
        store.add("drop", "y", "en", "ar", "");
        HistoryEntry drop = store.query("drop").get(0);

        store.delete(drop.id);
        assertTrue(store.query("drop").isEmpty());
        store.restore(drop);
        assertEquals(drop.id, store.query("drop").get(0).id);

        store.clearUnstarred();
        List<HistoryEntry> all = store.query(null);
        assertEquals(1, all.size());
        assertEquals("keep", all.get(0).sourceText);
        assertFalse(all.isEmpty());
    }
}
