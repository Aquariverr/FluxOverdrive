package com.aquariverr.fluxod.integration.jei;

import com.aquariverr.fluxod.client.screen.MiniatureUniverseScreen;
import com.aquariverr.fluxod.menu.MiniatureUniverseMenu;
import com.aquariverr.fluxod.miniature.model.RecipeReference;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.recipe.MachineRecipeAdapter;
import com.aquariverr.fluxod.miniature.recipe.MachineRecipeAdapters;
import com.aquariverr.fluxod.miniature.recipe.ResourceRecipeAdapter;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import com.aquariverr.fluxod.register.RegistryMenuTypes;
import mezz.jei.api.gui.ingredient.IRecipeSlotView;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.transfer.IRecipeTransferError;
import mezz.jei.api.recipe.transfer.IRecipeTransferHandlerHelper;
import mezz.jei.api.recipe.transfer.IUniversalRecipeTransferHandler;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.RecipeHolder;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Encodes recipe choice; JEI's inventory transfer machinery is never invoked. */
public final class MiniatureRecipeTransferHandler implements IUniversalRecipeTransferHandler<MiniatureUniverseMenu> {
    private final IRecipeTransferHandlerHelper helper;

    public MiniatureRecipeTransferHandler(IRecipeTransferHandlerHelper helper) { this.helper = helper; }

    @Override public Class<MiniatureUniverseMenu> getContainerClass() { return MiniatureUniverseMenu.class; }
    @Override public Optional<MenuType<MiniatureUniverseMenu>> getMenuType() { return Optional.of(RegistryMenuTypes.MINIATURE_UNIVERSE.get()); }

    @Override
    public IRecipeTransferError transferRecipe(MiniatureUniverseMenu menu, Object recipe, IRecipeSlotsView slots,
                                              Player player, boolean maxTransfer, boolean doTransfer) {
        var editor = MiniatureUniverseScreen.forMenu(menu).orElse(null);
        if (editor == null) return error("unavailable");
        Component problem = editor.recipeTransferProblem();
        if (problem != null) return helper.createUserErrorWithTooltip(problem);
        // Native recipes and Mekanism categories both expose a real RecipeHolder. Never invent an ID for a wrapper.
        if (!(recipe instanceof RecipeHolder<?> holder)) return error("missing_recipe_id");
        try {
            var machine = FactoryItemResources.itemOf(editor.recipeTransferMachine(), player.registryAccess());
            MachineRecipeAdapter adapter = MachineRecipeAdapters.forMachine(machine).stream()
                    .filter(candidate -> candidate.supportsRecipe(holder.value())).findFirst().orElse(null);
            if (adapter == null) return error("unsupported_recipe");
            RecipeReference reference = reference(adapter, holder, slots, player);
            problem = editor.recipeTransferProblem(reference);
            if (problem != null) return helper.createUserErrorWithTooltip(problem);
            if (doTransfer && !editor.selectRecipe(reference)) return error("unavailable");
            return null;
        } catch (IllegalArgumentException | IllegalStateException invalid) {
            return error("invalid_ingredient");
        }
    }

    private RecipeReference reference(MachineRecipeAdapter adapter, RecipeHolder<?> holder, IRecipeSlotsView slots, Player player) {
        List<IRecipeSlotView> inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
        List<RecipeReference.Binding> bindings = new ArrayList<>();
        if (adapter instanceof ResourceRecipeAdapter) {
            if (inputs.isEmpty() || inputs.size() > 9) throw new IllegalArgumentException("Unsupported resource slots");
            for (int index = 0; index < inputs.size(); index++) bindings.add(new RecipeReference.Binding(index, displayed(inputs.get(index), player)));
            return new RecipeReference(adapter.id(), holder.id().toString(), Math.min(3, inputs.size()), (inputs.size() + 2) / 3, bindings);
        }
        MachineRecipeAdapter.Pattern pattern = adapter.pattern(holder.value());
        List<Integer> guiSlots;
        if (holder.value() instanceof CraftingRecipe) {
            if (inputs.size() != 9) throw new IllegalArgumentException("Missing native crafting grid");
            @SuppressWarnings("unchecked") RecipeHolder<CraftingRecipe> crafting = (RecipeHolder<CraftingRecipe>) holder;
            // JEI centers narrow recipes and bottom-aligns some short recipes. Its API supplies the exact GUI mapping.
            guiSlots = helper.getGuiSlotIndexToIngredientMap(crafting).keySet().stream().sorted().toList();
        } else {
            if (inputs.size() != 1) throw new IllegalArgumentException("Missing native cooking input");
            guiSlots = List.of(0);
        }
        int inputIndex = 0;
        for (int slot = 0; slot < pattern.ingredients().size(); slot++) {
            var ingredient = pattern.ingredients().get(slot);
            if (ingredient.isEmpty()) continue;
            if (inputIndex >= guiSlots.size()) throw new IllegalArgumentException("Missing input binding");
            ResourceKey resource = displayed(inputs.get(guiSlots.get(inputIndex++)), player);
            if (!ingredient.test(FactoryItemResources.itemOf(resource, player.registryAccess()))) {
                throw new IllegalArgumentException("Displayed variant does not match the native ingredient");
            }
            bindings.add(new RecipeReference.Binding(slot, resource));
        }
        if (inputIndex != guiSlots.size()) throw new IllegalArgumentException("Extra input binding");
        return new RecipeReference(adapter.id(), holder.id().toString(), pattern.width(), pattern.height(), bindings);
    }

    private ResourceKey displayed(IRecipeSlotView slot, Player player) {
        Object value = slot.getDisplayedIngredient().orElseThrow(() -> new IllegalArgumentException("No displayed ingredient")).getIngredient();
        ResourceKey resource = MiniatureJeiIngredients.resourceOf(value, player.registryAccess());
        if (resource == null) throw new IllegalArgumentException("Unsupported displayed ingredient");
        return resource;
    }

    private IRecipeTransferError error(String key) {
        return helper.createUserErrorWithTooltip(Component.translatable("screen.flux_overdrive.miniature_universe.jei." + key));
    }
}
