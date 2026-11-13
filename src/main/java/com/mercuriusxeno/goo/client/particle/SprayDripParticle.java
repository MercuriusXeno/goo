package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.client.GooRenderUtil;
import com.mercuriusxeno.goo.client.GooSubmitter;
import com.mercuriusxeno.goo.network.DripFlights;
import com.mercuriusxeno.goo.registry.GooParticles;
import com.mercuriusxeno.goo.throwing.CoatRay;
import com.mercuriusxeno.goo.throwing.DripArc;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.core.particles.ColorParticleOption;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * A Goop or Slick spray drip: the mod's blocky goo drip, the trail-drip's
 * sprite in the ability's colors, thrown from the spray's source near and
 * above the glove on the arc its server drip flies, splatting where it
 * strikes as the trail-drip does and gone.
 * <p>
 * The client draws a frame after running its ticks, and the particle engine
 * takes a new particle in only after ticking the ones already flying, so a
 * drip reaches a frame a tick or more after the stream's tick launched it,
 * before its own first tick. Until that tick it draws nothing, the lifted
 * palm blob standing at the source alone, and its first tick throws it from
 * the source where it stands then, so the spray leaves its source for a
 * caster standing, walking, strafing or turning.
 * goop-and-slick-spray-throws-goo-drips-that-splat
 * goop-and-slick-coat-lands-where-each-drip-arc-strikes
 * goop-and-slick-spray-emanates-from-the-glove
 * palm-blob-lifts-as-the-spray-source
 */
public final class SprayDripParticle extends SingleQuadParticle {

    private final GooRenderUtil.UvRect uv;
    private final int rgb;
    private Source source;
    private DripArc.LivingNear living = DripArc.NO_LIVING;
    private boolean thrown;
    private SprayDrip drip;

    /**
     * Where a drip's spray source stands at a frame's partial tick.
     */
    @FunctionalInterface
    public interface Source {

        /**
         * @param partialTick the frame's partial tick
         * @return where the source stands
         */
        Vec3 at(float partialTick);
    }

    private SprayDripParticle(ClientLevel level, Vec3 apex, TextureAtlasSprite sprite, SprayDrip drip) {
        super(level, apex.x, apex.y, apex.z, sprite);
        this.uv = GooSubmitter.spriteUv(sprite);
        this.rgb = drip.rgb();
        this.source = partialTick -> apex;
        this.drip = drip;
        this.gravity = 0f;
        this.hasPhysics = false;
        this.rCol = ARGB.redFloat(rgb);
        this.gCol = ARGB.greenFloat(rgb);
        this.bCol = ARGB.blueFloat(rgb);
    }


    /**
     * Names the spray source the drip leaves and the launch it flies its arc
     * from: the drip's first tick throws it from the source at that launch,
     * and it flies until its segment meets a block, splatting there.
     * goop-and-slick-coat-lands-where-each-drip-arc-strikes
     * goop-and-slick-spray-emanates-from-the-glove
     *
     * @param sprayFrom where the caster's spray source stands, read when the drip is thrown
     * @param launch    the launch velocity the server's drip for its share took, blocks per tick
     * @param meets     the living the drip splats on when it meets one, as the server's drip strikes them
     */
    public void flyArcFrom(Source sprayFrom, Vec3 launch, DripArc.LivingNear meets) {
        this.source = sprayFrom;
        this.drip = new SprayDrip(rgb, launch);
        this.living = meets;
    }

    /** Throws the drip from its source on its first tick, flies it on, and splats it at its end. */
    @Override
    public void tick() {
        if (!thrown) {
            throwFromTheSource();
        }
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.age++;
        SprayDrip.Step step = drip.step(new Vec3(this.x, this.y, this.z), new Vec3(this.xd, this.yd, this.zd),
                CoatRay.clipping(this.level), living, DripFlights.airspaceOf(this.level));
        this.setPos(step.position().x, step.position().y, step.position().z);
        this.xd = step.velocity().x;
        this.yd = step.velocity().y;
        this.zd = step.velocity().z;
        if (step.lost()) {
            this.remove();
        } else if (step.splats()) {
            this.remove();
            this.level.addParticle(ColorParticleOption.create(GooParticles.TRAIL_DRIP_LAND.get(),
                    this.rCol, this.gCol, this.bCol), this.x, this.y, this.z, 0.0, 0.0, 0.0);
        }
    }


    /** Throws the drip from its source as the last frame drew it, which the tick's start reads at partial 0. */
    private void throwFromTheSource() {
        Vec3 from = source.at(0f);
        this.setPos(from.x, from.y, from.z);
        this.xd = drip.velocity().x;
        this.yd = drip.velocity().y;
        this.zd = drip.velocity().z;
        this.thrown = true;
    }

    /**
     * Draws the drip where its flight has carried it, and nothing before its
     * first tick throws it, while the lifted palm blob alone stands at the source.
     * palm-blob-lifts-as-the-spray-source
     *
     * @param reusedState the reusable render state for quad particles
     * @param camera      the active camera
     * @param rotation    the camera-facing rotation
     * @param partialTick the partial tick for interpolation
     */
    @Override
    protected void extractRotatedQuad(QuadParticleRenderState reusedState, Camera camera, Quaternionf rotation,
                                      float partialTick) {
        if (!thrown) {
            return;
        }
        Vec3 at = new Vec3(Mth.lerp(partialTick, this.xo, this.x), Mth.lerp(partialTick, this.yo, this.y),
                Mth.lerp(partialTick, this.zo, this.z));
        Vec3 cameraPos = camera.position();
        this.extractRotatedQuad(reusedState, rotation, (float) (at.x - cameraPos.x), (float) (at.y - cameraPos.y),
                (float) (at.z - cameraPos.z), partialTick);
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
            return new SprayDripParticle(level, new Vec3(x, y, z), sprites.get(0, 1),
                    SprayDrip.launch(new Vec3(xSpeed, ySpeed, zSpeed), palette, random));
        }
    }
}
