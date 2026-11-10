package com.aquariverr.fluxod.menu;

import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementController;
import com.aquariverr.fluxod.mixin.accessor.TileFluxDeviceAccessor;
import com.aquariverr.fluxod.network.RemoteFluxSnapshotPayload;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.neoforged.neoforge.network.PacketDistributor;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.common.connection.FluxMenu;

import javax.annotation.Nullable;
import java.util.UUID;

public final class RemoteFluxMenu extends FluxMenu {

    public static final int OPENING_MAGIC = 0x51;

    private final TileQuantumBatteryController menuDevice;
    @Nullable
    private final TileQuantumBatteryController serverController;
    @Nullable
    private final TileQuantumEntanglementController accessController;
    private final GlobalPos targetPos;
    private final BlockPos accessControllerPos;
    private final Player menuPlayer;
    @Nullable
    private RemoteSettings lastSettings;

    public RemoteFluxMenu(int containerId, Inventory inventory,
                          TileQuantumBatteryController controller,
                          TileQuantumEntanglementController accessController) {
        this(containerId, inventory, controller, controller, accessController,
                globalPos(controller), accessController.getBlockPos());
    }

    public RemoteFluxMenu(int containerId, Inventory inventory,
                          TileQuantumBatteryController clientProxy,
                          GlobalPos targetPos, BlockPos accessControllerPos) {
        this(containerId, inventory, clientProxy, null, null, targetPos, accessControllerPos);
    }

    private RemoteFluxMenu(int containerId, Inventory inventory,
                           TileQuantumBatteryController menuDevice,
                           @Nullable TileQuantumBatteryController serverController,
                           @Nullable TileQuantumEntanglementController accessController,
                           GlobalPos targetPos, BlockPos accessControllerPos) {
        super(containerId, inventory, menuDevice);
        this.menuDevice = menuDevice;
        this.serverController = serverController;
        this.accessController = accessController;
        this.targetPos = targetPos;
        this.accessControllerPos = accessControllerPos.immutable();
        this.menuPlayer = inventory.player;
        this.lastSettings = serverController == null ? null : RemoteSettings.capture(serverController);
    }

    @Override
    public boolean stillValid(Player player) {
        if (player.isSpectator()
                || !player.level().getBlockState(accessControllerPos)
                .is(RegistryBlocks.QUANTUM_BATTERY_ENTANGLEMENT_CONTROLLER.get())
                || player.distanceToSqr(accessControllerPos.getX() + 0.5,
                accessControllerPos.getY() + 0.5, accessControllerPos.getZ() + 0.5) > 64.0) {
            return false;
        }
        if (player.level().isClientSide) return true;
        return serverController != null
                && accessController != null
                && player.level().getBlockEntity(accessControllerPos) == accessController
                && accessController.canProvideRemoteAccess(player, serverController);
    }

    @Nullable
    public TileQuantumBatteryController resolveRemoteDevice(Player player, BlockPos lookupPos) {
        if (player.containerMenu != this
                || !targetPos.pos().equals(lookupPos)
                || !stillValid(player)) {
            return null;
        }
        return player.level().isClientSide ? menuDevice : serverController;
    }

    public boolean returnToQuantumBattery(ServerPlayer player, BlockPos requestedPos) {
        if (!targetPos.pos().equals(requestedPos)
                || player.containerMenu != this
                || serverController == null
                || accessController == null
                || !stillValid(player)) {
            return false;
        }
        serverController.openQuantumMenuFromEntanglement(player, accessController);
        return true;
    }

    public boolean applyRemoteSnapshot(Player player, GlobalPos requestedTarget, CompoundTag updateTag) {
        if (!player.level().isClientSide
                || player.containerMenu != this
                || !targetPos.equals(requestedTarget)
                || !stillValid(player)) {
            return false;
        }
        menuDevice.readCustomTag(updateTag, FluxConstants.NBT_TILE_UPDATE);
        ((TileFluxDeviceAccessor) (Object) menuDevice).fluxod$setGlobalPos(targetPos);
        return true;
    }

    @Override
    public void broadcastChanges() {
        super.broadcastChanges();
        if (!(menuPlayer instanceof ServerPlayer player)
                || serverController == null
                || player.containerMenu != this
                || !stillValid(player)) {
            return;
        }

        RemoteSettings settings = RemoteSettings.capture(serverController);
        if (settings.equals(lastSettings)) return;
        lastSettings = settings;

        CompoundTag updateTag = new CompoundTag();
        serverController.writeCustomTag(updateTag, FluxConstants.NBT_TILE_UPDATE);
        PacketDistributor.sendToPlayer(player, new RemoteFluxSnapshotPayload(targetPos, updateTag));
    }

    public static void writeOpeningData(RegistryFriendlyByteBuf buffer, GlobalPos targetPos,
                                        BlockPos accessControllerPos, CompoundTag updateTag) {
        buffer.writeByte(OPENING_MAGIC);
        GlobalPos.STREAM_CODEC.encode(buffer, targetPos);
        buffer.writeBlockPos(accessControllerPos);
        buffer.writeNbt(updateTag);
    }

    private static GlobalPos globalPos(TileQuantumBatteryController controller) {
        Level level = controller.getLevel();
        if (level == null) throw new IllegalStateException("Remote Flux controller has no level");
        return GlobalPos.of(level.dimension(), controller.getBlockPos());
    }

    private record RemoteSettings(int networkId, String customName, @Nullable UUID owner,
                                  int priority, long limit, boolean disableLimit,
                                  boolean surgeMode, boolean forcedLoading, int networkColor) {

        private static RemoteSettings capture(TileQuantumBatteryController controller) {
            return new RemoteSettings(controller.getNetworkID(), controller.getCustomName(),
                    controller.getOwnerUUID(), controller.getRawPriority(), controller.getRawLimit(),
                    controller.getDisableLimit(), controller.getSurgeMode(), controller.isForcedLoading(),
                    controller.getNetworkColorRgb());
        }
    }
}
