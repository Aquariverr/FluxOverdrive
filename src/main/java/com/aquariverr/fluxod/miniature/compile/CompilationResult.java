package com.aquariverr.fluxod.miniature.compile;

import com.aquariverr.fluxod.miniature.model.FactoryDiagnostic;

import java.util.Objects;

public record CompilationResult(CompiledFactory factory, FactoryDiagnostic diagnostic) {
    public CompilationResult {
        if ((factory == null) == (diagnostic == null)) throw new IllegalArgumentException("Exactly one compilation outcome is required");
    }

    public static CompilationResult success(CompiledFactory factory) { return new CompilationResult(Objects.requireNonNull(factory), null); }
    public static CompilationResult failure(FactoryDiagnostic diagnostic) { return new CompilationResult(null, Objects.requireNonNull(diagnostic)); }
    public boolean successful() { return factory != null; }
}
