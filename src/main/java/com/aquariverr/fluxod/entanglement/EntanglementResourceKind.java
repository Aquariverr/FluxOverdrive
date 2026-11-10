package com.aquariverr.fluxod.entanglement;

public enum EntanglementResourceKind {
    ITEM(1),
    FLUID(1 << 1),
    CHEMICAL(1 << 2);

    private final int mask;

    EntanglementResourceKind(int mask) {
        this.mask = mask;
    }

    public int mask() {
        return mask;
    }

    public boolean isPresent(int resources) {
        return (resources & mask) != 0;
    }
}
