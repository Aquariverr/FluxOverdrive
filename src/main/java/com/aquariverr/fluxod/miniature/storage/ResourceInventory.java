package com.aquariverr.fluxod.miniature.storage;

import com.aquariverr.fluxod.miniature.model.FactoryDiagnostic;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.ModelValidationException;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.model.ResourceKey;

import java.math.BigInteger;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

public final class ResourceInventory {

    private final Map<ResourceKey, BigInteger> amounts;

    public ResourceInventory(Map<ResourceKey, BigInteger> amounts) {
        Objects.requireNonNull(amounts, "amounts");
        if (amounts.size() > FactoryLimits.MAX_BOUNDARY_RESOURCES) {
            throw new ModelValidationException(FactoryDiagnostic.Code.RESOURCE_LIMIT_EXCEEDED,
                    "Inventory exceeds " + FactoryLimits.MAX_BOUNDARY_RESOURCES + " distinct resources");
        }
        Map<ResourceKey, BigInteger> snapshot = new LinkedHashMap<>();
        amounts.forEach((resource, amount) -> {
            Objects.requireNonNull(resource, "resource");
            FactoryLimits.requirePositiveQuantity(amount);
            FactoryLimits.requireInventoryQuantity(amount);
            snapshot.put(resource, amount);
        });
        this.amounts = Collections.unmodifiableMap(snapshot);
    }

    public static ResourceInventory empty() {
        return new ResourceInventory(Map.of());
    }

    public Map<ResourceKey, BigInteger> amounts() {
        return amounts;
    }

    public BigInteger amount(ResourceKey resource) {
        Objects.requireNonNull(resource, "resource");
        return amounts.getOrDefault(resource, BigInteger.ZERO);
    }

    public ResourceInventory insert(ResourceAmount amount) {
        Objects.requireNonNull(amount, "amount");
        Map<ResourceKey, BigInteger> updated = new LinkedHashMap<>(amounts);
        updated.merge(amount.resource(), amount.amount(), BigInteger::add);
        return new ResourceInventory(updated);
    }

    public Extraction extract(ResourceKey resource, BigInteger requested) {
        Objects.requireNonNull(resource, "resource");
        FactoryLimits.requirePositiveQuantity(requested);
        BigInteger available = amount(resource);
        BigInteger extracted = available.min(requested);
        if (extracted.signum() == 0) return new Extraction(this, BigInteger.ZERO);
        Map<ResourceKey, BigInteger> updated = new LinkedHashMap<>(amounts);
        BigInteger remaining = available.subtract(extracted);
        if (remaining.signum() == 0) {
            updated.remove(resource);
        } else {
            updated.put(resource, remaining);
        }
        return new Extraction(new ResourceInventory(updated), extracted);
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof ResourceInventory inventory && amounts.equals(inventory.amounts);
    }

    @Override
    public int hashCode() {
        return amounts.hashCode();
    }

    public record Extraction(ResourceInventory inventory, BigInteger extracted) {

        public Extraction {
            Objects.requireNonNull(inventory, "inventory");
            FactoryLimits.requireInventoryQuantity(extracted);
        }
    }
}
