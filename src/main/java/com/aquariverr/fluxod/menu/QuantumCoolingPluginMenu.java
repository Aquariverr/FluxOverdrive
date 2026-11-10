package com.aquariverr.fluxod.menu;

import com.aquariverr.fluxod.block.entity.QuantumBatteryCoolingModel;
import com.aquariverr.fluxod.block.entity.QuantumBatteryCoolingSource;
import com.aquariverr.fluxod.block.entity.QuantumCoolingMedia;
import com.aquariverr.fluxod.block.entity.TileQuantumAdvancedCoolingPlugin;
import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementController;
import com.aquariverr.fluxod.block.entity.TileQuantumSolidCoolingPlugin;
import com.aquariverr.fluxod.entanglement.QuantumPluginEndpointKey;
import com.aquariverr.fluxod.integration.mekanism.OptionalChemicalTank;
import com.aquariverr.fluxod.network.CoolingPluginSnapshotPayload;
import com.aquariverr.fluxod.register.RegistryBlocks;
import com.aquariverr.fluxod.register.RegistryMenuTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.neoforge.items.ItemStackHandler;
import net.neoforged.neoforge.items.SlotItemHandler;
import net.neoforged.neoforge.fluids.FluidUtil;
import net.neoforged.neoforge.network.PacketDistributor;

import javax.annotation.Nullable;

public final class QuantumCoolingPluginMenu extends AbstractContainerMenu {

    public static final int BUTTON_INTERACT_MAIN_HAND = 0;
    public static final int BUTTON_INTERACT_OFF_HAND = 1;
    public static final int SLOT_COLUMN_LEFT = 134;
    public static final int SOLID_PLUGIN_SLOT_Y = 64;
    public static final int PLAYER_INVENTORY_SLOT_Y = 130;
    public static final int PLAYER_HOTBAR_SLOT_Y = 188;

    private static final String TYPE_TAG = "PluginType";
    private static final String RATE_TAG = "CoolingRate";
    private static final String TARGET_TAG = "Target";
    private static final String FLUID_ID_TAG = "FluidId";
    private static final String FLUID_AMOUNT_TAG = "FluidAmount";
    private static final String FLUID_CAPACITY_TAG = "FluidCapacity";
    private static final String FLUID_TARGET_TAG = "FluidTarget";
    private static final String CHEMICAL_AVAILABLE_TAG = "ChemicalAvailable";
    private static final String CHEMICAL_ID_TAG = "ChemicalId";
    private static final String CHEMICAL_AMOUNT_TAG = "ChemicalAmount";
    private static final String CHEMICAL_CAPACITY_TAG = "ChemicalCapacity";
    private static final String CHEMICAL_TARGET_TAG = "ChemicalTarget";
    private static final String CHEMICAL_TINT_TAG = "ChemicalTint";
    private static final int SOLID_PLUGIN_SLOT_COUNT = TileQuantumSolidCoolingPlugin.INVENTORY_SIZE;
    private static final int PLAYER_SLOT_COUNT = 36;

    private final BlockPos pluginPos;
    private final PluginType pluginType;
    private final Player menuPlayer;
    private final boolean remoteAccess;
    private final BlockPos accessControllerPos;
    @Nullable
    private final TileQuantumEntanglementController accessController;
    @Nullable
    private final QuantumPluginEndpointKey endpointKey;
    @Nullable
    private final TileQuantumSolidCoolingPlugin solidPlugin;
    @Nullable
    private final TileQuantumAdvancedCoolingPlugin advancedPlugin;
    private CompoundTag snapshot;
    @Nullable
    private CompoundTag lastSentSnapshot;

    public QuantumCoolingPluginMenu(int containerId, Inventory inventory, RegistryFriendlyByteBuf buffer) {
        this(containerId, inventory, ClientContext.read(inventory.player, buffer));
    }

    public QuantumCoolingPluginMenu(int containerId, Inventory inventory, TileQuantumSolidCoolingPlugin plugin) {
        this(containerId, inventory, plugin.getBlockPos(), PluginType.SOLID, plugin, null,
                false, plugin.getBlockPos(), null, null, createSnapshot(plugin));
    }

    public QuantumCoolingPluginMenu(int containerId, Inventory inventory, TileQuantumAdvancedCoolingPlugin plugin) {
        this(containerId, inventory, plugin.getBlockPos(), PluginType.ADVANCED, null, plugin,
                false, plugin.getBlockPos(), null, null, createSnapshot(plugin));
    }

    public QuantumCoolingPluginMenu(int containerId, Inventory inventory, TileQuantumSolidCoolingPlugin plugin,
                                    TileQuantumEntanglementController accessController,
                                    QuantumPluginEndpointKey endpointKey) {
        this(containerId, inventory, plugin.getBlockPos(), PluginType.SOLID, plugin, null,
                true, accessController.getBlockPos(), accessController, endpointKey, createSnapshot(plugin));
    }

    public QuantumCoolingPluginMenu(int containerId, Inventory inventory, TileQuantumAdvancedCoolingPlugin plugin,
                                    TileQuantumEntanglementController accessController,
                                    QuantumPluginEndpointKey endpointKey) {
        this(containerId, inventory, plugin.getBlockPos(), PluginType.ADVANCED, null, plugin,
                true, accessController.getBlockPos(), accessController, endpointKey, createSnapshot(plugin));
    }

    private QuantumCoolingPluginMenu(int containerId, Inventory inventory, ClientContext context) {
        this(containerId, inventory, context.pos(), context.type(), null, null,
                context.remoteAccess(), context.accessControllerPos(), context.accessController(),
                context.endpointKey(), context.snapshot());
    }

    private QuantumCoolingPluginMenu(
            int containerId,
            Inventory inventory,
            BlockPos pluginPos,
            PluginType pluginType,
            @Nullable TileQuantumSolidCoolingPlugin solidPlugin,
            @Nullable TileQuantumAdvancedCoolingPlugin advancedPlugin,
            boolean remoteAccess,
            BlockPos accessControllerPos,
            @Nullable TileQuantumEntanglementController accessController,
            @Nullable QuantumPluginEndpointKey endpointKey,
            CompoundTag snapshot) {
        super(RegistryMenuTypes.QUANTUM_COOLING_PLUGIN.get(), containerId);
        this.pluginPos = pluginPos.immutable();
        this.pluginType = pluginType;
        this.menuPlayer = inventory.player;
        this.solidPlugin = solidPlugin;
        this.advancedPlugin = advancedPlugin;
        this.remoteAccess = remoteAccess;
        this.accessControllerPos = accessControllerPos.immutable();
        this.accessController = accessController;
        this.endpointKey = endpointKey;
        this.snapshot = snapshot.copy();

        if (pluginType == PluginType.SOLID) {
            ItemStackHandler handler = solidPlugin == null
                    ? new ItemStackHandler(SOLID_PLUGIN_SLOT_COUNT)
                    : solidPlugin.getInventory();
            for (int slot = 0; slot < SOLID_PLUGIN_SLOT_COUNT; slot++) {
                addSlot(new SlotItemHandler(handler, slot,
                        SLOT_COLUMN_LEFT + slot * 18, SOLID_PLUGIN_SLOT_Y));
            }
            addPlayerInventory(inventory);
        }
    }

    public static void writeOpeningData(RegistryFriendlyByteBuf buffer, BlockEntity plugin) {
        PluginType type = plugin instanceof TileQuantumSolidCoolingPlugin
                ? PluginType.SOLID
                : PluginType.ADVANCED;
        buffer.writeBlockPos(plugin.getBlockPos());
        buffer.writeVarInt(type.id);
        buffer.writeNbt(createSnapshot(plugin));
        buffer.writeBoolean(false);
    }

    public static void writeRemoteOpeningData(RegistryFriendlyByteBuf buffer, BlockEntity plugin,
                                              TileQuantumEntanglementController accessController,
                                              QuantumPluginEndpointKey endpointKey) {
        PluginType type = plugin instanceof TileQuantumSolidCoolingPlugin
                ? PluginType.SOLID
                : PluginType.ADVANCED;
        buffer.writeBlockPos(plugin.getBlockPos());
        buffer.writeVarInt(type.id);
        buffer.writeNbt(createSnapshot(plugin));
        buffer.writeBoolean(true);
        buffer.writeBlockPos(accessController.getBlockPos());
        endpointKey.write(buffer);
    }

    public PluginType getPluginType() {
        return pluginType;
    }

    public BlockPos getPluginPos() {
        return pluginPos;
    }

    public double getCoolingRateKelvinPerTick() {
        return QuantumBatteryCoolingModel.kelvin(snapshot.getLong(RATE_TAG));
    }

    public double getTargetKelvin() {
        return targetKelvin(snapshot.getLong(TARGET_TAG));
    }

    public String getFluidId() {
        return snapshot.getString(FLUID_ID_TAG);
    }

    public long getFluidAmount() {
        return Math.max(0L, snapshot.getLong(FLUID_AMOUNT_TAG));
    }

    public long getFluidCapacity() {
        return Math.max(1L, snapshot.getLong(FLUID_CAPACITY_TAG));
    }

    public double getFluidTargetKelvin() {
        return targetKelvin(snapshot.getLong(FLUID_TARGET_TAG));
    }

    public boolean isChemicalAvailable() {
        return snapshot.getBoolean(CHEMICAL_AVAILABLE_TAG);
    }

    public String getChemicalId() {
        return snapshot.getString(CHEMICAL_ID_TAG);
    }

    public long getChemicalAmount() {
        return Math.max(0L, snapshot.getLong(CHEMICAL_AMOUNT_TAG));
    }

    public long getChemicalCapacity() {
        return Math.max(1L, snapshot.getLong(CHEMICAL_CAPACITY_TAG));
    }

    public double getChemicalTargetKelvin() {
        return targetKelvin(snapshot.getLong(CHEMICAL_TARGET_TAG));
    }

    public int getChemicalTint() {
        return snapshot.getInt(CHEMICAL_TINT_TAG);
    }

    public void applySnapshot(CompoundTag updatedSnapshot) {
        snapshot = updatedSnapshot.copy();
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(menuPlayer instanceof ServerPlayer player) || player.containerMenu != this || !stillValid(player)) {
            return;
        }
        BlockEntity plugin = solidPlugin != null ? solidPlugin : advancedPlugin;
        if (plugin == null) return;
        CompoundTag current = createSnapshot(plugin);
        snapshot = current.copy();
        if (current.equals(lastSentSnapshot)) return;
        lastSentSnapshot = current.copy();
        PacketDistributor.sendToPlayer(player, new CoolingPluginSnapshotPayload(containerId, current));
    }

    @Override
    public boolean clickMenuButton(Player player, int id) {
        if (pluginType != PluginType.ADVANCED || advancedPlugin == null || !stillValid(player)) return false;
        InteractionHand hand = switch (id) {
            case BUTTON_INTERACT_MAIN_HAND -> InteractionHand.MAIN_HAND;
            case BUTTON_INTERACT_OFF_HAND -> InteractionHand.OFF_HAND;
            default -> null;
        };
        if (hand == null) return false;
        boolean interacted = FluidUtil.interactWithFluidHandler(player, hand, advancedPlugin.getFluidTank());
        if (!interacted) {
            interacted = advancedPlugin.getChemicalTank().interactWithHeldItem(player, hand);
        }
        if (interacted) {
            player.getInventory().setChanged();
            broadcastChanges();
        }
        return true;
    }

    @Override
    public boolean stillValid(Player player) {
        if (remoteAccess) return remoteAccessStillValid(player);
        if (player.isSpectator() || player.distanceToSqr(pluginPos.getX() + 0.5,
                pluginPos.getY() + 0.5, pluginPos.getZ() + 0.5) > 64.0) {
            return false;
        }
        if (!player.level().isClientSide) {
            BlockEntity expectedPlugin = solidPlugin != null ? solidPlugin : advancedPlugin;
            if (expectedPlugin == null || expectedPlugin.isRemoved()
                    || expectedPlugin.getLevel() != player.level()
                    || player.level().getBlockEntity(pluginPos) != expectedPlugin) {
                return false;
            }
        }
        return switch (pluginType) {
            case SOLID -> player.level().getBlockState(pluginPos).is(RegistryBlocks.QUANTUM_SOLID_COOLING_PLUGIN.get());
            case ADVANCED -> player.level().getBlockState(pluginPos).is(RegistryBlocks.QUANTUM_ADVANCED_COOLING_PLUGIN.get());
        };
    }

    private boolean remoteAccessStillValid(Player player) {
        if (player.isSpectator()
                || !player.level().getBlockState(accessControllerPos)
                .is(RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get())
                || player.distanceToSqr(accessControllerPos.getX() + 0.5,
                accessControllerPos.getY() + 0.5, accessControllerPos.getZ() + 0.5) > 64.0) {
            return false;
        }
        if (player.level().isClientSide) return true;
        BlockEntity plugin = solidPlugin != null ? solidPlugin : advancedPlugin;
        return accessController != null && endpointKey != null && plugin != null
                && accessController.canProvideRemotePluginAccess(player, plugin, endpointKey);
    }

    @Override
    public ItemStack quickMoveStack(Player player, int index) {
        if (pluginType != PluginType.SOLID || index < 0 || index >= slots.size()) return ItemStack.EMPTY;
        ItemStack source = slots.get(index).getItem();
        if (source.isEmpty()) return ItemStack.EMPTY;
        ItemStack original = source.copy();
        if (index < SOLID_PLUGIN_SLOT_COUNT) {
            if (!moveItemStackTo(source, SOLID_PLUGIN_SLOT_COUNT,
                    SOLID_PLUGIN_SLOT_COUNT + PLAYER_SLOT_COUNT, true)) {
                return ItemStack.EMPTY;
            }
        } else if (!moveItemStackTo(source, 0, SOLID_PLUGIN_SLOT_COUNT, false)) {
            return ItemStack.EMPTY;
        }
        if (source.isEmpty()) {
            slots.get(index).setByPlayer(ItemStack.EMPTY);
        } else {
            slots.get(index).setChanged();
        }
        return original;
    }

    private void addPlayerInventory(Inventory inventory) {
        for (int row = 0; row < 3; row++) {
            for (int column = 0; column < 9; column++) {
                addSlot(new net.minecraft.world.inventory.Slot(inventory,
                        column + row * 9 + 9, SLOT_COLUMN_LEFT + column * 18,
                        PLAYER_INVENTORY_SLOT_Y + row * 18));
            }
        }
        for (int column = 0; column < 9; column++) {
            addSlot(new net.minecraft.world.inventory.Slot(inventory, column,
                    SLOT_COLUMN_LEFT + column * 18, PLAYER_HOTBAR_SLOT_Y));
        }
    }

    private static CompoundTag createSnapshot(BlockEntity blockEntity) {
        CompoundTag tag = new CompoundTag();
        if (blockEntity instanceof TileQuantumSolidCoolingPlugin solid) {
            tag.putInt(TYPE_TAG, PluginType.SOLID.id);
            tag.putLong(RATE_TAG, solid.getMaxCoolingPerTickMilliKelvin());
            tag.putLong(TARGET_TAG, effectiveTarget(solid));
            return tag;
        }
        if (blockEntity instanceof TileQuantumAdvancedCoolingPlugin advanced) {
            tag.putInt(TYPE_TAG, PluginType.ADVANCED.id);
            tag.putLong(RATE_TAG, advanced.getMaxCoolingPerTickMilliKelvin());
            tag.putLong(TARGET_TAG, effectiveTarget(advanced));
            var fluid = advanced.getFluidTank().getFluid();
            ResourceLocation fluidId = fluid.isEmpty()
                    ? null
                    : BuiltInRegistries.FLUID.getKey(fluid.getFluid());
            tag.putString(FLUID_ID_TAG, fluidId == null ? "" : fluidId.toString());
            tag.putLong(FLUID_AMOUNT_TAG, advanced.getFluidTank().getFluidAmount());
            tag.putLong(FLUID_CAPACITY_TAG, advanced.getFluidTank().getCapacity());
            tag.putLong(FLUID_TARGET_TAG, QuantumCoolingMedia.targetFor(fluid));

            OptionalChemicalTank chemical = advanced.getChemicalTank();
            tag.putBoolean(CHEMICAL_AVAILABLE_TAG, chemical.isAvailable());
            tag.putString(CHEMICAL_ID_TAG, chemical.getChemicalId());
            tag.putLong(CHEMICAL_AMOUNT_TAG, chemical.getAmount());
            tag.putLong(CHEMICAL_CAPACITY_TAG, chemical.getCapacity());
            tag.putLong(CHEMICAL_TARGET_TAG, chemical.getTargetMilliKelvin());
            tag.putInt(CHEMICAL_TINT_TAG, chemical.getTint());
            return tag;
        }
        throw new IllegalArgumentException("Unsupported cooling plugin block entity: " + blockEntity);
    }

    private static long effectiveTarget(QuantumBatteryCoolingSource source) {
        long target = QuantumBatteryCoolingModel.NO_TARGET;
        for (QuantumBatteryCoolingSource.CoolingMedium medium : source.getCoolingMedia()) {
            if (medium.availableCoolingMilliKelvin() <= 0L) continue;
            target = target == QuantumBatteryCoolingModel.NO_TARGET
                    ? medium.targetMilliKelvin()
                    : Math.min(target, medium.targetMilliKelvin());
        }
        return target;
    }

    private static double targetKelvin(long targetMilliKelvin) {
        return targetMilliKelvin == QuantumBatteryCoolingModel.NO_TARGET
                ? -1.0
                : QuantumBatteryCoolingModel.kelvin(targetMilliKelvin);
    }

    public enum PluginType {
        SOLID(1),
        ADVANCED(2);

        private final int id;

        PluginType(int id) {
            this.id = id;
        }

        private static PluginType byId(int id) {
            return id == ADVANCED.id ? ADVANCED : SOLID;
        }
    }

    private record ClientContext(BlockPos pos, PluginType type, CompoundTag snapshot,
                                 boolean remoteAccess, BlockPos accessControllerPos,
                                 @Nullable TileQuantumEntanglementController accessController,
                                 @Nullable QuantumPluginEndpointKey endpointKey) {
        private static ClientContext read(Player player, RegistryFriendlyByteBuf buffer) {
            BlockPos pos = buffer.readBlockPos();
            PluginType type = PluginType.byId(buffer.readVarInt());
            CompoundTag snapshot = buffer.readNbt();
            boolean remoteAccess = buffer.readBoolean();
            BlockPos accessPos = remoteAccess ? buffer.readBlockPos() : pos;
            TileQuantumEntanglementController accessController = remoteAccess
                    && player.level().getBlockEntity(accessPos)
                    instanceof TileQuantumEntanglementController found ? found : null;
            QuantumPluginEndpointKey endpointKey = remoteAccess ? QuantumPluginEndpointKey.read(buffer) : null;
            return new ClientContext(pos, type, snapshot == null ? new CompoundTag() : snapshot,
                    remoteAccess, accessPos, accessController, endpointKey);
        }
    }
}
