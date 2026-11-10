package com.aquariverr.fluxod.miniature.storage;

import com.aquariverr.fluxod.miniature.model.FactoryDiagnostic;
import com.aquariverr.fluxod.miniature.model.ModelValidationException;
import com.aquariverr.fluxod.miniature.recipe.MachineRecipeAdapters;
import net.minecraft.core.component.DataComponentType;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemContainerContents;

import java.util.Objects;
import java.util.Set;

public final class VanillaMachinePolicy {

    private static final Set<DataComponentType<?>> PRESENTATION_COMPONENTS = Set.of(
            DataComponents.CUSTOM_NAME, DataComponents.ITEM_NAME, DataComponents.LORE,
            DataComponents.RARITY, DataComponents.ENCHANTMENT_GLINT_OVERRIDE,
            DataComponents.CUSTOM_MODEL_DATA, DataComponents.MAX_STACK_SIZE);
    private static final Set<DataComponentType<?>> BASELINE_COMPONENTS = Set.of(
            DataComponents.ENCHANTMENTS, DataComponents.REPAIR_COST, DataComponents.ATTRIBUTE_MODIFIERS,
            DataComponents.CONTAINER);

    public static void requireAccepted(ItemStack stack) {
        if (stack == null || stack.isEmpty() || MachineRecipeAdapters.forMachine(stack).isEmpty()) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_MACHINE,
                    "No registered miniature recipe adapter accepts this machine");
        }
        for (var change : stack.getComponentsPatch().entrySet()) {
            if (!PRESENTATION_COMPONENTS.contains(change.getKey())) {
                throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                        "Machine state and behavior component changes are not supported: " + componentId(change.getKey()));
            }
        }
        for (var component : stack.getComponents()) {
            if (!PRESENTATION_COMPONENTS.contains(component.type())
                    && (!(BASELINE_COMPONENTS.contains(component.type()) || MachineRecipeAdapters.forMachine(stack).stream()
                            .anyMatch(adapter -> adapter.supportsDefaultComponent(component.type())))
                    || !Objects.equals(component.value(), stack.getPrototype().get(component.type()))
                    || component.type() == DataComponents.CONTAINER && !ItemContainerContents.EMPTY.equals(component.value()))) {
                throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                        "Unknown or configured machine component is not supported: " + componentId(component.type()));
            }
        }
        Integer maxStackSize = stack.get(DataComponents.MAX_STACK_SIZE);
        if (maxStackSize == null || maxStackSize < 1 || maxStackSize > 99) {
            throw new ModelValidationException(FactoryDiagnostic.Code.INVALID_COMPONENTS,
                    "A machine must retain a valid natural stack size");
        }
    }

    private static String componentId(DataComponentType<?> type) {
        return String.valueOf(BuiltInRegistries.DATA_COMPONENT_TYPE.getKey(type));
    }

    private VanillaMachinePolicy() {}
}
