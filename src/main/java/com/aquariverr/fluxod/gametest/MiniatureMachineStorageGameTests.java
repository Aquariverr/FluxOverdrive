package com.aquariverr.fluxod.gametest;

import com.aquariverr.fluxod.FluxOverdrive;
import com.aquariverr.fluxod.miniature.model.FactoryDiagnostic;
import com.aquariverr.fluxod.miniature.model.GridPosition;
import com.aquariverr.fluxod.miniature.model.ModelValidationException;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import com.aquariverr.fluxod.miniature.storage.MachineInventory;
import com.aquariverr.fluxod.miniature.storage.VanillaMachinePolicy;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponents;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Rarity;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemContainerContents;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.neoforge.gametest.GameTestHolder;
import net.neoforged.neoforge.gametest.PrefixGameTestTemplate;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.aquariverr.fluxod.gametest.MiniatureStorageGameTestAssertions.assertDoesNotThrow;
import static com.aquariverr.fluxod.gametest.MiniatureStorageGameTestAssertions.assertEquals;
import static com.aquariverr.fluxod.gametest.MiniatureStorageGameTestAssertions.assertFalse;
import static com.aquariverr.fluxod.gametest.MiniatureStorageGameTestAssertions.assertSame;
import static com.aquariverr.fluxod.gametest.MiniatureStorageGameTestAssertions.assertThrows;
import static com.aquariverr.fluxod.gametest.MiniatureStorageGameTestAssertions.assertTrue;

@GameTestHolder(FluxOverdrive.MODID)
@PrefixGameTestTemplate(false)
public final class MiniatureMachineStorageGameTests {

    private static final GridPosition CELL = new GridPosition(0, 0);

    @GameTest(template = "empty")
    public static void policyAcceptsSupportedVanillaMachineTypes(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        for (Item item : List.of(Items.FURNACE, Items.BLAST_FURNACE, Items.SMOKER, Items.CRAFTING_TABLE, Items.STONECUTTER)) {
            assertDoesNotThrow(() -> VanillaMachinePolicy.requireAccepted(new ItemStack(item)));
        }
        for (Item item : List.of(Items.ANVIL, Items.BREWING_STAND, Items.IRON_INGOT)) {
            assertFailure(FactoryDiagnostic.Code.INVALID_MACHINE,
                    () -> VanillaMachinePolicy.requireAccepted(new ItemStack(item)));
        }
        assertFailure(FactoryDiagnostic.Code.INVALID_MACHINE,
                () -> VanillaMachinePolicy.requireAccepted(ItemStack.EMPTY));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void presentationChangesAndNaturalStackSizeOneAreAccepted(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack machine = new ItemStack(Items.FURNACE);
        machine.set(DataComponents.CUSTOM_NAME, Component.literal("Furnace A"));
        machine.set(DataComponents.ITEM_NAME, Component.literal("Named furnace"));
        machine.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("West line"))));
        machine.set(DataComponents.RARITY, Rarity.EPIC);
        machine.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
        machine.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(17));
        machine.set(DataComponents.MAX_STACK_SIZE, 1);

        assertDoesNotThrow(() -> VanillaMachinePolicy.requireAccepted(machine));
        assertEquals(1, machine.getMaxStackSize());
        MachineInventory inventory = MachineInventory.empty().insert(CELL, machine, registries).inventory();
        assertTrue(ItemStack.isSameItemSameComponents(machine, inventory.entry(CELL).orElseThrow().prototype()));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void machineStateComponentsAreRejectedEvenWhenEmpty(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        for (var component : List.of(DataComponents.BLOCK_ENTITY_DATA, DataComponents.CUSTOM_DATA)) {
            ItemStack machine = new ItemStack(Items.FURNACE);
            machine.set(component, CustomData.of(new CompoundTag()));
            assertFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                    () -> VanillaMachinePolicy.requireAccepted(machine));
        }
        ItemStack container = new ItemStack(Items.CRAFTING_TABLE);
        container.set(DataComponents.CONTAINER, ItemContainerContents.EMPTY);
        assertFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                () -> VanillaMachinePolicy.requireAccepted(container));
        ItemStack occupied = new ItemStack(Items.FURNACE);
        occupied.set(DataComponents.CONTAINER, ItemContainerContents.fromItems(List.of(new ItemStack(Items.IRON_INGOT))));
        assertFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                () -> VanillaMachinePolicy.requireAccepted(occupied));
        ItemStack emptyFurnace = new ItemStack(Items.FURNACE);
        assertEquals(ItemContainerContents.EMPTY, emptyFurnace.get(DataComponents.CONTAINER));
        assertDoesNotThrow(() -> VanillaMachinePolicy.requireAccepted(emptyFurnace));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unknownBehaviorChangesAndRemovedBehaviorDefaultsAreRejected(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack changed = new ItemStack(Items.FURNACE);
        changed.set(DataComponents.REPAIR_COST, 1);
        assertFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                () -> VanillaMachinePolicy.requireAccepted(changed));
        ItemStack removed = new ItemStack(Items.FURNACE);
        removed.remove(DataComponents.ENCHANTMENTS);
        assertFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                () -> VanillaMachinePolicy.requireAccepted(removed));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidNaturalStackSizesAndRemovedStackSizeAreRejected(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        for (int size : new int[]{0, -1, 100}) {
            ItemStack machine = new ItemStack(Items.FURNACE);
            machine.set(DataComponents.MAX_STACK_SIZE, size);
            assertFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                    () -> VanillaMachinePolicy.requireAccepted(machine));
        }
        ItemStack removed = new ItemStack(Items.FURNACE);
        removed.remove(DataComponents.MAX_STACK_SIZE);
        assertFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                () -> VanillaMachinePolicy.requireAccepted(removed));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void sixtyFourNaturallyUnstackableMachinesFitInOneCell(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack machine = unstackableMachine();
        MachineInventory inventory = MachineInventory.empty();
        for (int count = 1; count <= 64; count++) {
            MachineInventory.Insertion result = inventory.insert(CELL, machine, registries);
            inventory = result.inventory();
            assertTrue(result.remainder().isEmpty());
            assertEquals(count, inventory.entry(CELL).orElseThrow().count());
            assertEquals(1, inventory.entry(CELL).orElseThrow().prototype().getCount());
            assertEquals(1, machine.getCount());
        }
        MachineInventory.Insertion full = inventory.insert(CELL, machine, registries);
        assertSame(inventory, full.inventory());
        assertEquals(1, full.remainder().getCount());
        assertEquals(64, inventory.entry(CELL).orElseThrow().count());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void insertionAcceptsOnlyAvailableCapacityAndPreservesOfferedStack(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        MachineInventory initial = new MachineInventory(Map.of(CELL, new MachineInventory.Entry(new ItemStack(Items.FURNACE), 60, registries)));
        ItemStack offered = new ItemStack(Items.FURNACE, 12);
        MachineInventory.Insertion result = initial.insert(CELL, offered, registries);

        assertEquals(60, initial.entry(CELL).orElseThrow().count());
        assertEquals(64, result.inventory().entry(CELL).orElseThrow().count());
        assertEquals(8, result.remainder().getCount());
        assertEquals(12, offered.getCount());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void differentNamesAndStackSizeVariantsCannotMerge(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack original = new ItemStack(Items.FURNACE);
        original.set(DataComponents.CUSTOM_NAME, Component.literal("Original"));
        MachineInventory inventory = MachineInventory.empty().insert(CELL, original, registries).inventory();
        ItemStack named = new ItemStack(Items.FURNACE, 3);
        named.set(DataComponents.CUSTOM_NAME, Component.literal("Other"));
        assertFailure(FactoryDiagnostic.Code.INVALID_MACHINE, () -> inventory.insert(CELL, named, registries));
        ItemStack differentLimit = original.copy();
        differentLimit.set(DataComponents.MAX_STACK_SIZE, 1);
        assertFailure(FactoryDiagnostic.Code.INVALID_MACHINE, () -> inventory.insert(CELL, differentLimit, registries));

        assertEquals(1, inventory.entry(CELL).orElseThrow().count());
        assertEquals(3, named.getCount());
        assertEquals("Original", inventory.entry(CELL).orElseThrow().prototype().getHoverName().getString());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void fullCellsStillValidateOfferedVariantsAndConfiguration(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        MachineInventory inventory = new MachineInventory(Map.of(CELL,
                new MachineInventory.Entry(new ItemStack(Items.FURNACE), 64, registries)));
        ItemStack other = new ItemStack(Items.SMOKER, 5);
        assertFailure(FactoryDiagnostic.Code.INVALID_MACHINE, () -> inventory.insert(CELL, other, registries));
        ItemStack configured = new ItemStack(Items.FURNACE, 5);
        configured.set(DataComponents.CUSTOM_DATA, CustomData.of(new CompoundTag()));
        assertFailure(FactoryDiagnostic.Code.INVALID_COMPONENTS, () -> inventory.insert(CELL, configured, registries));

        assertEquals(64, inventory.entry(CELL).orElseThrow().count());
        assertEquals(5, other.getCount());
        assertEquals(5, configured.getCount());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void unstackableExtractionRemovesOnlyOneMachinePerTransfer(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        MachineInventory initial = new MachineInventory(Map.of(CELL, new MachineInventory.Entry(unstackableMachine(), 64, registries)));
        MachineInventory.Extraction result = initial.extract(CELL, 64);

        assertEquals(1, result.extracted().getCount());
        assertEquals(1, result.extracted().getMaxStackSize());
        assertEquals(63, result.inventory().entry(CELL).orElseThrow().count());
        assertEquals(64, initial.entry(CELL).orElseThrow().count());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void extractionRespectsRequestedStockAndNaturalLimits(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack prototype = new ItemStack(Items.FURNACE);
        prototype.set(DataComponents.MAX_STACK_SIZE, 16);
        MachineInventory initial = new MachineInventory(Map.of(CELL, new MachineInventory.Entry(prototype, 40, registries)));
        assertEquals(3, initial.extract(CELL, 3).extracted().getCount());
        MachineInventory.Extraction first = initial.extract(CELL, Integer.MAX_VALUE);
        assertEquals(16, first.extracted().getCount());
        MachineInventory.Extraction second = first.inventory().extract(CELL, 64);
        assertEquals(16, second.extracted().getCount());
        MachineInventory.Extraction last = second.inventory().extract(CELL, 64);
        assertEquals(8, last.extracted().getCount());
        assertTrue(last.inventory().entry(CELL).isEmpty());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void emptyExtractionDoesNotCreateEntries(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        MachineInventory inventory = MachineInventory.empty();
        MachineInventory.Extraction result = inventory.extract(CELL, 64);

        assertSame(inventory, result.inventory());
        assertTrue(result.extracted().isEmpty());
        assertTrue(inventory.entries().isEmpty());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void invalidExtractionCountsLeaveAssetsUntouched(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        MachineInventory inventory = new MachineInventory(Map.of(CELL,
                new MachineInventory.Entry(new ItemStack(Items.FURNACE), 7, registries)));
        for (int requested : new int[]{0, -1, Integer.MIN_VALUE}) {
            assertFailure(FactoryDiagnostic.Code.INVALID_MACHINE_COUNT, () -> inventory.extract(CELL, requested));
            assertEquals(7, inventory.entry(CELL).orElseThrow().count());
        }
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void entryRequiresSingleItemPrototypeAndBoundedCount(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        for (int count : new int[]{0, -1, 65, Integer.MAX_VALUE}) {
            assertFailure(FactoryDiagnostic.Code.INVALID_MACHINE_COUNT,
                    () -> new MachineInventory.Entry(new ItemStack(Items.FURNACE), count, registries));
        }
        assertFailure(FactoryDiagnostic.Code.INVALID_MACHINE_COUNT,
                () -> new MachineInventory.Entry(new ItemStack(Items.FURNACE, 2), 2, registries));
        assertFailure(FactoryDiagnostic.Code.INVALID_MACHINE,
                () -> new MachineInventory.Entry(ItemStack.EMPTY, 1, registries));
        assertDoesNotThrow(() -> new MachineInventory.Entry(unstackableMachine(), 64, registries));
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void entryAndMapDefensivelyCopyInputAndExposedStacks(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack prototype = new ItemStack(Items.FURNACE);
        MutableComponent name = Component.literal("Original");
        prototype.set(DataComponents.CUSTOM_NAME, name);
        MachineInventory.Entry entry = new MachineInventory.Entry(prototype, 4, registries);
        Map<GridPosition, MachineInventory.Entry> supplied = new HashMap<>();
        supplied.put(CELL, entry);
        MachineInventory inventory = new MachineInventory(supplied);
        prototype.setCount(9);
        name.append(" changed");
        supplied.clear();
        ItemStack exposed = inventory.entry(CELL).orElseThrow().prototype();
        exposed.setCount(17);
        ((MutableComponent) exposed.get(DataComponents.CUSTOM_NAME)).append(" changed again");

        assertEquals(1, entry.prototype().getCount());
        assertEquals("Original", entry.prototype().getHoverName().getString());
        assertEquals(4, inventory.entry(CELL).orElseThrow().count());
        assertThrows(UnsupportedOperationException.class, () -> inventory.entries().clear());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void transferResultsDoNotExposeTheirStoredItemStacks(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack supplied = new ItemStack(Items.FURNACE, 70);
        MachineInventory.Insertion insertion = MachineInventory.empty().insert(CELL, supplied, registries);
        insertion.remainder().setCount(55);
        supplied.setCount(1);
        assertEquals(6, insertion.remainder().getCount());
        MachineInventory.Extraction extraction = insertion.inventory().extract(CELL, 12);
        extraction.extracted().setCount(1);
        assertEquals(12, extraction.extracted().getCount());
        assertEquals(52, extraction.inventory().entry(CELL).orElseThrow().count());
        assertFalse(extraction.extracted().isEmpty());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void recursiveNameAndLoreGraphsCannotMutateStoredPrototypes(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack prototype = recursivelyNamedMachine();
        var expected = FactoryItemResources.keyOf(prototype, registries);
        MachineInventory.Entry entry = new MachineInventory.Entry(prototype, 5, registries);

        mutateNestedText(prototype);
        assertEquals(expected, FactoryItemResources.keyOf(entry.prototype(), registries));
        ItemStack exposed = entry.prototype();
        mutateNestedText(exposed);
        assertEquals(expected, FactoryItemResources.keyOf(entry.prototype(), registries));
        assertEquals(5, entry.count());
        helper.succeed();
    }

    @GameTest(template = "empty")
    public static void transferResultsRebuildNestedTextIndependently(GameTestHelper helper) {
        HolderLookup.Provider registries = helper.getLevel().registryAccess();
        ItemStack offered = recursivelyNamedMachine();
        offered.setCount(70);
        var expected = FactoryItemResources.keyOf(offered, registries);
        MachineInventory.Insertion insertion = MachineInventory.empty().insert(CELL, offered, registries);
        mutateNestedText(offered);
        mutateNestedText(insertion.remainder());
        assertEquals(expected, FactoryItemResources.keyOf(insertion.remainder(), registries));
        assertEquals(expected, FactoryItemResources.keyOf(insertion.inventory().entry(CELL).orElseThrow().prototype(), registries));
        assertEquals(6, insertion.remainder().getCount());

        MachineInventory.Extraction extraction = insertion.inventory().extract(CELL, 12);
        mutateNestedText(extraction.extracted());
        assertEquals(expected, FactoryItemResources.keyOf(extraction.extracted(), registries));
        assertEquals(expected, FactoryItemResources.keyOf(extraction.inventory().entry(CELL).orElseThrow().prototype(), registries));
        assertEquals(12, extraction.extracted().getCount());
        assertEquals(52, extraction.inventory().entry(CELL).orElseThrow().count());
        helper.succeed();
    }

    private static ItemStack recursivelyNamedMachine() {
        MutableComponent nestedSibling = Component.literal("child").append(Component.literal("grandchild"));
        MutableComponent translated = Component.translatable("test.machine",
                Component.literal("argument").append(Component.literal("argument child")));
        MutableComponent hover = Component.literal("hover").append(Component.literal("hover child"));
        MutableComponent name = Component.literal("Machine ").append(nestedSibling).append(translated)
                .withStyle(style -> style.withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, hover)));
        MutableComponent lore = Component.literal("Line ").append(Component.literal("lore child")
                .append(Component.literal("lore grandchild")));
        ItemStack machine = new ItemStack(Items.FURNACE);
        machine.set(DataComponents.CUSTOM_NAME, name);
        machine.set(DataComponents.LORE, new ItemLore(List.of(lore)));
        return machine;
    }

    private static void mutateNestedText(ItemStack machine) {
        Component name = machine.get(DataComponents.CUSTOM_NAME);
        ((MutableComponent) name.getSiblings().getFirst().getSiblings().getFirst()).withStyle(style -> style.withBold(true));
        TranslatableContents translated = (TranslatableContents) name.getSiblings().get(1).getContents();
        ((MutableComponent) translated.getArgs()[0]).withStyle(style -> style.withBold(true));
        Component hover = name.getStyle().getHoverEvent().getValue(HoverEvent.Action.SHOW_TEXT);
        ((MutableComponent) hover.getSiblings().getFirst()).withStyle(style -> style.withBold(true));
        Component lore = machine.get(DataComponents.LORE).lines().getFirst();
        ((MutableComponent) lore.getSiblings().getFirst().getSiblings().getFirst()).withStyle(style -> style.withBold(true));
    }

    private static ItemStack unstackableMachine() {
        ItemStack machine = new ItemStack(Items.FURNACE);
        machine.set(DataComponents.MAX_STACK_SIZE, 1);
        return machine;
    }

    private static void assertFailure(FactoryDiagnostic.Code expected, Runnable action) {
        assertEquals(expected, assertThrows(ModelValidationException.class, action::run).diagnostic().code());
    }
}
