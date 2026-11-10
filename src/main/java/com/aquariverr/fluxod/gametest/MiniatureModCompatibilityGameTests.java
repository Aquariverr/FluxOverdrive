package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.miniature.recipe.MachineRecipeAdapters;
import com.aquariverr.fluxod.miniature.recipe.MiniatureRecipeCatalog;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import com.aquariverr.fluxod.miniature.storage.VanillaMachinePolicy;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.List;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureModCompatibilityGameTests {
    @GameTest(template = "empty")
    public static void stonecutterUsesRegisteredRecipe(GameTestHelper helper) {
        var registry = helper.getLevel().registryAccess();
        var result = MiniatureRecipeCatalog.get(helper.getLevel().getServer()).search(
                FactoryItemResources.keyOf(new ItemStack(Items.STONECUTTER), registry),
                List.of(FactoryItemResources.keyOf(new ItemStack(Items.STONE), registry)), helper.getLevel());
        helper.assertTrue(!result.candidates().isEmpty(), "Stonecutting was not supported");
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void smelterTagAliasesUseNativeVanillaSmelting(GameTestHelper helper) {
        if (!ModList.get().isLoaded("mekanism")) { helper.succeed(); return; }
        var registry = helper.getLevel().registryAccess();
        for (String name : List.of("energized_smelter", "basic_smelting_factory", "ultimate_smelting_factory")) {
            var machine = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("mekanism:" + name)));
            VanillaMachinePolicy.requireAccepted(machine);
            var result = MiniatureRecipeCatalog.get(helper.getLevel().getServer()).search(FactoryItemResources.keyOf(machine, registry),
                    List.of(FactoryItemResources.keyOf(new ItemStack(Items.IRON_ORE), registry)), helper.getLevel());
            helper.assertTrue(result.candidates().stream().anyMatch(recipe -> recipe.outputs().stream()
                    .anyMatch(output -> output.resource().registryId().equals("minecraft:iron_ingot"))), "Smelter alias failed: " + name);
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void gtceuNativeMachinesResolveItemsFluidsAndCatalysts(GameTestHelper helper) {
        if (ModList.get().getModContainerById("gtceu")
                .map(mod -> mod.getModInfo().getVersion().getMajorVersion() >= 8).orElse(false)) Gtceu.verify(helper);
        helper.succeed();
    }

    private static final class Gtceu {
        static void verify(GameTestHelper helper) {
            var registry = helper.getLevel().registryAccess();
            int verified = 0;
            boolean fluidSeen = false;
            boolean catalystSeen = false;
            for (String machineId : List.of("lv_macerator", "lv_electrolyzer", "lv_mixer", "lv_assembler")) {
                var machine = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse("gtceu:" + machineId)));
                VanillaMachinePolicy.requireAccepted(machine);
                var adapter = (com.aquariverr.fluxod.miniature.recipe.ResourceRecipeAdapter) MachineRecipeAdapters.forMachine(machine).getFirst();
                int count = 0;
                for (var holder : helper.getLevel().getRecipeManager().getRecipes()) {
                    if (!adapter.supportsRecipe(holder.value())) continue;
                    var recipe = (com.gregtechceu.gtceu.api.recipe.GTRecipe) holder.value();
                    var available = new java.util.ArrayList<com.aquariverr.fluxod.miniature.model.ResourceKey>();
                    for (var content : recipe.getInputContents(com.gregtechceu.gtceu.api.capability.recipe.ItemRecipeCapability.CAP)) {
                        var items = ((net.neoforged.neoforge.common.crafting.SizedIngredient) content.content()).getItems();
                        if (items.length > 0) available.add(FactoryItemResources.keyOf(items[0], registry));
                    }
                    for (var content : recipe.getInputContents(com.gregtechceu.gtceu.api.capability.recipe.FluidRecipeCapability.CAP)) {
                        var fluids = ((net.neoforged.neoforge.fluids.crafting.SizedFluidIngredient) content.content()).getFluids();
                        if (fluids.length > 0) available.add(com.aquariverr.fluxod.miniature.storage.FactoryFluidResources.keyOf(fluids[0], registry));
                    }
                    var candidates = adapter.candidates(holder.id().toString(), recipe, available, helper.getLevel());
                    if (candidates.isEmpty()) continue;
                    var resolved = candidates.getFirst();
                    helper.assertTrue(MiniatureRecipeCatalog.get(helper.getLevel().getServer()).resolve(
                            FactoryItemResources.keyOf(machine, registry), resolved.reference(), helper.getLevel()).equals(resolved),
                            "GTCEu binding did not revalidate");
                    fluidSeen |= resolved.inputs().stream().anyMatch(input -> input.resource().kind() == com.aquariverr.fluxod.miniature.model.ResourceKind.FLUID);
                    boolean catalyst = recipe.inputs.values().stream().flatMap(List::stream).anyMatch(content -> content.chance() == 0);
                    if (catalyst) {
                        catalystSeen = true;
                        helper.assertTrue(resolved.inputs().stream().anyMatch(resolved.outputs()::contains), "Nonconsumable input was lost");
                    }
                    var conditional = recipe.copy();
                    conditional.conditions.add(null);
                    helper.assertTrue(!adapter.supportsRecipe(conditional), "Unchecked recipe conditions were ignored");
                    var random = recipe.copy();
                    var outputs = random.outputs.values().iterator().next();
                    var first = outputs.getFirst();
                    outputs.set(0, new com.gregtechceu.gtceu.api.recipe.content.Content(first.content(), 1, 100));
                    helper.assertTrue(!adapter.supportsRecipe(random), "Random outputs were made deterministic");
                    verified++;
                    if (++count >= 8) break;
                }
                helper.assertTrue(count > 0, "No native deterministic recipe resolved for " + machineId);
            }
            helper.assertTrue(verified >= 4 && fluidSeen && catalystSeen, "Missing native GTCEu fluid/catalyst coverage");
        }
    }
}
