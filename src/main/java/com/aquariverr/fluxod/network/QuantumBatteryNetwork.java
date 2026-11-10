package com.aquariverr.fluxod.network;

import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import com.aquariverr.fluxod.menu.QuantumEntanglementMenu;
import com.aquariverr.fluxod.menu.RemoteFluxMenu;
import com.aquariverr.fluxod.menu.QuantumCoolingPluginMenu;
import com.aquariverr.fluxod.menu.QuantumFeedbackPluginMenu;
import com.aquariverr.fluxod.menu.MiniatureUniverseMenu;
import com.aquariverr.fluxod.entanglement.QuantumPluginEndpointKey;
import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.PacketDistributor;
import sonar.fluxnetworks.common.connection.FluxMenu;

public final class QuantumBatteryNetwork {

    public static void register(RegisterPayloadHandlersEvent event) {
        var registrar = event.registrar("1");
        registrar.playToServer(OpenQuantumBatteryPayload.TYPE,
                OpenQuantumBatteryPayload.STREAM_CODEC, QuantumBatteryNetwork::handleOpenQuantumBattery);
        registrar.playToServer(BindQuantumBatteryPayload.TYPE,
                BindQuantumBatteryPayload.STREAM_CODEC, QuantumBatteryNetwork::handleBindQuantumBattery);
        registrar.playToClient(RemoteFluxSnapshotPayload.TYPE,
                RemoteFluxSnapshotPayload.STREAM_CODEC, QuantumBatteryNetwork::handleRemoteFluxSnapshot);
        registrar.playToClient(CoolingPluginSnapshotPayload.TYPE,
                CoolingPluginSnapshotPayload.STREAM_CODEC, QuantumBatteryNetwork::handleCoolingPluginSnapshot);
        registrar.playToClient(EntanglementPluginDirectoryPayload.TYPE,
                EntanglementPluginDirectoryPayload.STREAM_CODEC,
                QuantumBatteryNetwork::handleEntanglementPluginDirectory);
        registrar.playToClient(QuantumBatteryRenderStatePayload.TYPE,
                QuantumBatteryRenderStatePayload.STREAM_CODEC,
                QuantumBatteryNetwork::handleQuantumBatteryRenderState);
        registrar.playToServer(SelectEntanglementPluginPayload.TYPE,
                SelectEntanglementPluginPayload.STREAM_CODEC,
                QuantumBatteryNetwork::handleSelectEntanglementPlugin);
        registrar.playToClient(FeedbackPluginSnapshotPayload.TYPE,
                FeedbackPluginSnapshotPayload.STREAM_CODEC,
                QuantumBatteryNetwork::handleFeedbackPluginSnapshot);
        registrar.playToServer(UpdateFeedbackCurvePayload.TYPE,
                UpdateFeedbackCurvePayload.STREAM_CODEC,
                QuantumBatteryNetwork::handleUpdateFeedbackCurve);
        var miniatureRegistrar = event.registrar("13");
        miniatureRegistrar.playToServer(MiniatureFactoryCommandPayload.TYPE,
                MiniatureFactoryCommandPayload.STREAM_CODEC, QuantumBatteryNetwork::handleMiniatureCommand);
        miniatureRegistrar.playToClient(MiniatureFactorySnapshotPayload.TYPE,
                MiniatureFactorySnapshotPayload.STREAM_CODEC, QuantumBatteryNetwork::handleMiniatureSnapshot);
    }

    private static void handleMiniatureCommand(MiniatureFactoryCommandPayload payload,
                                               net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player && player.containerMenu instanceof MiniatureUniverseMenu menu) {
                var response = menu.handleCommand(player, payload);
                // Deliver the authoritative cursor before the acknowledgement unlocks the next GUI action.
                menu.broadcastChanges();
                PacketDistributor.sendToPlayer(player, response);
            }
        });
    }

    private static void handleMiniatureSnapshot(MiniatureFactorySnapshotPayload payload,
                                                net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof MiniatureUniverseMenu menu) menu.applySnapshot(payload);
        });
    }

    private static void handleQuantumBatteryRenderState(QuantumBatteryRenderStatePayload payload,
                                                          net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().level().getBlockEntity(payload.controllerPos())
                    instanceof TileQuantumBatteryController controller) {
                controller.applyClientRenderState(payload.energyLogBasisPoints(),
                        payload.stabilityBasisPoints(), payload.leakEventSequence(),
                        payload.constraintPowered(), payload.instabilityPenaltiesEnabled());
            }
        });
    }

    private static void handleEntanglementPluginDirectory(EntanglementPluginDirectoryPayload payload,
                                                            net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof QuantumEntanglementMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.applyPluginDirectory(payload.snapshot());
            }
        });
    }

    private static void handleSelectEntanglementPlugin(SelectEntanglementPluginPayload payload,
                                                        net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || player.isSpectator()
                    || !(player.containerMenu instanceof QuantumEntanglementMenu menu)
                    || menu.containerId != payload.containerId()
                    || !menu.getControllerPos().equals(payload.controllerPos())
                    || !menu.stillValid(player)) {
                return;
            }
            QuantumPluginEndpointKey key = QuantumPluginEndpointKey.load(payload.endpointKey());
            if (key != null) menu.selectEndpoint(player, key, payload.directoryRevision());
        });
    }

    private static void handleCoolingPluginSnapshot(CoolingPluginSnapshotPayload payload,
                                                     net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof QuantumCoolingPluginMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.applySnapshot(payload.snapshot());
            }
        });
    }

    private static void handleFeedbackPluginSnapshot(FeedbackPluginSnapshotPayload payload,
                                                      net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof QuantumFeedbackPluginMenu menu
                    && menu.containerId == payload.containerId()) {
                menu.applySnapshot(payload.snapshot());
            }
        });
    }

    private static void handleUpdateFeedbackCurve(UpdateFeedbackCurvePayload payload,
                                                   net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player() instanceof ServerPlayer player
                    && !player.isSpectator()
                    && player.containerMenu instanceof QuantumFeedbackPluginMenu menu
                    && menu.containerId == payload.containerId()
                    && menu.stillValid(player)) {
                menu.updateCurve(player, payload.points());
            }
        });
    }

    private static void handleRemoteFluxSnapshot(RemoteFluxSnapshotPayload payload,
                                                  net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (context.player().containerMenu instanceof RemoteFluxMenu menu) {
                menu.applyRemoteSnapshot(context.player(), payload.targetPos(), payload.updateTag());
            }
        });
    }

    private static void handleBindQuantumBattery(BindQuantumBatteryPayload payload,
                                                  net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)
                    || player.isSpectator()
                    || !(player.containerMenu instanceof QuantumEntanglementMenu menu)
                    || menu.containerId != payload.containerId()
                    || !menu.getControllerPos().equals(payload.controllerPos())
                    || !menu.stillValid(player)) return;

            ResourceKey<Level> dimension = player.serverLevel().dimension();
            String dimensionInput = payload.dimensionId().trim();
            if (!dimensionInput.isEmpty()) {
                ResourceLocation dimensionId = ResourceLocation.tryParse(dimensionInput);
                if (dimensionId == null) {
                    player.displayClientMessage(Component.translatable(
                            "message.flux_overdrive.quantum_entanglement.invalid_dimension"), true);
                    return;
                }
                dimension = ResourceKey.create(Registries.DIMENSION, dimensionId);
            }

            var targetLevel = player.getServer().getLevel(dimension);
            if (targetLevel == null) {
                player.displayClientMessage(Component.translatable(
                        "message.flux_overdrive.quantum_entanglement.invalid_dimension"), true);
                return;
            }
            BlockPos targetPos = new BlockPos(payload.targetX(), payload.targetY(), payload.targetZ());
            if (!targetLevel.isInWorldBounds(targetPos)) {
                player.displayClientMessage(Component.translatable(
                        "message.flux_overdrive.quantum_entanglement.invalid_coordinates"), true);
                return;
            }

            boolean bound = menu.bindBattery(player, GlobalPos.of(dimension, targetPos));
            player.displayClientMessage(Component.translatable(bound
                    ? "message.flux_overdrive.quantum_battery_bound_to_facility"
                    : "message.flux_overdrive.quantum_battery_bind_failed"), true);
        });
    }

    private static void handleOpenQuantumBattery(OpenQuantumBatteryPayload payload,
                                                  net.neoforged.neoforge.network.handling.IPayloadContext context) {
        context.enqueueWork(() -> {
            if (!(context.player() instanceof ServerPlayer player)) return;
            if (player.isSpectator()) return;
            if (player.containerMenu instanceof RemoteFluxMenu remoteMenu) {
                remoteMenu.returnToQuantumBattery(player, payload.controllerPos());
                return;
            }
            if (!(player.containerMenu instanceof FluxMenu menu)
                    || !(menu.mProvider instanceof TileQuantumBatteryController menuController)
                    || !menuController.getBlockPos().equals(payload.controllerPos())) return;
            if (!player.serverLevel().isLoaded(payload.controllerPos())) return;
            if (player.distanceToSqr(Vec3.atCenterOf(payload.controllerPos())) > 64.0) return;
            if (player.serverLevel().getBlockEntity(payload.controllerPos())
                    instanceof TileQuantumBatteryController controller && controller == menuController
                    && controller.canPlayerManage(player)) {
                controller.openQuantumMenu(player);
            }
        });
    }

    private QuantumBatteryNetwork() {}
}
