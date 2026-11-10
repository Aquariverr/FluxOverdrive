package com.aquariverr.fluxod.entanglement;

/**
 * Defines how the single selection on an entanglement facility is routed.
 * A missing selection keeps legacy constraint-port behavior for old worlds.
 */
public final class EntanglementRoutePolicy {

    public enum Mode {
        CONSTRAINT_GROUP,
        SINGLE_PLUGIN
    }

    public static Mode resolve(boolean hasSelection, boolean selectedConstraint) {
        return hasSelection && !selectedConstraint ? Mode.SINGLE_PLUGIN : Mode.CONSTRAINT_GROUP;
    }

    public static boolean automationEnabled(EntanglementResourceDirection direction,
                                            boolean inputEnabled, boolean outputEnabled) {
        return direction == EntanglementResourceDirection.INPUT ? inputEnabled : outputEnabled;
    }

    private EntanglementRoutePolicy() {}
}
