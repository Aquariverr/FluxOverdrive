package com.aquariverr.fluxod.mixin;

import com.aquariverr.fluxod.menu.RemoteFluxMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import sonar.fluxnetworks.api.network.SecurityLevel;
import sonar.fluxnetworks.register.Messages;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

@Mixin(value = Messages.class, remap = false)
public abstract class RemoteFluxMessagesMixin {

    @Inject(method = "lambda$onCreateNetwork$3(Ljava/util/function/Supplier;ILjava/lang/String;I"
            + "Lsonar/fluxnetworks/api/network/SecurityLevel;Ljava/lang/String;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void fluxod$validateRemoteCreateNetwork(Supplier<ServerPlayer> playerSupplier,
                                                            int token, String name, int color,
                                                            SecurityLevel security, String password,
                                                            CallbackInfo callback) {
        fluxod$cancelInvalidRemoteSession(playerSupplier, callback);
    }

    @Inject(method = "lambda$onDeleteNetwork$4(Ljava/util/function/Supplier;II)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void fluxod$validateRemoteDeleteNetwork(Supplier<ServerPlayer> playerSupplier,
                                                            int networkId, int token,
                                                            CallbackInfo callback) {
        fluxod$cancelInvalidRemoteSession(playerSupplier, callback);
    }

    @Inject(method = "lambda$onEditNetwork$6(Ljava/util/function/Supplier;IILjava/lang/String;I"
            + "Lsonar/fluxnetworks/api/network/SecurityLevel;Ljava/lang/String;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void fluxod$validateRemoteEditNetwork(Supplier<ServerPlayer> playerSupplier,
                                                          int networkId, int token, String name, int color,
                                                          SecurityLevel security, String password,
                                                          CallbackInfo callback) {
        fluxod$cancelInvalidRemoteSession(playerSupplier, callback);
    }

    @Inject(method = "lambda$onUpdateNetwork$7(Ljava/util/function/Supplier;I[IB)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void fluxod$validateRemoteUpdateNetwork(Supplier<ServerPlayer> playerSupplier,
                                                            int token, int[] networkIds, byte updateType,
                                                            CallbackInfo callback) {
        fluxod$cancelInvalidRemoteSession(playerSupplier, callback);
    }

    @Inject(method = "lambda$onEditMember$8(Ljava/util/function/Supplier;IILjava/util/UUID;B)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void fluxod$validateRemoteEditMember(Supplier<ServerPlayer> playerSupplier,
                                                         int networkId, int token, UUID memberId,
                                                         byte permission, CallbackInfo callback) {
        fluxod$cancelInvalidRemoteSession(playerSupplier, callback);
    }

    @Inject(method = "lambda$onEditConnection$9(Ljava/util/function/Supplier;IILjava/util/List;"
            + "Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void fluxod$validateRemoteEditConnection(Supplier<ServerPlayer> playerSupplier,
                                                             int networkId, int token, List<?> connections,
                                                             CompoundTag settings, CallbackInfo callback) {
        fluxod$cancelInvalidRemoteSession(playerSupplier, callback);
    }

    @Inject(method = "lambda$onWirelessMode$10(Ljava/util/function/Supplier;III)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void fluxod$validateRemoteWirelessMode(Supplier<ServerPlayer> playerSupplier,
                                                           int networkId, int token, int wirelessMode,
                                                           CallbackInfo callback) {
        fluxod$cancelInvalidRemoteSession(playerSupplier, callback);
    }

    @Inject(method = "lambda$onDisconnect$11(Ljava/util/function/Supplier;IILjava/util/List;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void fluxod$validateRemoteDisconnect(Supplier<ServerPlayer> playerSupplier,
                                                         int networkId, int token, List<?> connections,
                                                         CallbackInfo callback) {
        fluxod$cancelInvalidRemoteSession(playerSupplier, callback);
    }

    @Inject(method = "lambda$onUpdateConnections$12(Ljava/util/function/Supplier;IILjava/util/List;)V",
            at = @At("HEAD"), cancellable = true, remap = false, require = 1)
    private static void fluxod$validateRemoteUpdateConnections(Supplier<ServerPlayer> playerSupplier,
                                                                int networkId, int token,
                                                                List<?> connections,
                                                                CallbackInfo callback) {
        fluxod$cancelInvalidRemoteSession(playerSupplier, callback);
    }

    @Redirect(method = "lambda$onEditTile$2(Ljava/util/function/Supplier;ILnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;isLoaded(Lnet/minecraft/core/BlockPos;)Z",
                    ordinal = 0, remap = true), remap = false, require = 1)
    private static boolean fluxod$isRemoteEditTargetLoaded(Level level, BlockPos lookupPos,
                                                            Supplier<ServerPlayer> playerSupplier,
                                                            int token, BlockPos pos, CompoundTag tag) {
        ServerPlayer player = playerSupplier.get();
        if (player.containerMenu instanceof RemoteFluxMenu menu) {
            return menu.resolveRemoteDevice(player, lookupPos) != null;
        }
        return level.isLoaded(lookupPos);
    }

    @Redirect(method = "lambda$onEditTile$2(Ljava/util/function/Supplier;ILnet/minecraft/core/BlockPos;"
            + "Lnet/minecraft/nbt/CompoundTag;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)"
                            + "Lnet/minecraft/world/level/block/entity/BlockEntity;",
                    ordinal = 0, remap = true), remap = false, require = 1)
    private static BlockEntity fluxod$resolveRemoteEditTarget(Level level, BlockPos lookupPos,
                                                               Supplier<ServerPlayer> playerSupplier,
                                                               int token, BlockPos pos, CompoundTag tag) {
        return fluxod$resolveTarget(level, lookupPos, playerSupplier.get());
    }

    @Redirect(method = "lambda$onTileNetwork$5(Ljava/util/function/Supplier;Lnet/minecraft/core/BlockPos;"
            + "IILjava/lang/String;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)"
                            + "Lnet/minecraft/world/level/block/entity/BlockEntity;",
                    ordinal = 0, remap = true), remap = false, require = 1)
    private static BlockEntity fluxod$resolveRemoteNetworkTarget(Level level, BlockPos lookupPos,
                                                                  Supplier<ServerPlayer> playerSupplier,
                                                                  BlockPos pos, int networkId, int token,
                                                                  String password) {
        return fluxod$resolveTarget(level, lookupPos, playerSupplier.get());
    }

    @Redirect(method = "lambda$onDeviceBuffer$0(Ljava/util/function/Supplier;"
            + "Lnet/minecraft/network/FriendlyByteBuf;)V",
            at = @At(value = "INVOKE",
                    target = "Lnet/minecraft/world/level/Level;getBlockEntity(Lnet/minecraft/core/BlockPos;)"
                            + "Lnet/minecraft/world/level/block/entity/BlockEntity;",
                    ordinal = 0, remap = true), remap = false, require = 1)
    private static BlockEntity fluxod$resolveRemoteBufferTarget(Level level, BlockPos lookupPos,
                                                                 Supplier<ServerPlayer> playerSupplier,
                                                                 FriendlyByteBuf payload) {
        return fluxod$resolveTarget(level, lookupPos, playerSupplier.get());
    }

    @Nullable
    private static BlockEntity fluxod$resolveTarget(Level level, BlockPos lookupPos, ServerPlayer player) {
        if (player.containerMenu instanceof RemoteFluxMenu menu) {
            return menu.resolveRemoteDevice(player, lookupPos);
        }
        return level.getBlockEntity(lookupPos);
    }

    private static void fluxod$cancelInvalidRemoteSession(Supplier<ServerPlayer> playerSupplier,
                                                           CallbackInfo callback) {
        ServerPlayer player = playerSupplier.get();
        if (player.containerMenu instanceof RemoteFluxMenu menu && !menu.stillValid(player)) {
            callback.cancel();
        }
    }
}
