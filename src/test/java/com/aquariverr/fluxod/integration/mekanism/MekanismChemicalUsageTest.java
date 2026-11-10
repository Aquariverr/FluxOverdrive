package com.aquariverr.fluxod.integration.mekanism;

import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.recipe.ResolvedRecipe;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MekanismChemicalUsageTest {
    private static final ResourceKey ITEM = new ResourceKey(ResourceKind.ITEM, "mekanism:fluorite_gem");
    private static final ResourceKey ACID = new ResourceKey(ResourceKind.CHEMICAL, "mekanism:sulfuric_acid");
    private static final ResourceKey PRODUCT = new ResourceKey(ResourceKind.CHEMICAL, "mekanism:hydrofluoric_acid");

    @Test
    void eightChemicalUpgradeConsumptionPrecedesNormalizationAndMachineCount() {
        var nativeRecipe = recipe(BigInteger.valueOf(9));
        var batch = MekanismChemicalUsage.forBatch(nativeRecipe,
                MekanismChemicalUsage.DISSOLUTION_TICKS / MekanismChemicalUsage.FULL_CHEMICAL_UPGRADE_DIVISOR);
        assertEquals(nativeRecipe.reference(), batch.reference());
        assertEquals(nativeRecipe.outputs(), batch.outputs());
        assertEquals(List.of(amount(ITEM, 1), amount(ACID, 90)), batch.inputs());
        assertEquals(BigInteger.TEN, batch.normalization());
        assertEquals(List.of(amount(ITEM, 20), amount(ACID, 1_800)), batch.inputsForMachines(2));
        assertEquals(List.of(amount(PRODUCT, 2_000)), batch.outputsForMachines(2));
    }

    @Test
    void fixedUsageAndSingleTickRecipesRemainUnchanged() {
        var recipe = recipe(BigInteger.valueOf(9));
        assertSame(recipe, MekanismChemicalUsage.forBatch(recipe, 1));
    }

    @Test
    void nativeLongAmountsDoNotOverflowWhenMultipliedByDuration() {
        BigInteger perTick = BigInteger.valueOf(Long.MAX_VALUE);
        var batch = MekanismChemicalUsage.forBatch(recipe(perTick), Integer.MAX_VALUE);
        assertEquals(perTick.multiply(BigInteger.valueOf(Integer.MAX_VALUE)), batch.inputs().get(1).amount());
    }

    @Test
    void invalidDurationsAndEncodedQuantityOverflowAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> MekanismChemicalUsage.forBatch(recipe(BigInteger.ONE), 0));
        assertThrows(IllegalArgumentException.class, () -> MekanismChemicalUsage.forBatch(recipe(BigInteger.ONE), -1));
        assertThrows(ModelValidationException.class,
                () -> MekanismChemicalUsage.forBatch(recipe(FactoryLimits.MAX_ENCODED_QUANTITY), 2));
    }

    private static ResolvedRecipe recipe(BigInteger perTick) {
        return new ResolvedRecipe(new RecipeReference("flux_overdrive:mekanism_dissolving", "test:acid", 2, 1,
                List.of(new RecipeReference.Binding(0, ITEM), new RecipeReference.Binding(1, ACID))),
                List.of(amount(ITEM, 1), new ResourceAmount(ACID, perTick)), List.of(amount(PRODUCT, 100)));
    }

    private static ResourceAmount amount(ResourceKey key, long amount) { return new ResourceAmount(key, BigInteger.valueOf(amount)); }
}
