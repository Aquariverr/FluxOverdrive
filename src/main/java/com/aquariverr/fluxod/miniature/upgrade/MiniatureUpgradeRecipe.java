package com.aquariverr.fluxod.miniature.upgrade;

import com.aquariverr.fluxod.miniature.model.FactoryLayout;
import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Server-owned, one-way expansion. This is never executable in a crafting grid. */
public record MiniatureUpgradeRecipe(int fromSize, int toSize, List<Cost> materials) implements Recipe<RecipeInput> {
    public static final int MAX_MATERIALS = 8;
    public static final int MAX_COUNT = 2304;
    public static final RecipeType<MiniatureUpgradeRecipe> TYPE = new RecipeType<>() {
        @Override public String toString() { return "flux_overdrive:miniature_upgrade"; }
    };
    public static final MapCodec<MiniatureUpgradeRecipe> CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
            Codec.intRange(3, 9).fieldOf("from_size").forGetter(MiniatureUpgradeRecipe::fromSize),
            Codec.intRange(5, 11).fieldOf("to_size").forGetter(MiniatureUpgradeRecipe::toSize),
            Cost.CODEC.listOf(1, MAX_MATERIALS).fieldOf("materials").forGetter(MiniatureUpgradeRecipe::materials)
    ).apply(instance, MiniatureUpgradeRecipe::new));
    public static final RecipeSerializer<MiniatureUpgradeRecipe> SERIALIZER = new RecipeSerializer<>() {
        @Override public MapCodec<MiniatureUpgradeRecipe> codec() { return CODEC; }
        @Override public StreamCodec<RegistryFriendlyByteBuf, MiniatureUpgradeRecipe> streamCodec() { return STREAM_CODEC; }
    };
    public static final StreamCodec<RegistryFriendlyByteBuf, MiniatureUpgradeRecipe> STREAM_CODEC = new StreamCodec<>() {
        @Override public MiniatureUpgradeRecipe decode(RegistryFriendlyByteBuf buffer) {
            int from = buffer.readUnsignedByte(), to = buffer.readUnsignedByte(), count = buffer.readVarInt();
            if (count < 1 || count > MAX_MATERIALS) throw new IllegalArgumentException("Invalid upgrade cost count");
            List<Cost> costs = new ArrayList<>();
            for (int i = 0; i < count; i++) costs.add(new Cost(BuiltInRegistries.ITEM.byId(buffer.readVarInt()), buffer.readVarInt()));
            return new MiniatureUpgradeRecipe(from, to, costs);
        }
        @Override public void encode(RegistryFriendlyByteBuf buffer, MiniatureUpgradeRecipe recipe) {
            buffer.writeByte(recipe.fromSize()); buffer.writeByte(recipe.toSize()); buffer.writeVarInt(recipe.materials().size());
            for (Cost cost : recipe.materials()) { buffer.writeVarInt(BuiltInRegistries.ITEM.getId(cost.item())); buffer.writeVarInt(cost.count()); }
        }
    };

    public MiniatureUpgradeRecipe {
        materials = List.copyOf(materials);
        if (!FactoryLayout.VALID_SIZES.contains(fromSize) || fromSize == 11 || toSize != fromSize + 2
                || materials.isEmpty() || materials.size() > MAX_MATERIALS
                || materials.stream().map(Cost::item).distinct().count() != materials.size()) {
            throw new IllegalArgumentException("Upgrade must advance one tier with distinct, nonempty materials");
        }
    }

    public record Cost(Item item, int count) {
        public static final Codec<Cost> CODEC = RecordCodecBuilder.create(instance -> instance.group(
                BuiltInRegistries.ITEM.byNameCodec().fieldOf("item").forGetter(Cost::item),
                Codec.intRange(1, MAX_COUNT).fieldOf("count").forGetter(Cost::count)
        ).apply(instance, Cost::new));
        public Cost {
            Objects.requireNonNull(item);
            if (item == Items.AIR || count < 1 || count > MAX_COUNT) throw new IllegalArgumentException("Invalid upgrade material");
        }
        // Named/configured blocks or state-bearing plugin assets must never disappear as generic materials.
        public boolean matches(ItemStack stack) { return ItemStack.isSameItemSameComponents(stack, new ItemStack(item)); }
    }

    @Override public boolean matches(RecipeInput input, Level level) { return false; }
    @Override public ItemStack assemble(RecipeInput input, HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public boolean canCraftInDimensions(int width, int height) { return false; }
    @Override public ItemStack getResultItem(HolderLookup.Provider registries) { return ItemStack.EMPTY; }
    @Override public RecipeSerializer<?> getSerializer() { return SERIALIZER; }
    @Override public RecipeType<?> getType() { return TYPE; }
    @Override public boolean isSpecial() { return true; }
}
