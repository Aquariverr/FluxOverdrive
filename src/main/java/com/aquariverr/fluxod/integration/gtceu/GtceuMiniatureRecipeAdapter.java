package com.aquariverr.fluxod.integration.gtceu;

import com.aquariverr.fluxod.miniature.model.*;
import com.aquariverr.fluxod.miniature.recipe.*;
import com.aquariverr.fluxod.miniature.storage.FactoryFluidResources;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import com.gregtechceu.gtceu.api.GTValues;
import com.gregtechceu.gtceu.api.block.MetaMachineBlock;
import com.gregtechceu.gtceu.api.capability.recipe.*;
import com.gregtechceu.gtceu.api.machine.MachineDefinition;
import com.gregtechceu.gtceu.api.machine.MultiblockMachineDefinition;
import com.gregtechceu.gtceu.api.recipe.GTRecipe;
import com.gregtechceu.gtceu.api.recipe.content.Content;
import com.gregtechceu.gtceu.api.recipe.ingredient.IntCircuitIngredient;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.common.crafting.*;
import net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient;

import java.math.BigInteger;
import java.util.*;

/** Uses the native machine definition, recipe capabilities and exact input predicates, including addon machines. */
public final class GtceuMiniatureRecipeAdapter implements ResourceRecipeAdapter {
    private final MachineDefinition definition;
    private final net.minecraft.world.item.Item machine;

    private GtceuMiniatureRecipeAdapter(ItemStack machine, MachineDefinition definition) {
        this.machine = machine.getItem();
        this.definition = definition;
    }

    public static List<MachineRecipeAdapter> forMachine(ItemStack stack) {
        if (stack.getItem() instanceof BlockItem item && item.getBlock() instanceof MetaMachineBlock block) {
            var definition = block.getDefinition();
            // A controller item alone cannot prove a multiblock's structure, coils or environment.
            if (!(definition instanceof MultiblockMachineDefinition) && definition.getTier() >= 0
                    && definition.getRecipeTypes().length > 0) return List.of(new GtceuMiniatureRecipeAdapter(stack, definition));
        }
        return List.of();
    }

    @Override public String id() { return "flux_overdrive:gtceu"; }
    @Override public boolean supportsMachine(ItemStack stack) { return stack.is(machine); }

    @Override
    public boolean supportsRecipe(Recipe<?> candidate) {
        if (!(candidate instanceof GTRecipe recipe) || candidate.getClass() != GTRecipe.class
                || Arrays.stream(definition.getRecipeTypes()).noneMatch(type -> type == recipe.recipeType)
                || !recipe.conditions.isEmpty() || !recipe.ingredientActions.isEmpty() || !recipe.data.isEmpty()
                || !recipe.tickOutputs.isEmpty() || recipe.tickInputs.keySet().stream().anyMatch(cap -> cap != EURecipeCapability.CAP)
                || recipe.inputs.keySet().stream().anyMatch(cap -> !resourceCapability(cap))
                || recipe.outputs.keySet().stream().anyMatch(cap -> !resourceCapability(cap))) return false;
        int tier = definition.getTier();
        if (tier >= GTValues.V.length || recipe.getInputEUt().voltage() > GTValues.V[tier]
                || recipe.getInputEUt().amperage() > 1) return false;
        var inputs = inputContents(recipe);
        if (inputs.isEmpty() || inputs.size() > 9 || inputs.stream().noneMatch(content -> content.chance() > 0)) return false;
        if (inputs.stream().anyMatch(content -> !deterministic(content, true) || !supported(content.content()))) return false;
        return recipe.outputs.values().stream().flatMap(Collection::stream)
                .allMatch(content -> deterministic(content, false) && supported(content.content()));
    }

    private static boolean resourceCapability(RecipeCapability<?> cap) {
        return cap == ItemRecipeCapability.CAP || cap == FluidRecipeCapability.CAP;
    }

    private static boolean deterministic(Content content, boolean input) {
        return content.maxChance() > 0 && (content.chance() == content.maxChance() || input && content.chance() == 0);
    }

    private static boolean supported(Object value) {
        if (value instanceof SizedIngredient item) return item.count() > 0 && supportedItem(item.ingredient(), 0);
        if (value instanceof SizedFluidIngredient fluid) return fluid.amount() > 0 && FluidIngredientSupport.isSupported(fluid.ingredient());
        return false;
    }

    private static boolean supportedItem(Ingredient ingredient, int depth) {
        if (depth > 16) return false;
        if (!ingredient.isCustom()) return ingredient.getClass() == Ingredient.class;
        var custom = ingredient.getCustomIngredient();
        if (custom.getClass() == DataComponentIngredient.class || custom.getClass() == IntCircuitIngredient.class) return true;
        return custom.getClass() == CompoundIngredient.class
                && ((CompoundIngredient) custom).children().stream().allMatch(child -> supportedItem(child, depth + 1));
    }

    private static List<Content> inputContents(GTRecipe recipe) {
        List<Content> result = new ArrayList<>(recipe.getInputContents(ItemRecipeCapability.CAP));
        result.addAll(recipe.getInputContents(FluidRecipeCapability.CAP));
        return result;
    }

    @Override
    public List<ResolvedRecipe> candidates(String recipeId, Recipe<?> candidate, List<ResourceKey> available, ServerLevel level) {
        if (!supportsRecipe(candidate)) return List.of();
        var recipe = (GTRecipe) candidate;
        List<ResourceAmount> outputs = new ArrayList<>();
        for (var content : recipe.getOutputContents(ItemRecipeCapability.CAP)) {
            var ingredient = (SizedIngredient) content.content();
            var items = ingredient.getItems();
            if (items.length != 1 || items[0].isEmpty()) return List.of();
            outputs.add(new ResourceAmount(FactoryItemResources.keyOf(items[0], level.registryAccess()), BigInteger.valueOf(ingredient.count())));
        }
        for (var content : recipe.getOutputContents(FluidRecipeCapability.CAP)) {
            var ingredient = (SizedFluidIngredient) content.content();
            var fluids = ingredient.getFluids();
            if (fluids.length != 1 || fluids[0].isEmpty()) return List.of();
            outputs.add(new ResourceAmount(FactoryFluidResources.keyOf(fluids[0], level.registryAccess()), BigInteger.valueOf(ingredient.amount())));
        }
        if (outputs.isEmpty()) return List.of();
        List<Content> inputs = inputContents(recipe);
        List<List<ResourceAmount>> choices = new ArrayList<>();
        long combinations = 1;
        for (Content content : inputs) {
            List<ResourceAmount> matches = new ArrayList<>();
            for (ResourceKey key : available.stream().distinct().toList()) {
                try {
                    if (content.content() instanceof SizedIngredient item && key.kind() == ResourceKind.ITEM) {
                        var stack = FactoryItemResources.itemOf(key, level.registryAccess());
                        stack.setCount(item.count());
                        if (item.test(stack)) matches.add(new ResourceAmount(key, BigInteger.valueOf(item.count())));
                    } else if (content.content() instanceof SizedFluidIngredient fluid && key.kind() == ResourceKind.FLUID) {
                        var stack = FactoryFluidResources.fluidOf(key, level.registryAccess());
                        stack.setAmount(fluid.amount());
                        if (fluid.test(stack)) matches.add(new ResourceAmount(key, BigInteger.valueOf(fluid.amount())));
                    }
                } catch (IllegalArgumentException invalid) { /* Missing saved variants cannot bind. */ }
            }
            if (matches.isEmpty()) return List.of();
            combinations *= matches.size();
            if (combinations > MiniatureRecipeCatalog.MAX_CANDIDATES) throw new MiniatureRecipeCatalog.SearchLimit();
            choices.add(matches);
        }
        List<ResolvedRecipe> result = new ArrayList<>();
        bind(recipeId, inputs, choices, outputs, new ArrayList<>(), result);
        return result;
    }

    private void bind(String recipeId, List<Content> contents, List<List<ResourceAmount>> choices, List<ResourceAmount> outputs,
                      List<ResourceAmount> selected, List<ResolvedRecipe> result) {
        if (selected.size() == choices.size()) {
            List<RecipeReference.Binding> bindings = new ArrayList<>();
            List<ResourceAmount> withCatalysts = new ArrayList<>(outputs);
            List<ResourceAmount> catalysts = new ArrayList<>();
            for (int slot = 0; slot < selected.size(); slot++) {
                bindings.add(new RecipeReference.Binding(slot, selected.get(slot).resource()));
                if (contents.get(slot).chance() == 0) {
                    withCatalysts.add(selected.get(slot));
                    catalysts.add(selected.get(slot));
                }
            }
            result.add(new ResolvedRecipe(new RecipeReference(id(), recipeId, Math.min(3, selected.size()),
                    (selected.size() + 2) / 3, bindings), selected, withCatalysts, catalysts));
            return;
        }
        for (var choice : choices.get(selected.size())) {
            selected.add(choice);
            bind(recipeId, contents, choices, outputs, selected, result);
            selected.removeLast();
        }
    }
}
