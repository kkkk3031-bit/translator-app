package com.khaled.translatorapp;

import android.annotation.SuppressLint;
import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.bottomsheet.BottomSheetBehavior;
import com.google.android.material.bottomsheet.BottomSheetDialog;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Bottom sheet listing languages, with recent ones pinned on top and a search box. */
final class LanguagePicker {

    interface Listener {
        void onPicked(String code);
    }

    private LanguagePicker() {}

    static void show(Context context, boolean includeAuto, String current, List<String> recents,
                     Listener listener) {
        BottomSheetDialog dialog = new BottomSheetDialog(context);
        @SuppressLint("InflateParams") // the dialog supplies the parent
        View root = LayoutInflater.from(context).inflate(R.layout.sheet_language_picker, null);
        dialog.setContentView(root);
        dialog.getBehavior().setState(BottomSheetBehavior.STATE_EXPANDED);
        dialog.getBehavior().setSkipCollapsed(true);

        ((TextView) root.findViewById(R.id.pickerTitle)).setText(
                includeAuto ? R.string.pick_source : R.string.pick_target);

        List<String> all = Languages.all(context);
        Adapter adapter = new Adapter(context, current, code -> {
            dialog.dismiss();
            if (!code.equals(current)) listener.onPicked(code);
        });

        List<Row> unfiltered = new ArrayList<>();
        if (includeAuto) unfiltered.add(Row.language(Languages.AUTO));
        if (!recents.isEmpty()) {
            unfiltered.add(Row.header(context.getString(R.string.recent_languages)));
            for (String c : recents) unfiltered.add(Row.language(c));
            unfiltered.add(Row.header(context.getString(R.string.all_languages)));
        }
        for (String c : all) unfiltered.add(Row.language(c));
        adapter.setRows(unfiltered);

        RecyclerView list = root.findViewById(R.id.languageList);
        list.setLayoutManager(new LinearLayoutManager(context));
        list.setAdapter(adapter);

        EditText search = root.findViewById(R.id.languageSearch);
        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}

            @Override
            public void afterTextChanged(Editable s) {
                String q = s.toString().trim().toLowerCase(Locale.ROOT);
                if (q.isEmpty()) {
                    adapter.setRows(unfiltered);
                    return;
                }
                List<Row> filtered = new ArrayList<>();
                for (String c : all) {
                    String name = Languages.displayName(context, c).toLowerCase(Locale.ROOT);
                    if (name.contains(q) || c.toLowerCase(Locale.ROOT).startsWith(q)) {
                        filtered.add(Row.language(c));
                    }
                }
                adapter.setRows(filtered);
                list.scrollToPosition(0);
            }
        });
        dialog.show();
    }

    private static final class Row {
        final String code;   // null for headers
        final String header;

        private Row(String code, String header) {
            this.code = code;
            this.header = header;
        }

        static Row language(String code) { return new Row(code, null); }
        static Row header(String title) { return new Row(null, title); }
    }

    private static final class Adapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private static final int TYPE_HEADER = 0;
        private static final int TYPE_LANGUAGE = 1;

        private final Context context;
        private final String current;
        private final Listener listener;
        private List<Row> rows = new ArrayList<>();

        Adapter(Context context, String current, Listener listener) {
            this.context = context;
            this.current = current;
            this.listener = listener;
        }

        @SuppressWarnings("NotifyDataSetChanged") // small list, wholesale replacement
        void setRows(List<Row> rows) {
            this.rows = rows;
            notifyDataSetChanged();
        }

        @Override
        public int getItemViewType(int position) {
            return rows.get(position).code == null ? TYPE_HEADER : TYPE_LANGUAGE;
        }

        @Override
        public int getItemCount() {
            return rows.size();
        }

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            int layout = viewType == TYPE_HEADER ? R.layout.item_language_header : R.layout.item_language;
            View v = LayoutInflater.from(parent.getContext()).inflate(layout, parent, false);
            return new RecyclerView.ViewHolder(v) {};
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            Row row = rows.get(position);
            TextView tv = (TextView) holder.itemView;
            if (row.code == null) {
                tv.setText(row.header);
                return;
            }
            tv.setText(Languages.displayName(context, row.code));
            boolean selected = row.code.equals(current);
            tv.setCompoundDrawablesRelativeWithIntrinsicBounds(0, 0, selected ? R.drawable.ic_check : 0, 0);
            tv.setSelected(selected);
            tv.setOnClickListener(v -> listener.onPicked(row.code));
        }
    }
}
