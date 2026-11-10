package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.Config;
import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.QuantumEntanglementControllerBlock;
import com.aquariverr.fluxod.block.QuantumEntanglementResourcePortBlock;
import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementController;
import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementResourcePort;
import com.aquariverr.fluxod.entanglement.*;
import com.aquariverr.fluxod.integration.mekanism.QuantumCoolingChemicalIntegration;
import com.aquariverr.fluxod.miniature.compile.CompiledFactory;
import com.aquariverr.fluxod.miniature.compile.CompilationResult;
import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.recipe.MiniatureRecipeCatalog;
import com.aquariverr.fluxod.miniature.storage.*;
import com.aquariverr.fluxod.multiblock.QuantumEntanglementAssembler;
import com.aquariverr.fluxod.multiblock.QuantumEntanglementStructure;
import com.aquariverr.fluxod.multiblock.QuantumEntanglementTemplate;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemHandlerHelper;
import net.minecraft.world.level.material.Fluids;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

/** Real formed battery/facility routes; capability contracts are independent of recipe-family validation. */
@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureEntanglementResourceGameTests {
    private static final BlockPos ACCESS_POS = new BlockPos(37, 3, 2);
    private static final int OUTPUT = FactoryLimits.MAX_BOUNDARY_RESOURCES;
    private static final BigInteger FUNDS = BigInteger.valueOf(800_000_000L);

    @GameTest(template = "empty", batch = "miniature_entanglement")
    public static void miniatureDirectoryDeclaresBothDirectionsWithoutChangingExistingAdapters(GameTestHelper helper) {
        Fixture fixture = fixture(helper, FactoryStorage.empty(), FUNDS);
        QuantumPluginEndpoint endpoint = fixture.endpoint();
        helper.assertTrue(endpoint.key().adapterId().equals(QuantumPluginAdapters.MINIATURE_UNIVERSE_ID)
                        && endpoint.ringTier() == 3 && endpoint.remoteMenu(),
                "The real Tier 3 directory did not identify the miniature adapter and remote menu");
        for (EntanglementResourceDirection direction : EntanglementResourceDirection.values()) {
            helper.assertTrue(endpoint.supports(EntanglementResourceKind.ITEM, direction)
                            && endpoint.supports(EntanglementResourceKind.FLUID, direction)
                            && endpoint.supports(EntanglementResourceKind.CHEMICAL, direction)
                            == QuantumCoolingChemicalIntegration.isAvailable(),
                    "Miniature endpoint advertised the wrong resource directions or optional chemical capability");
        }
        helper.assertTrue(fixture.battery().getEntanglementEndpoints().stream().filter(QuantumPluginAdapters::isConstraint).count() == 3,
                "Registering the miniature adapter changed the three constraint endpoints");
        assertLegacyAdapter(helper, RegistryBlocks.QUANTUM_SOLID_COOLING_PLUGIN.get(), QuantumPluginAdapters.SOLID_COOLING_ID,
                EntanglementResourceKind.ITEM.mask(), true);
        assertLegacyAdapter(helper, RegistryBlocks.QUANTUM_ADVANCED_COOLING_PLUGIN.get(), QuantumPluginAdapters.ADVANCED_COOLING_ID,
                EntanglementResourceKind.FLUID.mask() | (QuantumCoolingChemicalIntegration.isAvailable()
                        ? EntanglementResourceKind.CHEMICAL.mask() : 0), true);
        assertLegacyAdapter(helper, RegistryBlocks.QUANTUM_STABILITY_FEEDBACK_PLUGIN.get(), QuantumPluginAdapters.STABILITY_FEEDBACK_ID, 0, true);
        assertLegacyAdapter(helper, RegistryBlocks.QUANTUM_TEMPERATURE_FEEDBACK_PLUGIN.get(), QuantumPluginAdapters.TEMPERATURE_FEEDBACK_ID, 0, true);
        assertLegacyAdapter(helper, RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE.get(), QuantumPluginAdapters.CONSTRAINT_ID, 0, false);
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_entanglement")
    public static void itemProxiesShareLocalAssetsAndHonorSimulationDirectionAndSingleCallLimit(GameTestHelper helper) {
        ResourceKey ore = key(helper, Items.IRON_ORE);
        ResourceKey ingot = key(helper, Items.IRON_INGOT);
        Fixture fixture = fixture(helper, FactoryStorage.empty().withOutputs(new ResourceInventory(Map.of(ingot, BigInteger.valueOf(20)))), FUNDS);
        allowInputs(helper, fixture.tile(), ore);
        IItemHandler input = items(helper, fixture.input());
        IItemHandler output = items(helper, fixture.output());
        int previous = Config.entanglementMaxItemTransfer;
        Config.entanglementMaxItemTransfer = 7;
        try {
            CompoundTag before = fixture.tile().factoryState().save();
            helper.assertTrue(input.insertItem(0, new ItemStack(Items.IRON_ORE, 20), true).getCount() == 13
                            && output.extractItem(OUTPUT, 20, true).getCount() == 7
                            && before.equals(fixture.tile().factoryState().save()),
                    "Remote item simulation changed assets or ignored the single-call quota");
            helper.assertTrue(output.insertItem(0, new ItemStack(Items.IRON_ORE, 20), false).getCount() == 20
                            && input.extractItem(OUTPUT, 20, false).isEmpty()
                            && output.extractItem(0, 20, false).isEmpty()
                            && input.insertItem(0, new ItemStack(Items.DIAMOND), false).getCount() == 1,
                    "An item port bypassed direction, committed-output, or exact-input restrictions");
            helper.assertTrue(input.insertItem(0, new ItemStack(Items.IRON_ORE, 20), false).getCount() == 13,
                    "Remote item insertion did not commit its bounded amount");
            helper.assertTrue(fixture.tile().itemHandler().insertItem(0, new ItemStack(Items.IRON_ORE, 2), false).isEmpty()
                            && input.getStackInSlot(0).getCount() == 9
                            && fixture.tile().factoryState().storage().orElseThrow().inputs().amount(ore).equals(BigInteger.valueOf(9)),
                    "Local and remote item inputs do not share the same inventory");
            helper.assertTrue(output.extractItem(OUTPUT, 20, false).getCount() == 7
                            && fixture.tile().itemHandler().extractItem(OUTPUT, 2, false).getCount() == 2
                            && fixture.tile().factoryState().storage().orElseThrow().outputs().amount(ingot).equals(BigInteger.valueOf(11)),
                    "Local and remote item outputs do not share committed inventory");
            helper.assertTrue(fixture.battery().getStoredEnergy().equals(FUNDS), "Resource access charged production energy");
        } finally {
            Config.entanglementMaxItemTransfer = previous;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_entanglement")
    public static void fluidProxiesPreserveComponentsAndShareBoundedLocalTransfers(GameTestHelper helper) {
        FluidStack water = new FluidStack(Fluids.WATER, 2_000);
        CompoundTag variant = new CompoundTag();
        variant.putString("variant", "s10_component_fluid");
        water.set(DataComponents.CUSTOM_DATA, CustomData.of(variant));
        ResourceKey key = FactoryFluidResources.keyOf(water, helper.getLevel().registryAccess());
        Fixture fixture = fixture(helper, FactoryStorage.empty().withOutputs(new ResourceInventory(Map.of(key, BigInteger.valueOf(5_000)))), FUNDS);
        allowInputs(helper, fixture.tile(), key);
        IFluidHandler input = fluids(helper, fixture.input());
        IFluidHandler output = fluids(helper, fixture.output());
        int previous = Config.entanglementMaxFluidTransfer;
        Config.entanglementMaxFluidTransfer = 700;
        try {
            CompoundTag before = fixture.tile().factoryState().save();
            helper.assertTrue(input.fill(water, IFluidHandler.FluidAction.SIMULATE) == 700
                            && output.drain(water, IFluidHandler.FluidAction.SIMULATE).getAmount() == 700
                            && before.equals(fixture.tile().factoryState().save()),
                    "Fluid simulation changed assets or exceeded the single-call quota");
            helper.assertTrue(input.fill(new FluidStack(Fluids.WATER, 2_000), IFluidHandler.FluidAction.EXECUTE) == 0
                            && output.drain(new FluidStack(Fluids.WATER, 2_000), IFluidHandler.FluidAction.EXECUTE).isEmpty()
                            && output.fill(water, IFluidHandler.FluidAction.EXECUTE) == 0
                            && input.drain(2_000, IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                    "Fluid proxy ignored component identity or port direction");
            helper.assertTrue(input.fill(water, IFluidHandler.FluidAction.EXECUTE) == 700
                            && fixture.tile().fluidHandler().fill(water.copyWithAmount(100), IFluidHandler.FluidAction.EXECUTE) == 100
                            && fixture.tile().factoryState().storage().orElseThrow().inputs().amount(key).equals(BigInteger.valueOf(800)),
                    "Local and remote fluid inputs used different stores");
            FluidStack extracted = output.drain(water, IFluidHandler.FluidAction.EXECUTE);
            helper.assertTrue(extracted.getAmount() == 700
                            && FactoryFluidResources.keyOf(extracted, helper.getLevel().registryAccess()).equals(key)
                            && output.drain(Integer.MAX_VALUE, IFluidHandler.FluidAction.EXECUTE).getAmount() == 700
                            && fixture.tile().factoryState().storage().orElseThrow().outputs().amount(key).equals(BigInteger.valueOf(3_600)),
                    "Remote fluid extraction changed identity or bypassed the quota");
            helper.assertTrue(fixture.battery().getStoredEnergy().equals(FUNDS), "Fluid access charged production energy");
        } finally {
            Config.entanglementMaxFluidTransfer = previous;
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_entanglement")
    public static void nativeChemicalProxiesShareInventoryAndRevokeHeldCapabilities(GameTestHelper helper) {
        if (QuantumCoolingChemicalIntegration.isAvailable()) {
            com.aquariverr.fluxod.integration.mekanism.MiniatureEntanglementChemicalVerification.transfers(helper);
        } else {
            Fixture fixture = fixture(helper, FactoryStorage.empty(), FUNDS);
            helper.assertTrue(fixture.tile().chemicalHandler() == null && fixture.input().getChemicalCapability(null) == null
                            && !fixture.endpoint().supports(EntanglementResourceKind.CHEMICAL, EntanglementResourceDirection.INPUT),
                    "Absent Mekanism still exposed chemical endpoints");
        }
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_entanglement")
    public static void heldProxiesRevokeOnUnbindAutomationDisableAndPluginRemoval(GameTestHelper helper) {
        ResourceKey ore = key(helper, Items.IRON_ORE);
        ResourceKey water = FactoryFluidResources.keyOf(new FluidStack(Fluids.WATER, 1), helper.getLevel().registryAccess());
        Fixture fixture = fixture(helper, FactoryStorage.empty().withOutputs(new ResourceInventory(Map.of(
                key(helper, Items.IRON_INGOT), BigInteger.TEN, water, BigInteger.valueOf(2_000)))), FUNDS);
        allowInputs(helper, fixture.tile(), ore, water);
        IItemHandler input = items(helper, fixture.input());
        IItemHandler output = items(helper, fixture.output());
        IFluidHandler fluidInput = fluids(helper, fixture.input());
        IFluidHandler fluidOutput = fluids(helper, fixture.output());
        CompoundTag before = fixture.tile().factoryState().save();
        var player = MiniatureResourceEditingGameTests.playerFixture(helper, fixture.tile()).player();
        fixture.access().unbindBattery(player);
        assertUnavailable(helper, input, output, fluidInput, fluidOutput);
        configureAccess(helper, fixture.access(), fixture.battery(), fixture.endpoint());
        helper.assertTrue(input.isItemValid(0, new ItemStack(Items.IRON_ORE)), "Rebinding did not restore the real proxy route");
        CompoundTag disabled = fixture.access().saveWithFullMetadata(helper.getLevel().registryAccess());
        disabled.putBoolean("AutomaticInputEnabled", false);
        disabled.putBoolean("AutomaticOutputEnabled", false);
        fixture.access().loadAdditional(disabled, helper.getLevel().registryAccess());
        fixture.access().validateStructure();
        assertUnavailable(helper, input, output, fluidInput, fluidOutput);
        configureAccess(helper, fixture.access(), fixture.battery(), fixture.endpoint());
        TileMiniatureUniverse original = fixture.tile();
        helper.getLevel().setBlockAndUpdate(original.getBlockPos(), Blocks.AIR.defaultBlockState());
        assertUnavailable(helper, input, output, fluidInput, fluidOutput);
        helper.assertTrue(original.isRemoved() && before.equals(original.factoryState().save())
                        && fixture.battery().getStoredEnergy().equals(FUNDS),
                "Revoked proxies changed original assets or charged energy");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_entanglement")
    public static void aggregateItemSimulationAndActivePullRespectOneSharedCapacity(GameTestHelper helper) {
        ResourceKey ore = key(helper, Items.IRON_ORE);
        BigInteger initial = FactoryLimits.MAX_INVENTORY_AMOUNT.subtract(BigInteger.valueOf(7));
        Fixture fixture = fixture(helper, FactoryStorage.empty().withInputs(new ResourceInventory(Map.of(ore, initial))), FUNDS);
        allowInputs(helper, fixture.tile(), ore);
        IItemHandler remote = items(helper, fixture.input());
        CompoundTag before = fixture.tile().factoryState().save();
        helper.assertTrue(ItemHandlerHelper.insertItem(remote, new ItemStack(Items.IRON_ORE, 20), true).getCount() == 13
                        && before.equals(fixture.tile().factoryState().save()),
                "Aggregate item simulation counted the same free capacity through multiple input slots");
        helper.assertTrue(ItemHandlerHelper.insertItem(remote, new ItemStack(Items.IRON_ORE, 20), false).getCount() == 13
                        && fixture.tile().factoryState().storage().orElseThrow().inputs().amount(ore).equals(FactoryLimits.MAX_INVENTORY_AMOUNT),
                "Aggregate item execution disagreed with the shared-capacity simulation");
        loadStorage(helper, fixture.tile(), FactoryStorage.empty().withInputs(new ResourceInventory(Map.of(ore, initial))));
        allowInputs(helper, fixture.tile(), ore);
        BlockPos chest = fixture.input().getBlockPos().relative(Direction.EAST);
        helper.getLevel().setBlockAndUpdate(chest, Blocks.CHEST.defaultBlockState());
        IItemHandler source = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, chest, Direction.WEST);
        helper.assertTrue(source != null && source.insertItem(0, new ItemStack(Items.IRON_ORE, 20), false).isEmpty(),
                "Could not stock the outward-facing active input source");
        fixture.input().serverTick();
        helper.assertTrue(source.getStackInSlot(0).getCount() == 13
                        && fixture.tile().factoryState().storage().orElseThrow().inputs().amount(ore).equals(FactoryLimits.MAX_INVENTORY_AMOUNT),
                "Active remote input lost its unaccepted remainder at the shared inventory capacity");
        helper.assertTrue(fixture.battery().getStoredEnergy().equals(FUNDS), "Active resource transfer charged production energy");
        helper.succeed();
    }

    @GameTest(template = "empty", batch = "miniature_entanglement")
    public static void multipleResourcePortsCannotAdvanceOrChargeTheBatteryOwnedBatch(GameTestHelper helper) {
        var runtime = MiniatureRuntimeGameTests.fixture(helper, line(helper), FUNDS);
        QuantumPluginEndpoint endpoint = endpoint(helper, runtime);
        TileQuantumEntanglementController access = buildAccess(helper, runtime.battery(), endpoint);
        Fixture fixture = wrap(helper, runtime, access, endpoint);
        runtime.tick();
        var started = runtime.tile().factoryState().storage().orElseThrow().batch();
        helper.assertTrue(started != null && started.paidTicks() == 1, "Real battery did not start one paid batch");
        BigInteger energy = runtime.battery().getStoredEnergy();
        for (int repeat = 0; repeat < 4; repeat++) {
            items(helper, fixture.output()).extractItem(OUTPUT, 1, true);
            items(helper, fixture.secondOutput()).extractItem(OUTPUT, 1, true);
            items(helper, fixture.input()).insertItem(0, new ItemStack(Items.IRON_ORE), true);
            fixture.input().serverTick();
            fixture.output().serverTick();
            fixture.secondOutput().serverTick();
            access.serverTick();
            runtime.tick();
        }
        var after = runtime.tile().factoryState().storage().orElseThrow().batch();
        helper.assertTrue(started.equals(after) && runtime.battery().getStoredEnergy().equals(energy)
                        && energy.equals(FUNDS.subtract(started.energyPerTick()))
                        && items(helper, fixture.output()).extractItem(OUTPUT, 64, true).isEmpty(),
                "Resource access duplicated the batch, advanced paid progress, charged twice, or exposed reserved outputs");
        helper.succeed();
    }

    public static Fixture fixture(GameTestHelper helper, FactoryStorage storage, BigInteger energy) {
        var runtime = MiniatureRuntimeGameTests.fixture(helper, line(helper), energy);
        loadStorage(helper, runtime.tile(), storage);
        QuantumPluginEndpoint endpoint = endpoint(helper, runtime);
        return wrap(helper, runtime, buildAccess(helper, runtime.battery(), endpoint), endpoint);
    }

    public static TileQuantumEntanglementController buildAccess(GameTestHelper helper, TileQuantumBatteryController battery,
                                                               QuantumPluginEndpoint endpoint) {
        var state = RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get().defaultBlockState()
                .setValue(QuantumEntanglementControllerBlock.FACING, Direction.NORTH);
        helper.setBlock(ACCESS_POS, state);
        for (var cell : QuantumEntanglementTemplate.cells()) {
            var part = QuantumEntanglementAssembler.expectedState(cell, state);
            if (cell.offset().equals(new QuantumEntanglementTemplate.Offset(1, 0, 0))) {
                part = RegistryBlocks.QUANTUM_ENTANGLEMENT_INPUT_PORT.get().defaultBlockState()
                        .setValue(QuantumEntanglementResourcePortBlock.FACING, Direction.EAST);
            } else if (cell.offset().equals(new QuantumEntanglementTemplate.Offset(-1, 0, 0))
                    || cell.offset().equals(new QuantumEntanglementTemplate.Offset(-1, 0, -1))) {
                part = RegistryBlocks.QUANTUM_ENTANGLEMENT_OUTPUT_PORT.get().defaultBlockState()
                        .setValue(QuantumEntanglementResourcePortBlock.FACING, Direction.WEST);
            }
            helper.setBlock(QuantumEntanglementStructure.worldPos(ACCESS_POS, Direction.NORTH, cell.offset()), part);
        }
        var access = (TileQuantumEntanglementController) helper.getLevel().getBlockEntity(helper.absolutePos(ACCESS_POS));
        helper.assertTrue(access != null, "Remote access controller was not placed");
        configureAccess(helper, access, battery, endpoint);
        return access;
    }

    public static void configureAccess(GameTestHelper helper, TileQuantumEntanglementController access,
                                       TileQuantumBatteryController battery, QuantumPluginEndpoint endpoint) {
        CompoundTag data = new CompoundTag();
        data.putBoolean("Formed", true);
        data.putLong("TargetPos", battery.getBlockPos().asLong());
        data.putString("TargetDimension", battery.getLevel().dimension().location().toString());
        data.putUUID("TargetId", battery.getBatteryId());
        data.put("SelectedEndpoint", endpoint.key().save());
        data.putBoolean("AutomaticInputEnabled", true);
        data.putBoolean("AutomaticOutputEnabled", true);
        access.loadAdditional(data, helper.getLevel().registryAccess());
        access.validateStructure();
        helper.assertTrue(access.isFormed(), "Real entanglement facility did not form");
    }

    public static void allowInputs(GameTestHelper helper, TileMiniatureUniverse tile, ResourceKey... keys) {
        var state = tile.factoryState();
        long generation = MiniatureRecipeCatalog.get(helper.getLevel().getServer()).generation();
        var compiled = new CompiledFactory(state.storage().orElseThrow().layout(), "0".repeat(64), generation,
                Arrays.stream(keys).map(key -> new ResourceAmount(key, BigInteger.ONE)).toList(), List.of(), List.of(), Map.of(), Map.of());
        helper.assertTrue(tile.applyCompilation(state, CompilationResult.success(compiled), generation), "Could not publish input contract");
    }

    private static void loadStorage(GameTestHelper helper, TileMiniatureUniverse tile, FactoryStorage storage) {
        CompoundTag wrapper = new CompoundTag();
        wrapper.put(TileMiniatureUniverse.STATE_TAG, FactoryState.of(storage, helper.getLevel().registryAccess()).save());
        tile.loadAdditional(wrapper, helper.getLevel().registryAccess());
    }

    private static FactoryStorage line(GameTestHelper helper) {
        return FactoryStorage.empty().insertMachine(new GridPosition(1, 0), new ItemStack(Items.FURNACE), helper.getLevel().registryAccess()).storage()
                .withConfiguration(new GridPosition(0, 0), new FactoryCell.Marker(new ResourceAmount(key(helper, Items.IRON_ORE), BigInteger.ONE), GridDirection.RIGHT))
                .withConfiguration(new GridPosition(2, 0), new FactoryCell.Arrow())
                .insertInput(new ItemStack(Items.IRON_ORE, 2), helper.getLevel().registryAccess()).storage();
    }

    private static QuantumPluginEndpoint endpoint(GameTestHelper helper, MiniatureRuntimeGameTests.Fixture runtime) {
        return runtime.battery().getEntanglementEndpoints().stream().filter(endpoint -> endpoint.absolutePos().equals(runtime.pluginPos()))
                .findFirst().orElseThrow(() -> new AssertionError("The miniature resource endpoint was not formed"));
    }

    private static Fixture wrap(GameTestHelper helper, MiniatureRuntimeGameTests.Fixture runtime,
                                TileQuantumEntanglementController access, QuantumPluginEndpoint endpoint) {
        return new Fixture(runtime, access, endpoint, port(helper, 1, 0), port(helper, -1, 0), port(helper, -1, -1));
    }

    private static TileQuantumEntanglementResourcePort port(GameTestHelper helper, int x, int z) {
        BlockPos pos = QuantumEntanglementStructure.worldPos(ACCESS_POS, Direction.NORTH, new QuantumEntanglementTemplate.Offset(x, 0, z));
        return (TileQuantumEntanglementResourcePort) helper.getLevel().getBlockEntity(helper.absolutePos(pos));
    }

    private static IItemHandler items(GameTestHelper helper, TileQuantumEntanglementResourcePort port) {
        IItemHandler handler = helper.getLevel().getCapability(Capabilities.ItemHandler.BLOCK, port.getBlockPos(), Direction.NORTH);
        helper.assertTrue(handler != null && handler.getSlots() > 0, "Selected resource port has no live item route");
        return handler;
    }

    private static IFluidHandler fluids(GameTestHelper helper, TileQuantumEntanglementResourcePort port) {
        IFluidHandler handler = helper.getLevel().getCapability(Capabilities.FluidHandler.BLOCK, port.getBlockPos(), Direction.NORTH);
        helper.assertTrue(handler != null && handler.getTanks() > 0, "Selected resource port has no live fluid route");
        return handler;
    }

    private static void assertUnavailable(GameTestHelper helper, IItemHandler input, IItemHandler output,
                                          IFluidHandler fluidInput, IFluidHandler fluidOutput) {
        helper.assertTrue(input.getSlots() == 0 && output.getSlots() == 0
                        && input.insertItem(0, new ItemStack(Items.IRON_ORE), false).getCount() == 1
                        && output.extractItem(OUTPUT, 10, false).isEmpty()
                        && fluidInput.fill(new FluidStack(Fluids.WATER, 1_000), IFluidHandler.FluidAction.EXECUTE) == 0
                        && fluidOutput.drain(1_000, IFluidHandler.FluidAction.EXECUTE).isEmpty(),
                "A held resource capability still accessed a revoked or removed endpoint");
    }

    private static ResourceKey key(GameTestHelper helper, net.minecraft.world.item.Item item) {
        return FactoryItemResources.keyOf(new ItemStack(item), helper.getLevel().registryAccess());
    }

    private static void assertLegacyAdapter(GameTestHelper helper, Block block, net.minecraft.resources.ResourceLocation id,
                                            int inputMask, boolean remoteMenu) {
        BlockPos pos = new BlockPos(1, 2, 1);
        helper.setBlock(pos, block.defaultBlockState());
        var description = QuantumPluginAdapters.describe(BlockPos.ZERO, helper.absolutePos(pos), 1,
                block.defaultBlockState(), helper.getLevel().getBlockEntity(helper.absolutePos(pos)));
        helper.assertTrue(description.key().adapterId().equals(id) && description.inputResources() == inputMask
                        && description.outputResources() == 0 && description.remoteMenu() == remoteMenu,
                "Existing plugin adapter contract changed: " + id);
    }

    public record Fixture(MiniatureRuntimeGameTests.Fixture runtime, TileQuantumEntanglementController access,
                          QuantumPluginEndpoint endpoint, TileQuantumEntanglementResourcePort input,
                          TileQuantumEntanglementResourcePort output, TileQuantumEntanglementResourcePort secondOutput) {
        public TileMiniatureUniverse tile() { return runtime.tile(); }
        public TileQuantumBatteryController battery() { return runtime.battery(); }
    }

    private MiniatureEntanglementResourceGameTests() {}
}
