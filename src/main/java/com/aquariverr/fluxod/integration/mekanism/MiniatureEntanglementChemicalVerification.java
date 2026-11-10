package com.aquariverr.fluxod.integration.mekanism;

import com.aquariverr.fluxod.Config;
import com.aquariverr.fluxod.gametest.MiniatureEntanglementResourceGameTests;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.miniature.storage.FactoryStorage;
import com.aquariverr.fluxod.miniature.storage.ResourceInventory;
import mekanism.api.Action;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.chemical.IChemicalHandler;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.BlockCapability;

import java.math.BigInteger;
import java.util.Map;

/** Loaded only after the mod-independent S10 GameTest verifies that Mekanism is installed. */
public final class MiniatureEntanglementChemicalVerification {
    private static final int OUTPUT = FactoryLimits.MAX_BOUNDARY_RESOURCES;
    private static final ResourceKey OXYGEN = new ResourceKey(ResourceKind.CHEMICAL, "mekanism:oxygen");
    private static final BlockCapability<IChemicalHandler, Direction> CAPABILITY = BlockCapability.createSided(
            ResourceLocation.fromNamespaceAndPath("mekanism", "chemical_handler"), IChemicalHandler.class);

    public static void transfers(GameTestHelper helper) {
        BigInteger funds = BigInteger.valueOf(800_000_000L);
        BigInteger initial = FactoryLimits.MAX_INVENTORY_AMOUNT.subtract(BigInteger.valueOf(707));
        var fixture = MiniatureEntanglementResourceGameTests.fixture(helper, FactoryStorage.empty()
                .withInputs(new ResourceInventory(Map.of(OXYGEN, initial)))
                .withOutputs(new ResourceInventory(Map.of(OXYGEN, BigInteger.valueOf(5_000)))), funds);
        MiniatureEntanglementResourceGameTests.allowInputs(helper, fixture.tile(), OXYGEN);
        IChemicalHandler input = helper.getLevel().getCapability(CAPABILITY, fixture.input().getBlockPos(), Direction.NORTH);
        IChemicalHandler output = helper.getLevel().getCapability(CAPABILITY, fixture.output().getBlockPos(), Direction.NORTH);
        IChemicalHandler local = helper.getLevel().getCapability(CAPABILITY, fixture.tile().getBlockPos(), Direction.NORTH);
        helper.assertTrue(input != null && output != null && local != null,
                "Native chemical capability did not connect the real miniature endpoint and both ports");
        ChemicalStack oxygen = MekanismMiniatureChemicalHandler.chemicalOf(OXYGEN, helper.getLevel().registryAccess()).copyWithAmount(2_000);
        long previous = Config.entanglementMaxChemicalTransfer;
        Config.entanglementMaxChemicalTransfer = 700;
        try {
            CompoundTag before = fixture.tile().factoryState().save();
            helper.assertTrue(input.insertChemical(oxygen, Action.SIMULATE).getAmount() == 1_300
                            && input.insertChemical(0, oxygen, Action.SIMULATE).getAmount() == 1_300
                            && output.extractChemical(Long.MAX_VALUE, Action.SIMULATE).getAmount() == 700
                            && output.extractChemical(OUTPUT, Long.MAX_VALUE, Action.SIMULATE).getAmount() == 700
                            && before.equals(fixture.tile().factoryState().save()),
                    "Chemical simulation changed inventory or exceeded the per-call quota");
            helper.assertTrue(output.insertChemical(oxygen, Action.EXECUTE).getAmount() == 2_000
                            && input.extractChemical(Long.MAX_VALUE, Action.EXECUTE).isEmpty()
                            && output.extractChemical(0, Long.MAX_VALUE, Action.EXECUTE).isEmpty(),
                    "Chemical proxy bypassed port direction or extracted input chemicals");
            output.setChemicalInTank(OUTPUT, oxygen);
            helper.assertTrue(before.equals(fixture.tile().factoryState().save()), "Direct chemical assignment bypassed the transfer contract");
            helper.assertTrue(input.insertChemical(oxygen, Action.EXECUTE).getAmount() == 1_300
                            && local.getChemicalInTank(0).getAmount() == Long.MAX_VALUE
                            && fixture.tile().factoryState().storage().orElseThrow().inputs().amount(OXYGEN)
                            .equals(FactoryLimits.MAX_INVENTORY_AMOUNT.subtract(BigInteger.valueOf(7))),
                    "Remote chemical input did not update the original BigInteger inventory");
            CompoundTag nearlyFull = fixture.tile().factoryState().save();
            helper.assertTrue(input.insertChemical(oxygen, Action.SIMULATE).getAmount() == 1_993
                            && nearlyFull.equals(fixture.tile().factoryState().save())
                            && input.insertChemical(oxygen, Action.EXECUTE).getAmount() == 1_993
                            && fixture.tile().factoryState().storage().orElseThrow().inputs().amount(OXYGEN).equals(FactoryLimits.MAX_INVENTORY_AMOUNT),
                    "Aggregate chemical insertion counted shared capacity more than once");
            helper.assertTrue(output.extractChemical(oxygen, Action.EXECUTE).getAmount() == 700
                            && output.extractChemical(OUTPUT, Long.MAX_VALUE, Action.EXECUTE).getAmount() == 700
                            && local.extractChemical(OUTPUT, 100, Action.EXECUTE).getAmount() == 100
                            && fixture.tile().factoryState().storage().orElseThrow().outputs().amount(OXYGEN).equals(BigInteger.valueOf(3_500)),
                    "Remote and local chemical outputs did not share one committed inventory");
            CompoundTag committed = fixture.tile().factoryState().save();
            fixture.access().loadAdditional(new CompoundTag(), helper.getLevel().registryAccess());
            fixture.access().validateStructure();
            assertUnavailable(helper, input, output, oxygen);
            MiniatureEntanglementResourceGameTests.configureAccess(helper, fixture.access(), fixture.battery(), fixture.endpoint());
            helper.assertTrue(output.extractChemical(1, Action.SIMULATE).getAmount() == 1,
                    "Rebinding did not restore the held native chemical proxy");
            var original = fixture.tile();
            helper.getLevel().setBlockAndUpdate(original.getBlockPos(), Blocks.AIR.defaultBlockState());
            assertUnavailable(helper, input, output, oxygen);
            helper.assertTrue(committed.equals(original.factoryState().save()) && fixture.battery().getStoredEnergy().equals(funds),
                    "Revoked chemical access mutated original assets or charged production energy");
        } finally {
            Config.entanglementMaxChemicalTransfer = previous;
        }
    }

    private static void assertUnavailable(GameTestHelper helper, IChemicalHandler input, IChemicalHandler output,
                                          ChemicalStack oxygen) {
        helper.assertTrue(input.getChemicalTanks() == 0 && output.getChemicalTanks() == 0
                        && input.insertChemical(oxygen, Action.EXECUTE).getAmount() == oxygen.getAmount()
                        && output.extractChemical(1, Action.EXECUTE).isEmpty(),
                "Held native chemical proxy accessed an unbound or removed factory");
    }

    private MiniatureEntanglementChemicalVerification() {}
}
