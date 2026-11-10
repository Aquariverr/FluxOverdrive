package com.aquariverr.fluxod.mixin.client;

import com.aquariverr.fluxod.menu.RemoteFluxMenu;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;
import sonar.fluxnetworks.register.ClientMessages;

import java.util.function.Supplier;

@Mixin(value = ClientMessages.class, remap = false)
public abstract class RemoteFluxClientMessagesMixin {

    @Redirect(method = "lambda$onDeviceBuffer$0(Ljava/util/function/Supplier;"
            + "Lnet/minecraft/network/FriendlyByteBuf;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/client/multiplayer/ClientLevel;getBlockEntity("
                            + "Lnet/minecraft/core/BlockPos;)Lnet/minecraft/world/level/block/entity/BlockEntity;",
                    ordinal = 0, remap = true), remap = false, require = 1)
    private static BlockEntity fluxod$resolveRemoteBufferTarget(ClientLevel level, BlockPos lookupPos,
                                                                 Supplier<LocalPlayer> playerSupplier,
                                                                 FriendlyByteBuf payload) {
        LocalPlayer player = playerSupplier.get();
        if (player.containerMenu instanceof RemoteFluxMenu menu) {
            return menu.resolveRemoteDevice(player, lookupPos);
        }
        return level.getBlockEntity(lookupPos);
    }
}
