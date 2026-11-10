package com.aquariverr.fluxod.miniature.storage;

import com.aquariverr.fluxod.miniature.model.FactoryDiagnostic;
import com.aquariverr.fluxod.miniature.model.ModelValidationException;
import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import net.minecraft.core.Holder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.material.Fluid;
import net.neoforged.neoforge.fluids.FluidStack;

import java.util.Objects;

/** Converts a fluid stack to the stable identity used by the miniature universe. */
public final class FactoryFluidResources {

    public static ResourceKey keyOf(FluidStack stack, HolderLookup.Provider registries) {
        Objects.requireNonNull(stack, "stack");
        Objects.requireNonNull(registries, "registries");
        if (stack.isEmpty()) throw invalid("An empty stack has no stored fluid identity");
        var holder = stack.getFluidHolder();
        var key = holder.unwrapKey().orElseThrow(() -> invalid("The fluid is not registered"));
        Holder.Reference<Fluid> registered = registries.lookupOrThrow(Registries.FLUID).get(key)
                .orElseThrow(() -> invalid("The fluid is missing from the registry: " + key.location()));
        if (registered.value() != stack.getFluid()) throw invalid("The fluid registry does not match the supplied stack");
        CompoundTag saved = asCompound(stack.copyWithAmount(1).save(registries));
        if (!saved.contains("id", Tag.TAG_STRING) || !key.location().toString().equals(saved.getString("id"))) {
            throw invalid("The fluid stack did not encode a stable registry identity");
        }
        CompoundTag components = saved.contains("components", Tag.TAG_COMPOUND)
                ? saved.getCompound("components") : new CompoundTag();
        return new ResourceKey(ResourceKind.FLUID, key.location().toString(), components);
    }

    public static FluidStack fluidOf(ResourceKey key, HolderLookup.Provider registries) {
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(registries, "registries");
        if (key.kind() != ResourceKind.FLUID) throw invalid("Only a fluid resource can be restored as a fluid stack");
        ResourceLocation id = ResourceLocation.parse(key.registryId());
        if (registries.lookupOrThrow(Registries.FLUID).get(
                net.minecraft.resources.ResourceKey.create(Registries.FLUID, id)).isEmpty()) {
            throw invalid("The fluid is missing from the registry: " + key.registryId());
        }
        CompoundTag encoded = new CompoundTag();
        encoded.putString("id", key.registryId());
        encoded.putInt("amount", 1);
        if (!key.components().isEmpty()) encoded.put("components", key.components());
        return FluidStack.parse(registries, encoded)
                .orElseThrow(() -> invalid("The fluid components cannot be restored"));
    }

    private static CompoundTag asCompound(Tag tag) {
        if (tag instanceof CompoundTag compound) return compound;
        throw invalid("The fluid stack did not encode as a compound");
    }

    private static ModelValidationException invalid(String message) {
        return new ModelValidationException(FactoryDiagnostic.Code.INVALID_RESOURCE_ID, message);
    }

    private FactoryFluidResources() {}
}
