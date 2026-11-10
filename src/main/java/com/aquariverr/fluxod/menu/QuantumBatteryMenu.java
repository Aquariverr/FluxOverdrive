package com.aquariverr.fluxod.menu;

import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import com.aquariverr.fluxod.block.entity.QuantumBatteryThermalModel;
import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementController;
import com.aquariverr.fluxod.multiblock.QuantumBatteryAssembler;
import com.aquariverr.fluxod.register.RegistryBlocks;
import com.aquariverr.fluxod.register.RegistryMenuTypes;
import com.aquariverr.fluxod.util.QuantumEnergyDisplay;
import net.minecraft.core.BlockPos;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ContainerData;
import net.minecraft.world.inventory.SimpleContainerData;
import net.minecraft.world.item.ItemStack;

import javax.annotation.Nullable;

public final class QuantumBatteryMenu extends AbstractContainerMenu {

    public static final int BUTTON_BUILD_TIER_1 = 10;
    public static final int BUTTON_BUILD_TIER_2 = 11;
    public static final int BUTTON_BUILD_TIER_3 = 12;
    public static final int BUTTON_OPEN_FLUX = 20;
    public static final int BUTTON_RETURN_ENTANGLEMENT = 21;
    public static final int BUTTON_UPGRADE_CORE = 30;

    private static final int DATA_COUNT = 23;
    private static final int LEAK_EVENT_SEQUENCE_MODULUS = 1 << 15;
    private static final int DATA_TIER = 0;
    private static final int DATA_SUSPENDED = 1;
    private static final int DATA_CREATIVE = 2;
    private static final int DATA_ASSEMBLING = 3;
    private static final int DATA_READY = 4;
    private static final int DATA_STABILITY = 5;
    private static final int DATA_CONSTRAINT_POWERED = 6;
    private static final int DATA_TEMPERATURE_LOW = 7;
    private static final int DATA_TEMPERATURE_HIGH = 8;
    private static final int DATA_ENERGY_SIGNIFICAND_LOW = 9;
    private static final int DATA_ENERGY_SIGNIFICAND_HIGH = 10;
    private static final int DATA_ENERGY_EXPONENT = 11;
    private static final int DATA_ENERGY_LOG = 12;
    private static final int DATA_NETWORK_COLOR_LOW = 13;
    private static final int DATA_NETWORK_COLOR_HIGH = 14;
    private static final int DATA_CORE_LEVEL = 15;
    private static final int DATA_CORE_PROGRESS = 16;
    private static final int DATA_CAN_UPGRADE_CORE = 17;
    private static final int DATA_LEAK_EVENT_SEQUENCE = 18;
    private static final int DATA_INSTABILITY_PENALTIES_ENABLED = 19;
    private static final int DATA_COOLING_TARGET_LOW = 20;
    private static final int DATA_COOLING_TARGET_HIGH = 21;
    private static final int DATA_COOLING_ACTIVE = 22;

    private final BlockPos controllerPos;
    private final BlockPos accessControllerPos;
    @Nullable
    private final TileQuantumBatteryController controller;
    @Nullable
    private final TileQuantumEntanglementController accessController;
    private final ContainerData data;
    private final boolean remoteAccess;

    public QuantumBatteryMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, readClientContext(inventory.player, buffer));
    }

    public QuantumBatteryMenu(int containerId, Inventory inventory, TileQuantumBatteryController controller) {
        this(containerId, inventory, controller, null, controller.getBlockPos(), controller.getBlockPos(),
                false, serverData(controller, inventory.player));
    }

    public QuantumBatteryMenu(int containerId, Inventory inventory, TileQuantumBatteryController controller,
                              TileQuantumEntanglementController accessController) {
        this(containerId, inventory, controller, accessController, controller.getBlockPos(),
                accessController.getBlockPos(), true, serverData(controller, inventory.player));
    }

    private QuantumBatteryMenu(int containerId, Inventory inventory, ClientContext context) {
        this(containerId, inventory, context.controller(), context.accessController(), context.controllerPos(),
                context.accessControllerPos(), context.remoteAccess(), new SimpleContainerData(DATA_COUNT));
    }

    private QuantumBatteryMenu(int containerId, Inventory inventory,
                                @Nullable TileQuantumBatteryController controller,
                                @Nullable TileQuantumEntanglementController accessController,
                                BlockPos controllerPos, BlockPos accessControllerPos,
                                boolean remoteAccess, ContainerData data) {
        super(RegistryMenuTypes.QUANTUM_BATTERY.get(), containerId);
        this.controller = controller;
        this.accessController = accessController;
        this.controllerPos = controllerPos;
        this.accessControllerPos = accessControllerPos;
        this.remoteAccess = remoteAccess;
        this.data = data;
        checkContainerDataCount(data, DATA_COUNT);
        addDataSlots(data);
    }

    public int getStructureTier() {
        return data.get(DATA_TIER);
    }

    public boolean isSuspended() {
        return data.get(DATA_SUSPENDED) != 0;
    }

    public boolean isCreativeBuilder() {
        return data.get(DATA_CREATIVE) != 0;
    }

    public boolean isAssembling() {
        return data.get(DATA_ASSEMBLING) != 0;
    }

    public boolean isStructureReady() {
        return data.get(DATA_READY) != 0;
    }

    public int getStabilityBasisPoints() {
        return Math.clamp(data.get(DATA_STABILITY), 0, 10_000);
    }

    public String getStabilityPercent() {
        return TileQuantumBatteryController.formatStabilityBasisPoints(getStabilityBasisPoints());
    }

    public boolean isConstraintPowered() {
        return data.get(DATA_CONSTRAINT_POWERED) != 0;
    }

    public double getTemperatureKelvin() {
        int centiKelvin = MenuDataEncoding.combineWords(
                data.get(DATA_TEMPERATURE_LOW), data.get(DATA_TEMPERATURE_HIGH));
        return Math.clamp(centiKelvin / 100.0,
                0.0, QuantumBatteryThermalModel.MAX_TEMPERATURE_K);
    }

    public double getThermalLoadPercent() {
        return QuantumBatteryThermalModel.thermalLoadPercent(getTemperatureKelvin());
    }

    public double getConstraintEnergyMultiplier() {
        return QuantumBatteryThermalModel.constraintEnergyMultiplier(getTemperatureKelvin());
    }

    public double getCoolingTargetKelvin() {
        int centiKelvin = MenuDataEncoding.combineWords(
                data.get(DATA_COOLING_TARGET_LOW), data.get(DATA_COOLING_TARGET_HIGH));
        return centiKelvin < 0 ? -1.0 : centiKelvin / 100.0;
    }

    public boolean hasCoolingTarget() {
        return getCoolingTargetKelvin() >= 0.0;
    }

    public boolean isCoolingActive() {
        return data.get(DATA_COOLING_ACTIVE) != 0;
    }

    public QuantumEnergyDisplay getEnergyDisplay() {
        int significand = MenuDataEncoding.combineWords(
                data.get(DATA_ENERGY_SIGNIFICAND_LOW), data.get(DATA_ENERGY_SIGNIFICAND_HIGH));
        return new QuantumEnergyDisplay(significand,
                data.get(DATA_ENERGY_EXPONENT), data.get(DATA_ENERGY_LOG));
    }

    public String getStoredEnergyCompact() {
        return getEnergyDisplay().compact();
    }

    public double getEnergyLogScale() {
        return getEnergyDisplay().normalizedLogScale();
    }

    public int getNetworkColor() {
        int color = MenuDataEncoding.combineWords(
                data.get(DATA_NETWORK_COLOR_LOW), data.get(DATA_NETWORK_COLOR_HIGH)) & 0xFFFFFF;
        return color == 0 ? 0x43D9FF : color;
    }

    public int getCoreLevel() {
        return Math.clamp(data.get(DATA_CORE_LEVEL), 1, 3);
    }

    public int getCoreUpgradeProgressBasisPoints() {
        return Math.clamp(data.get(DATA_CORE_PROGRESS), 0, 10_000);
    }

    public boolean canUpgradeCore() {
        return data.get(DATA_CAN_UPGRADE_CORE) != 0;
    }

    public int getLeakEventSequence() {
        return Math.clamp(data.get(DATA_LEAK_EVENT_SEQUENCE), 0,
                LEAK_EVENT_SEQUENCE_MODULUS - 1);
    }

    public boolean areInstabilityPenaltiesEnabled() {
        return data.get(DATA_INSTABILITY_PENALTIES_ENABLED) != 0;
    }

    public boolean isRemoteAccess() {
        return remoteAccess;
    }

    public BlockPos getControllerPos() {
        return controllerPos;
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (controller == null || player.level().isClientSide || player.isSpectator()) return false;
        if (id == BUTTON_UPGRADE_CORE) {
            if (!stillValid(player) || !controller.canPlayerManage(player)) return false;
            boolean upgraded = controller.tryUpgradeCore(player);
            player.displayClientMessage(Component.translatable(upgraded
                    ? "message.flux_overdrive.quantum_battery.core.upgraded"
                    : "message.flux_overdrive.quantum_battery.core.unavailable", controller.getCoreLevel()), true);
            if (upgraded) broadcastChanges();
            return true;
        }
        if (remoteAccess) {
            if (id == BUTTON_OPEN_FLUX
                    && player instanceof ServerPlayer serverPlayer
                    && accessController != null
                    && remoteAccessStillValid(player)) {
                return controller.openFluxMenuFromEntanglement(serverPlayer, accessController);
            }
            if (id == BUTTON_RETURN_ENTANGLEMENT && accessController != null
                    && remoteAccessStillValid(player)) {
                accessController.openMenu(player);
                return true;
            }
            return false;
        }
        if (id == BUTTON_OPEN_FLUX) {
            if (controller.canPlayerManage(player)) {
                controller.onPlayerInteract(player);
                return true;
            }
            return false;
        }
        if (id < BUTTON_BUILD_TIER_1 || id > BUTTON_BUILD_TIER_3 || !(player instanceof ServerPlayer serverPlayer)) {
            return false;
        }

        int targetTier = id - BUTTON_BUILD_TIER_1 + 1;
        QuantumBatteryAssembler.Result result = controller.startAssembly(serverPlayer, targetTier);
        player.displayClientMessage(QuantumBatteryAssembler.resultMessage(result), true);
        broadcastChanges();
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        if (remoteAccess) return remoteAccessStillValid(player);
        if (controller == null || controller.isRemoved()) return false;
        if (controller.getLevel() != player.level()
                || !player.level().getBlockState(controllerPos).is(RegistryBlocks.QUANTUM_BATTERY_CONTROLLER.get())
                || player.distanceToSqr(controllerPos.getX() + 0.5, controllerPos.getY() + 0.5,
                controllerPos.getZ() + 0.5) > 64.0) {
            return false;
        }
        return player.level().isClientSide || controller.canPlayerManage(player);
    }

    private boolean remoteAccessStillValid(Player player) {
        if (player.level().getBlockState(accessControllerPos)
                .is(RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get())
                && player.distanceToSqr(accessControllerPos.getX() + 0.5, accessControllerPos.getY() + 0.5,
                accessControllerPos.getZ() + 0.5) <= 64.0) {
            if (player.level().isClientSide) return true;
            return controller != null && accessController != null
                    && accessController.canProvideRemoteAccess(player, controller);
        }
        return false;
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        return ItemStack.EMPTY;
    }

    @Nullable
    private static TileQuantumBatteryController findController(Player player, BlockPos pos) {
        if (player.level().getBlockEntity(pos) instanceof TileQuantumBatteryController controller) {
            return controller;
        }
        return null;
    }

    private static ClientContext readClientContext(Player player, RegistryFriendlyByteBuf buffer) {
        BlockPos controllerPos = buffer.readBlockPos();
        boolean remoteAccess = buffer.readBoolean();
        if (!remoteAccess) {
            return new ClientContext(controllerPos, controllerPos, findController(player, controllerPos), null, false);
        }
        BlockPos accessControllerPos = buffer.readBlockPos();
        TileQuantumEntanglementController accessController =
                player.level().getBlockEntity(accessControllerPos) instanceof TileQuantumEntanglementController found
                        ? found : null;
        return new ClientContext(controllerPos, accessControllerPos, null, accessController, true);
    }

    private static ContainerData serverData(TileQuantumBatteryController controller, Player player) {
        int leakEventBaseline = controller.getLeakEventSequence();
        return new ContainerData() {
            @Override
            public int get(int index) {
                return switch (index) {
                    case DATA_TIER -> controller.getStructureTier();
                    case DATA_SUSPENDED -> controller.isStructureSuspended() ? 1 : 0;
                    case DATA_CREATIVE -> player.isCreative() ? 1 : 0;
                    case DATA_ASSEMBLING -> controller.isAssembling() ? 1 : 0;
                    case DATA_READY -> controller.isStructureReady() ? 1 : 0;
                    case DATA_STABILITY -> controller.getStabilityBasisPoints();
                    case DATA_CONSTRAINT_POWERED -> controller.isConstraintPowered() ? 1 : 0;
                    case DATA_TEMPERATURE_LOW -> MenuDataEncoding.lowWord(
                            controller.getTemperatureCentiKelvin());
                    case DATA_TEMPERATURE_HIGH -> MenuDataEncoding.highWord(
                            controller.getTemperatureCentiKelvin());
                    case DATA_ENERGY_SIGNIFICAND_LOW -> MenuDataEncoding.lowWord(
                            controller.getEnergyDisplay().significand());
                    case DATA_ENERGY_SIGNIFICAND_HIGH -> MenuDataEncoding.highWord(
                            controller.getEnergyDisplay().significand());
                    case DATA_ENERGY_EXPONENT -> controller.getEnergyDisplay().exponent();
                    case DATA_ENERGY_LOG -> controller.getEnergyDisplay().logBasisPoints();
                    case DATA_NETWORK_COLOR_LOW -> MenuDataEncoding.lowWord(controller.getNetworkColorRgb());
                    case DATA_NETWORK_COLOR_HIGH -> MenuDataEncoding.highWord(controller.getNetworkColorRgb());
                    case DATA_CORE_LEVEL -> controller.getCoreLevel();
                    case DATA_CORE_PROGRESS -> controller.getCoreUpgradeProgressBasisPoints();
                    case DATA_CAN_UPGRADE_CORE -> controller.canUpgradeCore() ? 1 : 0;
                    case DATA_LEAK_EVENT_SEQUENCE -> Math.floorMod(
                            controller.getLeakEventSequence() - leakEventBaseline,
                            LEAK_EVENT_SEQUENCE_MODULUS);
                    case DATA_INSTABILITY_PENALTIES_ENABLED ->
                            com.aquariverr.fluxod.Config.quantumInstabilityPenaltiesEnabled ? 1 : 0;
                    case DATA_COOLING_TARGET_LOW -> MenuDataEncoding.lowWord(
                            controller.getEffectiveCoolingTargetCentiKelvin());
                    case DATA_COOLING_TARGET_HIGH -> MenuDataEncoding.highWord(
                            controller.getEffectiveCoolingTargetCentiKelvin());
                    case DATA_COOLING_ACTIVE -> controller.isCoolingActive() ? 1 : 0;
                    default -> 0;
                };
            }

            @Override
            public void set(int index, int value) {}

            @Override
            public int getCount() {
                return DATA_COUNT;
            }
        };
    }

    private record ClientContext(BlockPos controllerPos, BlockPos accessControllerPos,
                                 @Nullable TileQuantumBatteryController controller,
                                 @Nullable TileQuantumEntanglementController accessController,
                                 boolean remoteAccess) {}
}
