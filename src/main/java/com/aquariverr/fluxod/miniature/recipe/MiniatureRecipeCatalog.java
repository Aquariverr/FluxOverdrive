package com.aquariverr.fluxod.miniature.recipe;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.miniature.model.FactoryCell;
import com.aquariverr.fluxod.miniature.model.FactoryLayout;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.model.RecipeReference;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceAmount;
import com.aquariverr.fluxod.miniature.model.FactoryDiagnostic;
import com.aquariverr.fluxod.miniature.compile.CompilationResult;
import com.aquariverr.fluxod.miniature.compile.FactoryCompiler;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import com.aquariverr.fluxod.miniature.storage.VanillaMachinePolicy;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeHolder;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.OnDatapackSyncEvent;
import net.neoforged.neoforge.event.server.ServerStoppedEvent;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@EventBusSubscriber(modid = FluxOverdrive.MODID)
public final class MiniatureRecipeCatalog {
    public static final int MAX_CANDIDATES = 256;
    public static final int MAX_BINDING_STEPS = 65_536;
    private static final Map<MinecraftServer, MiniatureRecipeCatalog> SERVERS = new IdentityHashMap<>();
    private final MinecraftServer server;
    private final Map<QueryKey, Search> searches = new LinkedHashMap<>();
    private long generation = 1;

    private MiniatureRecipeCatalog(MinecraftServer server) { this.server = server; }

    public static MiniatureRecipeCatalog get(MinecraftServer server) {
        if (!server.isSameThread()) throw new IllegalStateException("Recipe catalog is server-thread only");
        return SERVERS.computeIfAbsent(server, MiniatureRecipeCatalog::new);
    }

    public long generation() { return generation; }

    public com.aquariverr.fluxod.miniature.compile.FactoryBalancer.Result balance(FactoryLayout layout, ServerLevel level) {
        requireLevel(level);
        Map<QueryKey, List<ResolvedRecipe>> candidates = new LinkedHashMap<>();
        return com.aquariverr.fluxod.miniature.compile.FactoryBalancer.balance(layout, generation, (position, machine, incoming) -> {
            if (machine.recipe() != null) {
                try { return List.of(resolve(machine.stack().variant(), machine.recipe(), level)); }
                catch (IllegalArgumentException invalid) { throw FactoryCompiler.rejected(FactoryDiagnostic.Code.INVALID_RECIPE, position, null, null, null); }
            }
            var key = new QueryKey(machine.stack().variant(), incoming);
            return candidates.computeIfAbsent(key, ignored -> {
                if (candidates.size() >= 256) throw FactoryCompiler.rejected(FactoryDiagnostic.Code.BALANCE_SEARCH_LIMIT, position, null, null, null);
                Search found = search(machine.stack().variant(), incoming, level);
                if (found.failure() == RecipePreview.Status.SEARCH_LIMIT) throw FactoryCompiler.rejected(FactoryDiagnostic.Code.RECIPE_SEARCH_LIMIT, position, null, null, null);
                var resources = new java.util.HashSet<>(incoming);
                return found.candidates().stream().filter(recipe -> FactoryCompiler.map(recipe.inputs()).keySet().equals(resources)).toList();
            });
        });
    }

    public CompilationResult compile(FactoryLayout layout, ServerLevel level) {
        requireLevel(level);
        return FactoryCompiler.compile(layout, generation, (position, machine, incoming) -> {
            if (machine.recipe() != null) {
                try { return resolve(machine.stack().variant(), machine.recipe(), level); }
                catch (IllegalArgumentException exception) {
                    throw FactoryCompiler.rejected(FactoryDiagnostic.Code.INVALID_RECIPE, position, null, null, null);
                }
            }
            Search search = search(machine.stack().variant(), incoming.stream().map(ResourceAmount::resource).toList(), level);
            if (search.candidates().isEmpty()) throw FactoryCompiler.rejected(
                    search.failure() == RecipePreview.Status.SEARCH_LIMIT
                            ? FactoryDiagnostic.Code.RECIPE_SEARCH_LIMIT : FactoryDiagnostic.Code.NO_RECIPE,
                    position, search.missing().isEmpty() ? null : search.missing().getFirst(), null, null);
            // Prefer a stable candidate whose input proportions match, independently of machine count.
            var amounts = FactoryCompiler.map(incoming);
            ResolvedRecipe chosen = search.candidates().stream().filter(recipe ->
                    FactoryCompiler.matchesRatio(recipe, amounts))
                    .findFirst().orElse(search.candidates().getFirst());
            return resolve(machine.stack().variant(), chosen.reference(), level);
        });
    }

    @SubscribeEvent
    public static void onDatapackSync(OnDatapackSyncEvent event) {
        if (event.getPlayer() == null) get(event.getPlayerList().getServer()).invalidate();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) { SERVERS.remove(event.getServer()); }

    public void invalidate() {
        generation = Math.incrementExact(generation);
        searches.clear();
    }

    public static List<ResourceKey> markerInputs(FactoryLayout layout) {
        return layout.cells().stream().filter(FactoryCell.Marker.class::isInstance).map(FactoryCell.Marker.class::cast)
                .map(marker -> marker.resource().resource()).distinct()
                .sorted(Comparator.comparing(ResourceKey::registryId).thenComparing(MiniatureRecipeCatalog::variantOrder)).toList();
    }

    private static String variantOrder(ResourceKey key) {
        return new RecipeReference("flux_overdrive:input", "flux_overdrive:input", 1, 1,
                List.of(new RecipeReference.Binding(0, key))).variantId();
    }

    public Search search(ResourceKey machine, List<ResourceKey> available, ServerLevel level) {
        requireLevel(level);
        QueryKey key = new QueryKey(machine, List.copyOf(available));
        Search cached = searches.get(key);
        if (cached != null) return cached;
        Search found = find(machine, available, level);
        if (searches.size() >= 16) searches.remove(searches.keySet().iterator().next());
        searches.put(key, found);
        return found;
    }

    private Search find(ResourceKey machine, List<ResourceKey> available, ServerLevel level) {
        ItemStack prototype;
        try {
            prototype = FactoryItemResources.itemOf(machine, level.registryAccess());
            VanillaMachinePolicy.requireAccepted(prototype);
        } catch (IllegalArgumentException exception) {
            return new Search(List.of(), RecipePreview.Status.UNSUPPORTED, List.of());
        }
        var adapters = MachineRecipeAdapters.forMachine(prototype);
        List<RecipeHolder<?>> recipes = server.getRecipeManager().getRecipes().stream()
                .sorted(Comparator.comparing(holder -> holder.id().toString())).toList();
        Map<ResourceKey, ItemStack> items = new LinkedHashMap<>();
        for (ResourceKey key : available) {
            try { items.put(key, FactoryItemResources.itemOf(key, level.registryAccess())); }
            catch (IllegalArgumentException exception) { /* Preserved missing resources cannot bind a native input. */ }
        }
        List<ResolvedRecipe> candidates = new ArrayList<>();
        List<ResourceKey> closestMissing = List.of();
        int fewestMissing = Integer.MAX_VALUE;
        boolean supported = false;
        Budget budget = new Budget();
        try {
            for (MachineRecipeAdapter adapter : adapters) {
                for (RecipeHolder<?> holder : recipes) {
                    if (!adapter.supportsRecipe(holder.value()) || holder.id().toString().length() > RecipeReference.MAX_ID_LENGTH) continue;
                    if (adapter instanceof ResourceRecipeAdapter resourceAdapter) {
                        supported = true;
                        candidates.addAll(resourceAdapter.candidates(holder.id().toString(), holder.value(), available, level));
                        if (candidates.size() > MAX_CANDIDATES) throw new SearchLimit();
                        continue;
                    }
                    MachineRecipeAdapter.Pattern pattern;
                    try { pattern = adapter.pattern(holder.value()); }
                    catch (IllegalArgumentException exception) { continue; }
                    supported = true;
                    List<List<ResourceKey>> options = new ArrayList<>();
                    List<ResourceKey> missing = new ArrayList<>();
                    int missingCount = 0;
                    for (Ingredient ingredient : pattern.ingredients()) {
                        List<ResourceKey> matches = new ArrayList<>();
                        if (!ingredient.isEmpty()) {
                            for (var item : items.entrySet()) {
                                if (ingredient.test(item.getValue())) matches.add(item.getKey());
                            }
                            if (matches.isEmpty()) {
                                missingCount++;
                                if (!ingredient.hasNoItems()) {
                                    var example = java.util.Arrays.stream(ingredient.getItems()).filter(stack -> !stack.isEmpty())
                                            .sorted(Comparator.comparing(stack -> stack.getItemHolder().unwrapKey().orElseThrow().location().toString()))
                                            .findFirst();
                                    if (example.isPresent()) {
                                        try { missing.add(FactoryItemResources.keyOf(example.get(), level.registryAccess())); }
                                        catch (IllegalArgumentException exception) { /* An unrepresentable example must not disable other recipes. */ }
                                    }
                                }
                            }
                        }
                        options.add(matches);
                    }
                    if (missingCount > 0) {
                        if (missingCount < fewestMissing) { fewestMissing = missingCount; closestMissing = List.copyOf(missing); }
                    } else bind(adapter, holder, pattern, options, 0, new ArrayList<>(), candidates, budget, level);
                }
            }
        } catch (SearchLimit exception) {
            return new Search(List.of(), RecipePreview.Status.SEARCH_LIMIT, List.of());
        }
        candidates.sort(Comparator.comparing(ResolvedRecipe::reference, RecipeReference.ORDER));
        return new Search(candidates, supported && fewestMissing != Integer.MAX_VALUE
                ? RecipePreview.Status.MISSING_INPUTS : RecipePreview.Status.UNSUPPORTED, closestMissing);
    }

    private void bind(MachineRecipeAdapter adapter, RecipeHolder<?> holder, MachineRecipeAdapter.Pattern pattern,
                      List<List<ResourceKey>> options, int slot, List<RecipeReference.Binding> bindings,
                      List<ResolvedRecipe> candidates, Budget budget, ServerLevel level) {
        budget.step();
        if (slot == options.size()) {
            RecipeReference reference = new RecipeReference(adapter.id(), holder.id().toString(), pattern.width(), pattern.height(), bindings);
            try {
                ResolvedRecipe resolved = adapter.resolve(reference, holder.value(), level);
                if (candidates.size() >= MAX_CANDIDATES) throw new SearchLimit();
                candidates.add(resolved);
            } catch (IllegalArgumentException exception) {
                if (exception instanceof SearchLimit limit) throw limit;
                // Native validation may reject a complete binding (for example a changing tool remainder).
            }
            return;
        }
        if (pattern.ingredients().get(slot).isEmpty()) {
            bind(adapter, holder, pattern, options, slot + 1, bindings, candidates, budget, level);
        } else for (ResourceKey resource : options.get(slot)) {
            bindings.add(new RecipeReference.Binding(slot, resource));
            bind(adapter, holder, pattern, options, slot + 1, bindings, candidates, budget, level);
            bindings.removeLast();
        }
    }

    public ResolvedRecipe resolve(ResourceKey machine, RecipeReference reference, ServerLevel level) {
        requireLevel(level);
        ItemStack prototype = FactoryItemResources.itemOf(machine, level.registryAccess());
        VanillaMachinePolicy.requireAccepted(prototype);
        MachineRecipeAdapter adapter = MachineRecipeAdapters.forMachine(prototype).stream()
                .filter(value -> value.id().equals(reference.adapterId())).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Machine and recipe adapter do not match"));
        RecipeHolder<?> holder = server.getRecipeManager().byKey(ResourceLocation.parse(reference.recipeId()))
                .orElseThrow(() -> new IllegalArgumentException("Selected recipe no longer exists"));
        if (!adapter.supportsRecipe(holder.value())) throw new IllegalArgumentException("Selected recipe is unsupported");
        if (adapter instanceof ResourceRecipeAdapter resourceAdapter) {
            return resourceAdapter.candidates(reference.recipeId(), holder.value(),
                            reference.bindings().stream().map(RecipeReference.Binding::resource).toList(), level)
                    .stream().filter(candidate -> candidate.reference().equals(reference)).findFirst()
                    .orElseThrow(() -> new IllegalArgumentException("Selected resource recipe inputs are invalid"));
        }
        return adapter.resolve(reference, holder.value(), level);
    }

    public Map<Integer, RecipePreview> previews(FactoryLayout layout, ServerLevel level) {
        var routed = new RoutedRecipeInputs(layout, this, level);
        Map<Integer, RecipePreview> result = new LinkedHashMap<>();
        for (int index = 0; index < layout.cells().size(); index++) {
            if (layout.cells().get(index) instanceof FactoryCell.Machine machine) {
                var selection = selection(layout, new GridPosition(index % layout.size(), index / layout.size()), machine, routed, level);
                result.put(index, preview(machine, selection.available(), selection.search(), level));
            }
        }
        return result;
    }

    public RecipePreview preview(FactoryCell.Machine machine, List<ResourceKey> available, ServerLevel level) {
        return preview(machine, available, search(machine.stack().variant(), available, level), level);
    }

    private RecipePreview preview(FactoryCell.Machine machine, List<ResourceKey> available, Search search, ServerLevel level) {
        if (machine.recipe() != null) {
            try {
                // A verified explicit binding can be selected before its input routes exist (for example via JEI).
                // Compilation separately validates that the real routed quantities match this native recipe.
                ResolvedRecipe resolved = resolve(machine.stack().variant(), machine.recipe(), level);
                int selected = -1;
                for (int index = 0; index < search.candidates().size(); index++) {
                    if (search.candidates().get(index).reference().equals(resolved.reference())) { selected = index; break; }
                }
                return new RecipePreview(RecipePreview.Status.READY, selected < 0 ? 1 : search.candidates().size(),
                        Math.max(0, selected), resolved.reference(), resolved.normalization(),
                        resolved.inputsForMachines(1), resolved.outputsForMachines(1), List.of());
            } catch (IllegalArgumentException exception) {
                return new RecipePreview(RecipePreview.Status.INVALID_SELECTION, search.candidates().size(), -1,
                        machine.recipe(), BigInteger.ONE, List.of(), List.of(), machine.recipe().bindings().stream()
                        .map(RecipeReference.Binding::resource).filter(key -> !available.contains(key)).distinct().toList());
            }
        }
        if (search.candidates().isEmpty()) return new RecipePreview(search.failure(), 0, -1, null,
                BigInteger.ONE, List.of(), List.of(), search.missing());
        ResolvedRecipe resolved = resolve(machine.stack().variant(), search.candidates().getFirst().reference(), level);
        return new RecipePreview(RecipePreview.Status.AUTOMATIC,
                search.candidates().size(), 0, resolved.reference(), resolved.normalization(),
                resolved.inputsForMachines(1), resolved.outputsForMachines(1), List.of());
    }

    public RecipeReference cycle(FactoryLayout layout, GridPosition position, ServerLevel level) {
        if (!(layout.cellAt(position) instanceof FactoryCell.Machine machine)) throw new IllegalArgumentException("Not a machine");
        Search search = selection(layout, position, machine, new RoutedRecipeInputs(layout, this, level), level).search();
        if (search.candidates().isEmpty()) return null;
        int current = machine.recipe() == null ? 0 : -1;
        for (int index = 0; index < search.candidates().size(); index++) {
            if (search.candidates().get(index).reference().equals(machine.recipe())) { current = index; break; }
        }
        RecipeReference next = search.candidates().get((current + 1) % search.candidates().size()).reference();
        return resolve(machine.stack().variant(), next, level).reference();
    }

    private void requireLevel(ServerLevel level) {
        if (level.getServer() != server || !server.isSameThread()) throw new IllegalStateException("Invalid recipe catalog context");
    }

    private Selection selection(FactoryLayout layout, GridPosition position, FactoryCell.Machine machine,
                                RoutedRecipeInputs routed, ServerLevel level) {
        var markers = markerInputs(layout);
        try {
            var incoming = routed.inputs(position);
            var available = new java.util.LinkedHashSet<>(incoming); available.addAll(markers);
            var search = search(machine.stack().variant(), List.copyOf(available), level);
            // The automatic preview and cycling order start with recipes supplied by this machine's actual route.
            var candidates = new ArrayList<>(search.candidates());
            candidates.sort(Comparator.comparing(recipe -> !recipe.inputs().stream().allMatch(input -> incoming.contains(input.resource()))));
            return new Selection(new Search(candidates, search.failure(), search.missing()), List.copyOf(available));
        } catch (SearchLimit limit) {
            return new Selection(new Search(List.of(), RecipePreview.Status.SEARCH_LIMIT, List.of()), markers);
        }
    }
    private record Selection(Search search, List<ResourceKey> available) {}

    public record Search(List<ResolvedRecipe> candidates, RecipePreview.Status failure, List<ResourceKey> missing) {
        public Search { candidates = List.copyOf(candidates); missing = List.copyOf(missing); }
    }
    private record QueryKey(ResourceKey machine, List<ResourceKey> available) {}
    public static final class SearchLimit extends IllegalArgumentException {}
    private static final class Budget {
        private int steps;
        void step() { if (++steps > MAX_BINDING_STEPS) throw new SearchLimit(); }
    }
}
