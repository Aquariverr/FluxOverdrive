package com.aquariverr.fluxod.integration.mekanism;

import com.aquariverr.fluxod.miniature.model.RecipeReference;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.miniature.recipe.FluidIngredientSupport;
import com.aquariverr.fluxod.miniature.recipe.ResolvedRecipe;
import com.aquariverr.fluxod.miniature.recipe.ResourceRecipeAdapter;
import com.aquariverr.fluxod.miniature.storage.FactoryFluidResources;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import mekanism.api.chemical.ChemicalStack;
import mekanism.api.recipes.*;
import mekanism.api.recipes.basic.*;
import mekanism.api.recipes.ingredients.ChemicalStackIngredient;
import mekanism.api.recipes.ingredients.FluidStackIngredient;
import mekanism.api.recipes.ingredients.ItemStackIngredient;
import mekanism.api.recipes.ingredients.chemical.ChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.CompoundChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.SingleChemicalIngredient;
import mekanism.api.recipes.ingredients.chemical.TagChemicalIngredient;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.Recipe;
import net.neoforged.neoforge.common.crafting.CompoundIngredient;
import net.neoforged.neoforge.common.crafting.DataComponentIngredient;
import net.neoforged.neoforge.fluids.FluidStack;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

final class MekanismResourceRecipeAdapter implements ResourceRecipeAdapter {
    enum Kind {
        ENRICHING(BasicEnrichingRecipe.class), CRUSHING(BasicCrushingRecipe.class),
        PURIFYING(BasicPurifyingRecipe.class), INJECTING(BasicInjectingRecipe.class),
        CENTRIFUGING(BasicCentrifugingRecipe.class), INFUSING(BasicChemicalInfuserRecipe.class),
        ELECTROLYSIS(BasicElectrolysisRecipe.class), SMELTING(BasicSmeltingRecipe.class),
        COMPRESSING(BasicCompressingRecipe.class), METALLURGIC_INFUSING(BasicMetallurgicInfuserRecipe.class),
        PAINTING(BasicPaintingRecipe.class), NUCLEOSYNTHESIZING(BasicNucleosynthesizingRecipe.class),
        OXIDIZING(BasicChemicalOxidizerRecipe.class), PIGMENT_EXTRACTING(BasicPigmentExtractingRecipe.class),
        PIGMENT_MIXING(BasicPigmentMixingRecipe.class), ACTIVATING(BasicActivatingRecipe.class),
        WASHING(BasicWashingRecipe.class), CRYSTALLIZING(BasicChemicalCrystallizerRecipe.class),
        DISSOLVING(BasicChemicalDissolutionRecipe.class), COMBINING(BasicCombinerRecipe.class),
        REACTION(BasicPressurizedReactionRecipe.class), ROTARY(BasicRotaryRecipe.class),
        EVAPORATING(BasicFluidToFluidRecipe.class), SAWING(BasicSawmillRecipe.class);

        private final Class<?> recipeClass;

        Kind(Class<?> recipeClass) { this.recipeClass = recipeClass; }
    }

    private final String id;
    private final Set<String> machines;
    private final Kind kind;

    MekanismResourceRecipeAdapter(String id, Set<String> machines, Kind kind) {
        this.id = id;
        this.machines = Set.copyOf(machines);
        this.kind = kind;
    }

    @Override public String id() { return id; }

    @Override public boolean supportsDefaultComponent(net.minecraft.core.component.DataComponentType<?> component) {
        var id = net.minecraft.core.registries.BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(component);
        // BlockDeferredRegister supplies configuration defaults; ItemDeferredRegister adds empty
        // resource containers during ModifyDefaultComponentsEvent. The storage policy still requires
        // the exact prototype value and rejects changed or removed components, including filled tanks.
        return id != null && id.getNamespace().equals("mekanism")
                && Set.of("security", "redstone_control", "upgrades", "ejector", "side_config",
                        "energy", "items", "chemicals", "fluids", "dump_mode", "secondary_dump_mode", "sorting", "rotary_mode").contains(id.getPath());
    }

    @Override public boolean supportsMachine(ItemStack machine) {
        return !machine.isEmpty() && machine.getItem().builtInRegistryHolder().key().location().toString().startsWith("mekanism:")
                && machines.contains(machine.getItem().builtInRegistryHolder().key().location().toString());
    }

    @Override public boolean supportsRecipe(Recipe<?> recipe) {
        // These concrete native classes fix both the machine recipe type and deterministic output semantics.
        // Accepting their shared base classes (or subclasses) admits other machines and dynamic recipes.
        if (recipe.getClass() != kind.recipeClass) return false;
        return switch (kind) {
            case ENRICHING, CRUSHING, SMELTING -> supportedItem(((ItemStackToItemStackRecipe) recipe).getInput().ingredient().ingredient(), 0);
            case PURIFYING, INJECTING, COMPRESSING, METALLURGIC_INFUSING, PAINTING, NUCLEOSYNTHESIZING -> {
                var nativeRecipe = (ItemStackChemicalToItemStackRecipe) recipe;
                yield supportedItem(nativeRecipe.getItemInput().ingredient().ingredient(), 0)
                        && supportedChemical(nativeRecipe.getChemicalInput().ingredient(), 0);
            }
            case CENTRIFUGING, ACTIVATING -> supportedChemical(((ChemicalToChemicalRecipe) recipe).getInput().ingredient(), 0);
            case INFUSING, PIGMENT_MIXING -> supportedChemical(((ChemicalChemicalToChemicalRecipe) recipe).getLeftInput().ingredient(), 0)
                    && supportedChemical(((ChemicalChemicalToChemicalRecipe) recipe).getRightInput().ingredient(), 0);
            case ELECTROLYSIS -> FluidIngredientSupport.isSupported(((ElectrolysisRecipe) recipe).getInput().ingredient().ingredient());
            case OXIDIZING, PIGMENT_EXTRACTING -> supportedItem(((ItemStackToChemicalRecipe) recipe).getInput().ingredient().ingredient(), 0);
            case CRYSTALLIZING -> supportedChemical(((ChemicalCrystallizerRecipe) recipe).getInput().ingredient(), 0);
            case EVAPORATING -> FluidIngredientSupport.isSupported(((FluidToFluidRecipe) recipe).getInput().ingredient().ingredient());
            case WASHING -> {
                var value = (FluidChemicalToChemicalRecipe) recipe;
                yield FluidIngredientSupport.isSupported(value.getFluidInput().ingredient().ingredient())
                        && supportedChemical(value.getChemicalInput().ingredient(), 0);
            }
            case DISSOLVING -> {
                var value = (ChemicalDissolutionRecipe) recipe;
                yield supportedItem(value.getItemInput().ingredient().ingredient(), 0)
                        && supportedChemical(value.getChemicalInput().ingredient(), 0);
            }
            case COMBINING -> {
                var value = (CombinerRecipe) recipe;
                yield supportedItem(value.getMainInput().ingredient().ingredient(), 0)
                        && supportedItem(value.getExtraInput().ingredient().ingredient(), 0);
            }
            case REACTION -> {
                var value = (PressurizedReactionRecipe) recipe;
                yield supportedItem(value.getInputSolid().ingredient().ingredient(), 0)
                        && FluidIngredientSupport.isSupported(value.getInputFluid().ingredient().ingredient())
                        && supportedChemical(value.getInputChemical().ingredient(), 0);
            }
            case ROTARY -> {
                var value = (RotaryRecipe) recipe;
                yield (!value.hasFluidToChemical() || FluidIngredientSupport.isSupported(value.getFluidInput().ingredient().ingredient()))
                        && (!value.hasChemicalToFluid() || supportedChemical(value.getChemicalInput().ingredient(), 0));
            }
            case SAWING -> {
                var value = (SawmillRecipe) recipe;
                yield (value.getSecondaryChance() == 0 || value.getSecondaryChance() == 1)
                        && supportedItem(value.getInput().ingredient().ingredient(), 0);
            }
        };
    }

    @Override
    public List<ResolvedRecipe> candidates(String recipeId, Recipe<?> recipe, List<ResourceKey> available, ServerLevel level) {
        if (!supportsRecipe(recipe)) return List.of();
        List<ResolvedRecipe> candidates = switch (kind) {
            case ENRICHING, CRUSHING, SMELTING -> itemToItem(recipeId, (ItemStackToItemStackRecipe) recipe, available, level);
            case PURIFYING, INJECTING, COMPRESSING, METALLURGIC_INFUSING, PAINTING, NUCLEOSYNTHESIZING -> itemChemicalToItem(recipeId, (ItemStackChemicalToItemStackRecipe) recipe, available, level);
            case CENTRIFUGING, ACTIVATING -> chemicalToChemical(recipeId, (ChemicalToChemicalRecipe) recipe, available, level);
            case INFUSING, PIGMENT_MIXING -> twoChemicals(recipeId, (ChemicalChemicalToChemicalRecipe) recipe, available, level);
            case ELECTROLYSIS -> fluidToChemicals(recipeId, (ElectrolysisRecipe) recipe, available, level);
            default -> additionalCandidates(recipeId, recipe, available, level);
        };
        // Match native ingredients at their original amounts, then convert consumption to a full
        // operation with eight chemical upgrades where the native machine supports them.
        // Normalization, installed machines and environment parallelism
        // are applied later; the miniature universe's 20 paid ticks are not the native duration.
        int usageTicks = chemicalUsageTicks(recipe);
        return candidates.stream().map(candidate -> MekanismChemicalUsage.forBatch(candidate, usageTicks)).toList();
    }

    private int chemicalUsageTicks(Recipe<?> recipe) {
        if (recipe instanceof ChemicalDissolutionRecipe dissolution && dissolution.perTickUsage()) {
            return MekanismChemicalUsage.DISSOLUTION_TICKS / MekanismChemicalUsage.FULL_CHEMICAL_UPGRADE_DIVISOR;
        }
        if (recipe instanceof ItemStackChemicalToItemStackRecipe itemChemical && itemChemical.perTickUsage()) {
            if (kind == Kind.PURIFYING || kind == Kind.INJECTING) {
                return MekanismChemicalUsage.ITEM_CHEMICAL_TICKS / MekanismChemicalUsage.FULL_CHEMICAL_UPGRADE_DIVISOR;
            }
            return recipe instanceof NucleosynthesizingRecipe nucleosynthesizing
                    ? nucleosynthesizing.getDuration() : MekanismChemicalUsage.ITEM_CHEMICAL_TICKS;
        }
        return 1;
    }

    private List<ResolvedRecipe> additionalCandidates(String recipeId, Recipe<?> recipe, List<ResourceKey> available, ServerLevel level) {
        List<ResolvedRecipe> result = new ArrayList<>();
        if (recipe instanceof ItemStackToChemicalRecipe value) {
            for (var input : itemChoices(value.getInput(), available, level)) {
                if (value.test(input.value())) add(result, recipeId, List.of(input), level, value.getOutput(input.value()));
            }
        } else if (recipe instanceof ChemicalCrystallizerRecipe value) {
            for (var input : chemicalChoices(value.getInput(), available, level)) {
                if (value.test(input.value())) add(result, recipeId, List.of(input), level, value.getOutput(input.value()));
            }
        } else if (recipe instanceof FluidToFluidRecipe value) {
            for (var input : fluidChoices(value.getInput(), available, level)) {
                if (value.test(input.value())) add(result, recipeId, List.of(input), level, value.getOutput(input.value()));
            }
        } else if (recipe instanceof FluidChemicalToChemicalRecipe value) {
            for (var fluid : fluidChoices(value.getFluidInput(), available, level))
                for (var chemical : chemicalChoices(value.getChemicalInput(), available, level)) {
                    if (value.test(fluid.value(), chemical.value())) add(result, recipeId, List.of(fluid, chemical), level,
                            value.getOutput(fluid.value(), chemical.value()));
                }
        } else if (recipe instanceof ChemicalDissolutionRecipe value) {
            for (var item : itemChoices(value.getItemInput(), available, level))
                for (var chemical : chemicalChoices(value.getChemicalInput(), available, level)) {
                    if (value.test(item.value(), chemical.value())) add(result, recipeId, List.of(item, chemical), level,
                            value.getOutput(item.value(), chemical.value()));
                }
        } else if (recipe instanceof CombinerRecipe value) {
            for (var main : itemChoices(value.getMainInput(), available, level))
                for (var extra : itemChoices(value.getExtraInput(), available, level)) {
                    if (value.test(main.value(), extra.value())) add(result, recipeId, List.of(main, extra), level,
                            value.getOutput(main.value(), extra.value()));
                }
        } else if (recipe instanceof PressurizedReactionRecipe value) {
            for (var item : itemChoices(value.getInputSolid(), available, level))
                for (var fluid : fluidChoices(value.getInputFluid(), available, level))
                    for (var chemical : chemicalChoices(value.getInputChemical(), available, level)) {
                        if (!value.test(item.value(), fluid.value(), chemical.value())) continue;
                        var output = value.getOutput(item.value(), fluid.value(), chemical.value());
                        add(result, recipeId, List.of(item, fluid, chemical), level, output.item(), output.chemical());
                    }
        } else if (recipe instanceof RotaryRecipe value) {
            if (value.hasFluidToChemical()) for (var input : fluidChoices(value.getFluidInput(), available, level)) {
                if (value.test(input.value())) add(result, recipeId, List.of(input), level, value.getChemicalOutput(input.value()));
            }
            if (value.hasChemicalToFluid()) for (var input : chemicalChoices(value.getChemicalInput(), available, level)) {
                if (value.test(input.value())) add(result, recipeId, List.of(input), level, value.getFluidOutput(input.value()));
            }
        } else if (recipe instanceof SawmillRecipe value) {
            List<ItemStack> outputs = new ArrayList<>(value.getMainOutputDefinition());
            if (value.getSecondaryChance() == 1) outputs.addAll(value.getSecondaryOutputDefinition());
            for (var input : itemChoices(value.getInput(), available, level)) {
                if (value.test(input.value())) add(result, recipeId, List.of(input), level, outputs.toArray());
            }
        }
        return result;
    }

    private void add(List<ResolvedRecipe> result, String recipeId, List<? extends Choice<?>> inputs, ServerLevel level, Object... values) {
        List<ResourceAmount> outputs = new ArrayList<>();
        for (Object value : values) {
            if (value instanceof ItemStack item && !item.isEmpty()) outputs.add(amount(FactoryItemResources.keyOf(item, level.registryAccess()), item.getCount()));
            else if (value instanceof FluidStack fluid && !fluid.isEmpty()) outputs.add(amount(FactoryFluidResources.keyOf(fluid, level.registryAccess()), fluid.getAmount()));
            else if (value instanceof ChemicalStack chemical && !chemical.isEmpty()) outputs.add(amount(chemicalKey(chemical), chemical.getAmount()));
        }
        if (!outputs.isEmpty()) result.add(resolved(recipeId, inputs, outputs));
    }

    private List<ResolvedRecipe> itemToItem(String recipeId, ItemStackToItemStackRecipe recipe, List<ResourceKey> available, ServerLevel level) {
        List<Choice<ItemStack>> choices = itemChoices(recipe.getInput(), available, level);
        List<ResolvedRecipe> result = new ArrayList<>();
        for (Choice<ItemStack> input : choices) {
            if (!recipe.test(input.value())) continue;
            ItemStack output = recipe.getOutput(input.value());
            if (output.isEmpty()) continue;
            result.add(resolved(recipeId, List.of(input), List.of(
                    amount(FactoryItemResources.keyOf(output, level.registryAccess()), output.getCount()))));
        }
        return result;
    }

    private List<ResolvedRecipe> itemChemicalToItem(String recipeId, ItemStackChemicalToItemStackRecipe recipe,
                                                     List<ResourceKey> available, ServerLevel level) {
        List<Choice<ItemStack>> items = itemChoices(recipe.getItemInput(), available, level);
        List<Choice<ChemicalStack>> chemicals = chemicalChoices(recipe.getChemicalInput(), available, level);
        List<ResolvedRecipe> result = new ArrayList<>();
        for (Choice<ItemStack> item : items) for (Choice<ChemicalStack> chemical : chemicals) {
            if (!recipe.test(item.value(), chemical.value())) continue;
            ItemStack output = recipe.getOutput(item.value(), chemical.value());
            if (!output.isEmpty()) result.add(resolved(recipeId, List.of(item, chemical), List.of(
                    amount(FactoryItemResources.keyOf(output, level.registryAccess()), output.getCount()))));
        }
        return result;
    }

    private List<ResolvedRecipe> chemicalToChemical(String recipeId, ChemicalToChemicalRecipe recipe, List<ResourceKey> available, ServerLevel level) {
        List<Choice<ChemicalStack>> inputs = chemicalChoices(recipe.getInput(), available, level);
        List<ResolvedRecipe> result = new ArrayList<>();
        for (Choice<ChemicalStack> input : inputs) {
            if (!recipe.test(input.value())) continue;
            ChemicalStack output = recipe.getOutput(input.value());
            if (!output.isEmpty()) result.add(resolved(recipeId, List.of(input), List.of(
                    amount(chemicalKey(output), output.getAmount()))));
        }
        return result;
    }

    private List<ResolvedRecipe> twoChemicals(String recipeId, ChemicalChemicalToChemicalRecipe recipe, List<ResourceKey> available, ServerLevel level) {
        List<Choice<ChemicalStack>> left = chemicalChoices(recipe.getLeftInput(), available, level);
        List<Choice<ChemicalStack>> right = chemicalChoices(recipe.getRightInput(), available, level);
        List<ResolvedRecipe> result = new ArrayList<>();
        for (Choice<ChemicalStack> a : left) for (Choice<ChemicalStack> b : right) {
            if (!recipe.test(a.value(), b.value())) continue;
            ChemicalStack output = recipe.getOutput(a.value(), b.value());
            if (!output.isEmpty()) result.add(resolved(recipeId, List.of(a, b), List.of(
                    amount(chemicalKey(output), output.getAmount()))));
        }
        return result;
    }

    private List<ResolvedRecipe> fluidToChemicals(String recipeId, ElectrolysisRecipe recipe, List<ResourceKey> available, ServerLevel level) {
        List<Choice<FluidStack>> inputs = fluidChoices(recipe.getInput(), available, level);
        List<ResolvedRecipe> result = new ArrayList<>();
        for (Choice<FluidStack> input : inputs) {
            if (!recipe.test(input.value())) continue;
            ElectrolysisRecipe.ElectrolysisRecipeOutput output = recipe.getOutput(input.value());
            if (!output.left().isEmpty() && !output.right().isEmpty()) {
                result.add(resolved(recipeId, List.of(input), List.of(
                        amount(chemicalKey(output.left()), output.left().getAmount()),
                        amount(chemicalKey(output.right()), output.right().getAmount()))));
            }
        }
        return result;
    }

    private ResolvedRecipe resolved(String recipeId, List<? extends Choice<?>> choices, List<ResourceAmount> outputs) {
        List<ResourceAmount> inputs = choices.stream().map(choice -> amount(choice.key(), choice.amount())).toList();
        List<RecipeReference.Binding> bindings = new ArrayList<>();
        for (int slot = 0; slot < choices.size(); slot++) bindings.add(new RecipeReference.Binding(slot, choices.get(slot).key()));
        int width = choices.size();
        return new ResolvedRecipe(new RecipeReference(id, recipeId, width, 1, bindings), inputs, outputs);
    }

    private ResourceAmount amount(ResourceKey key, long amount) {
        return new ResourceAmount(key, BigInteger.valueOf(amount));
    }

    private static ResourceKey chemicalKey(ChemicalStack stack) {
        return new ResourceKey(ResourceKind.CHEMICAL, stack.getChemicalHolder().getRegisteredName());
    }

    private static List<Choice<ItemStack>> itemChoices(ItemStackIngredient ingredient, List<ResourceKey> available,
                                                        ServerLevel level) {
        List<Choice<ItemStack>> result = new ArrayList<>();
        for (ResourceKey key : available.stream().distinct().filter(key -> key.kind() == ResourceKind.ITEM).toList()) {
            try {
                ItemStack stack = FactoryItemResources.itemOf(key, level.registryAccess());
                long amount = ingredient.getNeededAmount(stack);
                if (amount <= 0 || amount > Integer.MAX_VALUE) continue;
                stack.setCount((int) amount);
                if (!ingredient.test(stack)) continue;
                ItemStack matching = ingredient.getMatchingInstance(stack);
                if (!matching.isEmpty() && matching.getCount() == amount
                        && key.equals(FactoryItemResources.keyOf(matching, level.registryAccess()))) {
                    result.add(new Choice<>(key, matching, amount));
                }
            } catch (IllegalArgumentException exception) { /* Invalid or missing saved variants cannot bind. */ }
        }
        return result;
    }

    private static List<Choice<FluidStack>> fluidChoices(FluidStackIngredient ingredient, List<ResourceKey> available,
                                                          ServerLevel level) {
        List<Choice<FluidStack>> result = new ArrayList<>();
        for (ResourceKey key : available.stream().distinct().filter(key -> key.kind() == ResourceKind.FLUID).toList()) {
            try {
                FluidStack stack = FactoryFluidResources.fluidOf(key, level.registryAccess());
                long amount = ingredient.getNeededAmount(stack);
                if (amount <= 0 || amount > Integer.MAX_VALUE) continue;
                stack.setAmount((int) amount);
                if (!ingredient.test(stack)) continue;
                FluidStack matching = ingredient.getMatchingInstance(stack);
                if (!matching.isEmpty() && matching.getAmount() == amount
                        && key.equals(FactoryFluidResources.keyOf(matching, level.registryAccess()))) {
                    result.add(new Choice<>(key, matching, amount));
                }
            } catch (IllegalArgumentException exception) { /* Invalid or missing saved variants cannot bind. */ }
        }
        return result;
    }

    private static List<Choice<ChemicalStack>> chemicalChoices(ChemicalStackIngredient ingredient,
                                                               List<ResourceKey> available, ServerLevel level) {
        List<Choice<ChemicalStack>> result = new ArrayList<>();
        for (ResourceKey key : available.stream().distinct().filter(key -> key.kind() == ResourceKind.CHEMICAL).toList()) {
            // Chemical identity in 10.7 has no data components. Never silently discard a forged variant.
            if (!key.components().isEmpty()) continue;
            try {
                ChemicalStack stack = MekanismMiniatureChemicalHandler.chemicalOf(key, level.registryAccess());
                long amount = ingredient.getNeededAmount(stack);
                if (amount <= 0) continue;
                stack.setAmount(amount);
                if (!ingredient.test(stack)) continue;
                ChemicalStack matching = ingredient.getMatchingInstance(stack);
                if (!matching.isEmpty() && matching.getAmount() == amount && key.equals(chemicalKey(matching))) {
                    result.add(new Choice<>(key, matching, amount));
                }
            } catch (IllegalArgumentException exception) { /* Invalid or missing saved variants cannot bind. */ }
        }
        return result;
    }

    // Native ingredient predicates are deterministic; arbitrary addon predicates need a separate adapter.
    private static boolean supportedItem(Ingredient ingredient, int depth) {
        if (depth > 16) return false;
        if (!ingredient.isCustom()) return ingredient.getClass() == Ingredient.class;
        if (ingredient.getCustomIngredient().getClass() == DataComponentIngredient.class) return true;
        return ingredient.getCustomIngredient().getClass() == CompoundIngredient.class
                && ((CompoundIngredient) ingredient.getCustomIngredient()).children().stream()
                .allMatch(child -> supportedItem(child, depth + 1));
    }

    private static boolean supportedChemical(ChemicalIngredient ingredient, int depth) {
        if (depth > 16) return false;
        if (ingredient.getClass() == SingleChemicalIngredient.class || ingredient.getClass() == TagChemicalIngredient.class) return true;
        return ingredient.getClass() == CompoundChemicalIngredient.class
                && ((CompoundChemicalIngredient) ingredient).children().stream().allMatch(child -> supportedChemical(child, depth + 1));
    }

    private record Choice<T>(ResourceKey key, T value, long amount) {}
}
