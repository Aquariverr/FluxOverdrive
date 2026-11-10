package com.aquariverr.fluxod.miniature.model;

import java.util.Objects;

public record MachineStack(ResourceKey variant, int count) {

    public MachineStack {
        Objects.requireNonNull(variant, "variant");
        if (variant.kind() != ResourceKind.ITEM) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_MACHINE,
                    "A machine variant must be an item");
        }
        if (count < 1 || count > FactoryLimits.MAX_MACHINES_PER_CELL) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_MACHINE_COUNT,
                    "A machine cell must contain between 1 and 64 machines");
        }
    }
}
