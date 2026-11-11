package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.client.GooRenderTypes;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * One ring of Deep Click's sound wave: the sculk shrieker's shriek ring, held
 * square to the wave's line of travel rather than lying flat and rising as a
 * shrieker's does, so a wave seen down its line reads as rings face on. It
 * stays where it was set, swelling a little and fading out. The speed it is
 * spawned with names the line it faces along, not a motion.
 * click-is-a-hit-scan-drawn-as-a-delayed-wave
 */
public final class ClickWaveParticle extends SingleQuadParticle {

    private static final int LIFETIME = 10;
    private static final float START_SIZE = 0.35f;
    private static final float SWELL = 0.35f;
    private static final float HALF_TURN = (float) Math.PI;
    /** The axis a quad faces before it is turned. */
    private static final Vec3 QUAD_NORMAL = new Vec3(0, 0, 1);
    /** The translucent particle layer, drawn on the pipeline that passes every depth test. */
    private static final Layer THROUGH_BLOCKS = new Layer(true, Layer.TRANSLUCENT.textureAtlasLocation(),
            GooRenderTypes.CLICK_WAVE_THROUGH_BLOCKS);

    private final Quaternionf facing;

    private ClickWaveParticle(ClientLevel level, double x, double y, double z, Vec3 along, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(0, 1));
        this.xd = 0;
        this.yd = 0;
        this.zd = 0;
        this.gravity = 0f;
        this.hasPhysics = false;
        this.lifetime = LIFETIME;
        this.quadSize = START_SIZE;
        this.facing = facingAlong(along);
    }

    /**
     * The turn that sets a quad square to a line.
     *
     * @param along the line, any length but zero
     * @return the turn from the quad's own facing to the line
     */
    static Quaternionf facingAlong(Vec3 along) {
        if (along.lengthSqr() == 0) {
            return new Quaternionf();
        }
        Vec3 unit = along.normalize();
        return new Quaternionf().rotationTo((float) QUAD_NORMAL.x, (float) QUAD_NORMAL.y, (float) QUAD_NORMAL.z,
                (float) unit.x, (float) unit.y, (float) unit.z);
    }

    @Override
    public float getQuadSize(float partialTick) {
        return START_SIZE + SWELL * Math.min(1f, (age + partialTick) / lifetime);
    }

    /** Draws the ring from both sides, so it shows whichever way it is seen from. */
    @Override
    public void extract(QuadParticleRenderState state, Camera camera, float partialTick) {
        this.alpha = 1f - Math.min(1f, (age + partialTick) / lifetime);
        extractRotatedQuad(state, camera, new Quaternionf(facing), partialTick);
        extractRotatedQuad(state, camera, new Quaternionf(facing).rotateY(HALF_TURN), partialTick);
    }

    /**
     * Draws through blocks: a submerged Click's rings stand inside the stone
     * the diver swims in, where the depth test would hide every one of them
     * (decision submerged-click-ripples-through-the-ground-to-its-range).
     */
    @Override
    public Layer getLayer() {
        return THROUGH_BLOCKS;
    }

    /**
     * Makes rings square to the line their spawn speed names.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        /**
         * Creates a provider over the shriek sprite.
         *
         * @param sprites the sprite set
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable ClickWaveParticle createParticle(SimpleParticleType type, ClientLevel level, double x,
                                                          double y, double z, double xSpeed, double ySpeed,
                                                          double zSpeed, RandomSource random) {
            return new ClickWaveParticle(level, x, y, z, new Vec3(xSpeed, ySpeed, zSpeed), sprites);
        }
    }
}
