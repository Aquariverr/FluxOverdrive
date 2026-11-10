package com.aquariverr.fluxod.miniature.compile;

import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.recipe.ResolvedRecipe;
import org.junit.jupiter.api.Test;
import java.math.BigInteger;
import java.util.*;
import static com.aquariverr.fluxod.miniature.compile.FactoryCompilerTest.*;
import static com.aquariverr.fluxod.miniature.model.GridDirection.*;
import static com.aquariverr.fluxod.miniature.model.FactoryDiagnostic.Code.*;
import static org.junit.jupiter.api.Assertions.*;

class FactoryBalancerTest {
    @Test void adjustsSourceAndIntermediateMarkersInOneRevisionAndIsIdempotent() {
        var layout = line(99, 64).withCell(pos(2, 0), marker(INGOT, 987, RIGHT));
        var balanced = balanced(layout, (p, m, inputs) -> List.of(SMELT));
        assertEquals(List.of(amount(ORE, 1)), balanced.compilation().factory().inputs());
        assertEquals(List.of(amount(INGOT, 1)), balanced.compilation().factory().outputs());
        assertEquals(BigInteger.ONE, ((FactoryCell.Marker) balanced.layout().cellAt(pos(2, 0))).resource().amount());
        assertEquals(64, balanced.layout().machineCount());
        assertEquals(layout.revision() + 1, balanced.layout().revision());
        assertEquals(SMELT.reference(), ((FactoryCell.Machine) balanced.layout().cellAt(pos(1, 0))).recipe());
        assertSame(balanced.layout(), balanced(balanced.layout(), (p, m, inputs) -> List.of(SMELT)).layout());
    }

    @Test void coupledNativeRatiosUseWholeRecipesWithoutMachineOrNormalizationMultipliers() {
        var first = recipe("first", List.of(amount(ORE, 3)), List.of(amount(FLUID, 250)));
        var second = recipe("second", List.of(amount(FLUID, 100), amount(DUST, 2)), List.of(amount(CRYSTAL, 1)));
        var layout = FactoryLayout.empty(5).withCell(pos(0, 2), marker(ORE, 1, RIGHT)).withCell(pos(1, 2), machine(64))
                .withCell(pos(2, 2), marker(FLUID, 1000, RIGHT)).withCell(pos(3, 2), machine(1))
                .withCell(pos(4, 2), new FactoryCell.Arrow()).withCell(pos(3, 1), marker(DUST, 1, DOWN));
        var result = balanced(layout, (p, m, inputs) -> List.of(p.x() == 1 ? first : second));
        assertEquals(Map.of(ORE, BigInteger.valueOf(6), DUST, BigInteger.TEN), FactoryCompiler.map(result.compilation().factory().inputs()));
        assertEquals(List.of(amount(CRYSTAL, 5)), result.compilation().factory().outputs());
        assertEquals(BigInteger.valueOf(500), ((FactoryCell.Marker) result.layout().cellAt(pos(2, 2))).resource().amount());
        assertEquals(Map.of(pos(1, 2), BigInteger.TWO, pos(3, 2), BigInteger.valueOf(5)), result.compilation().factory().operations());
    }

    @Test void everySplitBranchBecomesIntegralAndManualWeightsStayUnchanged() {
        var arrow = new FactoryCell.Arrow(Map.of(RIGHT, 1, UP, 3));
        var layout = line(1, 2).withCell(pos(0, 0), FactoryCell.Empty.INSTANCE).withCell(pos(1, 0), FactoryCell.Empty.INSTANCE)
                .withCell(pos(0, 1), marker(ORE, 1, RIGHT)).withCell(pos(1, 1), machine(2)).withCell(pos(2, 1), arrow);
        var result = balanced(layout, (p, m, inputs) -> List.of(SMELT));
        assertEquals(List.of(amount(ORE, 4)), result.compilation().factory().inputs());
        assertEquals(Map.of(0, List.of(amount(INGOT, 3)), 1, List.of(amount(INGOT, 1))), result.compilation().factory().outputRows());
        assertEquals(arrow, result.layout().cellAt(pos(2, 1)));
    }

    @Test void multipleSourcesOfOneResourceStayPositiveAndDisconnectedLinesScaleIndependently() {
        var layout = line(44, 1).withCell(pos(1, 1), marker(ORE, 55, UP))
                .withCell(pos(0, 2), marker(DUST, 100, RIGHT)).withCell(pos(1, 2), machine(1)).withCell(pos(2, 2), new FactoryCell.Arrow());
        var other = recipe("other", List.of(amount(DUST, 3)), List.of(amount(CRYSTAL, 1)));
        var result = balanced(layout, (p, m, inputs) -> List.of(p.y() == 0 ? SMELT : other));
        assertEquals(Map.of(ORE, BigInteger.TWO, DUST, BigInteger.valueOf(3)), FactoryCompiler.map(result.compilation().factory().inputs()));
        for (var position : List.of(pos(0, 0), pos(1, 1)))
            assertEquals(BigInteger.ONE, ((FactoryCell.Marker) result.layout().cellAt(position)).resource().amount());
    }

    @Test void incompatibleCoproductRatiosAreRejectedWithoutChangingTheLayout() {
        var layout = chain();
        var first = recipe("coproduct", SMELT.inputs(), List.of(amount(INGOT, 1), amount(DUST, 1)));
        var second = recipe("consume", List.of(amount(INGOT, 1), amount(DUST, 2)), List.of(amount(CRYSTAL, 1)));
        var result = FactoryBalancer.balance(layout, 1, (p, m, inputs) -> List.of(p.x() == 1 ? first : second));
        assertFalse(result.successful()); assertEquals(BALANCE_IMPOSSIBLE, result.compilation().diagnostic().code());
        assertSame(layout, result.layout());
    }

    @Test void backtracksNativeChoicesButNeverReplacesAnExplicitSelection() {
        var first = recipe("first", SMELT.inputs(), List.of(amount(INGOT, 1), amount(DUST, 1)));
        var alternative = recipe("alternative", SMELT.inputs(), List.of(amount(INGOT, 1), amount(DUST, 2)));
        var second = recipe("consume", List.of(amount(INGOT, 1), amount(DUST, 2)), List.of(amount(CRYSTAL, 1)));
        FactoryBalancer.RecipeCandidates choices = (p, m, inputs) -> p.x() == 1 ? List.of(first, alternative) : List.of(second);
        var result = balanced(chain(), choices);
        assertEquals(alternative.reference(), ((FactoryCell.Machine) result.layout().cellAt(pos(1, 0))).recipe());
        var selected = chain().withCell(pos(1, 0), new FactoryCell.Machine(machine(1).stack(), first.reference()));
        assertFalse(FactoryBalancer.balance(selected, 1, choices).successful());
        assertEquals(first.reference(), ((FactoryCell.Machine) selected.cellAt(pos(1, 0))).recipe());
        var missingThenValid = balanced(chain(), (p, m, inputs) -> p.x() == 1 ? List.of(first, SMELT)
                : inputs.equals(List.of(INGOT)) ? List.of(recipe("end", List.of(amount(INGOT, 1)), List.of(amount(CRYSTAL, 1)))) : List.of());
        assertEquals(SMELT.reference(), ((FactoryCell.Machine) missingThenValid.layout().cellAt(pos(1, 0))).recipe());
    }

    @Test void returningCatalystsAreReservedAfterBalancing() {
        var recipe = new ResolvedRecipe(SMELT.reference(), List.of(amount(ORE, 2), amount(CRYSTAL, 1)),
                List.of(amount(INGOT, 3), amount(CRYSTAL, 1)), List.of(amount(CRYSTAL, 1)));
        var result = balanced(line(1, 7).withCell(pos(1, 1), marker(CRYSTAL, 100, UP)), (p, m, inputs) -> List.of(recipe));
        assertEquals(List.of(amount(CRYSTAL, 1)), result.compilation().factory().catalysts());
        assertEquals(List.of(amount(ORE, 2)), result.compilation().factory().inputs());
        assertEquals(List.of(amount(INGOT, 3)), result.compilation().factory().outputs());
    }

    @Test void candidateSearchStopsAtItsBudgetWithoutApplyingPartialChoices() {
        var choices = new ArrayList<ResolvedRecipe>();
        for (int i = 0; i < FactoryBalancer.MAX_ATTEMPTS; i++)
            choices.add(recipe("wrong_" + i, SMELT.inputs(), List.of(amount(INGOT, 1), amount(DUST, 1))));
        choices.add(recipe("valid", SMELT.inputs(), List.of(amount(INGOT, 1), amount(DUST, 2))));
        var consumer = recipe("consume", List.of(amount(INGOT, 1), amount(DUST, 2)), List.of(amount(CRYSTAL, 1)));
        var original = chain(); int[] calls = {0};
        var result = FactoryBalancer.balance(original, 1, (p, m, inputs) -> {
            if (p.x() == 1) { calls[0]++; return choices; }
            return List.of(consumer);
        });
        assertEquals(FactoryBalancer.MAX_ATTEMPTS, calls[0]);
        assertEquals(BALANCE_SEARCH_LIMIT, result.compilation().diagnostic().code());
        assertSame(original, result.layout());
    }

    @Test void deadEndsRawBypassAndQuantityLimitsCannotBeRepairedByDiscardingResources() {
        var disconnected = line(1, 1).withCell(pos(2, 0), FactoryCell.Empty.INSTANCE);
        assertEquals(ROUTE_DEAD_END, FactoryBalancer.balance(disconnected, 1, (p, m, inputs) -> List.of(SMELT)).compilation().diagnostic().code());
        var bypass = line(1, 1).withCell(pos(2, 1), marker(INGOT, 10, UP));
        assertEquals(BYPASS_ROUTE, FactoryBalancer.balance(bypass, 1, (p, m, inputs) -> List.of(SMELT)).compilation().diagnostic().code());
        var huge = recipe("huge", List.of(new ResourceAmount(ORE, FactoryLimits.MAX_ENCODED_QUANTITY)), SMELT.outputs());
        var failure = FactoryBalancer.balance(line(1, 1), 1, (p, m, inputs) -> List.of(huge));
        assertEquals(QUANTITY_LIMIT_EXCEEDED, failure.compilation().diagnostic().code());
        assertDoesNotThrow(() -> CompilationPreview.from(failure.compilation()));
    }

    @Test void exactComponentsCannotBeSubstitutedByTheSolver() {
        var tag = new net.minecraft.nbt.CompoundTag(); tag.putString("test:name", "different");
        var other = new ResourceKey(ResourceKind.ITEM, ORE.registryId(), tag);
        assertFalse(FactoryBalancer.balance(line(1, 1).withCell(pos(0, 0), marker(other, 77, RIGHT)), 1,
                (p, m, inputs) -> List.of(SMELT)).successful());
    }

    private static FactoryLayout chain() {
        return FactoryLayout.empty(3).withCell(pos(0, 0), marker(ORE, 999, RIGHT)).withCell(pos(1, 0), machine(1)).withCell(pos(2, 0), machine(1));
    }
    private static FactoryBalancer.Result balanced(FactoryLayout layout, FactoryBalancer.RecipeCandidates resolver) {
        var result = FactoryBalancer.balance(layout, 1, resolver);
        assertTrue(result.successful(), () -> "" + result.compilation().diagnostic()); return result;
    }
}
