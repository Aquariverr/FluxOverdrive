package com.aquariverr.fluxod.miniature.model;

import java.math.BigInteger;

public enum ResourceKind {
    ITEM(1),
    FLUID(1000),
    CHEMICAL(1000);

    private final BigInteger minimumOutput;

    ResourceKind(int minimumOutput) {
        this.minimumOutput = BigInteger.valueOf(minimumOutput);
    }

    public BigInteger minimumOutput() {
        return minimumOutput;
    }
}
