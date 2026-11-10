package com.aquariverr.fluxod.miniature.recipe;

import com.aquariverr.fluxod.miniature.model.RecipeReference;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;

import java.util.List;

public interface MachineRecipeAdapter {
    String id();
    boolean supportsMachine(ItemStack machine);
    boolean supportsRecipe(Recipe<?> recipe);
    /** Additional inert defaults on the registered machine prototype; edited values remain forbidden. */
    default boolean supportsDefaultComponent(net.minecraft.core.component.DataComponentType<?> component) { return false; }
    Pattern pattern(Recipe<?> recipe);
    ResolvedRecipe resolve(RecipeReference reference, Recipe<?> recipe, ServerLevel level);

    record Pattern(int width, int height, List<Ingredient> ingredients) {
        public Pattern {
            ingredients = List.copyOf(ingredients);
            if (width < 1 || width > 3 || height < 1 || height > 3 || ingredients.size() != width * height
                    || ingredients.stream().allMatch(Ingredient::isEmpty)) throw new IllegalArgumentException("Unsupported recipe dimensions");
        }
    }
}
