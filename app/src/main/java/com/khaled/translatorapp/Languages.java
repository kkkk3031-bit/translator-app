package com.khaled.translatorapp;

import android.content.Context;

import java.text.Collator;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/** Language codes the translation engine supports, with display helpers. */
final class Languages {

    static final String AUTO = "auto";
    static final String DEFAULT_TARGET = "ar";

    private static final String[] CODES = {
            "af", "sq", "am", "ar", "hy", "az", "eu", "be", "bn", "bs", "bg", "ca", "ceb",
            "zh-CN", "zh-TW", "co", "hr", "cs", "da", "nl", "en", "eo", "et", "fi", "fr", "fy",
            "gl", "ka", "de", "el", "gu", "ht", "ha", "haw", "he", "hi", "hmn", "hu", "is", "ig",
            "id", "ga", "it", "ja", "jv", "kn", "kk", "km", "rw", "ko", "ku", "ckb", "ky", "lo",
            "la", "lv", "lt", "lb", "mk", "mg", "ms", "ml", "mt", "mi", "mr", "mn", "my", "ne",
            "no", "ny", "or", "ps", "fa", "pl", "pt", "pa", "ro", "ru", "sm", "gd", "sr", "st",
            "sn", "sd", "si", "sk", "sl", "so", "es", "su", "sw", "sv", "tl", "tg", "ta", "tt",
            "te", "th", "tr", "tk", "uk", "ur", "ug", "uz", "vi", "cy", "xh", "yi", "yo", "zu",
    };

    // "iw" is the legacy code for Hebrew that the engine sometimes reports.
    private static final Set<String> RTL = new HashSet<>(Arrays.asList(
            "ar", "fa", "he", "iw", "ur", "ps", "sd", "ug", "yi", "dv", "ckb"));

    private Languages() {}

    /** All supported codes, sorted by their display name in the current UI language. */
    static List<String> all(Context context) {
        List<String> codes = new ArrayList<>(Arrays.asList(CODES));
        Collator collator = Collator.getInstance(uiLocale(context));
        codes.sort((a, b) -> collator.compare(displayName(context, a), displayName(context, b)));
        return codes;
    }

    static String displayName(Context context, String code) {
        if (AUTO.equals(code)) return context.getString(R.string.lang_auto);
        Locale ui = uiLocale(context);
        String name = Locale.forLanguageTag(code).getDisplayName(ui);
        if (name.isEmpty()) return code;
        return name.substring(0, 1).toUpperCase(ui) + name.substring(1);
    }

    static boolean isRtl(String code) {
        if (code == null) return false;
        int dash = code.indexOf('-');
        return RTL.contains(dash < 0 ? code : code.substring(0, dash));
    }

    private static Locale uiLocale(Context context) {
        return context.getResources().getConfiguration().getLocales().get(0);
    }
}
