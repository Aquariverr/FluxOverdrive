package com.aquariverr.fluxod.miniature.compile;

import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.recipe.ResolvedRecipe;
import net.minecraft.nbt.CompoundTag;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;

import static com.aquariverr.fluxod.miniature.model.FactoryDiagnostic.Code.*;
import static com.aquariverr.fluxod.miniature.model.GridDirection.*;
import static org.junit.jupiter.api.Assertions.*;

class FactoryCompilerTest {
    static final ResourceKey ORE = item("ore"), INGOT = item("ingot"), DUST = item("dust"), CRYSTAL = item("crystal");
    static final ResourceKey FLUID = new ResourceKey(ResourceKind.FLUID, "test:fluid");
    static final ResourceKey MACHINE = item("machine");
    static final ResolvedRecipe SMELT = recipe("smelt", List.of(amount(ORE, 1)), List.of(amount(INGOT, 1)));

    @Test void nativeNonconsumableInputsBecomeReservedCatalystsInsteadOfProducts() {
        var recipe = new ResolvedRecipe(SMELT.reference(), List.of(amount(ORE, 1), amount(CRYSTAL, 1)),
                List.of(amount(INGOT, 1), amount(CRYSTAL, 1)), List.of(amount(CRYSTAL, 1)));
        var layout = line(1, 1).withCell(pos(1, 1), marker(CRYSTAL, 1, UP));
        var compiled = success(layout, recipe);
        assertEquals(List.of(amount(ORE, 1)), compiled.inputs());
        assertEquals(List.of(amount(INGOT, 1)), compiled.outputs());
        assertEquals(List.of(amount(CRYSTAL, 1)), compiled.catalysts());
        assertEquals(List.of(amount(INGOT, 1)), compiled.outputRows().get(0));
        assertDoesNotThrow(() -> CompilationPreview.from(CompilationResult.success(compiled)));
        assertNotEquals(compiled.fingerprint(), success(layout, new ResolvedRecipe(recipe.reference(), recipe.inputs(), recipe.outputs())).fingerprint());
    }

    @Test void returnedFluidCatalystDoesNotInflateProductNormalization() {
        var recipe = new ResolvedRecipe(SMELT.reference(), List.of(amount(ORE, 1), amount(FLUID, 1)),
                List.of(amount(INGOT, 1), amount(FLUID, 1)), List.of(amount(FLUID, 1)));
        assertEquals(BigInteger.ONE, recipe.normalization());
        var compiled = success(line(2, 2).withCell(pos(1, 1), marker(FLUID, 2, UP)), recipe);
        assertEquals(List.of(amount(ORE, 2)), compiled.inputs());
        assertEquals(List.of(amount(FLUID, 2)), compiled.catalysts());
        assertEquals(List.of(amount(INGOT, 2)), compiled.outputs());
    }

    @Test void catalystsMustBePresentInBothSidesOfNativeRecipe() {
        assertThrows(IllegalArgumentException.class, () -> new ResolvedRecipe(SMELT.reference(), SMELT.inputs(), SMELT.outputs(), List.of(amount(CRYSTAL, 1))));
        assertThrows(IllegalArgumentException.class, () -> new ResolvedRecipe(SMELT.reference(), List.of(amount(ORE, 1), amount(CRYSTAL, 1)), SMELT.outputs(), List.of(amount(CRYSTAL, 1))));
    }

    @Test void minimalLineAndDoubledMachinesHaveExactBoundaryAndCost() {
        CompiledFactory one = success(line(1, 1), SMELT);
        assertEquals(List.of(amount(ORE, 1)), one.inputs());
        assertEquals(List.of(amount(INGOT, 1)), one.outputs());
        assertEquals(Map.of(0, one.outputs()), one.outputRows());
        assertEquals(new BigInteger("20000000"), one.energyPerTick());
        assertTrue(one.catalysts().isEmpty());
        CompiledFactory two = success(line(2, 2), SMELT);
        assertEquals(List.of(amount(ORE, 2)), two.inputs());
        assertEquals(List.of(amount(INGOT, 2)), two.outputs());
        assertEquals(new BigInteger("800000000"), two.energyPerBatch());
    }

    @Test void documentedTwoStageExampleNormalizesAndNeverExposesIntermediateFluid() {
        var a = recipe("a", List.of(amount(ORE, 1)), List.of(amount(FLUID, 250)));
        var b = recipe("b", List.of(amount(FLUID, 100), amount(DUST, 1)), List.of(amount(CRYSTAL, 1)));
        FactoryLayout layout = FactoryLayout.empty(5)
                .withCell(pos(0, 2), marker(ORE, 8, RIGHT)).withCell(pos(1, 2), machine(2))
                .withCell(pos(2, 2), marker(FLUID, 2000, RIGHT)).withCell(pos(3, 2), machine(20))
                .withCell(pos(4, 2), new FactoryCell.Arrow())
                .withCell(pos(3, 1), marker(DUST, 20, DOWN));
        var result = FactoryCompiler.compile(layout, 1, (p, m, incoming) -> p.x() == 1 ? a : b);
        assertTrue(result.successful(), () -> "" + result.diagnostic());
        var compiled = result.factory();
        assertEquals(Map.of(ORE, BigInteger.valueOf(8), DUST, BigInteger.valueOf(20)), FactoryCompiler.map(compiled.inputs()));
        assertEquals(List.of(amount(CRYSTAL, 20)), compiled.outputs());
        assertEquals(22, compiled.machineCount());
        assertEquals(new BigInteger("440000000"), compiled.energyPerTick());
        assertEquals(new BigInteger("8800000000"), compiled.energyPerBatch());
    }

    @Test void multiOutputFlowsThroughOneUnmarkedRouteOrDirectOutput() {
        var recipe = recipe("multiple", List.of(amount(ORE, 1)), List.of(amount(INGOT, 2), amount(DUST, 3)));
        assertEquals(recipe.outputs(), success(line(1, 1), recipe).outputRows().get(0));
        var direct = FactoryLayout.empty(3).withCell(pos(0, 0), marker(ORE, 1, RIGHT))
                .withCell(pos(1, 0), new FactoryCell.Arrow()).withCell(pos(2, 0), machine(1));
        assertEquals(recipe.outputs(), success(direct, recipe).outputRows().get(0));
    }

    @Test void everyMachineDirectionCarriesAllProductsIncludingFluids() {
        var recipe = recipe("multiple", List.of(amount(ORE, 1)), List.of(amount(INGOT, 2), amount(FLUID, 1000)));
        for (GridDirection direction : GridDirection.values()) {
            var layout = FactoryLayout.empty(5)
                    .withCell(pos(2, 2), new FactoryCell.Machine(machine(1).stack(), null, direction))
                    .withCell(pos(2 - direction.deltaX(), 2 - direction.deltaY()), marker(ORE, 1, direction));
            int row = switch (direction) { case RIGHT -> 2; case DOWN -> 3; case LEFT, UP -> 1; };
            int start = direction == RIGHT ? 3 : direction == LEFT ? 1 : 2;
            if (direction == LEFT) layout = layout.withCell(pos(1, 2), new FactoryCell.Arrow(UP));
            for (int x = start; x < 5; x++) layout = layout.withCell(pos(x, row), new FactoryCell.Arrow(RIGHT));
            var compiled = success(layout, recipe);
            assertEquals(recipe.inputs(), compiled.inputs());
            assertEquals(Map.of(row, recipe.outputs()), compiled.outputRows());
        }
    }

    @Test void machineDoesNotEmitIntoAnAdjacentIndependentRoute() {
        var layout = FactoryLayout.empty(3)
                .withCell(pos(0, 0), machine(1)).withCell(pos(0, 1), marker(ORE, 1, UP))
                .withCell(pos(1, 0), new FactoryCell.Arrow(RIGHT)).withCell(pos(2, 0), new FactoryCell.Arrow(RIGHT))
                .withCell(pos(0, 2), marker(ORE, 1, RIGHT)).withCell(pos(1, 2), new FactoryCell.Arrow(UP))
                .withCell(pos(1, 1), machine(1)).withCell(pos(2, 1), new FactoryCell.Arrow(RIGHT));
        assertEquals(Map.of(0, SMELT.outputs(), 1, SMELT.outputs()), success(layout, SMELT).outputRows());
    }

    @Test void adjacentMachinesCanChainThroughTheirOutputDirections() {
        var layout = FactoryLayout.empty(3).withCell(pos(0, 0), marker(ORE, 1, RIGHT))
                .withCell(pos(1, 0), machine(1)).withCell(pos(2, 0), machine(1));
        var second = recipe("second", List.of(amount(INGOT, 1)), List.of(amount(CRYSTAL, 1)));
        var result = FactoryCompiler.compile(layout, 1, (p, m, incoming) -> p.x() == 1 ? SMELT : second);
        assertTrue(result.successful(), () -> "" + result.diagnostic());
        assertEquals(SMELT.inputs(), result.factory().inputs());
        assertEquals(second.outputs(), result.factory().outputs());
    }

    @Test void unusedAdjacentOutputCannotRescueWrongMachineDirection() {
        var layout = line(1, 1).withCell(pos(1, 0), new FactoryCell.Machine(machine(1).stack(), null, DOWN));
        assertFailure(layout, SMELT, ROUTE_DEAD_END, pos(1, 0));
        assertNotEquals(FactoryFingerprint.of(line(1, 1), Map.of(pos(1, 0), SMELT)),
                FactoryFingerprint.of(layout, Map.of(pos(1, 0), SMELT)));
    }

    @Test void markersNeverSupplementOrDiscardUpstreamAmounts() {
        for (int count : new int[]{1, 3}) {
            var layout = line(2, 2).withCell(pos(2, 0), marker(INGOT, count, RIGHT));
            var error = compile(layout, SMELT).diagnostic();
            assertEquals(count == 1 ? EXCESS_RESOURCE : MISSING_INPUT, error.code());
            assertEquals(pos(2, 0), error.position().orElseThrow());
            assertEquals(BigInteger.valueOf(count), error.expected().orElseThrow());
            assertEquals(BigInteger.TWO, error.actual().orElseThrow());
        }
    }

    @Test void filterCannotDiscardAnotherResource() {
        var recipe = recipe("multiple", List.of(amount(ORE, 1)), List.of(amount(INGOT, 1), amount(DUST, 1)));
        var error = compile(line(1, 1).withCell(pos(2, 0), marker(INGOT, 1, RIGHT)), recipe).diagnostic();
        assertEquals(EXCESS_RESOURCE, error.code());
        assertEquals(DUST, error.resource().orElseThrow());
        assertEquals(BigInteger.ZERO, error.expected().orElseThrow());
    }

    @Test void disconnectedMatchingResourceCannotPayAnotherMachinesDeficit() {
        FactoryLayout layout = line(1, 1).withCell(pos(0, 2), marker(ORE, 3, RIGHT))
                .withCell(pos(1, 2), machine(2)).withCell(pos(2, 2), new FactoryCell.Arrow());
        assertFailure(layout, recipe("two_ores", List.of(amount(ORE, 2)), SMELT.outputs()), MISSING_INPUT, pos(1, 0));
    }

    @Test void twoExternalEntriesMergeWithoutDuplication() {
        FactoryLayout layout = FactoryLayout.empty(3).withCell(pos(0, 1), marker(ORE, 1, RIGHT))
                .withCell(pos(1, 0), marker(ORE, 1, DOWN)).withCell(pos(1, 1), machine(2))
                .withCell(pos(2, 1), new FactoryCell.Arrow());
        assertEquals(List.of(amount(ORE, 2)), success(layout, SMELT).inputs());
    }

    @Test void equalInputAndOutputStillRequiresStartupMaterial() {
        var recipe = recipe("same", List.of(amount(ORE, 1)), List.of(amount(ORE, 2)));
        var compiled = success(line(1, 1), recipe);
        assertEquals(List.of(amount(ORE, 1)), compiled.inputs());
        assertEquals(List.of(amount(ORE, 2)), compiled.outputs());
    }

    @Test void rawInputBypassIsRejectedEvenAfterMergingWithProcessedSameResource() {
        var layout = FactoryLayout.empty(3).withCell(pos(0, 1), marker(ORE, 1, RIGHT))
                .withCell(pos(1, 1), machine(1)).withCell(pos(2, 1), new FactoryCell.Arrow())
                .withCell(pos(2, 0), marker(INGOT, 1, DOWN));
        assertFailure(layout, SMELT, BYPASS_ROUTE, pos(3, 1));
    }

    @Test void emptyArrowNoMachineAndEveryDeadEndAreRejected() {
        assertEquals(NO_MACHINES, compile(FactoryLayout.empty(3), SMELT).diagnostic().code());
        assertFailure(line(1, 1).withCell(pos(2, 2), new FactoryCell.Arrow()), SMELT, EMPTY_ROUTE, pos(2, 2));
        assertFailure(line(1, 1).withCell(pos(2, 0), FactoryCell.Empty.INSTANCE), SMELT, ROUTE_DEAD_END, pos(1, 0));
        assertFailure(line(1, 1).withCell(pos(2, 0), new FactoryCell.Arrow(UP)), SMELT, ROUTE_DEAD_END, pos(2, 0));
        assertFailure(line(1, 1).withCell(pos(2, 0), new FactoryCell.Arrow(LEFT)), SMELT, ROUTE_DEAD_END, pos(1, 0));
    }

    @Test void opposingOutputsNeverConnectAsInputs() {
        var layout = FactoryLayout.empty(3).withCell(pos(0, 0), machine(1))
                .withCell(pos(1, 0), new FactoryCell.Machine(machine(1).stack(), null, LEFT));
        assertFailure(layout, SMELT, ROUTE_DEAD_END, pos(0, 0));
        layout = line(1, 1).withCell(pos(0, 2), new FactoryCell.Arrow(RIGHT))
                .withCell(pos(1, 2), new FactoryCell.Arrow(LEFT));
        assertFailure(layout, SMELT, ROUTE_DEAD_END, pos(0, 2));
    }

    @Test void cycleDiagnosticNamesAnActualCycleCell() {
        var layout = FactoryLayout.empty(3).withCell(pos(0, 0), machine(1))
                .withCell(pos(1, 0), new FactoryCell.Arrow(DOWN)).withCell(pos(1, 1), new FactoryCell.Arrow(LEFT))
                .withCell(pos(0, 1), new FactoryCell.Arrow(UP));
        var diagnostic = compile(layout, SMELT).diagnostic();
        assertEquals(ROUTE_CYCLE, diagnostic.code());
        assertTrue(diagnostic.position().orElseThrow().x() < 2);
    }

    @Test void exactItemAndFluidComponentsCannotSubstitute() {
        CompoundTag tag = new CompoundTag(); tag.putString("test:variant", "different");
        for (ResourceKey key : List.of(ORE, FLUID)) {
            ResourceKey other = new ResourceKey(key.kind(), key.registryId(), tag);
            var recipe = recipe("components", List.of(amount(key, 1)), List.of(amount(INGOT, 1)));
            assertFailure(line(1, 1).withCell(pos(0, 0), marker(other, 1, RIGHT)), recipe, MISSING_INPUT, pos(1, 0));
        }
    }

    @Test void limitsRejectOversizedBoundaryAndArithmeticOverflowWithoutTruncating() {
        var layout = line(1, 1).withCell(pos(0, 0), new FactoryCell.Marker(new ResourceAmount(ORE,
                FactoryLimits.MAX_INVENTORY_AMOUNT.add(BigInteger.ONE)), RIGHT));
        assertFailure(layout, SMELT, QUANTITY_LIMIT_EXCEEDED, pos(0, 0));
        var recipe = recipe("overflow", List.of(amount(ORE, 1)),
                List.of(new ResourceAmount(INGOT, FactoryLimits.MAX_ENCODED_QUANTITY)));
        assertFailure(line(2, 2), recipe, QUANTITY_LIMIT_EXCEEDED, pos(1, 0));
    }

    @Test void allGridSizesHandleBottomAndRightBoundaries() {
        for (int n : FactoryLayout.VALID_SIZES) {
            FactoryLayout layout = FactoryLayout.empty(n).withCell(pos(n - 3, n - 1), marker(ORE, 1, RIGHT))
                    .withCell(pos(n - 2, n - 1), machine(1)).withCell(pos(n - 1, n - 1), new FactoryCell.Arrow());
            assertEquals(List.of(amount(INGOT, 1)), success(layout, SMELT).outputs());
        }
    }

    @Test void fingerprintAndCacheIdentityTrackLayoutRecipeAndGeneration() {
        var compiled = success(line(1, 1), SMELT);
        assertTrue(compiled.matches(line(1, 1), 1));
        assertFalse(compiled.matches(line(1, 1), 2));
        assertFalse(compiled.matches(line(2, 2), 1));
        assertEquals(compiled.fingerprint(), success(line(1, 1), SMELT).fingerprint());
        assertNotEquals(compiled.fingerprint(), success(line(2, 2), SMELT).fingerprint());
        assertNotEquals(compiled.fingerprint(), success(line(1, 1), recipe("changed", SMELT.inputs(), SMELT.outputs())).fingerprint());
        assertThrows(UnsupportedOperationException.class, () -> compiled.outputs().clear());
        assertThrows(UnsupportedOperationException.class, () -> compiled.outputRows().get(0).clear());
        assertThrows(UnsupportedOperationException.class, () -> compiled.recipes().clear());
    }

    @Test void outputsOfSeparateMachinesMergeOnlyAtTheirSharedArrow() {
        var layout = FactoryLayout.empty(5).withCell(pos(0, 1), marker(ORE, 1, RIGHT)).withCell(pos(1, 1), machine(1))
                .withCell(pos(2, 1), new FactoryCell.Arrow(RIGHT)).withCell(pos(3, 1), new FactoryCell.Arrow(DOWN))
                .withCell(pos(0, 3), marker(ORE, 2, RIGHT)).withCell(pos(1, 3), machine(2))
                .withCell(pos(2, 3), new FactoryCell.Arrow(RIGHT)).withCell(pos(3, 3), new FactoryCell.Arrow(UP))
                .withCell(pos(3, 2), new FactoryCell.Arrow(RIGHT)).withCell(pos(4, 2), new FactoryCell.Arrow(RIGHT));
        var compiled = success(layout, SMELT);
        assertEquals(List.of(amount(ORE, 3)), compiled.inputs());
        assertEquals(Map.of(2, List.of(amount(INGOT, 3))), compiled.outputRows());
        assertEquals(3, compiled.machineCount());
    }

    @Test void boundaryResourceLimitAppliesToTheUnionOfAllInputAndOutputRows() {
        for (int rows : new int[]{4, 5}) {
            FactoryLayout layout = FactoryLayout.empty(11);
            for (int row = 0; row < rows; row++) layout = layout.withCell(pos(9, row * 2), marker(ORE, 1, RIGHT))
                    .withCell(pos(10, row * 2), machine(1));
            var result = FactoryCompiler.compile(layout, 1, (p, m, input) -> recipe("many", List.of(amount(ORE, 1)),
                    java.util.stream.IntStream.range(0, 63).mapToObj(index -> amount(item("output_" + p.y() + "_" + index), 1)).toList()));
            if (rows == 4) {
                assertTrue(result.successful(), () -> "" + result.diagnostic());
                assertEquals(252, result.factory().outputs().size());
            } else assertEquals(RESOURCE_LIMIT_EXCEEDED, result.diagnostic().code());
        }
    }

    @Test void compilationPreviewRejectsContradictoryOutputRowsOrFailureAssets() {
        var factory = success(line(1, 1), SMELT);
        assertThrows(IllegalArgumentException.class, () -> new CompilationPreview(CompilationPreview.Status.READY,
                factory.inputs(), factory.outputs(), List.of(), Map.of(0, List.of(amount(INGOT, 2))), 1, null));
        assertThrows(IllegalArgumentException.class, () -> new CompilationPreview(CompilationPreview.Status.FAILED,
                factory.inputs(), List.of(), List.of(), Map.of(), 0, new FactoryDiagnostic(NO_RECIPE)));
        var preview = CompilationPreview.from(CompilationResult.success(factory));
        assertThrows(UnsupportedOperationException.class, () -> preview.outputRows().clear());
    }

    static FactoryLayout line(int input, int count) {
        return FactoryLayout.empty(3).withCell(pos(0, 0), marker(ORE, input, RIGHT))
                .withCell(pos(1, 0), machine(count)).withCell(pos(2, 0), new FactoryCell.Arrow());
    }
    static FactoryCell.Machine machine(int count) { return new FactoryCell.Machine(new MachineStack(MACHINE, count)); }
    static FactoryCell.Marker marker(ResourceKey key, int amount, GridDirection direction) { return new FactoryCell.Marker(amount(key, amount), direction); }
    static GridPosition pos(int x, int y) { return new GridPosition(x, y); }
    static ResourceKey item(String name) { return new ResourceKey(ResourceKind.ITEM, "test:" + name); }
    static ResourceAmount amount(ResourceKey key, int amount) { return new ResourceAmount(key, BigInteger.valueOf(amount)); }
    static ResolvedRecipe recipe(String id, List<ResourceAmount> inputs, List<ResourceAmount> outputs) {
        return new ResolvedRecipe(new RecipeReference("test:adapter", "test:" + id, 1, 1,
                List.of(new RecipeReference.Binding(0, ORE))), inputs, outputs);
    }
    static CompilationResult compile(FactoryLayout layout, ResolvedRecipe recipe) { return FactoryCompiler.compile(layout, 1, (p, m, r) -> recipe); }
    static CompiledFactory success(FactoryLayout layout, ResolvedRecipe recipe) {
        var result = compile(layout, recipe); assertTrue(result.successful(), () -> "" + result.diagnostic()); return result.factory();
    }
    static void assertFailure(FactoryLayout layout, ResolvedRecipe recipe, FactoryDiagnostic.Code code, GridPosition position) {
        var result = compile(layout, recipe); assertFalse(result.successful()); assertEquals(code, result.diagnostic().code());
        assertEquals(position, result.diagnostic().position().orElseThrow());
    }
}
