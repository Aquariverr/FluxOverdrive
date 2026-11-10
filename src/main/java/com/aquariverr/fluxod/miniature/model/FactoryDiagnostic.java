package com.aquariverr.fluxod.miniature.model;

import java.math.BigInteger;
import java.util.Objects;
import java.util.Optional;

public record FactoryDiagnostic(Code code, Optional<GridPosition> position, Optional<ResourceKey> resource,
                                Optional<BigInteger> expected, Optional<BigInteger> actual) {

    public FactoryDiagnostic {
        Objects.requireNonNull(code, "code");
        Objects.requireNonNull(position, "position");
        Objects.requireNonNull(resource, "resource");
        Objects.requireNonNull(expected, "expected");
        Objects.requireNonNull(actual, "actual");
    }

    public FactoryDiagnostic(Code code) {
        this(code, Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty());
    }

    public enum Code {
        INVALID_GRID_SIZE,
        INVALID_POSITION,
        INVALID_CELL_COUNT,
        INVALID_REVISION,
        INVALID_RESOURCE_ID,
        INVALID_COMPONENTS,
        COMPONENT_LIMIT_EXCEEDED,
        INVALID_QUANTITY,
        QUANTITY_LIMIT_EXCEEDED,
        INVALID_MACHINE,
        INVALID_MACHINE_COUNT,
        INVALID_ENVIRONMENT,
        EMPTY_OUTPUTS,
        RESOURCE_LIMIT_EXCEEDED,
        NO_MACHINES,
        ROUTE_DEAD_END,
        ROUTE_CYCLE,
        EMPTY_ROUTE,
        AMBIGUOUS_SPLIT,
        MISSING_INPUT,
        EXCESS_RESOURCE,
        BYPASS_ROUTE,
        NO_RECIPE,
        INVALID_RECIPE,
        RECIPE_SEARCH_LIMIT,
        NON_INTEGRAL_RECIPE,
        NON_INTEGRAL_SPLIT,
        DURATION_LIMIT_EXCEEDED,
        BALANCE_IMPOSSIBLE,
        BALANCE_SEARCH_LIMIT,
        INVALID_OUTPUT_ROUTING
    }
}
