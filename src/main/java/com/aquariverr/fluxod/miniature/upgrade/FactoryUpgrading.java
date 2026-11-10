package com.aquariverr.fluxod.miniature.upgrade;

import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.storage.FactoryEditing;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import com.aquariverr.fluxod.miniature.storage.FactoryState;
import com.aquariverr.fluxod.network.MiniatureFactorySnapshotPayload.Result;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

public final class FactoryUpgrading {
    private static List<MiniatureUpgradeRecipe> recipes(ServerLevel level, int size) {
        return level.getRecipeManager().getAllRecipesFor(MiniatureUpgradeRecipe.TYPE).stream()
                .map(holder -> holder.value()).filter(recipe -> recipe.fromSize() == size).limit(2).toList();
    }

    public static UpgradePreview preview(ServerLevel level, int size) {
        if (size == 11) return new UpgradePreview(UpgradePreview.Status.MAXIMUM, List.of());
        var recipes = recipes(level, size);
        if (recipes.size() != 1) return new UpgradePreview(recipes.isEmpty() ? UpgradePreview.Status.NO_RECIPE : UpgradePreview.Status.AMBIGUOUS, List.of());
        return new UpgradePreview(UpgradePreview.Status.AVAILABLE, recipes.getFirst().materials().stream().map(cost ->
                new ResourceAmount(FactoryItemResources.keyOf(new ItemStack(cost.item()), level.registryAccess()), BigInteger.valueOf(cost.count()))).toList());
    }

    public static Prepared prepare(FactoryState state, Inventory inventory, ServerLevel level) {
        var storage = state.storage().orElseThrow(() -> new FactoryEditing.Rejected(Result.STATE_UNAVAILABLE));
        if (storage.batch() != null) throw new FactoryEditing.Rejected(Result.BATCH_LOCKED);
        if (storage.layout().size() == 11) throw new FactoryEditing.Rejected(Result.UPGRADE_MAXIMUM);
        var recipes = recipes(level, storage.layout().size());
        if (recipes.size() != 1) throw new FactoryEditing.Rejected(recipes.isEmpty() ? Result.UPGRADE_NO_RECIPE : Result.UPGRADE_AMBIGUOUS);
        List<ItemStack> before = inventory.items.stream().map(ItemStack::copy).toList();
        List<ItemStack> after = new ArrayList<>(before.stream().map(ItemStack::copy).toList());
        for (var cost : recipes.getFirst().materials()) {
            int remaining = cost.count();
            for (int slot = 0; slot < after.size() && remaining > 0; slot++) {
                ItemStack stack = after.get(slot);
                if (!cost.matches(stack)) continue;
                int taken = Math.min(stack.getCount(), remaining);
                stack.shrink(taken);
                remaining -= taken;
            }
            if (remaining != 0) throw new FactoryEditing.Rejected(Result.UPGRADE_MISSING_MATERIALS);
        }
        return new Prepared(FactoryState.of(storage.upgrade(), level.registryAccess()), before, after);
    }

    /** All validation and response encoding precede the server-thread commit in the menu. */
    public record Prepared(FactoryState state, List<ItemStack> before, List<ItemStack> after) {
        public boolean stillMatches(Inventory inventory) {
            for (int slot = 0; slot < before.size(); slot++) if (!ItemStack.matches(before.get(slot), inventory.items.get(slot))) return false;
            return true;
        }
        public void commitMaterials(Inventory inventory) {
            for (int slot = 0; slot < after.size(); slot++) inventory.items.set(slot, after.get(slot).copy());
            inventory.setChanged();
        }
    }
    private FactoryUpgrading() {}
}
