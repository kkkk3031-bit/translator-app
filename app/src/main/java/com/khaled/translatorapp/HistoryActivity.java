package com.khaled.translatorapp;

import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.appbar.MaterialToolbar;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.snackbar.Snackbar;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/** Past translations: search, star, swipe to delete, tap to reopen on the main screen. */
public class HistoryActivity extends AppCompatActivity {

    private static final String EXTRA_SOURCE_TEXT = "source_text";
    private static final String EXTRA_TRANSLATION = "translation";
    private static final String EXTRA_SOURCE_LANG = "source_lang";
    private static final String EXTRA_TARGET_LANG = "target_lang";
    private static final String EXTRA_DETECTED_LANG = "detected_lang";

    private final ExecutorService io = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private HistoryStore store;
    private HistoryAdapter adapter;
    private TextView emptyText;
    private String filter = "";

    static HistoryEntry entryFrom(Intent data) {
        return new HistoryEntry(0, data.getStringExtra(EXTRA_SOURCE_TEXT),
                data.getStringExtra(EXTRA_TRANSLATION), data.getStringExtra(EXTRA_SOURCE_LANG),
                data.getStringExtra(EXTRA_TARGET_LANG), data.getStringExtra(EXTRA_DETECTED_LANG),
                false, 0);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_history);
        store = HistoryStore.get(this);
        emptyText = findViewById(R.id.emptyText);

        MaterialToolbar toolbar = findViewById(R.id.toolbar);
        toolbar.setNavigationOnClickListener(v -> finish());
        toolbar.setOnMenuItemClickListener(item -> {
            if (item.getItemId() == R.id.action_clear) {
                confirmClear();
                return true;
            }
            return false;
        });
        MenuItem searchItem = toolbar.getMenu().findItem(R.id.action_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        searchView.setQueryHint(getString(R.string.search));
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                filter = newText;
                reload();
                return true;
            }
        });

        adapter = new HistoryAdapter(this, new HistoryAdapter.Listener() {
            @Override
            public void onOpen(HistoryEntry e) {
                setResult(RESULT_OK, new Intent()
                        .putExtra(EXTRA_SOURCE_TEXT, e.sourceText)
                        .putExtra(EXTRA_TRANSLATION, e.translatedText)
                        .putExtra(EXTRA_SOURCE_LANG, e.sourceLang)
                        .putExtra(EXTRA_TARGET_LANG, e.targetLang)
                        .putExtra(EXTRA_DETECTED_LANG, e.detectedLang));
                finish();
            }

            @Override
            public void onToggleStar(HistoryEntry e) {
                boolean starred = !e.starred;
                adapter.replace(e.withStarred(starred));
                io.execute(() -> store.setStarred(e.id, starred));
            }
        });

        RecyclerView list = findViewById(R.id.historyList);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        new ItemTouchHelper(new ItemTouchHelper.SimpleCallback(0,
                ItemTouchHelper.LEFT | ItemTouchHelper.RIGHT) {
            @Override
            public boolean onMove(RecyclerView rv, RecyclerView.ViewHolder vh, RecyclerView.ViewHolder t) {
                return false;
            }

            @Override
            public void onSwiped(RecyclerView.ViewHolder vh, int direction) {
                HistoryEntry removed = adapter.removeAt(vh.getBindingAdapterPosition());
                updateEmpty();
                io.execute(() -> store.delete(removed.id));
                Snackbar.make(findViewById(R.id.historyRoot), R.string.deleted, Snackbar.LENGTH_LONG)
                        .setAction(R.string.undo, v -> io.execute(() -> {
                            store.restore(removed);
                            mainHandler.post(HistoryActivity.this::reload);
                        }))
                        .show();
            }
        }).attachToRecyclerView(list);

        reload();
    }

    @Override
    protected void onDestroy() {
        io.shutdown();
        super.onDestroy();
    }

    private void reload() {
        String query = filter;
        io.execute(() -> {
            List<HistoryEntry> entries = store.query(query);
            mainHandler.post(() -> {
                if (isDestroyed() || !query.equals(filter)) return;
                adapter.setEntries(entries);
                updateEmpty();
            });
        });
    }

    private void updateEmpty() {
        boolean empty = adapter.getItemCount() == 0;
        emptyText.setVisibility(empty ? View.VISIBLE : View.GONE);
        emptyText.setText(filter.trim().isEmpty() ? R.string.history_empty : R.string.history_no_matches);
    }

    private void confirmClear() {
        new MaterialAlertDialogBuilder(this)
                .setTitle(R.string.clear_history)
                .setMessage(R.string.clear_history_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.clear, (d, w) -> io.execute(() -> {
                    store.clearUnstarred();
                    mainHandler.post(this::reload);
                }))
                .show();
    }
}
