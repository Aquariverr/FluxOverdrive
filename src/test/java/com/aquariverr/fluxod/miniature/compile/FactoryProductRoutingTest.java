package com.aquariverr.fluxod.miniature.compile;

import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.storage.ProductRoutingCodec;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.*;
import static com.aquariverr.fluxod.miniature.compile.FactoryCompilerTest.*;
import static com.aquariverr.fluxod.miniature.model.GridDirection.*;
import static com.aquariverr.fluxod.miniature.model.FactoryDiagnostic.Code.*;
import static org.junit.jupiter.api.Assertions.*;

class FactoryProductRoutingTest {
    private static final ResourceKey GAS = new ResourceKey(ResourceKind.CHEMICAL, "test:gas");
    private static final com.aquariverr.fluxod.miniature.recipe.ResolvedRecipe RECIPE = recipe("three_products",
            List.of(amount(ORE, 2)), List.of(amount(INGOT, 3), amount(FLUID, 5), amount(GAS, 7)));
    private static FactoryLayout layout(int ore) {
        var layout = FactoryLayout.empty(3).withCell(pos(0, 1), marker(ORE, ore, RIGHT))
                .withCell(pos(1, 1), new FactoryCell.Machine(machine(64).stack(), RECIPE.reference(), RIGHT,
                        Map.of(INGOT, RIGHT, FLUID, UP, GAS, DOWN)));
        for (var p : List.of(pos(1, 0), pos(2, 0), pos(2, 1), pos(1, 2), pos(2, 2))) layout = layout.withCell(p, new FactoryCell.Arrow());
        return layout;
    }

    @Test void itemFluidAndChemicalProductsLeaveByTheirOwnDirectionsAndBalanceExactly() {
        var result = FactoryBalancer.balance(layout(77), 1, (p, m, in) -> List.of(RECIPE));
        assertTrue(result.successful(), () -> "" + result.compilation().diagnostic());
        assertEquals(List.of(amount(ORE, 2)), result.compilation().factory().inputs());
        assertEquals(Map.of(0, List.of(amount(FLUID, 5)), 1, List.of(amount(INGOT, 3)), 2, List.of(amount(GAS, 7))), result.compilation().factory().outputRows());
        assertEquals(layout(77).cellAt(pos(1, 1)), result.layout().cellAt(pos(1, 1)));
        assertEquals(result.compilation().factory().outputRows(), success(result.layout(), RECIPE).outputRows());
    }

    @Test void individuallyRoutedProductsCanBeSplitAgainWithoutDuplicatingOtherProducts() {
        var layout = layout(1).withCell(pos(2, 2), new FactoryCell.Arrow(Map.of(RIGHT, 1, UP, 1)));
        var result = FactoryBalancer.balance(layout, 1, (p, m, in) -> List.of(RECIPE));
        assertTrue(result.successful(), () -> "" + result.compilation().diagnostic());
        assertEquals(List.of(amount(ORE, 4)), result.compilation().factory().inputs());
        assertEquals(Map.of(INGOT, BigInteger.valueOf(6), GAS, BigInteger.valueOf(7)), FactoryCompiler.map(result.compilation().factory().outputRows().get(1)));
        assertEquals(List.of(amount(GAS, 7)), result.compilation().factory().outputRows().get(2));
        assertEquals(List.of(amount(FLUID, 10)), result.compilation().factory().outputRows().get(0));
    }

    @Test void defaultFacingDoesNotCreateAnUnusedPortAndRoutingAffectsFingerprint() {
        var original = layout(2); var machine = (FactoryCell.Machine) original.cellAt(pos(1, 1));
        var changed = original.withCell(pos(1, 1), new FactoryCell.Machine(machine.stack(), machine.recipe(), RIGHT,
                Map.of(INGOT, UP, FLUID, UP, GAS, DOWN))).withCell(pos(2, 1), FactoryCell.Empty.INSTANCE);
        var compiled = success(changed, RECIPE);
        assertFalse(compiled.outputRows().containsKey(1));
        assertNotEquals(success(original, RECIPE).fingerprint(), compiled.fingerprint());
        assertEquals(Set.of(UP, DOWN), ((FactoryCell.Machine) changed.cellAt(pos(1, 1))).directions());
    }

    @Test void incompleteOrUnconnectedRoutesNeverDiscardProducts() {
        var original = layout(2); var machine = (FactoryCell.Machine) original.cellAt(pos(1, 1));
        var incomplete = original.withCell(pos(1, 1), new FactoryCell.Machine(machine.stack(), machine.recipe(), RIGHT, Map.of(INGOT, RIGHT, FLUID, UP, DUST, DOWN)));
        assertEquals(INVALID_OUTPUT_ROUTING, compile(incomplete, RECIPE).diagnostic().code());
        assertEquals(INVALID_OUTPUT_ROUTING, FactoryBalancer.balance(incomplete, 1, (p, m, in) -> List.of(RECIPE)).compilation().diagnostic().code());
        assertEquals(ROUTE_DEAD_END, compile(original.withCell(pos(1, 0), FactoryCell.Empty.INSTANCE), RECIPE).diagnostic().code());
    }

    @Test void rotationAndNbtPreserveTheFullImmutableProductMap() {
        var machine = (FactoryCell.Machine) layout(2).cellAt(pos(1, 1));
        var rotated = machine.facing(DOWN);
        assertEquals(Map.of(INGOT, DOWN, FLUID, RIGHT, GAS, LEFT), rotated.outputDirections());
        assertEquals(machine, rotated.facing(RIGHT));
        assertEquals(machine.outputDirections(), ProductRoutingCodec.read(ProductRoutingCodec.write(machine.outputDirections())));
        assertThrows(UnsupportedOperationException.class, () -> machine.outputDirections().clear());
        var duplicate = ProductRoutingCodec.write(machine.outputDirections()); duplicate.add(duplicate.get(0).copy());
        assertThrows(IllegalArgumentException.class, () -> ProductRoutingCodec.read(duplicate));
    }

    @Test void differentComponentsOfOneFluidKeepIndependentPorts() {
        var tag = new net.minecraft.nbt.CompoundTag(); tag.putString("test:quality", "pure");
        var pure = new ResourceKey(ResourceKind.FLUID, FLUID.registryId(), tag);
        var recipe = recipe("fluid_variants", List.of(amount(ORE, 1)), List.of(amount(FLUID, 2), amount(pure, 3)));
        var layout = layout(1).withCell(pos(1, 1), new FactoryCell.Machine(machine(1).stack(), recipe.reference(), RIGHT,
                Map.of(FLUID, UP, pure, DOWN))).withCell(pos(2, 1), FactoryCell.Empty.INSTANCE);
        assertEquals(Map.of(0, List.of(amount(FLUID, 2)), 2, List.of(amount(pure, 3))), success(layout, recipe).outputRows());
    }

    @Test void upgradingExtendsAnIndependentRightPortEvenWhenMachineFacingIsUp() {
        var recipe = recipe("two_products", List.of(amount(ORE, 2)), List.of(amount(INGOT, 3), amount(FLUID, 5)));
        var layout = FactoryLayout.empty(3).withCell(pos(1, 1), marker(ORE, 2, RIGHT))
                .withCell(pos(2, 1), new FactoryCell.Machine(machine(1).stack(), recipe.reference(), UP, Map.of(INGOT, RIGHT, FLUID, UP)))
                .withCell(pos(2, 0), new FactoryCell.Arrow());
        var expanded = layout.upgrade();
        assertEquals(new FactoryCell.Arrow(), expanded.cellAt(pos(3, 1)));
        assertEquals(new FactoryCell.Arrow(), expanded.cellAt(pos(4, 1)));
        assertEquals(success(layout, recipe).outputRows(), success(expanded, recipe).outputRows());
    }
}
