package me.foesio.foMiningComp.api;

/** Why a completed competition ended. */
public enum CompletionReason {
    TIME_EXPIRED,
    MANUAL_STOP,
    API_STOP,
    REPLACED,
    DISABLED,
    RELOADED,
    UNKNOWN
}
