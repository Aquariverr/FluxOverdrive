package com.aquariverr.fluxod.client.gui.miniature;

import com.aquariverr.fluxod.miniature.model.ResourceKey;
import com.aquariverr.fluxod.miniature.model.ResourceKind;
import com.aquariverr.fluxod.miniature.storage.FactoryFluidResources;
import com.aquariverr.fluxod.miniature.storage.FactoryItemResources;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.neoforged.fml.ModList;
import net.neoforged.neoforge.client.extensions.common.IClientFluidTypeExtensions;

/** Shared by grid cells, inventory rows, recipe ingredients and compiled outputs. */
public final class MiniatureResourceRendering {
    public record Visual(ItemStack item, ResourceLocation texture, int tint, Component name) {
        public void render(GuiGraphics graphics, int x, int y) {
            if (!item.isEmpty()) graphics.renderItem(item, x, y);
            else if (texture != null) {
                var sprite = Minecraft.getInstance().getTextureAtlas(TextureAtlas.LOCATION_BLOCKS).apply(texture);
                graphics.blit(x, y, 0, 16, 16, sprite, (tint >> 16 & 255) / 255F,
                        (tint >> 8 & 255) / 255F, (tint & 255) / 255F, (tint >>> 24) / 255F);
            } else {
                graphics.fill(x + 2, y + 2, x + 14, y + 14, tint);
                graphics.drawCenteredString(Minecraft.getInstance().font, "?", x + 8, y + 4, 0xFFFFFFFF);
            }
        }
    }

    public static Visual resolve(ResourceKey key) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level != null) {
            try {
                if (key.kind() == ResourceKind.ITEM) {
                    ItemStack item = FactoryItemResources.itemOf(key, minecraft.level.registryAccess());
                    return new Visual(item, null, 0xFFFFFFFF, item.getHoverName());
                }
                if (key.kind() == ResourceKind.FLUID) {
                    var fluid = FactoryFluidResources.fluidOf(key, minecraft.level.registryAccess());
                    var extension = IClientFluidTypeExtensions.of(fluid.getFluid());
                    return new Visual(ItemStack.EMPTY, extension.getStillTexture(fluid), extension.getTintColor(fluid), fluid.getHoverName());
                }
                if (key.kind() == ResourceKind.CHEMICAL && ModList.get().isLoaded("mekanism")) {
                    return ChemicalVisual.resolve(key);
                }
            } catch (IllegalArgumentException | IllegalStateException ignored) {
                // Unavailable saved resources retain a visible generic icon and their original registry identity.
            }
        }
        return new Visual(ItemStack.EMPTY, null, key.kind() == ResourceKind.FLUID ? 0xFF487DC6 : 0xFFB880CB,
                Component.literal(key.registryId()));
    }

    public static Component kind(ResourceKey key) {
        return Component.translatable("screen.flux_overdrive.miniature_universe.resource_kind."
                + key.kind().name().toLowerCase(java.util.Locale.ROOT));
    }

    public static Component amount(ResourceKey key, Object value) {
        return Component.translatable("screen.flux_overdrive.miniature_universe.resource_amount."
                + key.kind().name().toLowerCase(java.util.Locale.ROOT), value);
    }

    /** This nested class is resolved only when Mekanism is present; the public API contains no optional types. */
    private static final class ChemicalVisual {
        static Visual resolve(ResourceKey key) {
            ResourceLocation id = ResourceLocation.parse(key.registryId());
            if (!mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.containsKey(id)) throw new IllegalArgumentException("Missing chemical");
            var chemical = mekanism.api.MekanismAPI.CHEMICAL_REGISTRY.get(id);
            return new Visual(ItemStack.EMPTY, chemical.getIcon(), 0xFF000000 | chemical.getTint(), chemical.getTextComponent());
        }
    }

    private MiniatureResourceRendering() {}
}
