package com.aquariverr.fluxod.block.entity;

import com.aquariverr.fluxod.Config;
import com.aquariverr.fluxod.entanglement.QuantumPluginEndpointKey;
import com.aquariverr.fluxod.register.RegistryBlockEntityTypes;
import com.aquariverr.fluxod.menu.QuantumCoolingPluginMenu;
import net.minecraft.core.BlockPos;
import net.minecraft.core.HolderLookup;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.item.ItemStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.items.IItemHandler;
import net.neoforged.neoforge.items.ItemStackHandler;

import javax.annotation.Nonnull;
import java.util.ArrayList;
import java.util.List;

import static com.aquariverr.fluxod.block.entity.QuantumCoolingMath.ceilDiv;
import static com.aquariverr.fluxod.block.entity.QuantumCoolingMath.saturatingAdd;
import static com.aquariverr.fluxod.block.entity.QuantumCoolingMath.saturatingMultiply;

public final class TileQuantumSolidCoolingPlugin extends BlockEntity implements QuantumBatteryCoolingSource {

    public static final int INVENTORY_SIZE = 9;

    private static final String INVENTORY_TAG = "Inventory";
    private static final String COOLING_CREDITS_TAG = "CoolingCredits";
    private static final String CHANNEL_PREFIX = "solid:";
    private static final long[] TARGETS = {
            QuantumCoolingMedia.TARGET_273_K,
            QuantumCoolingMedia.TARGET_263_K,
            QuantumCoolingMedia.TARGET_253_K
    };

    private final long[] coolingCredits = new long[TARGETS.length];
    private final QuantumCoolingTickBudget coolingTickBudget = new QuantumCoolingTickBudget();
    private final ItemStackHandler inventory = new ItemStackHandler(INVENTORY_SIZE) {
        @Override
        protected void onContentsChanged(int slot) {
            setChanged();
        }

        @Override
        public boolean isItemValid(int slot, @Nonnull ItemStack stack) {
            return QuantumCoolingMedia.isSolidCoolant(stack);
        }
    };
    private final IItemHandler inputHandler = new InputOnlyItemHandler(inventory);

    public TileQuantumSolidCoolingPlugin(BlockPos pos, BlockState state) {
        super(RegistryBlockEntityTypes.QUANTUM_SOLID_COOLING_PLUGIN.get(), pos, state);
    }

    public ItemStackHandler getInventory() {
        return inventory;
    }

    public IItemHandler getInputHandler() {
        return inputHandler;
    }

    public void openMenu(Player player) {
        if (!(player instanceof ServerPlayer serverPlayer) || player.isSpectator()) return;
        SimpleMenuProvider provider = new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new QuantumCoolingPluginMenu(containerId, inventory, this),
                Component.translatable("screen.flux_overdrive.quantum_cooling.solid_title"));
        serverPlayer.openMenu(provider, buffer -> QuantumCoolingPluginMenu.writeOpeningData(buffer, this));
    }

    public void openMenuFromEntanglement(ServerPlayer player,
                                         TileQuantumEntanglementController accessController,
                                         QuantumPluginEndpointKey endpointKey) {
        if (player.isSpectator()
                || !accessController.canProvideRemotePluginAccess(player, this, endpointKey)) {
            return;
        }
        SimpleMenuProvider provider = new SimpleMenuProvider(
                (containerId, inventory, ignored) -> new QuantumCoolingPluginMenu(
                        containerId, inventory, this, accessController, endpointKey),
                Component.translatable("screen.flux_overdrive.quantum_cooling.solid_title"));
        player.openMenu(provider, buffer -> QuantumCoolingPluginMenu.writeRemoteOpeningData(
                buffer, this, accessController, endpointKey));
    }

    @Override
    public List<CoolingMedium> getCoolingMedia() {
        List<CoolingMedium> media = new ArrayList<>(TARGETS.length);
        for (int i = 0; i < TARGETS.length; i++) {
            long available = QuantumCoolingConsumption.effectiveCooling(storedCoolingAvailable(i));
            if (available > 0L) {
                media.add(new CoolingMedium(channel(TARGETS[i]), TARGETS[i], available));
            }
        }
        return media;
    }

    @Override
    public long getMaxCoolingPerTickMilliKelvin() {
        return QuantumBatteryCoolingModel.milliKelvin(Config.quantumSolidCoolingRate);
    }

    @Override
    public long consumeCooling(String channelId, long requestedMilliKelvin) {
        int index = targetIndex(parseTarget(channelId));
        if (index < 0 || requestedMilliKelvin <= 0L) return 0L;

        long requested = limitToTickBudget(requestedMilliKelvin);
        if (requested <= 0L) return 0L;

        requested = Math.min(requested,
                QuantumCoolingConsumption.effectiveCooling(storedCoolingAvailable(index)));
        if (requested <= 0L) return 0L;

        long storedRequested = QuantumCoolingConsumption.storedCoolingFor(requested);
        long remaining = storedRequested;
        long fromCredit = Math.min(remaining, coolingCredits[index]);
        coolingCredits[index] -= fromCredit;
        remaining -= fromCredit;

        long coolingPerItem = coolingPerItem(TARGETS[index]);
        for (int slot = 0; slot < inventory.getSlots() && remaining > 0L && coolingPerItem > 0L; slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (stack.isEmpty() || QuantumCoolingMedia.targetFor(stack) != TARGETS[index]) continue;
            int itemsToConsume = (int) Math.min(ceilDiv(remaining, coolingPerItem), stack.getCount());
            ItemStack extracted = inventory.extractItem(slot, itemsToConsume, false);
            coolingCredits[index] = saturatingAdd(coolingCredits[index],
                    saturatingMultiply(extracted.getCount(), coolingPerItem));
            long fromItems = Math.min(remaining, coolingCredits[index]);
            coolingCredits[index] -= fromItems;
            remaining -= fromItems;
        }

        long consumed = QuantumCoolingConsumption.effectiveCooling(storedRequested - remaining);
        recordTickConsumption(consumed);
        if (consumed > 0L) setChanged();
        return consumed;
    }

    @Override
    protected void saveAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.saveAdditional(tag, registries);
        tag.put(INVENTORY_TAG, inventory.serializeNBT(registries));
        tag.putLongArray(COOLING_CREDITS_TAG, coolingCredits);
    }

    @Override
    public void loadAdditional(CompoundTag tag, HolderLookup.Provider registries) {
        super.loadAdditional(tag, registries);
        if (tag.contains(INVENTORY_TAG)) {
            CompoundTag inventoryTag = tag.getCompound(INVENTORY_TAG).copy();
            inventoryTag.putInt("Size", INVENTORY_SIZE);
            inventory.deserializeNBT(registries, inventoryTag);
        }
        long[] savedCredits = tag.getLongArray(COOLING_CREDITS_TAG);
        for (int i = 0; i < coolingCredits.length; i++) {
            coolingCredits[i] = i < savedCredits.length ? Math.max(0L, savedCredits[i]) : 0L;
        }
    }

    private static long coolingPerItem(long target) {
        double kelvin = switch ((int) target) {
            case (int) QuantumCoolingMedia.TARGET_273_K -> Config.quantumIceCooling;
            case (int) QuantumCoolingMedia.TARGET_263_K -> Config.quantumPackedIceCooling;
            case (int) QuantumCoolingMedia.TARGET_253_K -> Config.quantumBlueIceCooling;
            default -> 0.0;
        };
        return QuantumBatteryCoolingModel.milliKelvin(kelvin);
    }

    private long storedCoolingAvailable(int targetIndex) {
        long available = coolingCredits[targetIndex];
        long target = TARGETS[targetIndex];
        long coolingPerItem = coolingPerItem(target);
        for (int slot = 0; slot < inventory.getSlots(); slot++) {
            ItemStack stack = inventory.getStackInSlot(slot);
            if (QuantumCoolingMedia.targetFor(stack) == target) {
                available = saturatingAdd(available,
                        saturatingMultiply(stack.getCount(), coolingPerItem));
            }
        }
        return available;
    }

    private long limitToTickBudget(long requested) {
        long maximum = getMaxCoolingPerTickMilliKelvin();
        return level == null
                ? Math.min(requested, maximum)
                : coolingTickBudget.limit(level.getGameTime(), maximum, requested);
    }

    private void recordTickConsumption(long consumed) {
        if (level != null && consumed > 0L) {
            coolingTickBudget.record(level.getGameTime(), consumed);
        }
    }

    private static String channel(long target) {
        return CHANNEL_PREFIX + target;
    }

    private static long parseTarget(String channelId) {
        if (!channelId.startsWith(CHANNEL_PREFIX)) return QuantumBatteryCoolingModel.NO_TARGET;
        try {
            return Long.parseLong(channelId.substring(CHANNEL_PREFIX.length()));
        } catch (NumberFormatException ignored) {
            return QuantumBatteryCoolingModel.NO_TARGET;
        }
    }

    private static int targetIndex(long target) {
        for (int i = 0; i < TARGETS.length; i++) {
            if (TARGETS[i] == target) return i;
        }
        return -1;
    }

    private record InputOnlyItemHandler(IItemHandler delegate) implements IItemHandler {
        @Override
        public int getSlots() {
            return delegate.getSlots();
        }

        @Override
        public ItemStack getStackInSlot(int slot) {
            return delegate.getStackInSlot(slot);
        }

        @Override
        public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
            return delegate.insertItem(slot, stack, simulate);
        }

        @Override
        public ItemStack extractItem(int slot, int amount, boolean simulate) {
            return ItemStack.EMPTY;
        }

        @Override
        public int getSlotLimit(int slot) {
            return delegate.getSlotLimit(slot);
        }

        @Override
        public boolean isItemValid(int slot, ItemStack stack) {
            return delegate.isItemValid(slot, stack);
        }
    }
}
