package com.aquariverr.fluxod.miniature.model;

import java.math.BigInteger;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class FactoryUpgradeTest {
    private static final ResourceKey ITEM = new ResourceKey(ResourceKind.ITEM, "minecraft:iron_ingot");

    @Test void fourExpansionsKeepCoordinatesRecipesAndEveryAssetCount() {
        var recipe = new RecipeReference("flux_overdrive:vanilla", "minecraft:iron_ingot_from_smelting_iron_ore", 1, 1,
                List.of(new RecipeReference.Binding(0, ITEM)));
        FactoryLayout layout = FactoryLayout.empty(3)
                .withCell(new GridPosition(2, 0), new FactoryCell.Machine(new MachineStack(ITEM, 64), recipe))
                .withCell(new GridPosition(2, 1), new FactoryCell.Marker(new ResourceAmount(ITEM, BigInteger.TEN), GridDirection.RIGHT))
                .withCell(new GridPosition(2, 2), new FactoryCell.Arrow());
        for (int next : List.of(5, 7, 9, 11)) {
            FactoryLayout old = layout;
            layout = old.upgrade();
            assertEquals(next, layout.size());
            assertEquals(old.revision() + 1, layout.revision());
            assertEquals(64, layout.machineCount());
            for (int y = 0; y < old.size(); y++) for (int x = 0; x < old.size(); x++)
                assertEquals(old.cellAt(new GridPosition(x, y)), layout.cellAt(new GridPosition(x, y)));
            for (int y = 0; y < 3; y++) for (int x = 3; x < next; x++)
                assertEquals(new FactoryCell.Arrow(), layout.cellAt(new GridPosition(x, y)));
            for (int y = 3; y < next; y++) for (int x = 0; x < next; x++)
                assertSame(FactoryCell.Empty.INSTANCE, layout.cellAt(new GridPosition(x, y)));
        }
    }

    @Test void unusedEdgesNeverCreateNewRoutes() {
        for (GridDirection direction : List.of(GridDirection.UP, GridDirection.DOWN, GridDirection.LEFT)) {
            var expanded = FactoryLayout.empty(3)
                    .withCell(new GridPosition(2, 0), new FactoryCell.Arrow(direction))
                    .withCell(new GridPosition(2, 1), new FactoryCell.Marker(new ResourceAmount(ITEM, BigInteger.ONE), direction)).upgrade();
            for (int y = 0; y < 5; y++) for (int x = 3; x < 5; x++)
                assertSame(FactoryCell.Empty.INSTANCE, expanded.cellAt(new GridPosition(x, y)));
        }
    }

    @Test void maximumAndExhaustedRevisionRejectWithoutChangingOriginal() {
        var max = FactoryLayout.empty(11);
        assertThrows(ModelValidationException.class, max::upgrade);
        var exhausted = new FactoryLayout(3, FactoryLayout.empty(3).cells(), Long.MAX_VALUE);
        assertEquals(FactoryDiagnostic.Code.INVALID_REVISION, assertThrows(ModelValidationException.class, exhausted::upgrade).diagnostic().code());
        assertEquals(121, max.cells().size());
        assertEquals(0, max.revision());
        assertEquals(Long.MAX_VALUE, exhausted.revision());
    }
}
