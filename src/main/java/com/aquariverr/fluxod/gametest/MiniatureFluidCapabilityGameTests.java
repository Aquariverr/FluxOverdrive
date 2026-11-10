package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.miniature.storage.FactoryFluidResources;
import com.aquariverr.fluxod.miniature.storage.FactoryState;
import com.aquariverr.fluxod.miniature.storage.FactoryStorage;
import com.aquariverr.fluxod.miniature.storage.ResourceInventory;
import com.aquariverr.fluxod.miniature.compile.CompiledFactory;
import com.aquariverr.fluxod.miniature.compile.CompilationResult;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.recipe.MiniatureRecipeCatalog;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.material.Fluids;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.math.BigInteger;
import java.util.Map;
import java.util.List;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureFluidCapabilityGameTests {
    private static final BlockPos TEST_POS = new BlockPos(2, 2, 2);

    @GameTest(template = "empty")
    public static void simulateFillDoesNotChangeFluidInventory(GameTestHelper helper) {
        TileMiniatureUniverse tile = fixture(helper, FactoryStorage.empty());
        ResourceKey water = FactoryFluidResources.keyOf(new FluidStack(Fluids.WATER, 1), helper.getLevel().registryAccess());
        allowInput(helper, tile, water);
        IFluidHandler handler = capability(helper, tile);
        CompoundTag before = tile.factoryState().save();
        int accepted = handler.fill(new FluidStack(Fluids.WATER, 2_000), IFluidHandler.FluidAction.SIMULATE);
        helper.assertTrue(accepted == 2_000, "Fluid simulation did not report the available input capacity");
        helper.assertTrue(before.equals(tile.factoryState().save()), "Simulated fluid fill mutated the factory state");
        helper.assertTrue(handler.fill(new FluidStack(Fluids.WATER, 1_500), IFluidHandler.FluidAction.EXECUTE) == 1_500,
                "Fluid execution did not accept the exact transfer amount");
        helper.assertTrue(tile.factoryState().storage().orElseThrow().inputs().amount(water).equals(BigInteger.valueOf(1_500)),
                "Fluid input was not stored under its stable resource identity");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fluidOutputCrossesIntegerBridgeWithoutLoss(GameTestHelper helper) {
        ResourceKey water = FactoryFluidResources.keyOf(new FluidStack(Fluids.WATER, 1), helper.getLevel().registryAccess());
        BigInteger initial = BigInteger.valueOf(Integer.MAX_VALUE).multiply(BigInteger.TWO).add(BigInteger.valueOf(9));
        TileMiniatureUniverse tile = fixture(helper, FactoryStorage.empty().withOutputs(
                new ResourceInventory(Map.of(water, initial))));
        IFluidHandler handler = capability(helper, tile);
        helper.assertTrue(handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.SIMULATE).getAmount() == Integer.MAX_VALUE,
                "Simulated fluid drain did not expose the int-sized bridge limit");
        helper.assertTrue(tile.factoryState().storage().orElseThrow().outputs().amount(water).equals(initial),
                "Simulated fluid drain mutated the factory state");
        helper.assertTrue(handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE).getAmount() == Integer.MAX_VALUE,
                "First fluid bridge transfer was truncated");
        helper.assertTrue(handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE).getAmount() == Integer.MAX_VALUE,
                "Second fluid bridge transfer was truncated");
        helper.assertTrue(handler.drain(9, IFluidHandler.FluidAction.EXECUTE).getAmount() == 9
                        && tile.factoryState().storage().orElseThrow().outputs().amount(water).signum() == 0,
                "Fluid output lost quantity across repeated int-sized transfers");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fluidRequiresExactCompiledInputAndInvalidationStopsAutomation(GameTestHelper helper) {
        TileMiniatureUniverse tile = fixture(helper, FactoryStorage.empty());
        IFluidHandler handler = capability(helper, tile);
        FluidStack water = new FluidStack(Fluids.WATER, 1_000);
        CompoundTag before = tile.factoryState().save();
        helper.assertTrue(handler.fill(water, IFluidHandler.FluidAction.SIMULATE) == 0
                && handler.fill(water, IFluidHandler.FluidAction.EXECUTE) == 0, "Uncompiled factory accepted fluid");
        CompoundTag component = new CompoundTag();
        component.putString("variant", "exact");
        FluidStack variant = water.copy();
        variant.set(DataComponents.CUSTOM_DATA, CustomData.of(component));
        ResourceKey key = FactoryFluidResources.keyOf(variant, helper.getLevel().registryAccess());
        allowInput(helper, tile, key);
        helper.assertTrue(handler.fill(water, IFluidHandler.FluidAction.EXECUTE) == 0
                && before.equals(tile.factoryState().save()), "Different components satisfied a compiled fluid input");
        helper.assertTrue(handler.fill(variant, IFluidHandler.FluidAction.EXECUTE) == 1_000, "Exact variant rejected");
        helper.assertTrue(handler.drain(1_000, IFluidHandler.FluidAction.EXECUTE).isEmpty(), "Capability extracted input fluid");
        MiniatureRecipeCatalog.get(helper.getLevel().getServer()).invalidate();
        helper.assertTrue(handler.fill(variant, IFluidHandler.FluidAction.SIMULATE) == 0, "Reload retained stale automation filter");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullResourceTableRejectsSimulateAndExecuteEqually(GameTestHelper helper) {
        var full = new java.util.LinkedHashMap<ResourceKey, BigInteger>();
        helper.getLevel().registryAccess().lookupOrThrow(net.minecraft.core.registries.Registries.ITEM).listElements()
                .filter(holder -> holder.value() != net.minecraft.world.item.Items.AIR)
                .limit(FactoryLimits.MAX_BOUNDARY_RESOURCES)
                .forEach(holder -> full.put(new ResourceKey(ResourceKind.ITEM, holder.key().location().toString()), BigInteger.ONE));
        TileMiniatureUniverse tile = fixture(helper, FactoryStorage.empty().withInputs(new ResourceInventory(full)));
        FluidStack water = new FluidStack(Fluids.WATER, 1_000);
        allowInput(helper, tile, FactoryFluidResources.keyOf(water, helper.getLevel().registryAccess()));
        IFluidHandler handler = capability(helper, tile);
        CompoundTag before = tile.factoryState().save();
        helper.assertTrue(handler.fill(water, IFluidHandler.FluidAction.SIMULATE) == 0
                && handler.fill(water, IFluidHandler.FluidAction.EXECUTE) == 0
                && before.equals(tile.factoryState().save()), "Full resource table reported a phantom transfer");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fluidCapacityAndRemovedHandlerNeverLoseResources(GameTestHelper helper) {
        ResourceKey water = FactoryFluidResources.keyOf(new FluidStack(Fluids.WATER, 1), helper.getLevel().registryAccess());
        TileMiniatureUniverse tile = fixture(helper, FactoryStorage.empty().withInputs(new ResourceInventory(
                Map.of(water, FactoryLimits.MAX_INVENTORY_AMOUNT.subtract(BigInteger.valueOf(7))))));
        allowInput(helper, tile, water);
        IFluidHandler handler = capability(helper, tile);
        helper.assertTrue(handler.fill(new FluidStack(Fluids.WATER, Integer.MAX_VALUE), IFluidHandler.FluidAction.SIMULATE) == 7
                && handler.fill(new FluidStack(Fluids.WATER, Integer.MAX_VALUE), IFluidHandler.FluidAction.EXECUTE) == 7
                && tile.factoryState().storage().orElseThrow().inputs().amount(water).equals(FactoryLimits.MAX_INVENTORY_AMOUNT),
                "Capacity overflow or inaccurate partial transfer");
        tile.setRemoved();
        helper.assertTrue(handler.fill(new FluidStack(Fluids.WATER, 1), IFluidHandler.FluidAction.SIMULATE) == 0
                && handler.getFluidInTank(0).isEmpty() && handler.drain(1, IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                "Stale capability accessed removed factory");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void componentFluidBridgePreservesBothInventoriesAcrossReload(GameTestHelper helper) {
        FluidStack variant = new FluidStack(Fluids.WATER, Integer.MAX_VALUE);
        CompoundTag components = new CompoundTag();
        components.putString("variant", "persisted_s08");
        variant.set(DataComponents.CUSTOM_DATA, CustomData.of(components));
        ResourceKey key = FactoryFluidResources.keyOf(variant, helper.getLevel().registryAccess());
        BigInteger twoTransfers = BigInteger.valueOf(Integer.MAX_VALUE).multiply(BigInteger.TWO);
        BigInteger total = twoTransfers.add(BigInteger.valueOf(9));
        TileMiniatureUniverse tile = fixture(helper, FactoryStorage.empty()
                .withOutputs(new ResourceInventory(Map.of(key, total))));
        allowInput(helper, tile, key);
        IFluidHandler handler = capability(helper, tile);
        for (int transfer = 0; transfer < 2; transfer++) {
            helper.assertTrue(handler.fill(variant, IFluidHandler.FluidAction.EXECUTE) == Integer.MAX_VALUE,
                    "Component fluid input did not accept an int-sized transfer");
        }
        helper.assertTrue(tile.factoryState().storage().orElseThrow().inputs().amount(key).equals(twoTransfers)
                        && handler.getFluidInTank(0).getAmount() == Integer.MAX_VALUE,
                "Fluid input overflowed or exposed a negative tank amount");
        CompoundTag before = tile.factoryState().save();
        helper.assertTrue(handler.drain(new FluidStack(Fluids.WATER, Integer.MAX_VALUE),
                        IFluidHandler.FluidAction.EXECUTE).isEmpty()
                        && handler.drain(variant, IFluidHandler.FluidAction.SIMULATE).getAmount() == Integer.MAX_VALUE
                        && before.equals(tile.factoryState().save()),
                "Drain ignored component identity or simulation changed either inventory");
        FluidStack first = handler.drain(variant, IFluidHandler.FluidAction.EXECUTE);
        helper.assertTrue(first.getAmount() == Integer.MAX_VALUE
                        && FactoryFluidResources.keyOf(first, helper.getLevel().registryAccess()).equals(key),
                "Typed fluid extraction changed the saved component identity");

        CompoundTag saved = tile.saveWithFullMetadata(helper.getLevel().registryAccess());
        tile.loadAdditional(saved, helper.getLevel().registryAccess());
        handler = capability(helper, tile);
        helper.assertTrue(handler.fill(variant, IFluidHandler.FluidAction.EXECUTE) == 0,
                "Reload restored an executable input contract without recompilation");
        helper.assertTrue(handler.drain(variant, IFluidHandler.FluidAction.EXECUTE).getAmount() == Integer.MAX_VALUE
                        && handler.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE).getAmount() == 9
                        && handler.drain(1, IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                "Mid-transfer reload lost or duplicated a component fluid output");
        helper.assertTrue(tile.factoryState().storage().orElseThrow().inputs().amount(key).equals(twoTransfers)
                        && tile.factoryState().storage().orElseThrow().outputs().amounts().isEmpty(),
                "Output extraction changed the matching input inventory");
        helper.succeed();
    }

    /** Capability tests supply an immutable input contract; native recipe validation has separate real-recipe tests. */
    public static void allowInput(GameTestHelper helper, TileMiniatureUniverse tile, ResourceKey key) {
        var state = tile.factoryState();
        long generation = MiniatureRecipeCatalog.get(helper.getLevel().getServer()).generation();
        var compiled = new CompiledFactory(state.storage().orElseThrow().layout(), "0".repeat(64), generation,
                List.of(new ResourceAmount(key, BigInteger.ONE)), List.of(), List.of(), Map.of(), Map.of());
        helper.assertTrue(tile.applyCompilation(state, CompilationResult.success(compiled), generation), "Could not publish input contract");
    }

    private static IFluidHandler capability(GameTestHelper helper, TileMiniatureUniverse tile) {
        IFluidHandler handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, tile.getBlockPos(), Direction.NORTH);
        helper.assertTrue(handler != null, "Missing registered NeoForge fluid capability");
        return handler;
    }

    public static TileMiniatureUniverse fixture(GameTestHelper helper, FactoryStorage storage) {
        helper.setBlock(TEST_POS, RegistryBlocks.MINIATURE_UNIVERSE.get().defaultBlockState());
        TileMiniatureUniverse tile = (TileMiniatureUniverse) helper.getLevel().getBlockEntity(helper.absolutePos(TEST_POS));
        helper.assertTrue(tile != null, "Missing miniature fluid fixture");
        CompoundTag wrapper = new CompoundTag();
        wrapper.put(TileMiniatureUniverse.STATE_TAG, FactoryState.of(storage, helper.getLevel().registryAccess()).save());
        tile.loadAdditional(wrapper, helper.getLevel().registryAccess());
        return tile;
    }

    private MiniatureFluidCapabilityGameTests() {}
}
