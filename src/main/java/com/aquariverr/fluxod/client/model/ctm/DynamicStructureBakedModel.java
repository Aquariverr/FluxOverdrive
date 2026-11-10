package com.aquariverr.fluxod.client.model.ctm;

import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.BlockAndTintGetter;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.client.model.BakedModelWrapper;
import net.neoforged.neoforge.client.model.data.ModelData;
import net.neoforged.neoforge.client.model.data.ModelProperty;

import javax.annotation.Nullable;
import java.util.List;

public final class DynamicStructureBakedModel extends BakedModelWrapper<BakedModel> {
    private static final ModelProperty<Boolean> HIDE_STATIC = new ModelProperty<>();
    private final boolean controllerModel;

    public DynamicStructureBakedModel(BakedModel originalModel) {
        this(originalModel, false);
    }

    public DynamicStructureBakedModel(BakedModel originalModel, boolean controllerModel) {
        super(originalModel);
        this.controllerModel = controllerModel;
    }

    @Override
    public ModelData getModelData(BlockAndTintGetter level, BlockPos pos, BlockState state, ModelData modelData) {
        return super.getModelData(level, pos, state, modelData).derive()
                .with(HIDE_STATIC, controllerModel
                        ? TileQuantumBatteryController.isDynamicControllerBlock(level, pos)
                        : TileQuantumBatteryController.isDynamicStructureBlock(level, pos))
                .build();
    }

    @Override
    public List<BakedQuad> getQuads(@Nullable BlockState state, @Nullable Direction side,
                                    RandomSource random, ModelData modelData,
                                    @Nullable RenderType renderType) {
        if (Boolean.TRUE.equals(modelData.get(HIDE_STATIC))
                && !Boolean.TRUE.equals(modelData.get(ConnectedTextureBakedModel.DYNAMIC_RENDER))) {
            return List.of();
        }
        return super.getQuads(state, side, random, modelData, renderType);
    }
}
