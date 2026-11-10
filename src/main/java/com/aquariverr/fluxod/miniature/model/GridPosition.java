package com.aquariverr.fluxod.miniature.model;

public record GridPosition(int x, int y) {

    public GridPosition {
        if (x < 0 || x > FactoryLimits.MAX_GRID_SIZE || y < 0 || y >= FactoryLimits.MAX_GRID_SIZE) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_POSITION,
                    "Position is outside the largest factory grid and its output column");
        }
    }
}
