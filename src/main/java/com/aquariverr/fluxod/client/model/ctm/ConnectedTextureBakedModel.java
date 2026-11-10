package com.aquariverr.fluxod.client.model.ctm;

import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.IQuadTransformer;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;

public final class ConnectedTextureBakedModel extends BakedModelWrapper<BakedModel> {
    @FunctionalInterface
    interface ConnectionComputer {
        long compute(BlockAndTintGetter level, BlockPos pos, BlockState state);
    }

    private static final ModelProperty<Long> CONNECTIONS = new ModelProperty<>();
    private static final ModelProperty<Boolean> HIDE_STATIC = new ModelProperty<>();
    public static final ModelProperty<Boolean> DYNAMIC_RENDER = new ModelProperty<>();
    public static final ModelProperty<Long> CONNECTIONS_OVERRIDE = new ModelProperty<>();

    private final Map<TextureAtlasSprite, ConnectedTextureSet> texturesByBase;
    private final ConnectionComputer connectionComputer;
    private final Map<RetextureKey, BakedQuad> quadCache = new ConcurrentHashMap<>();

    ConnectedTextureBakedModel(BakedModel originalModel, ConnectedTextureSet textures) {
        this(originalModel, textures, ConnectedTextureLogic::compute);
    }

    ConnectedTextureBakedModel(BakedModel originalModel, ConnectedTextureSet textures,
                               ConnectionComputer connectionComputer) {
        this(originalModel, List.of(textures), connectionComputer);
    }

    ConnectedTextureBakedModel(BakedModel originalModel, List<ConnectedTextureSet> textureSets,
                               ConnectionComputer connectionComputer) {
        super(originalModel);
        Map<TextureAtlasSprite, ConnectedTextureSet> byBase = new IdentityHashMap<>();
        for (ConnectedTextureSet textureSet : textureSets) {
            byBase.put(textureSet.base(), textureSet);
        }
        this.texturesByBase = Collections.unmodifiableMap(byBase);
        this.connectionComputer = connectionComputer;
    }

    public static long computeVirtualConnections(BlockPos pos, Predicate<BlockPos> occupied) {
        return ConnectedTextureLogic.compute(pos, occupied);
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        ModelData parentData = super.getModelData(level, pos, state, modelData);
        return parentData.derive()
                .with(CONNECTIONS, connectionComputer.compute(level, pos, state))
                .with(HIDE_STATIC, TileQuantumBatteryController.isDynamicStructureBlock(level, pos))
                .build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                    RandomSource random, ModelData modelData,
                                    @Nullable RenderType renderType) {
        List<BakedQuad> original = super.getQuads(state, side, random, modelData, renderType);
        if (Boolean.TRUE.equals(modelData.get(HIDE_STATIC))
                && !Boolean.TRUE.equals(modelData.get(DYNAMIC_RENDER))) {
            return List.of();
        }
        if (state == null || side == null
                || (!modelData.has(CONNECTIONS) && !modelData.has(CONNECTIONS_OVERRIDE))) {
            return original;
        }

        Long overriddenConnections = modelData.get(CONNECTIONS_OVERRIDE);
        int connectionMask = overriddenConnections != null
                ? ConnectedTextureMask.unpack(overriddenConnections, side.get3DDataValue())
                : ConnectedTextureMask.unpack(modelData.get(CONNECTIONS), side.get3DDataValue());
        List<BakedQuad> result = null;
        for (int index = 0; index < original.size(); index++) {
            BakedQuad quad = original.get(index);
            ConnectedTextureSet textureSet = texturesByBase.get(quad.getSprite());
            if (textureSet == null) {
                continue;
            }

            TextureAtlasSprite selected = textureSet.sprite(connectionMask);
            if (result == null) {
                result = new ArrayList<>(original);
            }
            result.set(index, quadCache.computeIfAbsent(
                    new RetextureKey(quad, selected), key -> retexture(key.quad(), key.sprite())));
        }
        return result == null ? original : List.copyOf(result);
    }

    private static BakedQuad retexture(BakedQuad quad, TextureAtlasSprite replacement) {
        TextureAtlasSprite original = quad.getSprite();
        int[] vertices = quad.getVertices().clone();

        for (int vertex = 0; vertex < 4; vertex++) {
            int uv = vertex * IQuadTransformer.STRIDE + IQuadTransformer.UV0;
            float oldU = Float.intBitsToFloat(vertices[uv]);
            float oldV = Float.intBitsToFloat(vertices[uv + 1]);
            vertices[uv] = Float.floatToRawIntBits(replacement.getU(original.getUOffset(oldU)));
            vertices[uv + 1] = Float.floatToRawIntBits(replacement.getV(original.getVOffset(oldV)));
        }

        return new BakedQuad(vertices, quad.getTintIndex(), quad.getDirection(), replacement,
                quad.isShade(), quad.hasAmbientOcclusion());
    }

    private record RetextureKey(BakedQuad quad, TextureAtlasSprite sprite) {}
}
