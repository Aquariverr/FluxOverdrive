package com.aquariverr.fluxod.mixin.client;

import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import com.aquariverr.fluxod.menu.RemoteFluxMenu;
import com.aquariverr.fluxod.mixin.accessor.TileFluxDeviceAccessor;
import com.aquariverr.fluxod.register.RegistryBlocks;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.world.entity.player.Inventory;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import sonar.fluxnetworks.api.FluxConstants;
import sonar.fluxnetworks.common.connection.FluxMenu;
import sonar.fluxnetworks.register.RegistryMenuTypes;

@Mixin(value = RegistryMenuTypes.class, remap = false)
public abstract class RegistryMenuTypesMixin {

    @Inject(method = "lambda$register$0(ILnet/minecraft/world/entity/player/Inventory;"
            + "Lnet/minecraft/network/RegistryFriendlyByteBuf;)Lsonar/fluxnetworks/common/connection/FluxMenu;",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void fluxod$createRemoteFluxMenu(int containerId, Inventory inventory,
                                                     RegistryFriendlyByteBuf buffer,
                                                     CallbackInfoReturnable<FluxMenu> callback) {
        int readerIndex = buffer.readerIndex();
        if (!buffer.isReadable() || buffer.readUnsignedByte() != RemoteFluxMenu.OPENING_MAGIC) {
            buffer.readerIndex(readerIndex);
            return;
        }

        GlobalPos targetPos = GlobalPos.STREAM_CODEC.decode(buffer);
        BlockPos accessControllerPos = buffer.readBlockPos();
        CompoundTag updateTag = buffer.readNbt();
        if (updateTag == null) updateTag = new CompoundTag();

        TileQuantumBatteryController proxy = new TileQuantumBatteryController(targetPos.pos(),
                RegistryBlocks.QUANTUM_BATTERY_CONTROLLER.get().defaultBlockState());
        proxy.markAsRemoteFluxProxy();
        proxy.setLevel(inventory.player.level());
        proxy.readCustomTag(updateTag, FluxConstants.NBT_TILE_UPDATE);
        ((TileFluxDeviceAccessor) (Object) proxy).fluxod$setGlobalPos(targetPos);
        callback.setReturnValue(new RemoteFluxMenu(containerId, inventory, proxy,
                targetPos, accessControllerPos));
    }
}
