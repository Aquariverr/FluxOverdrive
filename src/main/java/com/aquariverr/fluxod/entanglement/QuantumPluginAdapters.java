package com.aquariverr.fluxod.entanglement;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.entity.TileQuantumAdvancedCoolingPlugin;
import com.aquariverr.fluxod.block.entity.TileQuantumEnergyConstraintInterface;
import com.aquariverr.fluxod.block.entity.TileQuantumEntanglementController;
import com.aquariverr.fluxod.block.entity.TileQuantumSolidCoolingPlugin;
import com.aquariverr.fluxod.block.entity.TileQuantumFeedbackPlugin;
import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.register.RegistryBlocks;
import com.aquariverr.fluxod.integration.mekanism.QuantumCoolingChemicalIntegration;
import com.aquariverr.fluxod.multiblock.QuantumBatteryPlugin;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;

public final class QuantumPluginAdapters {

    public static final ResourceLocation CONSTRAINT_ID = FluxOverdrive.location("constraint_interface");
    public static final ResourceLocation SOLID_COOLING_ID = FluxOverdrive.location("solid_cooling");
    public static final ResourceLocation ADVANCED_COOLING_ID = FluxOverdrive.location("advanced_cooling");
    public static final ResourceLocation STABILITY_FEEDBACK_ID = FluxOverdrive.location("stability_feedback");
    public static final ResourceLocation TEMPERATURE_FEEDBACK_ID = FluxOverdrive.location("temperature_feedback");
    public static final ResourceLocation MINIATURE_UNIVERSE_ID = FluxOverdrive.location("miniature_universe");
    public static final ResourceLocation UNSUPPORTED_ID = FluxOverdrive.location("unsupported_plugin");

    private static final List<QuantumPluginAdapter> ADAPTERS = new CopyOnWriteArrayList<>();
    private static final QuantumPluginAdapter UNSUPPORTED = new BasicAdapter(UNSUPPORTED_ID, 0, false) {
        @Override
        public boolean matches(BlockState state, @Nullable BlockEntity blockEntity) {
            return state.getBlock() instanceof QuantumBatteryPlugin;
        }
    };

    static {
        register(new BasicAdapter(CONSTRAINT_ID, 0, false) {
            @Override
            public boolean matches(BlockState state, @Nullable BlockEntity blockEntity) {
                return blockEntity instanceof TileQuantumEnergyConstraintInterface;
            }
        });
        register(new BasicAdapter(SOLID_COOLING_ID, EntanglementResourceKind.ITEM.mask(), true) {
            @Override
            public boolean matches(BlockState state, @Nullable BlockEntity blockEntity) {
                return blockEntity instanceof TileQuantumSolidCoolingPlugin;
            }

            @Override
            public boolean openRemoteMenu(ServerPlayer player, BlockEntity blockEntity,
                                          TileQuantumEntanglementController accessController,
                                          QuantumPluginEndpointKey endpointKey) {
                if (!(blockEntity instanceof TileQuantumSolidCoolingPlugin plugin)) return false;
                plugin.openMenuFromEntanglement(player, accessController, endpointKey);
                return true;
            }
        });
        register(new BasicAdapter(ADVANCED_COOLING_ID,
                EntanglementResourceKind.FLUID.mask() | EntanglementResourceKind.CHEMICAL.mask(), true) {
            @Override
            public boolean matches(BlockState state, @Nullable BlockEntity blockEntity) {
                return blockEntity instanceof TileQuantumAdvancedCoolingPlugin;
            }

            @Override
            public int inputResources(BlockState state, @Nullable BlockEntity blockEntity) {
                int resources = EntanglementResourceKind.FLUID.mask();
                if (QuantumCoolingChemicalIntegration.isAvailable()) {
                    resources |= EntanglementResourceKind.CHEMICAL.mask();
                }
                return resources;
            }

            @Override
            public boolean openRemoteMenu(ServerPlayer player, BlockEntity blockEntity,
                                          TileQuantumEntanglementController accessController,
                                          QuantumPluginEndpointKey endpointKey) {
                if (!(blockEntity instanceof TileQuantumAdvancedCoolingPlugin plugin)) return false;
                plugin.openMenuFromEntanglement(player, accessController, endpointKey);
                return true;
            }
        });
        register(feedbackAdapter(STABILITY_FEEDBACK_ID,
                RegistryBlocks.QUANTUM_STABILITY_FEEDBACK_PLUGIN.get()));
        register(feedbackAdapter(TEMPERATURE_FEEDBACK_ID,
                RegistryBlocks.QUANTUM_TEMPERATURE_FEEDBACK_PLUGIN.get()));
        register(new BasicAdapter(MINIATURE_UNIVERSE_ID, 0, true) {
            @Override
            public boolean matches(BlockState state, @Nullable BlockEntity blockEntity) {
                return state.is(RegistryBlocks.MINIATURE_UNIVERSE.get())
                        && blockEntity instanceof TileMiniatureUniverse;
            }

            @Override
            public int inputResources(BlockState state, @Nullable BlockEntity blockEntity) {
                return EntanglementResourceKind.ITEM.mask() | EntanglementResourceKind.FLUID.mask()
                        | (QuantumCoolingChemicalIntegration.isAvailable() ? EntanglementResourceKind.CHEMICAL.mask() : 0);
            }

            @Override
            public int outputResources(BlockState state, @Nullable BlockEntity blockEntity) {
                return inputResources(state, blockEntity);
            }

            @Override
            public boolean openRemoteMenu(ServerPlayer player, BlockEntity blockEntity,
                                          TileQuantumEntanglementController accessController,
                                          QuantumPluginEndpointKey endpointKey) {
                return blockEntity instanceof TileMiniatureUniverse plugin
                        && plugin.openMenuFromEntanglement(player, accessController, endpointKey);
            }
        });
    }

    public static void register(QuantumPluginAdapter adapter) {
        Objects.requireNonNull(adapter, "adapter");
        for (QuantumPluginAdapter registered : ADAPTERS) {
            if (registered.id().equals(adapter.id())) {
                throw new IllegalArgumentException("Duplicate quantum plugin adapter: " + adapter.id());
            }
        }
        ADAPTERS.add(adapter);
    }

    public static QuantumPluginAdapter find(BlockState state, @Nullable BlockEntity blockEntity) {
        for (QuantumPluginAdapter adapter : ADAPTERS) {
            if (adapter.matches(state, blockEntity)) return adapter;
        }
        return UNSUPPORTED;
    }

    public static QuantumPluginEndpoint describe(BlockPos batteryPos, BlockPos pluginPos, int ringTier,
                                                 BlockState state, @Nullable BlockEntity blockEntity) {
        QuantumPluginAdapter adapter = find(state, blockEntity);
        BlockPos relativePos = pluginPos.subtract(batteryPos);
        ResourceLocation iconItemId = BuiltInRegistries.ITEM.getKey(state.getBlock().asItem());
        if (iconItemId == null) iconItemId = BuiltInRegistries.ITEM.getKey(Items.BARRIER);
        QuantumPluginEndpointKey key = new QuantumPluginEndpointKey(adapter.id(), relativePos,
                adapter.localEndpointId(state, blockEntity));
        return new QuantumPluginEndpoint(key, pluginPos, ringTier,
                QuantumPluginLocation.fromRelativeOffset(relativePos.getX(), relativePos.getY(), relativePos.getZ()),
                iconItemId,
                state.getBlock().getDescriptionId(), adapter.inputResources(state, blockEntity),
                adapter.outputResources(state, blockEntity),
                adapter.supportsRemoteMenu(state, blockEntity));
    }

    @Nullable
    public static QuantumPluginAdapter find(ResourceLocation id) {
        if (UNSUPPORTED_ID.equals(id)) return UNSUPPORTED;
        for (QuantumPluginAdapter adapter : ADAPTERS) {
            if (adapter.id().equals(id)) return adapter;
        }
        return null;
    }

    public static boolean isConstraint(QuantumPluginEndpointKey key) {
        return key != null && CONSTRAINT_ID.equals(key.adapterId());
    }

    public static boolean isConstraint(QuantumPluginEndpoint endpoint) {
        return endpoint != null && isConstraint(endpoint.key());
    }

    private abstract static class BasicAdapter implements QuantumPluginAdapter {
        private final ResourceLocation id;
        private final int inputResources;
        private final boolean remoteMenu;

        private BasicAdapter(ResourceLocation id, int inputResources, boolean remoteMenu) {
            this.id = id;
            this.inputResources = inputResources;
            this.remoteMenu = remoteMenu;
        }

        @Override
        public ResourceLocation id() {
            return id;
        }

        @Override
        public int inputResources(BlockState state, @Nullable BlockEntity blockEntity) {
            return inputResources;
        }

        @Override
        public boolean supportsRemoteMenu(BlockState state, @Nullable BlockEntity blockEntity) {
            return remoteMenu;
        }
    }

    private static QuantumPluginAdapter feedbackAdapter(ResourceLocation id,
                                                         net.minecraft.world.level.block.Block block) {
        return new BasicAdapter(id, 0, true) {
            @Override
            public boolean matches(BlockState state, @Nullable BlockEntity blockEntity) {
                return state.is(block) && blockEntity instanceof TileQuantumFeedbackPlugin;
            }

            @Override
            public boolean openRemoteMenu(ServerPlayer player, BlockEntity blockEntity,
                                          TileQuantumEntanglementController accessController,
                                          QuantumPluginEndpointKey endpointKey) {
                if (!(blockEntity instanceof TileQuantumFeedbackPlugin plugin)) return false;
                plugin.openMenuFromEntanglement(player, accessController, endpointKey);
                return true;
            }
        };
    }

    private QuantumPluginAdapters() {}
}
