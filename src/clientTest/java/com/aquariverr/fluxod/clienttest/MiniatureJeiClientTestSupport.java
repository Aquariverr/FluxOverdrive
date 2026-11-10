package com.aquariverr.fluxod.clienttest;

import com.aquariverr.fluxod.client.screen.MiniatureUniverseScreen;
import com.aquariverr.fluxod.miniature.model.RecipeReference;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.constants.RecipeTypes;
import mezz.jei.api.gui.handlers.IGhostIngredientHandler;
import mezz.jei.api.ingredients.ITypedIngredient;
import mezz.jei.api.gui.ingredient.IRecipeSlotsView;
import mezz.jei.api.recipe.RecipeIngredientRole;
import mezz.jei.api.recipe.category.IRecipeCategory;
import mezz.jei.api.runtime.IJeiRuntime;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.minecraft.world.item.crafting.CraftingRecipe;
import net.minecraft.world.item.crafting.SmeltingRecipe;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;

/** Test-only runtime observer. Tests use handlers retrieved from JEI's real registration managers. */
@JeiPlugin
public final class MiniatureJeiClientTestSupport implements IModPlugin {
    private static IJeiRuntime runtime;
    private static RecipeHolder<SmeltingRecipe> smeltingRecipe;
    private static RecipeHolder<CraftingRecipe> craftingRecipe;
    private static IRecipeCategory<Object> infusingCategory;
    private static RecipeHolder<?> infusingRecipe;
    private static IRecipeCategory<Object> dissolvingCategory;
    private static RecipeHolder<?> dissolvingRecipe;

    @Override public ResourceLocation getPluginUid() {
        return ResourceLocation.fromNamespaceAndPath(MiniatureClientTest.MOD_ID, "jei_runtime_verification");
    }

    @Override public void onRuntimeAvailable(IJeiRuntime available) { runtime = available; }
    @Override public void onRuntimeUnavailable() { runtime = null; }

    static boolean ready() { return runtime != null; }

    static IngredientAvailability ingredientAvailability(Object ingredient) {
        var manager = runtime.getIngredientManager();
        boolean registered = manager.getIngredientTypeChecked(ingredient).isPresent();
        boolean recognized = manager.createTypedIngredient(ingredient, false).isPresent();
        return new IngredientAvailability(registered, recognized, Map.of("entry", "native_jei_ingredient_type_probe",
                "ingredientClass", ingredient.getClass().getName(), "ingredientManager", manager.getClass().getName(),
                "nativeTypeRegistered", registered, "typedIngredientCreated", recognized,
                "registeredIngredientClasses", manager.getRegisteredIngredientTypes().stream()
                        .map(type -> type.getIngredientClass().getName()).sorted().toList()));
    }

    record IngredientAvailability(boolean registered, boolean recognized, Map<String, Object> evidence) {}

    static NativeRecipeAvailability nativeRecipeAvailability() {
        var manager = runtime.getRecipeManager();
        var smeltingVisible = manager.createRecipeLookup(RecipeTypes.SMELTING).get().toList();
        var smeltingAll = manager.createRecipeLookup(RecipeTypes.SMELTING).includeHidden().get().toList();
        var craftingVisible = manager.createRecipeLookup(RecipeTypes.CRAFTING).get().toList();
        var craftingAll = manager.createRecipeLookup(RecipeTypes.CRAFTING).includeHidden().get().toList();
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.put("entry", "native_jei_recipe_registration_probe");
        evidence.put("recipeManager", manager.getClass().getName());
        evidence.put("smeltingCategoryClass", manager.getRecipeCategory(RecipeTypes.SMELTING).getClass().getName());
        evidence.put("craftingCategoryClass", manager.getRecipeCategory(RecipeTypes.CRAFTING).getClass().getName());
        evidence.put("smeltingVisibleRecipeCount", smeltingVisible.size());
        evidence.put("smeltingIncludingHiddenRecipeCount", smeltingAll.size());
        evidence.put("craftingVisibleRecipeCount", craftingVisible.size());
        evidence.put("craftingIncludingHiddenRecipeCount", craftingAll.size());
        evidence.put("smeltingHolderIdSample", smeltingAll.stream().map(recipe -> recipe.id().toString()).sorted().limit(8).toList());
        evidence.put("craftingHolderIdSample", craftingAll.stream().map(recipe -> recipe.id().toString()).sorted().limit(8).toList());
        return new NativeRecipeAvailability(smeltingAll.isEmpty() && craftingAll.isEmpty(), evidence);
    }

    record NativeRecipeAvailability(boolean bothCollectionsEmpty, Map<String, Object> evidence) {}

    static Map<String, Object> drop(MiniatureUniverseScreen screen, Object ingredient, double x, double y) {
        GhostRegistration registration = ghostHandler(screen);
        IGhostIngredientHandler<MiniatureUniverseScreen> handler = registration.handler();
        ITypedIngredient<Object> typed = typed(ingredient);
        var preview = handler.getTargetsTyped(screen, typed, false);
        var targets = handler.getTargetsTyped(screen, typed, true);
        require(areas(preview).equals(areas(targets)), "JEI hover and drag disagree on ghost target rectangles");
        validateClipping(screen, targets);
        var matching = targets.stream().filter(target -> target.getArea().contains((int) x, (int) y)).toList();
        require(matching.size() == 1, "JEI drop must hit exactly one registered target, found " + matching.size());
        Map<String, Object> evidence = new LinkedHashMap<>();
        evidence.putAll(registration.evidence());
        evidence.put("entry", "registered_IGhostIngredientHandler.getTargetsTyped/Target.accept");
        evidence.put("handler", handler.getClass().getName());
        evidence.put("ingredientType", typed.getType().getIngredientClass().getName());
        evidence.put("targets", areas(targets));
        evidence.put("dropX", x);
        evidence.put("dropY", y);
        matching.getFirst().accept(ingredient);
        handler.onComplete();
        return evidence;
    }

    static Map<String, Object> verifyTargets(MiniatureUniverseScreen screen, Object ingredient, int count,
                                              double allowedX, double allowedY, double excludedX, double excludedY) {
        var registration = ghostHandler(screen);
        var handler = registration.handler();
        var targets = handler.getTargetsTyped(screen, typed(ingredient), false);
        validateClipping(screen, targets);
        require(count < 0 || targets.size() == count, "Wrong number of visible editable JEI ghost targets: " + targets.size());
        require(targets.stream().filter(target -> target.getArea().contains((int) allowedX, (int) allowedY)).count() == 1,
                "Expected visible cell is not a JEI target");
        require(targets.stream().noneMatch(target -> target.getArea().contains((int) excludedX, (int) excludedY)),
                "JEI exposed an output/hidden/occupied cell as a ghost target");
        Map<String, Object> evidence = new LinkedHashMap<>(registration.evidence());
        evidence.putAll(Map.of("entry", "registered_IGhostIngredientHandler.getTargetsTyped", "handler", handler.getClass().getName(),
                "targets", areas(targets), "targetCount", targets.size(), "excludedX", excludedX, "excludedY", excludedY));
        return evidence;
    }

    static String findSmeltingRecipe() {
        smeltingRecipe = runtime.getRecipeManager().createRecipeLookup(RecipeTypes.SMELTING).get()
                .filter(recipe -> recipe.value().getIngredients().getFirst().test(new ItemStack(Items.IRON_ORE))
                        && recipe.value().getResultItem(Minecraft.getInstance().level.registryAccess()).is(Items.IRON_INGOT))
                .findFirst().orElseThrow(() -> new IllegalStateException("JEI did not register native iron ore smelting"));
        return smeltingRecipe.id().toString();
    }

    static void findCraftingRecipe(String id) {
        craftingRecipe = runtime.getRecipeManager().createRecipeLookup(RecipeTypes.CRAFTING).get()
                .filter(recipe -> recipe.id().toString().equals(id)).findFirst()
                .orElseThrow(() -> new IllegalStateException("JEI did not register native crafting recipe " + id));
    }

    static Map<String, Object> showSmelting(MiniatureUniverseScreen editor) {
        return show(editor, runtime.getRecipeManager().getRecipeCategory(RecipeTypes.SMELTING), smeltingRecipe);
    }

    static Map<String, Object> showCrafting(MiniatureUniverseScreen editor) {
        return show(editor, runtime.getRecipeManager().getRecipeCategory(RecipeTypes.CRAFTING), craftingRecipe);
    }

    private static <R> Map<String, Object> show(MiniatureUniverseScreen editor, IRecipeCategory<R> category, R recipe) {
        require(Minecraft.getInstance().screen == editor, "JEI recipe browser needs the editor as its parent");
        runtime.getRecipesGui().showRecipes(category, List.of(recipe), List.of());
        return verifyNativeBrowser(editor);
    }

    static Map<String, Object> verifyNativeBrowser(MiniatureUniverseScreen editor) {
        var current = Minecraft.getInstance().screen;
        require(current != null && current != editor && current.getClass().getName().equals("mezz.jei.gui.recipes.RecipesGui"),
                "Public JEI API did not open the native RecipesGui: " + current);
        require(runtime.getRecipesGui().getParentScreen().orElse(null) == editor,
                "JEI did not retain the original miniature editor as parent");
        require(Minecraft.getInstance().player.containerMenu == editor.getMenu(), "Opening JEI changed the active container menu");
        require(editor.getMenu().stillValid(Minecraft.getInstance().player), "Opening JEI invalidated the retained miniature menu");
        return Map.of("entry", "IRecipesGui.showRecipes", "screenClass", current.getClass().getName(),
                "editorTemporarilyReplaced", true, "originalMenuRetained", true, "containerRemainsValid", true);
    }

    static void closeBrowser(MiniatureUniverseScreen editor) {
        verifyNativeBrowser(editor);
        Minecraft.getInstance().screen.onClose();
        require(Minecraft.getInstance().screen == editor && Minecraft.getInstance().player.containerMenu == editor.getMenu(),
                "JEI close did not restore the original editor and menu");
    }

    static Map<String, Object> transferSmelting(MiniatureUniverseScreen screen, boolean execute, boolean expectedError) {
        require(smeltingRecipe != null, "No actual JEI recipe was selected for transfer");
        var category = runtime.getRecipeManager().getRecipeCategory(RecipeTypes.SMELTING);
        var layout = runtime.getRecipeManager().createRecipeLayoutDrawableOrShowError(category, smeltingRecipe,
                runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
        return transfer(screen, category, smeltingRecipe, layout.getRecipeSlotsView(), execute, expectedError);
    }

    static TransferObservation transferCrafting(MiniatureUniverseScreen screen, List<Integer> guiIndices,
                                                  List<Integer> patternIndices) {
        var category = runtime.getRecipeManager().getRecipeCategory(RecipeTypes.CRAFTING);
        var layout = runtime.getRecipeManager().createRecipeLayoutDrawableOrShowError(category, craftingRecipe,
                runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
        var slots = layout.getRecipeSlotsView();
        var inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
        require(inputs.size() == 9, "Native crafting category did not create its nine GUI input slots");
        List<RecipeReference.Binding> bindings = new ArrayList<>();
        for (int index = 0; index < 9; index++) {
            var ingredient = inputs.get(index).getDisplayedIngredient();
            require(ingredient.isPresent() == guiIndices.contains(index), "Native recipe has an unexpected occupied GUI slot " + index);
        }
        for (int index = 0; index < guiIndices.size(); index++) {
            Object ingredient = inputs.get(guiIndices.get(index)).getDisplayedIngredient().orElseThrow().getIngredient();
            require(ingredient instanceof ItemStack, "Native crafting INPUT is not an item");
            bindings.add(new RecipeReference.Binding(patternIndices.get(index), FactoryItemResources.keyOf(
                    ((ItemStack) ingredient).copy(), Minecraft.getInstance().level.registryAccess())));
        }
        Map<String, Object> evidence = new LinkedHashMap<>(transfer(screen, category, craftingRecipe, slots, true, false));
        evidence.put("occupiedJeiGuiSlots", guiIndices);
        evidence.put("expectedPatternSlots", patternIndices);
        evidence.put("expectedExactBindings", bindings);
        return new TransferObservation(evidence, List.copyOf(bindings));
    }

    @SuppressWarnings("unchecked")
    static String findInfusingRecipe() {
        var type = runtime.getRecipeManager().getRecipeType(ResourceLocation.parse("mekanism:chemical_infusing"))
                .orElseThrow(() -> new IllegalStateException("Mekanism did not register its native JEI infusing category"));
        infusingCategory = (IRecipeCategory<Object>) runtime.getRecipeManager().getRecipeCategory(type);
        Object recipe = runtime.getRecipeManager().createRecipeLookup(type).get().findFirst().orElseThrow();
        require(recipe instanceof RecipeHolder<?>, "Native Mekanism JEI category did not expose a real recipe holder");
        infusingRecipe = (RecipeHolder<?>) recipe;
        return infusingRecipe.id().toString();
    }

    static Map<String, Object> showInfusing(MiniatureUniverseScreen editor) {
        return show(editor, infusingCategory, infusingRecipe);
    }

    @SuppressWarnings("unchecked")
    static void findDissolvingRecipe() {
        var type = runtime.getRecipeManager().getRecipeType(ResourceLocation.parse("mekanism:dissolution")).orElseThrow();
        dissolvingCategory = (IRecipeCategory<Object>) runtime.getRecipeManager().getRecipeCategory(type);
        dissolvingRecipe = (RecipeHolder<?>) runtime.getRecipeManager().createRecipeLookup(type).get()
                .filter(recipe -> recipe instanceof RecipeHolder<?> holder
                        && holder.id().toString().equals("mekanism:processing/uranium/hydrofluoric_acid"))
                .findFirst().orElseThrow();
    }

    static Map<String, Object> showDissolving(MiniatureUniverseScreen editor) {
        return show(editor, dissolvingCategory, dissolvingRecipe);
    }

    static Map<String, Object> transferDissolving(MiniatureUniverseScreen screen) {
        var layout = runtime.getRecipeManager().createRecipeLayoutDrawableOrShowError(dissolvingCategory, dissolvingRecipe,
                runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
        return transfer(screen, dissolvingCategory, dissolvingRecipe, layout.getRecipeSlotsView(), true, false);
    }

    static TransferObservation transferInfusing(MiniatureUniverseScreen screen) {
        var layout = runtime.getRecipeManager().createRecipeLayoutDrawableOrShowError(infusingCategory, infusingRecipe,
                runtime.getJeiHelpers().getFocusFactory().getEmptyFocusGroup());
        var slots = layout.getRecipeSlotsView();
        var inputs = slots.getSlotViews(RecipeIngredientRole.INPUT);
        require(inputs.size() == 2, "Chemical infuser must declare exactly two chemical INPUT slots");
        Object first = inputs.getFirst().getDisplayedIngredient().orElseThrow().getIngredient();
        Object second = inputs.get(1).getDisplayedIngredient().orElseThrow().getIngredient();
        require(inputs.getFirst().getSlotName().orElse("").equals("leftInput")
                && inputs.get(1).getSlotName().orElse("").equals("rightInput"), "Chemical infuser INPUT order differs from its named left/right gauges");
        var bindings = List.of(new RecipeReference.Binding(0, ChemicalIdentity.key(first)),
                new RecipeReference.Binding(1, ChemicalIdentity.key(second)));
        require(!bindings.getFirst().resource().equals(bindings.get(1).resource()), "Infuser fixture needs distinct chemicals to prove binding order");
        require(slots.getSlotViews(RecipeIngredientRole.OUTPUT).size() == 1, "Chemical infuser fixture lacks its separate output gauge");
        Map<String, Object> evidence = new LinkedHashMap<>(transfer(screen, infusingCategory, infusingRecipe, slots, true, false));
        evidence.put("inputOrder", List.of("leftInput", "rightInput"));
        evidence.put("outputExcludedFromBindings", true);
        evidence.put("expectedExactBindings", bindings);
        return new TransferObservation(evidence, bindings);
    }

    private static <R> Map<String, Object> transfer(MiniatureUniverseScreen screen, IRecipeCategory<R> category,
                                                   R recipe, IRecipeSlotsView slots, boolean execute, boolean expectedError) {
        var handler = runtime.getRecipeTransferManager().getRecipeTransferHandler(screen.getMenu(), category)
                .orElseThrow(() -> new IllegalStateException("JEI did not register a miniature recipe transfer handler"));
        String delegateClass = registeredTransferDelegateClass(handler);
        var error = handler.transferRecipe(screen.getMenu(), recipe, slots,
                Minecraft.getInstance().player, false, execute);
        require((error != null) == expectedError, "Unexpected JEI transfer result: " + transferErrorDescription(error)
                + "; selectedMachine=" + screen.recipeTransferMachine() + "; screenProblem=" + screen.recipeTransferProblem());
        return Map.of("entry", "registered_IRecipeTransferHandler.transferRecipe", "handler", handler.getClass().getName(),
                "registeredDelegate", delegateClass,
                "recipe", ((RecipeHolder<?>) recipe).id().toString(), "doTransfer", execute, "returnedError", error != null,
                "activeScreen", Minecraft.getInstance().screen.getClass().getName(),
                "slotSource", "JEI_recipe_layout_drawable");
    }

    @SuppressWarnings("removal")
    private static String transferErrorDescription(mezz.jei.api.recipe.transfer.IRecipeTransferError error) {
        if (error == null) return "none";
        return error.getClass().getName() + "; type=" + error.getType() + "; tooltip="
                + error.getTooltip().stream().map(net.minecraft.network.chat.Component::getString).toList();
    }

    private static String registeredTransferDelegateClass(Object handler) {
        Object delegate = handler;
        if (handler.getClass().getName().equals("mezz.jei.library.recipes.UniversalRecipeTransferHandlerAdapter")) {
            try {
                // JEI wraps universal registrations. Read its identity for verification, but invoke only the runtime wrapper.
                var field = handler.getClass().getDeclaredField("universalRecipeTransferHandler");
                require(field.trySetAccessible(), "Cannot inspect JEI's registered universal handler delegate");
                delegate = field.get(handler);
            } catch (ReflectiveOperationException failure) {
                throw new IllegalStateException("Cannot verify JEI's registered universal handler delegate", failure);
            }
        }
        require(delegate != null && delegate.getClass().getName().equals("com.aquariverr.fluxod.integration.jei.MiniatureRecipeTransferHandler"),
                "JEI resolved an unrelated recipe transfer handler: " + handler.getClass().getName() + "; delegate=" + delegate);
        return delegate.getClass().getName();
    }

    record TransferObservation(Map<String, Object> evidence, List<RecipeReference.Binding> bindings) {}

    /** Resolves only in the guarded Mekanism scenario; no optional chemical type in the test entry point. */
    private static final class ChemicalIdentity {
        static ResourceKey key(Object value) {
            require(value instanceof mekanism.api.chemical.ChemicalStack, "Infuser INPUT must be a native chemical");
            var chemical = (mekanism.api.chemical.ChemicalStack) value;
            return new ResourceKey(ResourceKind.CHEMICAL, chemical.getChemicalHolder().getRegisteredName());
        }
    }

    private static GhostRegistration ghostHandler(MiniatureUniverseScreen screen) {
        var returned = runtime.getScreenHelper().getGhostIngredientHandlers(screen);
        var identities = Collections.newSetFromMap(new IdentityHashMap<IGhostIngredientHandler<MiniatureUniverseScreen>, Boolean>());
        var handlers = new ArrayList<IGhostIngredientHandler<MiniatureUniverseScreen>>();
        for (var handler : returned) {
            // JEI 19.27 adds an exact-class registration both directly and again in its isInstance scan.
            // Deduplicate only the same object; distinct duplicate registrations must still fail.
            if (handler.getClass().getName().startsWith("com.aquariverr.fluxod.integration.jei.") && identities.add(handler)) {
                handlers.add(handler);
            }
        }
        var returnedNames = returned.stream().map(handler -> handler.getClass().getName() + "@"
                + Integer.toHexString(System.identityHashCode(handler))).toList();
        require(handlers.size() == 1, "Expected exactly one unique miniature ghost handler registered in JEI; returned=" + returnedNames);
        return new GhostRegistration(handlers.getFirst(), Map.of("screenHelper", runtime.getScreenHelper().getClass().getName(),
                "returnedGhostHandlers", returnedNames, "returnedGhostHandlerCount", returned.size(),
                "uniqueMiniatureHandlerInstances", handlers.size()));
    }

    private record GhostRegistration(IGhostIngredientHandler<MiniatureUniverseScreen> handler, Map<String, Object> evidence) {}

    private static ITypedIngredient<Object> typed(Object ingredient) {
        return runtime.getIngredientManager().createTypedIngredient(ingredient, false)
                .orElseThrow(() -> new IllegalStateException("JEI does not recognize ghost ingredient " + ingredient));
    }

    private static <T> void validateClipping(MiniatureUniverseScreen screen, List<IGhostIngredientHandler.Target<T>> targets) {
        int left = screen.getGuiLeft() + 8;
        int top = screen.getGuiTop() + 51;
        for (var target : targets) {
            Rect2i area = target.getArea();
            require(area.getWidth() > 0 && area.getHeight() > 0 && area.getX() >= left && area.getY() >= top
                    && area.getX() + area.getWidth() <= left + 172 && area.getY() + area.getHeight() <= top + 81,
                    "JEI target exceeds the clipped miniature grid");
        }
    }

    private static <T> List<Map<String, Integer>> areas(List<IGhostIngredientHandler.Target<T>> targets) {
        return targets.stream().map(target -> target.getArea()).map(area -> Map.of(
                "x", area.getX(), "y", area.getY(), "width", area.getWidth(), "height", area.getHeight())).toList();
    }

    private static void require(boolean value, String message) {
        if (!value) throw new IllegalStateException(message);
    }
}
