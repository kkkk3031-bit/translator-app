package com.khaled.translatorapp;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.view.View;
import android.widget.TextView;

/** Small helpers shared by the main screen, the popup and history. */
final class TextActions {

    private TextActions() {}

    static void copy(Context context, String text) {
        ClipboardManager cm = context.getSystemService(ClipboardManager.class);
        cm.setPrimaryClip(ClipData.newPlainText("translation", text));
    }

    static void share(Context context, String text) {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_TEXT, text);
        context.startActivity(Intent.createChooser(send, null));
    }

    /** Lays out {@code view}'s text in the direction of {@code lang} (e.g. RTL for Arabic). */
    static void applyDirection(TextView view, String lang) {
        view.setTextDirection(Languages.isRtl(lang) ? View.TEXT_DIRECTION_RTL : View.TEXT_DIRECTION_LTR);
    }

    /** For UI-language text such as error messages. */
    static void applyUiDirection(TextView view) {
        view.setTextDirection(View.TEXT_DIRECTION_LOCALE);
    }
}
