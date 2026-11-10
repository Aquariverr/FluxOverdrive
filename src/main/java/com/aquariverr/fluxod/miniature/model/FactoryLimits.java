package com.aquariverr.fluxod.miniature.model;

import java.math.BigInteger;

public final class FactoryLimits {

    public static final int MAX_GRID_SIZE = 11;
    public static final int MAX_MACHINES_PER_CELL = 64;
    public static final int MAX_TOTAL_MACHINES = 7_744;
    public static final int MAX_BATCH_PARALLELISM = 128_000_000; // 64 machines * 1000 normalization * 2000 environment
    public static final int MAX_BATCH_TICKS = Integer.MAX_VALUE / 20 * 20;
    public static final int MAX_RECIPE_RESOURCES = 64;
    public static final int MAX_BOUNDARY_RESOURCES = 256;
    public static final BigInteger MAX_INVENTORY_AMOUNT = BigInteger.TEN.pow(30);
    public static final int MAX_QUANTITY_BYTES = 32;
    public static final BigInteger MAX_ENCODED_QUANTITY = BigInteger.ONE
            .shiftLeft(MAX_QUANTITY_BYTES * Byte.SIZE).subtract(BigInteger.ONE);
    public static final int MAX_COMPONENT_BYTES = 64 * 1024;
    public static final int MAX_MESSAGE_BYTES = 1024 * 1024;

    public static BigInteger requireQuantity(BigInteger quantity) {
        if (quantity == null || quantity.signum() < 0) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_QUANTITY,
                    "Quantity must be a nonnegative integer");
        }
        if (quantity.compareTo(MAX_ENCODED_QUANTITY) > 0) {
            throw new ModelValidationException(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                    "Quantity exceeds the unsigned " + MAX_QUANTITY_BYTES + "-byte limit");
        }
        return quantity;
    }

    public static BigInteger requirePositiveQuantity(BigInteger quantity) {
        requireQuantity(quantity);
        if (quantity.signum() == 0) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_QUANTITY,
                    "Quantity must be a positive integer");
        }
        return quantity;
    }

    public static BigInteger requireInventoryQuantity(BigInteger quantity) {
        requireQuantity(quantity);
        if (quantity.compareTo(MAX_INVENTORY_AMOUNT) > 0) {
            throw new ModelValidationException(FactoryDiagnostic.Code.QUANTITY_LIMIT_EXCEEDED,
                    "Inventory quantity exceeds " + MAX_INVENTORY_AMOUNT);
        }
        return quantity;
    }

    private FactoryLimits() {}
}
