package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.QuantumEntanglementControllerBlock;
import com.aquariverr.fluxod.block.QuantumEntanglementResourcePortBlock;
import com.aquariverr.fluxod.block.QuantumEntanglementRedstonePortBlock;
import com.aquariverr.fluxod.block.entity.QuantumFeedbackCurve;
import com.aquariverr.fluxod.block.entity.QuantumFeedbackCurveCodec;
import com.aquariverr.fluxod.block.entity.QuantumFeedbackMetric;
import com.aquariverr.fluxod.block.entity.TileQuantumAdvancedCoolingPlugin;
import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementController;
import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementResourcePort;
import com.aquariverr.fluxod.block.entity.TileQuantumFeedbackPlugin;
import com.aquariverr.fluxod.entanglement.EntanglementResourceDirection;
import com.aquariverr.fluxod.entanglement.QuantumPluginEndpoint;
import com.aquariverr.fluxod.multiblock.QuantumBatteryAssembler;
import com.aquariverr.fluxod.multiblock.QuantumBatteryStructure;
import com.aquariverr.fluxod.multiblock.QuantumEntanglementAssembler;
import com.aquariverr.fluxod.multiblock.QuantumEntanglementStructure;
import com.aquariverr.fluxod.multiblock.QuantumEntanglementTemplate;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.Fluids;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.capabilities.Capabilities;
import net.neoforged.neoforge.fluids.FluidStack;
import net.neoforged.neoforge.fluids.capability.IFluidHandler;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;
import net.neoforged.neoforge.items.IItemHandler;
import sonar.fluxnetworks.api.FluxConstants;

import java.util.UUID;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class QuantumEntanglementGameTests {

    private static final BlockPos INPUT_POS = new BlockPos(1, 1, 1);
    private static final BlockPos OUTPUT_POS = new BlockPos(2, 1, 1);
    private static final BlockPos BATTERY_POS = new BlockPos(12, 12, 12);
    private static final BlockPos ENTANGLEMENT_POS = new BlockPos(28, 3, 12);
    private static final BlockPos FRONT_TANK_POS = new BlockPos(30, 3, 12);
    private static final BlockPos SIDE_TANK_POS = new BlockPos(29, 3, 11);
    private static final ResourceKey<Registry<Object>> MEKANISM_CHEMICAL_REGISTRY =
            ResourceKey.createRegistryKey(ResourceLocation.fromNamespaceAndPath("mekanism", "chemical"));
    private static final ResourceKey<Object> SODIUM = ResourceKey.create(
            MEKANISM_CHEMICAL_REGISTRY, ResourceLocation.fromNamespaceAndPath("mekanism", "sodium"));
    private static final TagKey<Object> CHEMICALS_253_K = TagKey.create(
            MEKANISM_CHEMICAL_REGISTRY, FluxOverdrive.location("quantum_coolants_253k"));

    @GameTest(template = "empty")
    public static void entanglementControllerUsesBlockModel(GameTestHelper helper) {
        BlockState state = RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get().defaultBlockState();
        helper.assertTrue(state.getRenderShape() == RenderShape.MODEL,
                "Entanglement controller inherited the invisible block-entity render shape");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void entanglementPartsShareTemplateMembership(GameTestHelper helper) {
        BlockPos origin = new BlockPos(6, 1, 6);
        for (Direction facing : Direction.Plane.HORIZONTAL) {
            BlockState controllerState = RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get()
                    .defaultBlockState().setValue(QuantumEntanglementControllerBlock.FACING, facing);
            helper.setBlock(origin, controllerState);

            int casingIndex = 0;
            for (QuantumEntanglementTemplate.Cell cell : QuantumEntanglementTemplate.cells()) {
                BlockPos position = QuantumEntanglementStructure.worldPos(origin, facing, cell.offset());
                BlockState partState;
                if (cell.type() == QuantumEntanglementTemplate.CellType.ANTENNA) {
                    partState = RegistryBlocks.QUANTUM_ENTANGLEMENT_ANTENNA.get().defaultBlockState();
                } else {
                    partState = switch (casingIndex++) {
                        case 0 -> RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_ENTANGLEMENT_PORT.get()
                                .defaultBlockState();
                        case 1 -> RegistryBlocks.QUANTUM_ENTANGLEMENT_INPUT_PORT.get().defaultBlockState();
                        case 2 -> RegistryBlocks.QUANTUM_ENTANGLEMENT_OUTPUT_PORT.get().defaultBlockState();
                        case 3 -> RegistryBlocks.QUANTUM_ENTANGLEMENT_REDSTONE_PORT.get().defaultBlockState();
                        default -> RegistryBlocks.QUANTUM_ENTANGLEMENT_CASING.get().defaultBlockState();
                    };
                }
                helper.setBlock(position, partState);
                helper.assertTrue(QuantumEntanglementStructure.controllerOriginFor(
                                position, facing, cell.offset()).equals(origin),
                        "Could not recover the entanglement controller origin");
                helper.assertTrue(QuantumEntanglementStructure.cellAt(origin, facing, position) == cell,
                        "Could not recover the entanglement template cell");
                helper.assertTrue(QuantumEntanglementStructure.matchesPart(
                                helper.getLevel(), helper.absolutePos(origin), facing, helper.absolutePos(position)),
                        "Valid entanglement part was excluded from connected textures");
            }

            BlockPos outside = origin.relative(facing.getOpposite(), 8);
            helper.setBlock(outside, RegistryBlocks.QUANTUM_ENTANGLEMENT_ANTENNA.get().defaultBlockState());
            helper.assertTrue(!QuantumEntanglementStructure.matchesPart(
                            helper.getLevel(), helper.absolutePos(origin), facing, helper.absolutePos(outside)),
                    "Entanglement part outside the template was connected");
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void resourcePortCapabilities(GameTestHelper helper) {
        helper.setBlock(INPUT_POS, RegistryBlocks.QUANTUM_ENTANGLEMENT_INPUT_PORT.get().defaultBlockState());
        helper.setBlock(OUTPUT_POS, RegistryBlocks.QUANTUM_ENTANGLEMENT_OUTPUT_PORT.get().defaultBlockState());

        TileQuantumEntanglementResourcePort input = resourcePort(helper, INPUT_POS);
        TileQuantumEntanglementResourcePort output = resourcePort(helper, OUTPUT_POS);
        helper.assertTrue(input.getResourceDirection() == EntanglementResourceDirection.INPUT,
                "Input port has the wrong resource direction");
        helper.assertTrue(output.getResourceDirection() == EntanglementResourceDirection.OUTPUT,
                "Output port has the wrong resource direction");

        assertCapabilities(helper, INPUT_POS);
        assertCapabilities(helper, OUTPUT_POS);
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void feedbackPluginsDriveLocalAndEntangledRedstone(GameTestHelper helper) {
        TileQuantumBatteryController battery = buildTierOneFeedbackBattery(helper);
        BlockPos stabilityPosition = BATTERY_POS.offset(-3, 0, 0);
        BlockPos temperaturePosition = BATTERY_POS.offset(0, 3, 0);
        TileQuantumFeedbackPlugin stability = feedbackPlugin(helper, stabilityPosition);
        TileQuantumFeedbackPlugin temperature = feedbackPlugin(helper, temperaturePosition);

        stability.serverTick();
        battery.setTemperatureKelvin(40_000.0);
        temperature.serverTick();
        helper.assertTrue(stability.isSourceAvailable(), "Stability feedback did not resolve its battery");
        helper.assertTrue(temperature.isSourceAvailable(), "Temperature feedback did not resolve its battery");
        helper.assertTrue(stability.getNetworkColorRgb() == battery.getNetworkColorRgb(),
                "Feedback plugin did not inherit the battery network color");
        helper.assertTrue(temperature.getNetworkColorRgb() == battery.getNetworkColorRgb(),
                "Temperature feedback did not inherit the battery network color");
        helper.assertTrue(stability.getRedstoneSignal() == 15,
                "Full stability did not produce redstone level 15");
        helper.assertTrue(temperature.getRedstoneSignal() == 15,
                "Maximum temperature did not produce redstone level 15");

        BlockPos absoluteStability = helper.absolutePos(stabilityPosition);
        BlockState stabilityState = helper.getLevel().getBlockState(absoluteStability);
        for (Direction direction : Direction.values()) {
            helper.assertTrue(stabilityState.getSignal(helper.getLevel(), absoluteStability, direction) == 15,
                    "Feedback plugin did not weak-power every side");
        }

        QuantumFeedbackCurve custom = new QuantumFeedbackCurve(QuantumFeedbackMetric.STABILITY, java.util.List.of(
                new QuantumFeedbackCurve.Point(0, 15),
                new QuantumFeedbackCurve.Point(5_000, 0),
                new QuantumFeedbackCurve.Point(10_000, 9)));
        helper.assertTrue(custom.equals(QuantumFeedbackCurveCodec.load(
                        QuantumFeedbackMetric.STABILITY, QuantumFeedbackCurveCodec.save(custom))),
                "Feedback curve did not survive NBT encoding");

        QuantumPluginEndpoint stabilityEndpoint = battery.getEntanglementEndpoints().stream()
                .filter(endpoint -> endpoint.absolutePos().equals(absoluteStability))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Stability feedback endpoint was not formed"));
        TileQuantumEntanglementController entanglement = buildEntanglement(helper);
        BlockPos redstonePortPosition = QuantumEntanglementStructure.worldPos(
                ENTANGLEMENT_POS, Direction.NORTH, new QuantumEntanglementTemplate.Offset(1, 0, 0));
        BlockState redstonePortState = RegistryBlocks.QUANTUM_ENTANGLEMENT_REDSTONE_PORT.get()
                .defaultBlockState().setValue(QuantumEntanglementRedstonePortBlock.FACING, Direction.EAST);
        helper.setBlock(redstonePortPosition, redstonePortState);
        entanglement.validateStructure();
        configureEntanglement(helper, entanglement, battery, stabilityEndpoint);
        entanglement.serverTick();

        BlockPos absolutePort = helper.absolutePos(redstonePortPosition);
        BlockState formedPortState = helper.getLevel().getBlockState(absolutePort);
        helper.assertTrue(entanglement.isPortMember(absolutePort),
                "Redstone port was not accepted as an entanglement member");
        helper.assertTrue(entanglement.getRedstoneSignal() == 15,
                "Entanglement controller did not sample the selected feedback plugin");
        helper.assertTrue(formedPortState.getSignal(helper.getLevel(), absolutePort, Direction.WEST) == 15,
                "Redstone port did not output through its outward face");
        helper.assertTrue(formedPortState.getDirectSignal(helper.getLevel(), absolutePort, Direction.WEST) == 0,
                "Redstone port unexpectedly emitted a strong signal");
        helper.assertTrue(formedPortState.getSignal(helper.getLevel(), absolutePort, Direction.EAST) == 0,
                "Redstone port leaked signal through its back face");

        CompoundTag unbound = new CompoundTag();
        unbound.putBoolean("Formed", true);
        entanglement.loadAdditional(unbound, helper.getLevel().registryAccess());
        entanglement.validateStructure();
        entanglement.serverTick();
        helper.assertTrue(entanglement.getRedstoneSignal() == 0,
                "Unbound entanglement facility retained a stale redstone signal");
        helper.assertTrue(formedPortState.getSignal(helper.getLevel(), absolutePort, Direction.WEST) == 0,
                "Unbound redstone port retained a stale output");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sodiumChemicalCoolantTag(GameTestHelper helper) {
        if (!ModList.get().isLoaded("mekanism")) {
            helper.succeed();
            return;
        }
        Registry<Object> chemicals = helper.getLevel().registryAccess()
                .registryOrThrow(MEKANISM_CHEMICAL_REGISTRY);
        var sodium = chemicals.getHolderOrThrow(SODIUM);
        helper.assertTrue(sodium.is(CHEMICALS_253_K),
                "Mekanism sodium is missing from the 253 K quantum coolant tag");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void activeInputPullsFromFrontMekanismTank(GameTestHelper helper) {
        if (!ModList.get().isLoaded("mekanism")) {
            helper.succeed();
            return;
        }

        TileQuantumBatteryController battery = buildTierTwoBattery(helper);
        BlockPos advancedPosition = BATTERY_POS.offset(6, 0, 0);
        QuantumPluginEndpoint advanced = battery.getEntanglementEndpoints().stream()
                .filter(endpoint -> endpoint.absolutePos().equals(helper.absolutePos(advancedPosition)))
                .findFirst()
                .orElseThrow(() -> new AssertionError("Tier two advanced plugin endpoint was not formed"));

        TileQuantumEntanglementController entanglement = buildEntanglement(helper);
        configureEntanglement(helper, entanglement, battery, advanced);
        helper.assertTrue(entanglement.isFormed(), "Entanglement structure did not form");

        Block tankBlock = BuiltInRegistries.BLOCK.get(
                ResourceLocation.fromNamespaceAndPath("mekanism", "basic_fluid_tank"));
        helper.assertTrue(tankBlock != Blocks.AIR, "Mekanism basic fluid tank is unavailable");
        helper.setBlock(FRONT_TANK_POS, tankBlock.defaultBlockState());
        helper.setBlock(SIDE_TANK_POS, tankBlock.defaultBlockState());

        IFluidHandler frontSource = helper.getLevel().getCapability(
                Capabilities.FluidHandler.BLOCK, helper.absolutePos(FRONT_TANK_POS), Direction.WEST);
        IFluidHandler sideSource = helper.getLevel().getCapability(
                Capabilities.FluidHandler.BLOCK, helper.absolutePos(SIDE_TANK_POS), Direction.SOUTH);
        helper.assertTrue(frontSource != null, "Front tank did not expose a west-side fluid capability");
        helper.assertTrue(sideSource != null, "Side tank did not expose a south-side fluid capability");
        helper.assertTrue(frontSource.fill(new FluidStack(Fluids.WATER, 1_000),
                        IFluidHandler.FluidAction.EXECUTE) == 1_000,
                "Could not fill the front Mekanism tank");
        helper.assertTrue(sideSource.fill(new FluidStack(Fluids.WATER, 1_000),
                        IFluidHandler.FluidAction.EXECUTE) == 1_000,
                "Could not fill the side Mekanism tank");

        BlockPos inputPosition = QuantumEntanglementStructure.worldPos(
                ENTANGLEMENT_POS, Direction.NORTH, new QuantumEntanglementTemplate.Offset(1, 0, 0));
        TileQuantumEntanglementResourcePort inputPort = resourcePort(helper, inputPosition);
        helper.assertTrue(inputPort.getResourceDirection() == EntanglementResourceDirection.INPUT,
                "Placed resource port is not an input port");
        helper.assertTrue(entanglement.isPortMember(inputPort.getBlockPos()),
                "Input port is not a member of the formed entanglement structure");
        helper.assertTrue(TileQuantumEntanglementController.getLoadedControllersNear(
                        helper.getLevel(), inputPort.getBlockPos(), 4).contains(entanglement.getBlockPos()),
                "Formed entanglement controller is missing from the loaded controller index");
        helper.assertTrue(entanglement.isBound(), "Entanglement controller lost its battery binding");
        helper.assertTrue(entanglement.isTargetAvailable(), "Entanglement target battery is unavailable");
        helper.assertTrue(advanced.key().equals(entanglement.getSelectedEndpointKey()),
                "Entanglement controller lost its selected advanced endpoint");
        IFluidHandler routed = entanglement.resolveSelectedCapability(
                com.aquariverr.fluxod.entanglement.EntanglementResourceKind.FLUID,
                EntanglementResourceDirection.INPUT, Capabilities.FluidHandler.BLOCK);
        helper.assertTrue(routed != null, "Selected advanced endpoint did not resolve a fluid capability");
        helper.assertTrue(routed.getTanks() > 0, "Selected advanced endpoint has no fluid tanks");
        IFluidHandler portCapability = inputPort.getFluidCapability(null);
        helper.assertTrue(portCapability != null && portCapability.getTanks() > 0,
                "Input port did not expose the selected fluid capability");
        TileQuantumAdvancedCoolingPlugin advancedPlugin = (TileQuantumAdvancedCoolingPlugin)
                helper.getLevel().getBlockEntity(helper.absolutePos(advancedPosition));
        helper.assertTrue(advancedPlugin != null, "Advanced cooling plugin block entity was not created");
        helper.assertTrue(advancedPlugin.getFluidTank().getFluidAmount() == 0,
                "Advanced cooling tank was not empty before the transfer");

        inputPort.serverTick();

        helper.assertTrue(frontSource.getFluidInTank(0).isEmpty(),
                "Input port did not actively pull from its front tank");
        helper.assertTrue(sideSource.getFluidInTank(0).getAmount() == 1_000,
                "Input port read a tank from a non-facing side");
        helper.assertTrue(advancedPlugin.getFluidTank().getFluidAmount() == 1_000,
                "Pulled water did not reach the selected advanced cooling plugin");
        helper.succeed();
    }

    private static TileQuantumBatteryController buildTierTwoBattery(GameTestHelper helper) {
        helper.setBlock(BATTERY_POS, RegistryBlocks.QUANTUM_BATTERY_CONTROLLER.get().defaultBlockState());
        TileQuantumBatteryController battery = (TileQuantumBatteryController)
                helper.getLevel().getBlockEntity(helper.absolutePos(BATTERY_POS));
        helper.assertTrue(battery != null, "Battery controller block entity was not created");

        CompoundTag coreData = new CompoundTag();
        battery.writeCustomTag(coreData, FluxConstants.NBT_SAVE_ALL);
        coreData.putInt("CoreLevel", 2);
        coreData.putUUID("BatteryId", battery.getBatteryId());
        battery.readCustomTag(coreData, FluxConstants.NBT_SAVE_ALL);

        for (int tier = 1; tier <= 2; tier++) {
            for (QuantumBatteryStructure.Cell cell : QuantumBatteryStructure.layer(tier).cells()) {
                QuantumBatteryStructure.Offset offset = cell.offset();
                Block block = QuantumBatteryAssembler.expectedBlock(tier, cell.type());
                if (tier == 1 && offset.equals(new QuantumBatteryStructure.Offset(3, 0, 0))) {
                    block = RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE.get();
                } else if (tier == 2 && offset.equals(new QuantumBatteryStructure.Offset(-6, 0, 0))) {
                    block = RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE_3X3.get();
                } else if (tier == 2 && offset.equals(new QuantumBatteryStructure.Offset(6, 0, 0))) {
                    block = RegistryBlocks.QUANTUM_ADVANCED_COOLING_PLUGIN.get();
                }
                helper.setBlock(BATTERY_POS.offset(offset.x(), offset.y(), offset.z()),
                        block.defaultBlockState());
            }
        }
        battery.validateStructure();
        helper.assertTrue(battery.isOperational(), "Tier two battery did not become operational");
        return battery;
    }

    private static TileQuantumBatteryController buildTierOneFeedbackBattery(GameTestHelper helper) {
        helper.setBlock(BATTERY_POS, RegistryBlocks.QUANTUM_BATTERY_CONTROLLER.get().defaultBlockState());
        TileQuantumBatteryController battery = (TileQuantumBatteryController)
                helper.getLevel().getBlockEntity(helper.absolutePos(BATTERY_POS));
        helper.assertTrue(battery != null, "Battery controller block entity was not created");

        for (QuantumBatteryStructure.Cell cell : QuantumBatteryStructure.layer(1).cells()) {
            QuantumBatteryStructure.Offset offset = cell.offset();
            Block block = QuantumBatteryAssembler.expectedBlock(1, cell.type());
            if (offset.equals(new QuantumBatteryStructure.Offset(3, 0, 0))) {
                block = RegistryBlocks.QUANTUM_ENERGY_CONSTRAINT_INTERFACE.get();
            } else if (offset.equals(new QuantumBatteryStructure.Offset(-3, 0, 0))) {
                block = RegistryBlocks.QUANTUM_STABILITY_FEEDBACK_PLUGIN.get();
            } else if (offset.equals(new QuantumBatteryStructure.Offset(0, 3, 0))) {
                block = RegistryBlocks.QUANTUM_TEMPERATURE_FEEDBACK_PLUGIN.get();
            }
            helper.setBlock(BATTERY_POS.offset(offset.x(), offset.y(), offset.z()),
                    block.defaultBlockState());
        }
        battery.validateStructure();
        helper.assertTrue(battery.isOperational(), "Tier one feedback battery did not become operational");
        return battery;
    }

    private static TileQuantumEntanglementController buildEntanglement(GameTestHelper helper) {
        BlockState controllerState = RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get()
                .defaultBlockState().setValue(QuantumEntanglementControllerBlock.FACING, Direction.NORTH);
        helper.setBlock(ENTANGLEMENT_POS, controllerState);
        for (QuantumEntanglementTemplate.Cell cell : QuantumEntanglementTemplate.cells()) {
            BlockPos target = QuantumEntanglementStructure.worldPos(
                    ENTANGLEMENT_POS, Direction.NORTH, cell.offset());
            BlockState state = QuantumEntanglementAssembler.expectedState(cell, controllerState);
            if (cell.offset().equals(new QuantumEntanglementTemplate.Offset(1, 0, 0))) {
                state = RegistryBlocks.QUANTUM_ENTANGLEMENT_INPUT_PORT.get().defaultBlockState()
                        .setValue(QuantumEntanglementResourcePortBlock.FACING, Direction.EAST);
            }
            helper.setBlock(target, state);
        }
        TileQuantumEntanglementController controller = (TileQuantumEntanglementController)
                helper.getLevel().getBlockEntity(helper.absolutePos(ENTANGLEMENT_POS));
        helper.assertTrue(controller != null, "Entanglement controller block entity was not created");
        controller.validateStructure();
        return controller;
    }

    private static void configureEntanglement(GameTestHelper helper,
                                              TileQuantumEntanglementController controller,
                                              TileQuantumBatteryController battery,
                                              QuantumPluginEndpoint endpoint) {
        CompoundTag data = new CompoundTag();
        data.putUUID("Owner", UUID.randomUUID());
        data.putBoolean("Formed", true);
        data.putLong("TargetPos", battery.getBlockPos().asLong());
        data.putString("TargetDimension", helper.getLevel().dimension().location().toString());
        data.putUUID("TargetId", battery.getBatteryId());
        data.put("SelectedEndpoint", endpoint.key().save());
        data.putBoolean("AutomaticInputEnabled", true);
        data.putBoolean("AutomaticOutputEnabled", true);
        controller.loadAdditional(data, helper.getLevel().registryAccess());
        controller.validateStructure();
    }

    private static TileQuantumEntanglementResourcePort resourcePort(GameTestHelper helper, BlockPos position) {
        var blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(position));
        helper.assertTrue(blockEntity instanceof TileQuantumEntanglementResourcePort,
                "Resource port block entity was not created");
        return (TileQuantumEntanglementResourcePort) blockEntity;
    }

    private static TileQuantumFeedbackPlugin feedbackPlugin(GameTestHelper helper, BlockPos position) {
        var blockEntity = helper.getLevel().getBlockEntity(helper.absolutePos(position));
        helper.assertTrue(blockEntity instanceof TileQuantumFeedbackPlugin,
                "Feedback plugin block entity was not created");
        return (TileQuantumFeedbackPlugin) blockEntity;
    }

    private static void assertCapabilities(GameTestHelper helper, BlockPos position) {
        BlockPos absolutePos = helper.absolutePos(position);
        IItemHandler itemHandler = helper.getLevel().getCapability(
                Capabilities.ItemHandler.BLOCK, absolutePos, (Direction) null);
        IFluidHandler fluidHandler = helper.getLevel().getCapability(
                Capabilities.FluidHandler.BLOCK, absolutePos, (Direction) null);
        helper.assertTrue(itemHandler != null, "Resource port did not expose an item capability");
        helper.assertTrue(fluidHandler != null, "Resource port did not expose a fluid capability");
        helper.assertTrue(itemHandler.getSlots() == 0,
                "Unrouted resource port exposed remote item slots");
        helper.assertTrue(fluidHandler.getTanks() == 0,
                "Unrouted resource port exposed remote fluid tanks");
    }

    private QuantumEntanglementGameTests() {}
}
