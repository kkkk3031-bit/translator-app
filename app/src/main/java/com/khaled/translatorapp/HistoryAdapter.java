package com.khaled.translatorapp;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

final class HistoryAdapter extends RecyclerView.Adapter<HistoryAdapter.Holder> {

    interface Listener {
        void onOpen(HistoryEntry e);
        void onToggleStar(HistoryEntry e);
    }

    private final Context context;
    private final Listener listener;
    private List<HistoryEntry> entries = new ArrayList<>();

    HistoryAdapter(Context context, Listener listener) {
        this.context = context;
        this.listener = listener;
    }

    @SuppressWarnings("NotifyDataSetChanged") // full reload after a query
    void setEntries(List<HistoryEntry> entries) {
        this.entries = new ArrayList<>(entries);
        notifyDataSetChanged();
    }

    void replace(HistoryEntry updated) {
        for (int i = 0; i < entries.size(); i++) {
            if (entries.get(i).id == updated.id) {
                entries.set(i, updated);
                notifyItemChanged(i);
                return;
            }
        }
    }

    HistoryEntry removeAt(int position) {
        HistoryEntry e = entries.remove(position);
        notifyItemRemoved(position);
        return e;
    }

    @Override
    public int getItemCount() {
        return entries.size();
    }

    @NonNull
    @Override
    public Holder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        return new Holder(LayoutInflater.from(parent.getContext()).inflate(R.layout.item_history, parent, false));
    }

    @Override
    public void onBindViewHolder(@NonNull Holder h, int position) {
        HistoryEntry e = entries.get(position);
        String from = e.effectiveSourceLang();
        String langs = (Languages.AUTO.equals(from) ? "?" : from.toUpperCase(Locale.ROOT))
                + " → " + e.targetLang.toUpperCase(Locale.ROOT);
        CharSequence when = DateUtils.getRelativeTimeSpanString(e.createdAt,
                System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS);
        h.meta.setText(langs + " · " + when);
        h.source.setText(e.sourceText);
        TextActions.applyDirection(h.source, from);
        h.translation.setText(e.translatedText);
        TextActions.applyDirection(h.translation, e.targetLang);
        h.star.setIconResource(e.starred ? R.drawable.ic_star : R.drawable.ic_star_border);
        h.star.setContentDescription(context.getString(e.starred ? R.string.unstar : R.string.star));
        h.star.setOnClickListener(v -> listener.onToggleStar(e));
        h.itemView.setOnClickListener(v -> listener.onOpen(e));
    }

    static final class Holder extends RecyclerView.ViewHolder {
        final TextView meta;
        final TextView source;
        final TextView translation;
        final MaterialButton star;

        Holder(View v) {
            super(v);
            meta = v.findViewById(R.id.historyMeta);
            source = v.findViewById(R.id.historySource);
            translation = v.findViewById(R.id.historyTranslation);
            star = v.findViewById(R.id.historyStar);
        }
    }
}
