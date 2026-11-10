package com.aquariverr.fluxod.miniature.model;

import java.math.BigInteger;
import java.util.Objects;

public record ResourceAmount(ResourceKey resource, BigInteger amount) {

    public ResourceAmount {
        Objects.requireNonNull(resource, "resource");
        FactoryLimits.requirePositiveQuantity(amount);
    }

    public static ResourceAmount defaultMarker(ResourceKey resource) {
        return new ResourceAmount(resource, resource.kind().minimumOutput());
    }
}
