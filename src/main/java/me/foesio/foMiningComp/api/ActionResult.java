package me.foesio.foMiningComp.api;

/** Outcome of a request made through the FoMiningComp service. */
public enum ActionResult {
    SUCCESS,
    NOT_FOUND,
    DISABLED,
    NO_ACTIVE_COMPETITION,
    CANCELLED,
    INVALID_INPUT,
    INELIGIBLE,
    OVERFLOW,
    BUSY,
    PROVIDER_DISABLED
}
