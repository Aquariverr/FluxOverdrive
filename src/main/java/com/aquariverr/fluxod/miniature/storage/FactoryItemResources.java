package com.aquariverr.fluxod.miniature.storage;

import com.aquariverr.fluxod.miniature.model.FactoryDiagnostic;
import com.aquariverr.fluxod.miniature.model.ModelValidationException;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.component.DataComponentMap;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.contents.NbtContents;
import net.minecraft.network.chat.contents.SelectorContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;

import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

public final class FactoryItemResources {

    public static ResourceKey keyOf(ItemStack stack, HolderLookup.Provider registries) {
        return keyOf(stack, registries, new ValidationContext());
    }

    private static ResourceKey keyOf(ItemStack stack, HolderLookup.Provider registries, ValidationContext context) {
        ResourceKey previous = context.items.get(stack);
        if (previous != null) return previous;
        context.enter(stack);
        try {
            ResourceKey key = checkedKeyOf(stack, registries, context);
            context.items.put(stack, key);
            return key;
        } finally {
            context.exit(stack);
        }
    }

    private static ResourceKey checkedKeyOf(ItemStack stack, HolderLookup.Provider registries, ValidationContext context) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(registries, "registries");
        if (stack.isEmpty()) {
            throw invalidResource("An empty stack has no stored item identity");
        }
        var itemKey = stack.getItemHolder().unwrapKey().orElseThrow(
                () -> invalidResource("The item is not registered"));
        Holder.Reference<Item> registered = registries.lookupOrThrow(Registries.ITEM).get(itemKey)
                .orElseThrow(() -> invalidResource("The item is missing from the registry: " + itemKey.location()));
        if (registered.value() != stack.getItem()) {
            throw invalidResource("The item registry does not match the supplied stack");
        }
        ResourceKey key = new ResourceKey(ResourceKind.ITEM, itemKey.location().toString(),
                encodeComponents(stack.getComponents(), registries, context));
        ItemStack restored = itemOf(key, registries, context);
        requireSameEffectiveComponents(stack.getComponents(), restored.getComponents());
        return key;
    }

    public static ItemStack itemOf(ResourceKey key, HolderLookup.Provider registries) {
        return itemOf(key, registries, new ValidationContext());
    }

    private static ItemStack itemOf(ResourceKey key, HolderLookup.Provider registries, ValidationContext context) {
        ItemStack previous = context.restored.get(key);
        if (previous != null) return previous;
        context.enter(key);
        try {
            ItemStack restored = checkedItemOf(key, registries, context);
            context.restored.put(key, restored);
            return restored;
        } finally {
            context.exit(key);
        }
    }

    private static ItemStack checkedItemOf(ResourceKey key, HolderLookup.Provider registries, ValidationContext context) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(registries, "registries");
        if (key.kind() != ResourceKind.ITEM) {
            throw invalidResource("Only an item resource can be restored as an item stack");
        }
        var itemKey = net.minecraft.resources.ResourceKey.create(Registries.ITEM,
                ResourceLocation.parse(key.registryId()));
        Holder.Reference<Item> registered = registries.lookupOrThrow(Registries.ITEM).get(itemKey)
                .orElseThrow(() -> invalidResource("The item is missing from the registry: " + key.registryId()));
        if (registered.value() == Items.AIR) {
            throw invalidResource("Air cannot be a stored item resource");
        }
        try {
            CompoundTag encoded = key.components();
            DataComponentMap components = DataComponentMap.CODEC.parse(
                    registries.createSerializationContext(NbtOps.INSTANCE), encoded)
                    .getOrThrow(FactoryItemResources::invalidComponents);
            ItemStack.validateComponents(components).getOrThrow(FactoryItemResources::invalidComponents);
            ItemStack stack = new ItemStack(registered, 1);
            // A full map must also preserve absent defaults, which applying values alone cannot do.
            for (DataComponentType<?> type : Set.copyOf(stack.getComponents().keySet())) {
                stack.remove(type);
            }
            stack.applyComponents(components);
            if (stack.getMaxStackSize() < 1 || !encoded.equals(encodeComponents(stack.getComponents(), registries, context))) {
                throw invalidComponents("The encoded components do not reconstruct the same item identity");
            }
            return stack;
        } catch (ModelValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidComponents("Cannot restore item components: " + exception.getMessage());
        }
    }

    private static CompoundTag encodeComponents(DataComponentMap components, HolderLookup.Provider registries,
                                                ValidationContext context) {
        try {
            for (DataComponentType<?> type : components.keySet()) {
                if (type.isTransient() || BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type) == null) {
                    throw invalidComponents("Every effective component must be registered and persistent: "
                            + BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type));
                }
            }
            for (var type : java.util.List.of(DataComponents.CUSTOM_NAME, DataComponents.ITEM_NAME)) {
                Component name = components.get(type);
                if (name != null) requirePersistentText(name, registries, context);
            }
            ItemLore lore = components.get(DataComponents.LORE);
            if (lore != null) {
                for (Component line : lore.lines()) requirePersistentText(line, registries, context);
            }
            var food = components.get(DataComponents.FOOD);
            if (food != null && food.usingConvertsTo().isPresent()) {
                ItemStack converted = food.usingConvertsTo().orElseThrow();
                if (converted.getCount() != 1) throw invalidComponents("Food conversion must retain exactly one item");
                keyOf(converted, registries, context);
            }
            ItemStack.validateComponents(components).getOrThrow(FactoryItemResources::invalidComponents);
            Tag encoded = DataComponentMap.CODEC.encodeStart(
                    registries.createSerializationContext(NbtOps.INSTANCE), components)
                    .getOrThrow(FactoryItemResources::invalidComponents);
            if (!(encoded instanceof CompoundTag compound)) {
                throw invalidComponents("Item components must encode as a compound");
            }
            return compound;
        } catch (ModelValidationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw invalidComponents("Cannot encode item components: " + exception.getMessage());
        }
    }

    private static void requireSameEffectiveComponents(DataComponentMap original, DataComponentMap restored) {
        if (!original.keySet().equals(restored.keySet())) {
            throw invalidComponents("The effective component set changed while restoring the item");
        }
        for (var component : original) {
            DataComponentType<?> type = component.type();
            if (!Objects.equals(component.value(), restored.get(type))
                    && type != DataComponents.CUSTOM_NAME && type != DataComponents.ITEM_NAME && type != DataComponents.LORE
                    // NeoForge PossibleEffect compares supplier identity; the exact encoded effect was checked by itemOf.
                    && type != DataComponents.FOOD) {
                throw invalidComponents("The item component cannot be restored without changes: "
                        + BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type));
            }
        }
        // Native text codecs canonicalize literal translation arguments into strings and derive lore styling.
        // Their exact encoded identity was checked by itemOf; nested item assets are validated before encoding.
    }

    private static void requirePersistentText(Component text, HolderLookup.Provider registries, ValidationContext context) {
        context.enter(text);
        try {
            for (Component sibling : text.getSiblings()) requirePersistentText(sibling, registries, context);
            if (text.getContents() instanceof TranslatableContents translated) {
                for (Object argument : translated.getArgs()) {
                    if (argument instanceof Component component) requirePersistentText(component, registries, context);
                }
            } else if (text.getContents() instanceof SelectorContents selector) {
                selector.getSeparator().ifPresent(separator -> requirePersistentText(separator, registries, context));
            } else if (text.getContents() instanceof NbtContents nbt) {
                nbt.getSeparator().ifPresent(separator -> requirePersistentText(separator, registries, context));
            }
            HoverEvent hover = text.getStyle().getHoverEvent();
            if (hover != null) {
                Component hoverText = hover.getValue(HoverEvent.Action.SHOW_TEXT);
                if (hoverText != null) requirePersistentText(hoverText, registries, context);
                HoverEvent.ItemStackInfo hoverItem = hover.getValue(HoverEvent.Action.SHOW_ITEM);
                if (hoverItem != null) keyOf(hoverItem.getItemStack(), registries, context);
                HoverEvent.EntityTooltipInfo hoverEntity = hover.getValue(HoverEvent.Action.SHOW_ENTITY);
                if (hoverEntity != null) {
                    hoverEntity.name.ifPresent(name -> requirePersistentText(name, registries, context));
                }
            }
        } finally {
            context.exit(text);
        }
    }

    private static final class ValidationContext {
        private static final int MAX_DEPTH = 64;
        private static final int MAX_VISITS = 4_096;
        private final Set<Object> active = Collections.newSetFromMap(new IdentityHashMap<>());
        private final Map<ItemStack, ResourceKey> items = new IdentityHashMap<>();
        private final Map<ResourceKey, ItemStack> restored = new HashMap<>();
        private int visits;

        private void enter(Object value) {
            if (active.size() >= MAX_DEPTH || ++visits > MAX_VISITS || !active.add(value)) {
                throw invalidComponents("Item components contain a cycle or exceed the validation nesting/work limit");
            }
        }

        private void exit(Object value) {
            active.remove(value);
        }
    }

    private static ModelValidationException invalidComponents(String message) {
        return new ModelValidationException(FactoryDiagnostic.Code.INVALID_COMPONENTS, message);
    }

    private static ModelValidationException invalidResource(String message) {
        return new ModelValidationException(FactoryDiagnostic.Code.INVALID_RESOURCE_ID, message);
    }

    private FactoryItemResources() {}
}
