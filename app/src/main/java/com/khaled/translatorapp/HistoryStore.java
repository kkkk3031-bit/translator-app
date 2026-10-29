package com.khaled.translatorapp;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Translation history in SQLite. Methods block: call them off the main thread.
 * Keeps the newest {@link #MAX_UNSTARRED} unstarred entries; starred ones are kept forever.
 */
final class HistoryStore extends SQLiteOpenHelper {

    static final int MAX_UNSTARRED = 200;
    private static final String TABLE = "history";
    private static final String[] COLUMNS = {
            "_id", "source_text", "translated_text", "source_lang", "target_lang",
            "detected_lang", "starred", "created_at"};

    private static volatile HistoryStore instance;

    static HistoryStore get(Context context) {
        if (instance == null) {
            synchronized (HistoryStore.class) {
                if (instance == null) instance = new HistoryStore(context.getApplicationContext());
            }
        }
        return instance;
    }

    private HistoryStore(Context context) {
        this(context, "history.db");
    }

    /** @param name database file, or null for an in-memory database (tests). */
    HistoryStore(Context context, String name) {
        super(context, name, null, 1);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " ("
                + "_id INTEGER PRIMARY KEY AUTOINCREMENT, "
                + "source_text TEXT NOT NULL, "
                + "translated_text TEXT NOT NULL, "
                + "source_lang TEXT NOT NULL, "
                + "target_lang TEXT NOT NULL, "
                + "detected_lang TEXT NOT NULL DEFAULT '', "
                + "starred INTEGER NOT NULL DEFAULT 0, "
                + "created_at INTEGER NOT NULL)");
        db.execSQL("CREATE INDEX history_created ON " + TABLE + "(created_at)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Only one version so far.
    }

    /**
     * Records a translation. Translating the same text into the same language
     * again refreshes the existing entry (keeping its star) instead of duplicating it.
     */
    void add(String sourceText, String translatedText, String sourceLang, String targetLang,
             String detectedLang) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues v = new ContentValues();
            v.put("translated_text", translatedText);
            v.put("source_lang", sourceLang);
            v.put("detected_lang", detectedLang);
            v.put("created_at", System.currentTimeMillis());
            int updated = db.update(TABLE, v, "source_text = ? AND target_lang = ?",
                    new String[]{sourceText, targetLang});
            if (updated == 0) {
                v.put("source_text", sourceText);
                v.put("target_lang", targetLang);
                db.insert(TABLE, null, v);
            }
            db.execSQL("DELETE FROM " + TABLE + " WHERE starred = 0 AND _id NOT IN ("
                    + "SELECT _id FROM " + TABLE + " WHERE starred = 0 "
                    + "ORDER BY created_at DESC LIMIT " + MAX_UNSTARRED + ")");
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /** Newest first; {@code filter} matches source or translation, case-insensitively for ASCII. */
    List<HistoryEntry> query(String filter) {
        String selection = null;
        String[] args = null;
        if (filter != null && !filter.trim().isEmpty()) {
            String like = "%" + escapeLike(filter.trim()) + "%";
            selection = "source_text LIKE ? ESCAPE '\\' OR translated_text LIKE ? ESCAPE '\\'";
            args = new String[]{like, like};
        }
        List<HistoryEntry> out = new ArrayList<>();
        try (Cursor c = getReadableDatabase().query(TABLE, COLUMNS, selection, args, null, null,
                "created_at DESC")) {
            while (c.moveToNext()) {
                out.add(new HistoryEntry(c.getLong(0), c.getString(1), c.getString(2), c.getString(3),
                        c.getString(4), c.getString(5), c.getInt(6) != 0, c.getLong(7)));
            }
        }
        return out;
    }

    void setStarred(long id, boolean starred) {
        ContentValues v = new ContentValues();
        v.put("starred", starred ? 1 : 0);
        getWritableDatabase().update(TABLE, v, "_id = ?", new String[]{String.valueOf(id)});
    }

    void delete(long id) {
        getWritableDatabase().delete(TABLE, "_id = ?", new String[]{String.valueOf(id)});
    }

    /** Puts back an entry removed with {@link #delete} (for undo). */
    void restore(HistoryEntry e) {
        ContentValues v = new ContentValues();
        v.put("_id", e.id);
        v.put("source_text", e.sourceText);
        v.put("translated_text", e.translatedText);
        v.put("source_lang", e.sourceLang);
        v.put("target_lang", e.targetLang);
        v.put("detected_lang", e.detectedLang);
        v.put("starred", e.starred ? 1 : 0);
        v.put("created_at", e.createdAt);
        getWritableDatabase().insertWithOnConflict(TABLE, null, v, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Deletes everything except starred entries. */
    void clearUnstarred() {
        getWritableDatabase().delete(TABLE, "starred = 0", null);
    }

    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }
}
