package com.aquariverr.fluxod.integration.jade;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.block.FluxAutoLinkCrystalBlock;
import com.aquariverr.fluxod.block.FluxAutoExtractCrystalBlock;
import com.aquariverr.fluxod.block.FluxExtractCrystalBlock;
import com.aquariverr.fluxod.block.FluxFEStorageBlock;
import com.aquariverr.fluxod.block.FluxLinkCrystalBlock;
import com.aquariverr.fluxod.block.FluxStorageTerminalBlock;
import com.aquariverr.fluxod.block.entity.TileFluxFEStorage;
import com.aquariverr.fluxod.block.entity.TileFluxWirelessCrystal;
import com.aquariverr.fluxod.block.entity.TileFluxAutoCrystal;
import com.aquariverr.fluxod.block.QuantumBatteryControllerBlock;
import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;

import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec2;
import snownee.jade.api.*;
import snownee.jade.api.config.IPluginConfig;
import snownee.jade.api.ui.Element;
import snownee.jade.api.ui.IElementHelper;

import javax.annotation.Nullable;
import java.util.Locale;
import sonar.fluxnetworks.api.FluxTranslate;
import sonar.fluxnetworks.api.device.FluxDeviceType;
import sonar.fluxnetworks.api.energy.EnergyType;
import sonar.fluxnetworks.client.ClientCache;
import sonar.fluxnetworks.common.block.FluxControllerBlock;
import sonar.fluxnetworks.common.block.FluxPlugBlock;
import sonar.fluxnetworks.common.block.FluxPointBlock;
import sonar.fluxnetworks.common.connection.FluxNetwork;
import sonar.fluxnetworks.common.connection.TransferHandler;
import sonar.fluxnetworks.common.device.*;
import sonar.fluxnetworks.common.util.FluxUtils;

@SuppressWarnings("unused")
@WailaPlugin(FluxOverdrive.MODID)
public class JadePlugin implements IWailaPlugin {

    @Override
    public void register(IWailaCommonRegistration registration) {
        registration.registerBlockDataProvider(FluxDeviceServerProvider.INSTANCE, TileFluxDevice.class);
    }

    @Override
    public void registerClient(IWailaClientRegistration registration) {
        registerFnBlocks(registration, BuiltinRemover.INSTANCE);
        registerFnBlocks(registration, FluxDeviceProvider.INSTANCE);
        registration.registerBlockComponent(BuiltinRemover.INSTANCE, FluxFEStorageBlock.class);
         registration.registerBlockComponent(BuiltinRemover.INSTANCE, FluxLinkCrystalBlock.class);
         registration.registerBlockComponent(BuiltinRemover.INSTANCE, FluxExtractCrystalBlock.class);
         registration.registerBlockComponent(BuiltinRemover.INSTANCE, FluxAutoLinkCrystalBlock.class);
        registration.registerBlockComponent(BuiltinRemover.INSTANCE, FluxAutoExtractCrystalBlock.class);
        registration.registerBlockComponent(BuiltinRemover.INSTANCE,
                com.aquariverr.fluxod.block.FluxStorageBlock.Ender.class);
        registration.registerBlockComponent(BuiltinRemover.INSTANCE,
                com.aquariverr.fluxod.block.FluxStorageBlock.Nether.class);
        registration.registerBlockComponent(BuiltinRemover.INSTANCE, FluxStorageTerminalBlock.class);
        registration.registerBlockComponent(BuiltinRemover.INSTANCE, QuantumBatteryControllerBlock.class);

        registration.registerBlockComponent(FluxFEStorageProvider.INSTANCE, FluxFEStorageBlock.class);
         registration.registerBlockComponent(FluxLinkCrystalProvider.INSTANCE, FluxLinkCrystalBlock.class);
         registration.registerBlockComponent(FluxLinkCrystalProvider.INSTANCE, FluxExtractCrystalBlock.class);
         registration.registerBlockComponent(FluxLinkCrystalProvider.INSTANCE, FluxAutoLinkCrystalBlock.class);
        registration.registerBlockComponent(FluxLinkCrystalProvider.INSTANCE, FluxAutoExtractCrystalBlock.class);
        registration.registerBlockComponent(FluxOverdriveStorageProvider.INSTANCE,
                com.aquariverr.fluxod.block.FluxStorageBlock.Ender.class);
        registration.registerBlockComponent(FluxOverdriveStorageProvider.INSTANCE,
                com.aquariverr.fluxod.block.FluxStorageBlock.Nether.class);
        registration.registerBlockComponent(FluxOverdriveStorageProvider.INSTANCE, FluxStorageTerminalBlock.class);
        registration.registerBlockComponent(QuantumBatteryProvider.INSTANCE, QuantumBatteryControllerBlock.class);
    }

    private static void registerFnBlocks(IWailaClientRegistration reg, IBlockComponentProvider provider) {
        reg.registerBlockComponent(provider, FluxPlugBlock.class);
        reg.registerBlockComponent(provider, FluxPointBlock.class);
        reg.registerBlockComponent(provider, FluxControllerBlock.class);
        reg.registerBlockComponent(provider, sonar.fluxnetworks.common.block.FluxStorageBlock.Basic.class);
        reg.registerBlockComponent(provider, sonar.fluxnetworks.common.block.FluxStorageBlock.Herculean.class);
        reg.registerBlockComponent(provider, sonar.fluxnetworks.common.block.FluxStorageBlock.Gargantuan.class);
    }

    enum BuiltinRemover implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            tooltip.remove(JadeIds.UNIVERSAL_ENERGY_STORAGE);
        }

        @Override
        public int getDefaultPriority() {
            return TooltipPosition.TAIL;
        }

        @Override
        public ResourceLocation getUid() {
            return FluxOverdrive.location("remove_builtin");
        }
    }

    enum FluxDeviceProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockEntity be = accessor.getBlockEntity();
            if (!(be instanceof TileFluxDevice device)) return;

            addNetworkName(tooltip, device);
            addTransferInfo(tooltip, device, accessor.getServerData());
            FluxDeviceType type = device.getDeviceType();

            if (type.isStorage()) {
                TransferHandler handler = device.getTransferHandler();
                if (handler instanceof FluxStorageHandler sh) {
                    addEnergyBarInfo(tooltip, device, sh.getBuffer(), sh.getMaxEnergyStorage());
                } else {
                    addStorageInfo(tooltip, device);
                }
            } else {
                addConnectorInfo(tooltip, device);
            }
        }

        @Override
        public ResourceLocation getUid() {
            return FluxOverdrive.location("flux_device");
        }

        static void addNetworkName(ITooltip tooltip, TileFluxDevice device) {
            int id = device.getNetworkID();
            if (id > 0) {
                FluxNetwork network = ClientCache.getNetwork(id);
                if (!network.getNetworkName().isEmpty()) {
                    tooltip.add(IElementHelper.get().text(
                            Component.literal(network.getNetworkName()).withStyle(ChatFormatting.AQUA)));
                    return;
                }
                tooltip.add(IElementHelper.get().text(
                        Component.literal("Network #" + id).withStyle(ChatFormatting.AQUA)));
            } else {
                tooltip.add(IElementHelper.get().text(
                        FluxTranslate.ERROR_NO_SELECTED.makeComponent().withStyle(ChatFormatting.AQUA)));
            }
        }

        static void addTransferInfo(ITooltip tooltip, TileFluxDevice device, @Nullable CompoundTag serverData) {
            String info;
            if (serverData != null && serverData.contains(FluxDeviceServerProvider.KEY_TRANSFER_INFO)) {
                info = serverData.getString(FluxDeviceServerProvider.KEY_TRANSFER_INFO);
            } else {
                info = FluxUtils.getTransferInfo(device, EnergyType.FE);
            }
            if (!info.isEmpty()) {
                tooltip.add(IElementHelper.get().text(Component.literal(info)));
            }
        }

        static void addEnergyBarInfo(ITooltip tooltip, TileFluxDevice device, long buffer, long capacity) {
            tooltip.add(new EnergyBarElement(buffer, capacity));
            addTransferLimit(tooltip, device);
            addPriority(tooltip, device);
            addForcedLoading(tooltip, device);
        }

        static void addConnectorInfo(ITooltip tooltip, TileFluxDevice device) {
            Component label = FluxTranslate.INTERNAL_BUFFER.makeComponent();
            tooltip.add(IElementHelper.get().text(label.copy().append(" ")
                    .append(Component.literal(EnergyType.FE.getStorageCompact(device.getTransferBuffer()))
                            .withStyle(ChatFormatting.GREEN))));

            addTransferLimit(tooltip, device);
            addPriority(tooltip, device);
            addForcedLoading(tooltip, device);
        }

        static void addStorageInfo(ITooltip tooltip, TileFluxDevice device) {
            addTransferLimit(tooltip, device);
            addPriority(tooltip, device);
            addForcedLoading(tooltip, device);
        }

        static void addTransferLimit(ITooltip tooltip, TileFluxDevice device) {
            IElementHelper e = IElementHelper.get();
            Component label = FluxTranslate.TRANSFER_LIMIT.makeComponent();
            if (device.getDisableLimit()) {
                tooltip.add(e.text(label.copy().append(" ")
                        .append(FluxTranslate.UNLIMITED.makeComponent().withStyle(ChatFormatting.GREEN))));
            } else {
                tooltip.add(e.text(label.copy().append(" ")
                        .append(Component.literal(EnergyType.FE.getUsageCompact(device.getRawLimit()))
                                .withStyle(ChatFormatting.GREEN))));
            }
        }

        static void addPriority(ITooltip tooltip, TileFluxDevice device) {
            IElementHelper e = IElementHelper.get();
            Component label = FluxTranslate.PRIORITY.makeComponent();
            if (device.getSurgeMode()) {
                tooltip.add(e.text(label.copy().append(" ")
                        .append(FluxTranslate.SURGE.makeComponent().withStyle(ChatFormatting.GREEN))));
            } else {
                tooltip.add(e.text(label.copy().append(" ")
                        .append(Component.literal(String.valueOf(device.getRawPriority()))
                                .withStyle(ChatFormatting.GREEN))));
            }
        }

        static void addForcedLoading(ITooltip tooltip, TileFluxDevice device) {
            if (device.isForcedLoading()) {
                tooltip.add(IElementHelper.get().text(
                        FluxTranslate.FORCED_LOADING.makeComponent().withStyle(ChatFormatting.GOLD)));
            }
        }
    }

    enum FluxFEStorageProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockEntity be = accessor.getBlockEntity();
            if (!(be instanceof TileFluxFEStorage storage)) return;

            FluxDeviceProvider.addNetworkName(tooltip, storage);
            FluxDeviceProvider.addTransferInfo(tooltip, storage, accessor.getServerData());
            FluxDeviceProvider.addEnergyBarInfo(tooltip, storage, storage.getFEBuffer(), storage.getFECapacity());
        }

        @Override
        public ResourceLocation getUid() {
            return FluxOverdrive.location("flux_fe_storage");
        }
    }

    enum FluxLinkCrystalProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockEntity be = accessor.getBlockEntity();
            if (!(be instanceof TileFluxWirelessCrystal crystal)) return;

            CompoundTag serverData = accessor.getServerData();

            IElementHelper e = IElementHelper.get();

            FluxDeviceProvider.addNetworkName(tooltip, crystal);
            FluxDeviceProvider.addTransferInfo(tooltip, crystal, serverData);

            int linkCount;
            if (serverData.contains(FluxDeviceServerProvider.KEY_LINK_COUNT)) {
                linkCount = serverData.getInt(FluxDeviceServerProvider.KEY_LINK_COUNT);
            } else {
                linkCount = crystal.getLinkedTargets().size();
            }
            tooltip.add(e.text(
                    Component.translatable("jade.flux_overdrive.links").append(": ")
                            .append(Component.literal(String.valueOf(linkCount))
                                    .withStyle(ChatFormatting.GOLD))));

            int autoCount = 0;
            boolean hasAutoCount = false;
            if (serverData.contains(FluxDeviceServerProvider.KEY_AUTO_LINK_COUNT)) {
                autoCount = serverData.getInt(FluxDeviceServerProvider.KEY_AUTO_LINK_COUNT);
                hasAutoCount = true;
            } else if (be instanceof TileFluxAutoCrystal autoCrystal) {
                autoCount = autoCrystal.getAutoLinkedTargets().size();
                hasAutoCount = true;
            }
            if (hasAutoCount && autoCount > 0) {
                tooltip.add(e.text(
                        Component.translatable("jade.flux_overdrive.auto_links").append(": ")
                                .append(Component.literal(String.valueOf(autoCount))
                                        .withStyle(ChatFormatting.GOLD))));
            }

            TransferHandler handler = crystal.getTransferHandler();
            if (handler.getBuffer() > 0) {
                Component label = FluxTranslate.INTERNAL_BUFFER.makeComponent();
                tooltip.add(e.text(label.copy().append(" ")
                        .append(Component.literal(EnergyType.FE.getStorageCompact(handler.getBuffer()))
                                .withStyle(ChatFormatting.GREEN))));
            }

            FluxDeviceProvider.addTransferLimit(tooltip, crystal);
            FluxDeviceProvider.addPriority(tooltip, crystal);
            FluxDeviceProvider.addForcedLoading(tooltip, crystal);
        }

        @Override
        public ResourceLocation getUid() {
            return FluxOverdrive.location("flux_link_crystal");
        }
    }

    enum FluxOverdriveStorageProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockEntity be = accessor.getBlockEntity();
            if (!(be instanceof TileFluxDevice device)) return;

            TransferHandler handler = device.getTransferHandler();
            if (!(handler instanceof FluxStorageHandler sh)) return;

            FluxDeviceProvider.addNetworkName(tooltip, device);
            FluxDeviceProvider.addTransferInfo(tooltip, device, accessor.getServerData());
            FluxDeviceProvider.addEnergyBarInfo(tooltip, device, sh.getBuffer(), sh.getMaxEnergyStorage());
        }

        @Override
        public ResourceLocation getUid() {
            return FluxOverdrive.location("flux_overdrive_storage");
        }
    }

    enum QuantumBatteryProvider implements IBlockComponentProvider {
        INSTANCE;

        @Override
        public void appendTooltip(ITooltip tooltip, BlockAccessor accessor, IPluginConfig config) {
            BlockEntity be = accessor.getBlockEntity();
            if (!(be instanceof TileQuantumBatteryController controller)) return;

            CompoundTag data = accessor.getServerData();
            int tier = data.contains(FluxDeviceServerProvider.KEY_QUANTUM_TIER)
                    ? data.getInt(FluxDeviceServerProvider.KEY_QUANTUM_TIER)
                    : controller.getStructureTier();
            boolean suspended = data.contains(FluxDeviceServerProvider.KEY_QUANTUM_SUSPENDED)
                    ? data.getBoolean(FluxDeviceServerProvider.KEY_QUANTUM_SUSPENDED)
                    : controller.isStructureSuspended();
            boolean ready = data.contains(FluxDeviceServerProvider.KEY_QUANTUM_READY)
                    ? data.getBoolean(FluxDeviceServerProvider.KEY_QUANTUM_READY)
                    : controller.isStructureReady();
            boolean constraintPowered = data.contains(FluxDeviceServerProvider.KEY_QUANTUM_CONSTRAINT_POWERED)
                    ? data.getBoolean(FluxDeviceServerProvider.KEY_QUANTUM_CONSTRAINT_POWERED)
                    : controller.isConstraintPowered();
            int stabilityBasisPoints = data.contains(FluxDeviceServerProvider.KEY_QUANTUM_STABILITY)
                    ? data.getInt(FluxDeviceServerProvider.KEY_QUANTUM_STABILITY)
                    : controller.getStabilityBasisPoints();
            double temperatureKelvin = data.contains(FluxDeviceServerProvider.KEY_QUANTUM_TEMPERATURE)
                    ? data.getDouble(FluxDeviceServerProvider.KEY_QUANTUM_TEMPERATURE)
                    : controller.getTemperatureKelvin();
            double coolingTargetKelvin = data.contains(FluxDeviceServerProvider.KEY_QUANTUM_COOLING_TARGET)
                    ? data.getDouble(FluxDeviceServerProvider.KEY_QUANTUM_COOLING_TARGET)
                    : controller.getEffectiveCoolingTargetKelvin();
            boolean coolingActive = data.contains(FluxDeviceServerProvider.KEY_QUANTUM_COOLING_ACTIVE)
                    ? data.getBoolean(FluxDeviceServerProvider.KEY_QUANTUM_COOLING_ACTIVE)
                    : controller.isCoolingActive();
            FluxDeviceProvider.addNetworkName(tooltip, controller);
            FluxDeviceProvider.addTransferInfo(tooltip, controller, data);
            tooltip.add(IElementHelper.get().text(Component.translatable(
                    "jade.flux_overdrive.quantum_battery.tier", tier)));
            if (data.contains(FluxDeviceServerProvider.KEY_QUANTUM_ENERGY)
                    && data.contains(FluxDeviceServerProvider.KEY_QUANTUM_CAPACITY)) {
                tooltip.add(IElementHelper.get().text(Component.translatable(
                        "jade.flux_overdrive.quantum_battery.energy",
                        data.getString(FluxDeviceServerProvider.KEY_QUANTUM_ENERGY),
                        data.getString(FluxDeviceServerProvider.KEY_QUANTUM_CAPACITY))
                        .withStyle(ChatFormatting.AQUA)));
            }
            tooltip.add(IElementHelper.get().text(Component.translatable(
                    "jade.flux_overdrive.quantum_battery.stability",
                    TileQuantumBatteryController.formatStabilityBasisPoints(stabilityBasisPoints))
                    .withStyle(constraintPowered && stabilityBasisPoints > 0
                            ? ChatFormatting.AQUA : ChatFormatting.YELLOW)));
            tooltip.add(IElementHelper.get().text(Component.translatable(
                    "jade.flux_overdrive.quantum_battery.temperature",
                    String.format(Locale.ROOT, "%.1f K", temperatureKelvin))));
            if (coolingTargetKelvin >= 0.0) {
                tooltip.add(IElementHelper.get().text(Component.translatable(
                        coolingActive
                                ? "jade.flux_overdrive.quantum_battery.cooling_active"
                                : "jade.flux_overdrive.quantum_battery.cooling_idle",
                        String.format(Locale.ROOT, "%.0f K", coolingTargetKelvin))
                        .withStyle(coolingActive ? ChatFormatting.AQUA : ChatFormatting.GRAY)));
            }
            if (suspended) {
                tooltip.add(IElementHelper.get().text(Component.translatable(
                        "jade.flux_overdrive.quantum_battery.suspended").withStyle(ChatFormatting.YELLOW)));
            } else if (!ready) {
                tooltip.add(IElementHelper.get().text(Component.translatable(
                        "jade.flux_overdrive.quantum_battery.not_ready").withStyle(ChatFormatting.YELLOW)));
            } else if (!constraintPowered && stabilityBasisPoints == 0) {
                tooltip.add(IElementHelper.get().text(Component.translatable(
                        "jade.flux_overdrive.quantum_battery.stability_zero").withStyle(ChatFormatting.YELLOW)));
            } else if (!constraintPowered) {
                tooltip.add(IElementHelper.get().text(Component.translatable(
                        "jade.flux_overdrive.quantum_battery.constraint_low").withStyle(ChatFormatting.YELLOW)));
            } else if (stabilityBasisPoints < 10_000) {
                tooltip.add(IElementHelper.get().text(Component.translatable(
                        "jade.flux_overdrive.quantum_battery.recovering").withStyle(ChatFormatting.GREEN)));
            }
            tooltip.add(IElementHelper.get().text(Component.translatable(
                    "jade.flux_overdrive.quantum_battery.transfer",
                    EnergyType.FE.getUsageCompact(controller.getTransferHandler().getLimit()))));
        }

        @Override
        public ResourceLocation getUid() {
            return FluxOverdrive.location("quantum_battery");
        }
    }

    private static class EnergyBarElement extends Element {

        private static final int WIDTH = 100;
        private static final int HEIGHT = 12;
        private static final int BAR_BG = 0x40660000;
        private static final int BAR_FILL = 0xFFAA0000;
        private static final int TEXT_COLOR = 0xFFFFFF;

        private final long stored;
        private final long capacity;

        EnergyBarElement(long stored, long capacity) {
            this.stored = stored;
            this.capacity = capacity;
        }

        @Override
        public Vec2 getSize() {
            return new Vec2(WIDTH, HEIGHT);
        }

        @Override
        public void render(GuiGraphics gfx, float x, float y, float maxX, float maxY) {
            int xi = Mth.floor(x);
            int yi = Mth.floor(y);

            gfx.fill(xi, yi, xi + WIDTH, yi + HEIGHT, BAR_BG);

            float ratio = capacity > 0 ? Math.clamp((float) ((double) stored / capacity), 0f, 1f) : 0f;
            int fillWidth = Mth.floor(WIDTH * ratio);
            if (fillWidth > 0) {
                gfx.fill(xi, yi, xi + fillWidth, yi + HEIGHT, BAR_FILL);
            }

            String text = EnergyType.FE.getStorageCompact(stored) + " / " + EnergyType.FE.getStorageCompact(capacity);
            gfx.drawCenteredString(Minecraft.getInstance().font, Component.literal(text).withColor(TEXT_COLOR),
                    xi + WIDTH / 2, yi + 2, TEXT_COLOR);
        }
    }

    enum FluxDeviceServerProvider implements IServerDataProvider<BlockAccessor> {
        INSTANCE;

        static final String KEY_TRANSFER_INFO = "fod.transferInfo";
        static final String KEY_LINK_COUNT = "fod.linkCount";
        static final String KEY_AUTO_LINK_COUNT = "fod.autoLinkCount";
        static final String KEY_QUANTUM_TIER = "fod.quantumTier";
        static final String KEY_QUANTUM_SUSPENDED = "fod.quantumSuspended";
        static final String KEY_QUANTUM_READY = "fod.quantumReady";
        static final String KEY_QUANTUM_CONSTRAINT_POWERED = "fod.quantumConstraintPowered";
        static final String KEY_QUANTUM_STABILITY = "fod.quantumStability";
        static final String KEY_QUANTUM_ENERGY = "fod.quantumEnergy";
        static final String KEY_QUANTUM_CAPACITY = "fod.quantumCapacity";
        static final String KEY_QUANTUM_TEMPERATURE = "fod.quantumTemperature";
        static final String KEY_QUANTUM_COOLING_TARGET = "fod.quantumCoolingTarget";
        static final String KEY_QUANTUM_COOLING_ACTIVE = "fod.quantumCoolingActive";

        @Override
        public void appendServerData(CompoundTag tag, BlockAccessor accessor) {
            BlockEntity be = accessor.getBlockEntity();
            if (!(be instanceof TileFluxDevice device)) return;

            String transferInfo = FluxUtils.getTransferInfo(device, EnergyType.FE);
            if (!transferInfo.isEmpty()) {
                tag.putString(KEY_TRANSFER_INFO, transferInfo);
            }

            if (be instanceof TileFluxWirelessCrystal crystal) {
                tag.putInt(KEY_LINK_COUNT, crystal.getLinkedTargets().size());
            }
            if (be instanceof TileFluxAutoCrystal autoCrystal) {
                tag.putInt(KEY_AUTO_LINK_COUNT, autoCrystal.getAutoLinkedTargets().size());
            }
            if (be instanceof TileQuantumBatteryController controller) {
                tag.putInt(KEY_QUANTUM_TIER, controller.getStructureTier());
                tag.putBoolean(KEY_QUANTUM_SUSPENDED, controller.isStructureSuspended());
                tag.putBoolean(KEY_QUANTUM_READY, controller.isStructureReady());
                tag.putBoolean(KEY_QUANTUM_CONSTRAINT_POWERED, controller.isConstraintPowered());
                tag.putInt(KEY_QUANTUM_STABILITY, controller.getStabilityBasisPoints());
                tag.putString(KEY_QUANTUM_ENERGY, controller.getStoredEnergyCompact());
                tag.putString(KEY_QUANTUM_CAPACITY, controller.getCapacityCompact());
                tag.putDouble(KEY_QUANTUM_TEMPERATURE, controller.getTemperatureKelvin());
                tag.putDouble(KEY_QUANTUM_COOLING_TARGET, controller.getEffectiveCoolingTargetKelvin());
                tag.putBoolean(KEY_QUANTUM_COOLING_ACTIVE, controller.isCoolingActive());
            }
        }

        @Override
        public ResourceLocation getUid() {
            return FluxOverdrive.location("flux_device_server_data");
        }
    }
}
