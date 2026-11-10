package com.aquariverr.fluxod.client.model.ctm;

import com.aquariverr.fluxod.FluxOverdrive;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.Material;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.inventory.InventoryMenu;

import java.util.function.Function;

final class ConnectedTextureSet {
    private final TextureAtlasSprite base;
    private final TextureAtlasSprite[] sprites;

    private ConnectedTextureSet(TextureAtlasSprite base, TextureAtlasSprite[] sprites) {
        this.base = base;
        this.sprites = sprites;
    }

    static ConnectedTextureSet bake(ResourceLocation blockId,
                                     Function<Material, TextureAtlasSprite> textureGetter) {
        return bake(blockId, null, textureGetter);
    }

    static ConnectedTextureSet bake(ResourceLocation textureSetId, ResourceLocation baseSpriteId,
                                    Function<Material, TextureAtlasSprite> textureGetter) {
        TextureAtlasSprite[] sprites = new TextureAtlasSprite[256];
        for (int mask = 0; mask < sprites.length; mask++) {
            if (!ConnectedTextureMask.isCanonical(mask)) {
                continue;
            }

            ResourceLocation spriteId = ResourceLocation.fromNamespaceAndPath(
                    textureSetId.getNamespace(),
                    "block/ctm/" + textureSetId.getPath() + "/" + ConnectedTextureMask.spriteName(mask));
            TextureAtlasSprite sprite = textureGetter.apply(new Material(InventoryMenu.BLOCK_ATLAS, spriteId));
            if (sprite.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                FluxOverdrive.LOGGER.error("Missing connected texture sprite {}", spriteId);
                return null;
            }
            sprites[mask] = sprite;
        }

        TextureAtlasSprite base = sprites[0];
        if (baseSpriteId != null) {
            base = textureGetter.apply(new Material(InventoryMenu.BLOCK_ATLAS, baseSpriteId));
            if (base.contents().name().equals(MissingTextureAtlasSprite.getLocation())) {
                FluxOverdrive.LOGGER.error("Missing connected texture base sprite {}", baseSpriteId);
                return null;
            }
        }
        return new ConnectedTextureSet(base, sprites);
    }

    TextureAtlasSprite base() {
        return base;
    }

    TextureAtlasSprite sprite(int mask) {
        return sprites[ConnectedTextureMask.normalize(mask)];
    }
}
