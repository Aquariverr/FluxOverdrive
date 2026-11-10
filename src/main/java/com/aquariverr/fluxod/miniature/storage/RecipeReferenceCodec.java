package com.aquariverr.fluxod.miniature.storage;

import com.aquariverr.fluxod.miniature.model.RecipeReference;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;

import java.util.ArrayList;
import java.util.Set;

import static com.aquariverr.fluxod.miniature.storage.ResourceNbtCodec.requireFields;
import static com.aquariverr.fluxod.miniature.storage.ResourceNbtCodec.requireType;

public final class RecipeReferenceCodec {
    public static CompoundTag write(RecipeReference reference) {
        CompoundTag tag = new CompoundTag();
        tag.putString("adapter", reference.adapterId());
        tag.putString("recipe", reference.recipeId());
        tag.putInt("width", reference.width());
        tag.putInt("height", reference.height());
        ListTag bindings = new ListTag();
        for (var binding : reference.bindings()) {
            CompoundTag entry = new CompoundTag();
            entry.putInt("slot", binding.slot());
            entry.put("resource", ResourceNbtCodec.writeKey(binding.resource()));
            bindings.add(entry);
        }
        tag.put("bindings", bindings);
        return tag;
    }

    public static RecipeReference read(CompoundTag tag) {
        requireFields(tag, Set.of("adapter", "recipe", "width", "height", "bindings"));
        requireType(tag, "adapter", Tag.TAG_STRING);
        requireType(tag, "recipe", Tag.TAG_STRING);
        requireType(tag, "width", Tag.TAG_INT);
        requireType(tag, "height", Tag.TAG_INT);
        requireType(tag, "bindings", Tag.TAG_LIST);
        ListTag saved = (ListTag) tag.get("bindings");
        if (saved.getElementType() != Tag.TAG_COMPOUND || saved.isEmpty() || saved.size() > 9) {
            throw new IllegalArgumentException("Invalid saved recipe bindings");
        }
        var bindings = new ArrayList<RecipeReference.Binding>();
        for (Tag child : saved) {
            CompoundTag entry = (CompoundTag) child;
            requireFields(entry, Set.of("slot", "resource"));
            requireType(entry, "slot", Tag.TAG_INT);
            requireType(entry, "resource", Tag.TAG_COMPOUND);
            bindings.add(new RecipeReference.Binding(entry.getInt("slot"), ResourceNbtCodec.readKey(entry.getCompound("resource"))));
        }
        return new RecipeReference(tag.getString("adapter"), tag.getString("recipe"), tag.getInt("width"), tag.getInt("height"), bindings);
    }

    private RecipeReferenceCodec() {}
}
