package com.aquariverr.fluxod.miniature.model;

import com.aquariverr.fluxod.miniature.storage.RecipeReferenceCodec;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class RecipeReferenceTest {
    private static final ResourceKey ORE = new ResourceKey(ResourceKind.ITEM, "minecraft:iron_ore");

    @Test
    void shapedSlotsSurviveStrictRoundTripAndDifferFromUnorderedIngredients() {
        RecipeReference diagonal = reference(List.of(new RecipeReference.Binding(0, ORE), new RecipeReference.Binding(3, ORE)));
        RecipeReference row = reference(List.of(new RecipeReference.Binding(0, ORE), new RecipeReference.Binding(1, ORE)));
        assertEquals(diagonal, RecipeReferenceCodec.read(RecipeReferenceCodec.write(diagonal)));
        assertNotEquals(diagonal.variantId(), row.variantId());
        assertEquals(64, diagonal.variantId().length());
    }

    @Test
    void equalRegistryIdsInDifferentResourceKindsHaveDifferentVariants() {
        var variants = java.util.Arrays.stream(ResourceKind.values())
                .map(kind -> reference(List.of(new RecipeReference.Binding(0, new ResourceKey(kind, "test:shared")))).variantId())
                .distinct().toList();
        assertEquals(3, variants.size());
    }

    @Test
    void nestedComponentKeyOrderDoesNotChangeStableVariantReference() {
        CompoundTag first = new CompoundTag();
        first.putInt("Aa", 3);
        first.putInt("BB", 7);
        CompoundTag second = new CompoundTag();
        second.putInt("BB", 7);
        second.putInt("Aa", 3);
        CompoundTag a = new CompoundTag();
        CompoundTag b = new CompoundTag();
        a.put("minecraft:custom_data", first);
        b.put("minecraft:custom_data", second);
        assertEquals(reference(a).variantId(), reference(b).variantId());
        second.putLong("Aa", 3);
        b.put("minecraft:custom_data", second);
        assertNotEquals(reference(a).variantId(), reference(b).variantId());
    }

    @Test
    void referencesRejectDuplicateUnsortedOutsideAndAcceptFluidBindings() {
        assertThrows(IllegalArgumentException.class, () -> reference(List.of(new RecipeReference.Binding(0, ORE), new RecipeReference.Binding(0, ORE))));
        assertThrows(IllegalArgumentException.class, () -> reference(List.of(new RecipeReference.Binding(2, ORE), new RecipeReference.Binding(1, ORE))));
        assertThrows(IllegalArgumentException.class, () -> reference(List.of(new RecipeReference.Binding(4, ORE))));
        assertDoesNotThrow(() -> new RecipeReference.Binding(0, new ResourceKey(ResourceKind.FLUID, "minecraft:water")));
        assertThrows(IllegalArgumentException.class, () -> reference(List.of()));
    }

    @Test
    void malformedPersistenceCannotAddClientOutputsOrUnknownFields() {
        CompoundTag saved = RecipeReferenceCodec.write(reference(List.of(new RecipeReference.Binding(0, ORE))));
        saved.put("outputs", new ListTag());
        assertThrows(IllegalArgumentException.class, () -> RecipeReferenceCodec.read(saved));
        saved.remove("outputs");
        saved.putString("width", "2");
        assertThrows(IllegalArgumentException.class, () -> RecipeReferenceCodec.read(saved));
    }

    @Test
    void referenceDefensivelyCopiesBindingsAndOrdersRecipeIdsBeforeVariants() {
        var bindings = new java.util.ArrayList<>(List.of(new RecipeReference.Binding(0, ORE)));
        RecipeReference first = reference(bindings);
        bindings.clear();
        assertEquals(1, first.bindings().size());
        assertThrows(UnsupportedOperationException.class, () -> first.bindings().clear());
        RecipeReference last = new RecipeReference(first.adapterId(), "test:z", 2, 2, first.bindings());
        assertTrue(RecipeReference.ORDER.compare(first, last) < 0);
    }

    private static RecipeReference reference(List<RecipeReference.Binding> bindings) {
        return new RecipeReference("flux_overdrive:crafting", "test:a", 2, 2, bindings);
    }

    private static RecipeReference reference(CompoundTag components) {
        return reference(List.of(new RecipeReference.Binding(0, new ResourceKey(ResourceKind.ITEM, "minecraft:iron_ore", components))));
    }
}
