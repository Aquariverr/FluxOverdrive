package com.aquariverr.fluxod.miniature.model;

import java.util.Objects;

public final class ModelValidationException extends IllegalArgumentException {

    private final FactoryDiagnostic diagnostic;

    public ModelValidationException(FactoryDiagnostic.Code code, String message) {
        this(new FactoryDiagnostic(code), message);
    }

    public ModelValidationException(FactoryDiagnostic diagnostic, String message) {
        super(message);
        this.diagnostic = Objects.requireNonNull(diagnostic, "diagnostic");
    }

    public FactoryDiagnostic diagnostic() {
        return diagnostic;
    }
}
