package com.khaled.translatorapp;

/** A translation failure, classified so the UI can show a helpful message. */
final class TranslationException extends Exception {

    enum Kind {
        OFFLINE,
        TIMEOUT,
        RATE_LIMITED,
        SERVER,
        BAD_RESPONSE,
        NETWORK,
        CANCELLED;

        /** Worth retrying automatically: likely to succeed a moment later. */
        boolean isTransient() {
            return this == TIMEOUT || this == SERVER || this == NETWORK;
        }

        int messageRes() {
            switch (this) {
                case OFFLINE: return R.string.error_offline;
                case TIMEOUT: return R.string.error_timeout;
                case RATE_LIMITED: return R.string.error_rate_limited;
                case SERVER: return R.string.error_server;
                case BAD_RESPONSE: return R.string.error_bad_response;
                default: return R.string.error_network;
            }
        }
    }

    final Kind kind;

    TranslationException(Kind kind, String message) {
        super(message);
        this.kind = kind;
    }

    TranslationException(Kind kind, String message, Throwable cause) {
        super(message, cause);
        this.kind = kind;
    }
}
