package com.aquariverr.fluxod.miniature.recipe;

import com.aquariverr.fluxod.miniature.model.RecipeReference;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.CraftingInput;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SingleRecipeInput;
import net.minecraft.world.item.crafting.AbstractCookingRecipe;
import net.minecraft.world.item.crafting.StonecutterRecipe;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final record VanillaRecipeAdapter(String id, Item machine, List<Class<?>> recipeClasses) implements MachineRecipeAdapter {
    @Override
    public boolean supportsMachine(ItemStack stack) { return !stack.isEmpty() && stack.is(machine); }

    @Override
    public boolean supportsRecipe(Recipe<?> recipe) {
        return recipeClasses.contains(recipe.getClass()) && !recipe.isSpecial()
                && recipe.getIngredients().stream().allMatch(ingredient -> supportedIngredient(ingredient, 0));
    }

    private static boolean supportedIngredient(Ingredient ingredient, int depth) {
        if (depth > 16) return false;
        if (!ingredient.isCustom() || ingredient.getCustomIngredient().getClass() == DataComponentIngredient.class) return true;
        return ingredient.getCustomIngredient().getClass() == CompoundIngredient.class
                && ingredient.getCustomIngredient() instanceof CompoundIngredient alternatives
                && alternatives.children().stream().allMatch(child -> supportedIngredient(child, depth + 1));
    }

    @Override
    public Pattern pattern(Recipe<?> recipe) {
        if (!supportsRecipe(recipe)) throw new IllegalArgumentException("Unsupported recipe implementation or ingredient");
        var ingredients = new ArrayList<>(recipe.getIngredients());
        int width = recipe instanceof ShapedRecipe shaped ? shaped.getWidth() : Math.min(3, ingredients.size());
        int height = recipe instanceof ShapedRecipe shaped ? shaped.getHeight() : (ingredients.size() + 2) / 3;
        while (ingredients.size() < width * height) ingredients.add(Ingredient.EMPTY);
        return new Pattern(width, height, ingredients);
    }

    @Override
    public ResolvedRecipe resolve(RecipeReference reference, Recipe<?> recipe, ServerLevel level) {
        Pattern pattern = pattern(recipe);
        if (!id.equals(reference.adapterId()) || pattern.width() != reference.width() || pattern.height() != reference.height()) {
            throw new IllegalArgumentException("Recipe adapter or input dimensions do not match");
        }
        List<ItemStack> items = new ArrayList<>(Collections.nCopies(reference.width() * reference.height(), ItemStack.EMPTY));
        for (var binding : reference.bindings()) items.set(binding.slot(), FactoryItemResources.itemOf(binding.resource(), level.registryAccess()));
        if (recipe instanceof CraftingRecipe crafting) {
            return resolveNative(reference, crafting, CraftingInput.of(reference.width(), reference.height(), items), level);
        }
        if (recipe instanceof AbstractCookingRecipe cooking && items.size() == 1) {
            return resolveNative(reference, cooking, new SingleRecipeInput(items.getFirst()), level);
        }
        if (recipe instanceof StonecutterRecipe cutting && items.size() == 1) {
            return resolveNative(reference, cutting, new SingleRecipeInput(items.getFirst()), level);
        }
        throw new IllegalArgumentException("Unsupported native recipe input");
    }

    private static <I extends RecipeInput> ResolvedRecipe resolveNative(RecipeReference reference, Recipe<I> recipe,
                                                                       I input, ServerLevel level) {
        if (!recipe.matches(input, level)) throw new IllegalArgumentException("Native recipe rejected exact input slots");
        var inputs = new ArrayList<ResourceAmount>();
        for (int slot = 0; slot < input.size(); slot++) add(inputs, input.getItem(slot), level);
        var outputs = new ArrayList<ResourceAmount>();
        ItemStack result = recipe.assemble(input, level.registryAccess());
        if (result.isEmpty()) throw new IllegalArgumentException("Recipe has no deterministic result");
        add(outputs, result, level);
        var remaining = recipe.getRemainingItems(input);
        if (remaining.size() != input.size()) throw new IllegalArgumentException("Invalid remaining item slots");
        for (int slot = 0; slot < remaining.size(); slot++) {
            ItemStack remainder = remaining.get(slot);
            ItemStack consumed = input.getItem(slot);
            if (!remainder.isEmpty() && (consumed.isEmpty() || consumed.isDamageableItem()
                    || remainder.isDamageableItem() || remainder.is(consumed.getItem()))) {
                throw new IllegalArgumentException("Catalysts and changing tools require an explicit adapter");
            }
            add(outputs, remainder, level);
        }
        return new ResolvedRecipe(reference, inputs, outputs);
    }

    private static void add(List<ResourceAmount> resources, ItemStack stack, ServerLevel level) {
        if (!stack.isEmpty()) resources.add(new ResourceAmount(FactoryItemResources.keyOf(stack, level.registryAccess()),
                BigInteger.valueOf(stack.getCount())));
    }
}
