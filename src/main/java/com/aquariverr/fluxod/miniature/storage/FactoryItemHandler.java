package com.aquariverr.fluxod.miniature.storage;

import com.aquariverr.fluxod.block.entity.TileMiniatureUniverse;
import com.aquariverr.fluxod.miniature.model.FactoryLimits;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import net.minecraft.world.item.ItemStack;
import net.neoforged.neoforge.items.IItemHandler;

import java.math.BigInteger;
import java.util.List;

/** Input and output slots share the actual GUI inventories; machine assets and reservations are never exposed. */
public final class FactoryItemHandler implements IItemHandler {
    private static final int INPUT_SLOTS = FactoryLimits.MAX_BOUNDARY_RESOURCES;
    private final TileMiniatureUniverse owner;

    public FactoryItemHandler(TileMiniatureUniverse owner) { this.owner = owner; }
    @Override public int getSlots() { return INPUT_SLOTS * 2; }
    @Override public int getSlotLimit(int slot) {
        if (!validSlot(slot)) return 0;
        FactoryStorage storage = storage();
        if (storage == null) return 0;
        ResourceKey key = resource(storage, slot);
        return slot < INPUT_SLOTS ? (key == null ? storage.inputLimit() : storage.inputLimit(key))
                .min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact() : Integer.MAX_VALUE;
    }

    private boolean validSlot(int slot) { return slot >= 0 && slot < getSlots(); }
    private FactoryStorage storage() {
        return FactoryAutomation.storage(owner);
    }

    private ResourceKey resource(FactoryStorage storage, int slot) {
        if (!validSlot(slot)) return null;
        var inventory = slot < INPUT_SLOTS ? storage.inputs() : storage.outputs();
        List<ResourceKey> items = inventory.amounts().keySet().stream().filter(key -> key.kind() == ResourceKind.ITEM).toList();
        int index = slot % INPUT_SLOTS;
        return index < items.size() ? items.get(index) : null;
    }

    @Override public ItemStack getStackInSlot(int slot) {
        FactoryStorage storage = storage();
        if (storage == null) return ItemStack.EMPTY;
        ResourceKey key = resource(storage, slot);
        if (key == null) return ItemStack.EMPTY;
        try {
            ItemStack stack = FactoryItemResources.itemOf(key, owner.getLevel().registryAccess());
            stack.setCount((slot < INPUT_SLOTS ? storage.inputs() : storage.outputs()).amount(key)
                    .min(BigInteger.valueOf(Integer.MAX_VALUE)).intValueExact());
            return stack;
        } catch (IllegalArgumentException exception) { return ItemStack.EMPTY; }
    }

    @Override public boolean isItemValid(int slot, ItemStack stack) {
        FactoryStorage storage = storage();
        if (storage == null || slot < 0 || slot >= INPUT_SLOTS || stack.isEmpty()) return false;
        try {
            ResourceKey key = FactoryItemResources.keyOf(stack, owner.getLevel().registryAccess());
            List<ResourceKey> items = storage.inputs().amounts().keySet().stream()
                    .filter(resource -> resource.kind() == ResourceKind.ITEM).toList();
            int existing = items.indexOf(key);
            // A resource has one insertion slot: its existing entry or the first empty entry.
            // Otherwise multi-slot simulations count the same remaining capacity repeatedly.
            return slot == (existing >= 0 ? existing : items.size()) && FactoryAutomation.accepts(owner, key);
        } catch (IllegalArgumentException exception) { return false; }
    }

    @Override public ItemStack insertItem(int slot, ItemStack stack, boolean simulate) {
        if (!isItemValid(slot, stack)) return stack;
        FactoryStorage storage = storage();
        try {
            ResourceKey key = FactoryItemResources.keyOf(stack, owner.getLevel().registryAccess());
            int amount = storage.inputCapacity(key)
                    .min(BigInteger.valueOf(stack.getCount())).intValueExact();
            if (amount == 0) return stack;
            ItemStack offered = stack.copyWithCount(amount);
            owner.insertInput(offered, simulate);
            return stack.copyWithCount(stack.getCount() - amount);
        } catch (IllegalArgumentException | IllegalStateException exception) { return stack; }
    }

    @Override public ItemStack extractItem(int slot, int amount, boolean simulate) {
        FactoryStorage storage = storage();
        if (storage == null || slot < INPUT_SLOTS || !validSlot(slot) || amount <= 0) return ItemStack.EMPTY;
        ResourceKey key = resource(storage, slot);
        if (key == null) return ItemStack.EMPTY;
        try { return owner.extractOutput(key, amount, simulate); }
        catch (IllegalArgumentException | IllegalStateException exception) { return ItemStack.EMPTY; }
    }
}
