package com.aquariverr.fluxod.client.particle;

import com.aquariverr.fluxod.block.entity.TileQuantumBatteryController;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.WaterDropParticle;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.SimpleParticleType;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class QuantumRainParticle extends WaterDropParticle {

    private final boolean bypassCollision;

    private QuantumRainParticle(ClientLevel level, double x, double y, double z, boolean bypassCollision) {
        super(level, x, y, z);
        this.bypassCollision = bypassCollision;
    }

    @Override
    public void tick() {
        if (!bypassCollision) {
            super.tick();
            return;
        }

        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.lifetime-- <= 0) {
            this.remove();
            return;
        }

        this.yd -= this.gravity;
        this.setPos(this.x + this.xd, this.y + this.yd, this.z + this.zd);
        this.xd *= 0.98F;
        this.yd *= 0.98F;
        this.zd *= 0.98F;

        BlockPos blockPos = BlockPos.containing(this.x, this.y, this.z);
        double fluidHeight = this.level.getFluidState(blockPos).getHeight(this.level, blockPos);
        if (fluidHeight > 0.0 && this.y < blockPos.getY() + fluidHeight) {
            this.remove();
        }
    }

    private static boolean isDynamicTarget(ClientLevel level, double x, double y, double z) {
        BlockPos position = BlockPos.containing(x, y, z);
        return TileQuantumBatteryController.isDynamicStructureBlock(level, position)
                || TileQuantumBatteryController.isDynamicStructureBlock(level, position.below())
                || TileQuantumBatteryController.isDynamicStructureBlock(level, position.above());
    }

    public static final class Provider implements ParticleProvider<SimpleParticleType> {
        private final SpriteSet sprite;

        public Provider(SpriteSet sprite) {
            this.sprite = sprite;
        }

        @Override
        public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z,
                                       double xSpeed, double ySpeed, double zSpeed) {
            QuantumRainParticle particle = new QuantumRainParticle(
                    level, x, y, z, isDynamicTarget(level, x, y, z));
            particle.pickSprite(sprite);
            return particle;
        }
    }
}
