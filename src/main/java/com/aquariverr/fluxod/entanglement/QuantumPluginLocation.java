package com.aquariverr.fluxod.entanglement;

public enum QuantumPluginLocation {
    UP("up"),
    DOWN("down"),
    EAST("east"),
    SOUTH("south"),
    WEST("west"),
    NORTH("north");

    private final String serializedName;

    QuantumPluginLocation(String serializedName) {
        this.serializedName = serializedName;
    }

    public String serializedName() {
        return serializedName;
    }

    public static QuantumPluginLocation fromRelativeOffset(int x, int y, int z) {
        if (y > 0) return UP;
        if (y < 0) return DOWN;
        if (x > 0) return EAST;
        if (z > 0) return SOUTH;
        if (x < 0) return WEST;
        return NORTH;
    }

    public static QuantumPluginLocation byId(int id) {
        QuantumPluginLocation[] values = values();
        return id >= 0 && id < values.length ? values[id] : NORTH;
    }
}
