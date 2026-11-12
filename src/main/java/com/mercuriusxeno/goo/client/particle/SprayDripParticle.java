package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.registry.GooParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.ARGB;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A Goop or Slick spray drip: the mod's blocky goo drip, the trail-drip's
 * sprite in the ability's colors, thrown from the glove onto the end of its
 * coat ray, where it splats as the trail-drip does and is gone. It swings
 * with its caster's glove at launch.
 * goop-and-slick-spray-throws-goo-drips-that-splat
 * glove-stream-motes-swing-with-the-glove-at-launch
 */
public final class SprayDripParticle extends GloveMote {

    private final Vec3 apex;
    private final GooRenderUtil.UvRect uv;
    private SprayDrip drip;

    private SprayDripParticle(ClientLevel level, Vec3 apex, TextureAtlasSprite sprite, SprayDrip drip) {
        super(level, apex.x, apex.y, apex.z, sprite);
        this.apex = apex;
        this.uv = GooSubmitter.spriteUv(sprite);
        this.gravity = 0f;
        this.hasPhysics = false;
        this.rCol = ARGB.redFloat(drip.rgb());
        this.gCol = ARGB.greenFloat(drip.rgb());
        this.bCol = ARGB.blueFloat(drip.rgb());
        fly(drip);
    }

    /**
     * Throws the drip onto its coat ray's end, carrying its caster's travel
     * at launch, where its provider's launch held the two together.
     * goop-and-slick-spray-throws-goo-drips-that-splat
     *
     * @param coatEnd the end of the drip's coat ray
     * @param travel  the caster's travel velocity, blocks per tick
     */
    public void landOn(Vec3 coatEnd, Vec3 travel) {
        fly(SprayDrip.toward(apex, coatEnd, travel, drip.rgb()));
    }

    private void fly(SprayDrip flight) {
        this.drip = flight;
        this.xd = flight.velocity().x;
        this.yd = flight.velocity().y;
        this.zd = flight.velocity().z;
    }

    /** Swings the drip with the glove over its opening ticks, flies it on, and splats it at its end. */
    @Override
    public void tick() {
        swingWithTheGlove();
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        SprayDrip.Step step = drip.step(new Vec3(this.x, this.y, this.z), new Vec3(this.xd, this.yd, this.zd),
                this.age);
        this.setPos(step.position().x, step.position().y, step.position().z);
        this.xd = step.velocity().x;
        this.yd = step.velocity().y;
        this.zd = step.velocity().z;
        this.age++;
        if (step.splats()) {
            this.remove();
            this.level.addParticle(ColorParticleOption.create(GooParticles.TRAIL_DRIP_LAND.get(),
                    this.rCol, this.gCol, this.bCol), this.x, this.y, this.z, 0.0, 0.0, 0.0);
        }
    }

    @Override
    public Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    @Override
    protected float getU0() {
        return uv.u0();
    }

    @Override
    protected float getU1() {
        return uv.u1();
    }

    @Override
    protected float getV0() {
        return uv.v0();
    }

    @Override
    protected float getV1() {
        return uv.v1();
    }

    /**
     * Makes an ability's spray drips from the stream's launches, in its palette.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;
        private final BlendPalette palette;

        /**
         * Creates a provider over the trail-drip sprite in an ability's palette.
         *
         * @param sprites the sprite set from the spray drip's definition
         * @param palette the colors its drips blend between
         */
        public Provider(SpriteSet sprites, BlendPalette palette) {
            this.sprites = sprites;
            this.palette = palette;
        }

        @Override
        public @Nullable SprayDripParticle createParticle(SimpleParticleType type, ClientLevel level, double x,
                                                          double y, double z, double xSpeed, double ySpeed,
                                                          double zSpeed, RandomSource random) {
            Vec3 apex = new Vec3(x, y, z);
            Vec3 end = GooStreamHandler.launchEnd(apex, new Vec3(xSpeed, ySpeed, zSpeed));
            return new SprayDripParticle(level, apex, sprites.get(0, 1),
                    SprayDrip.launch(apex, end, Vec3.ZERO, palette, random));
        }
    }
}
