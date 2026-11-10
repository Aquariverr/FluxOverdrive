package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.miniature.model.FactoryDiagnostic;
import com.aquariverr.fluxod.miniature.model.ModelValidationException;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import com.mojang.serialization.Codec;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.Unit;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import static com.aquariverr.fluxod.gametest.MiniatureStorageGameTestAssertions.assertEquals;
import static com.aquariverr.fluxod.gametest.MiniatureStorageGameTestAssertions.assertFalse;
import static com.aquariverr.fluxod.gametest.MiniatureStorageGameTestAssertions.assertNotEquals;
import static com.aquariverr.fluxod.gametest.MiniatureStorageGameTestAssertions.assertThrows;
import static com.aquariverr.fluxod.gametest.MiniatureStorageGameTestAssertions.assertTrue;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureItemResourcesGameTests {

    @GameTest(template = "empty")
    public static void effectiveComponentsIncludeDefaultsAndIgnoreStackCount(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack stack = new ItemStack(Items.IRON_INGOT, 23);
        ResourceKey key = FactoryItemResources.keyOf(stack, registries);

        assertEquals("minecraft:iron_ingot", key.registryId());
        assertEquals(64, key.components().getInt("minecraft:max_stack_size"));
        assertEquals(key, FactoryItemResources.keyOf(stack.copyWithCount(1), registries));
        ItemStack restored = FactoryItemResources.itemOf(key, registries);
        assertEquals(1, restored.getCount());
        assertTrue(ItemStack.isSameItemSameComponents(stack, restored));
        assertEquals(key, FactoryItemResources.keyOf(restored, registries));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void removedDefaultsStayAbsentAfterReconstruction(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack stack = new ItemStack(Items.IRON_INGOT);
        stack.remove(DataComponents.LORE);
        stack.remove(DataComponents.RARITY);
        ResourceKey key = FactoryItemResources.keyOf(stack, registries);

        assertFalse(key.components().contains("minecraft:lore"));
        assertFalse(key.components().contains("!minecraft:lore"));
        ItemStack restored = FactoryItemResources.itemOf(key, registries);
        assertFalse(restored.has(DataComponents.LORE));
        assertFalse(restored.has(DataComponents.RARITY));
        assertTrue(ItemStack.isSameItemSameComponents(stack, restored));
        assertEquals(key, FactoryItemResources.keyOf(restored, registries));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullComponentMapsPreserveCustomDataAndNamedVariants(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag data = new CompoundTag();
        data.putLong("energy", 4_000_000_000L);
        data.putByteArray("bytes", new byte[]{1, 2, 3});
        ItemStack stack = new ItemStack(Items.IRON_INGOT);
        stack.set(DataComponents.CUSTOM_DATA, CustomData.of(data));
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Refined iron"));
        ResourceKey key = FactoryItemResources.keyOf(stack, registries);
        ItemStack restored = FactoryItemResources.itemOf(key, registries);

        assertTrue(ItemStack.isSameItemSameComponents(stack, restored));
        assertEquals(data, restored.get(DataComponents.CUSTOM_DATA).copyTag());
        assertNotEquals(key, FactoryItemResources.keyOf(new ItemStack(Items.IRON_INGOT), registries));
        restored.set(DataComponents.CUSTOM_NAME, Component.literal("Other iron"));
        assertNotEquals(key, FactoryItemResources.keyOf(restored, registries));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void originalAndRestoredStacksDoNotMutateResourceIdentity(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack stack = new ItemStack(Items.IRON_INGOT);
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Original"));
        ResourceKey key = FactoryItemResources.keyOf(stack, registries);
        CompoundTag saved = key.components();
        stack.set(DataComponents.CUSTOM_NAME, Component.literal("Changed"));
        ItemStack restored = FactoryItemResources.itemOf(key, registries);
        restored.set(DataComponents.CUSTOM_NAME, Component.literal("Changed again"));

        assertEquals(saved, key.components());
        assertEquals("Original", FactoryItemResources.itemOf(key, registries).getHoverName().getString());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void transientComponentsCannotDisappearDuringEncoding(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack stack = new ItemStack(Items.IRON_INGOT);
        stack.set(DataComponents.CREATIVE_SLOT_LOCK, Unit.INSTANCE);

        assertFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                () -> FactoryItemResources.keyOf(stack, registries));
        assertTrue(stack.has(DataComponents.CREATIVE_SLOT_LOCK));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unregisteredComponentTypesAreRejected(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        DataComponentType<Integer> unregistered = DataComponentType.<Integer>builder().persistent(Codec.INT).build();
        ItemStack stack = new ItemStack(Items.IRON_INGOT);
        stack.set(unregistered, 3);

        assertFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                () -> FactoryItemResources.keyOf(stack, registries));
        assertEquals(3, stack.get(unregistered));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void missingItemIdsAndAirNeverFallBackToEmptyStacks(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        for (String id : new String[]{"missing:removed_item", "minecraft:air"}) {
            assertFailure(FactoryDiagnostic.Code.INVALID_RESOURCE_ID,
                    () -> FactoryItemResources.itemOf(new ResourceKey(ResourceKind.ITEM, id), registries));
        }
        assertFailure(FactoryDiagnostic.Code.INVALID_RESOURCE_ID,
                () -> FactoryItemResources.keyOf(ItemStack.EMPTY, registries));
        assertFailure(FactoryDiagnostic.Code.INVALID_RESOURCE_ID,
                () -> FactoryItemResources.itemOf(new ResourceKey(ResourceKind.FLUID, "minecraft:water"), registries));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownOrInvalidComponentValuesRejectTheWholeIdentity(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        CompoundTag unknown = new CompoundTag();
        unknown.putInt("missing:component", 1);
        assertFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                () -> FactoryItemResources.itemOf(new ResourceKey(ResourceKind.ITEM, "minecraft:iron_ingot", unknown), registries));

        CompoundTag invalid = new CompoundTag();
        invalid.putInt("minecraft:max_stack_size", 0);
        assertFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                () -> FactoryItemResources.itemOf(new ResourceKey(ResourceKind.ITEM, "minecraft:iron_ingot", invalid), registries));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void lossyNbtNumberCoercionIsRejected(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ResourceKey original = FactoryItemResources.keyOf(new ItemStack(Items.IRON_INGOT), registries);
        CompoundTag altered = original.components();
        altered.putByte("minecraft:max_stack_size", (byte) 64);

        assertFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                () -> FactoryItemResources.itemOf(new ResourceKey(ResourceKind.ITEM, original.registryId(), altered), registries));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidCrossComponentStateCannotBeEncoded(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack stack = new ItemStack(Items.IRON_INGOT);
        stack.set(DataComponents.MAX_DAMAGE, 100);

        assertFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                () -> FactoryItemResources.keyOf(stack, registries));
        assertEquals(100, stack.get(DataComponents.MAX_DAMAGE));
        assertEquals(64, stack.getMaxStackSize());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void nativeTextNormalizationPreservesEncodedIdentity(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack original = new ItemStack(Items.FURNACE);
        original.set(DataComponents.CUSTOM_NAME, MutableComponent.create(new TranslatableContents(
                "test.machine", "%s", new Object[]{Component.literal("argument")})));
        ResourceKey key = FactoryItemResources.keyOf(original, registries);
        ItemStack restored = FactoryItemResources.itemOf(key, registries);

        assertTrue(((TranslatableContents) original.get(DataComponents.CUSTOM_NAME).getContents()).getArgs()[0]
                instanceof Component);
        assertEquals("argument", ((TranslatableContents) restored.get(DataComponents.CUSTOM_NAME).getContents()).getArgs()[0]);
        assertEquals(original.getHoverName().getString(), restored.getHoverName().getString());
        assertEquals(key, FactoryItemResources.keyOf(restored, registries));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void transientComponentsInsideHoverItemsAreRejected(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack nested = new ItemStack(Items.IRON_INGOT);
        nested.set(DataComponents.CREATIVE_SLOT_LOCK, Unit.INSTANCE);
        ItemStack machine = new ItemStack(Items.FURNACE);
        machine.set(DataComponents.CUSTOM_NAME, Component.literal("Furnace").withStyle(style -> style.withHoverEvent(
                new HoverEvent(HoverEvent.Action.SHOW_ITEM, new HoverEvent.ItemStackInfo(nested)))));

        assertFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                () -> FactoryItemResources.keyOf(machine, registries));
        assertTrue(nested.has(DataComponents.CREATIVE_SLOT_LOCK));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void alternatingTextAndHoverItemsShareANestingBudget(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack shallow = nestedHoverItems(3);
        ResourceKey key = FactoryItemResources.keyOf(shallow, registries);
        assertEquals(key, FactoryItemResources.keyOf(FactoryItemResources.itemOf(key, registries), registries));
        ItemStack excessive = nestedHoverItems(80);

        assertFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                () -> FactoryItemResources.keyOf(excessive, registries));
        assertEquals(1, excessive.getCount());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void cyclicTextAndHoverItemsAreRejectedBeforeEncoding(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack machine = new ItemStack(Items.FURNACE);
        MutableComponent name = Component.literal("Cycle");
        machine.set(DataComponents.CUSTOM_NAME, name);
        HoverEvent.ItemStackInfo self = new HoverEvent.ItemStackInfo(machine);
        name.withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_ITEM, self)));

        assertFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                () -> FactoryItemResources.keyOf(machine, registries));
        assertEquals(1, machine.getCount());
        helper.succeed();
    }

    private static ItemStack nestedHoverItems(int depth) {
        ItemStack nested = new ItemStack(Items.IRON_INGOT);
        for (int index = 0; index < depth; index++) {
            HoverEvent.ItemStackInfo child = new HoverEvent.ItemStackInfo(nested);
            ItemStack parent = new ItemStack(Items.FURNACE);
            parent.set(DataComponents.CUSTOM_NAME, Component.literal("Layer").withStyle(style -> style.withHoverEvent(
                    new HoverEvent(HoverEvent.Action.SHOW_ITEM, child))));
            nested = parent;
        }
        return nested;
    }

    private static void assertFailure(FactoryDiagnostic.Code expected, Runnable action) {
        assertEquals(expected, assertThrows(ModelValidationException.class, action::run).diagnostic().code());
    }
}


final class MiniatureStorageGameTestAssertions {

    static void assertEquals(Object expected, Object actual) {
        if (!java.util.Objects.equals(expected, actual)) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("Expected " + expected + ", got " + actual);
        }
    }

    static void assertNotEquals(Object expected, Object actual) {
        if (java.util.Objects.equals(expected, actual)) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("Expected distinct values");
        }
    }

    static void assertTrue(boolean condition) {
        if (!condition) {
            throw new net.minecraft.gametest.framework.GameTestAssertException("Expected condition to be true");
        }
    }

    static void assertFalse(boolean condition) {
        assertTrue(!condition);
    }

    static void assertSame(Object expected, Object actual) {
        assertTrue(expected == actual);
    }

    static void assertDoesNotThrow(Runnable action) {
        action.run();
    }

    static <T extends RuntimeException> T assertThrows(Class<T> type, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException exception) {
            if (type.isInstance(exception)) {
                return type.cast(exception);
            }
            throw exception;
        }
        throw new net.minecraft.gametest.framework.GameTestAssertException("Expected " + type.getSimpleName());
    }

    private MiniatureStorageGameTestAssertions() {}
}
