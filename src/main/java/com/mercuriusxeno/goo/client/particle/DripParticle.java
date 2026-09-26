package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.DripFall;
import com.mercuriusxeno.goo.client.GooRenderUtil;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.renderer.state.level.QuadParticleRenderState;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.joml.Quaternionf;
import org.jspecify.annotations.Nullable;

/**
 * Blocky slime drip particle shared by the trail-drip and the tap-drip.
 * Modeled after vanilla's lava/water drip particles - falls under gravity,
 * splats on ground contact. Spawned directly into the fall phase (no hang
 * phase). Each drip's provider names its look, its layer and the splat its
 * fall spawns.
 */
public abstract class DripParticle extends SingleQuadParticle {

    /** Gravity shared with the server's drip arrival timing. */
    private static final float DRIP_GRAVITY = (float) DripFall.GRAVITY;

    /** Initial particle size for drip collision box. */
    private static final float DRIP_SIZE = 0.01f;

    /** Drag per tick shared with the server's drip arrival timing. */
    private static final float DRAG = (float) DripFall.DRAG;

    /** Lifetime divisor for randomized particle duration. */
    private static final double LIFETIME_DIVISOR = 64.0;

    /** Minimum lifetime random factor. */
    private static final double LIFETIME_MIN_FACTOR = 0.2;

    /** Lifetime random range. */
    private static final double LIFETIME_RANGE = 0.8;

    /** Scale factor for land splat quad size. */
    private static final float LAND_QUAD_SCALE = 1.2f;

    /** Land splat lifetime divisor. */
    private static final double LAND_LIFETIME_DIVISOR = 10.0;

    private final Layer layer;
    private final GooRenderUtil.UvRect uv;

    /**
     * Creates a goo drip drawing the given look on the given layer.
     *
     * @param level the client level
     * @param x     the X spawn position
     * @param y     the Y spawn position
     * @param z     the Z spawn position
     * @param look  the sprite, UVs and color the quad draws
     * @param layer the particle layer, bound to the look's atlas
     */
    private DripParticle(ClientLevel level, double x, double y, double z, DripLook look, Layer layer) {
        super(level, x, y, z, look.sprite());
        this.setSize(DRIP_SIZE, DRIP_SIZE);
        this.gravity = DRIP_GRAVITY;
        this.layer = layer;
        this.uv = look.uv();
        this.rCol = ARGB.redFloat(look.rgb());
        this.gCol = ARGB.greenFloat(look.rgb());
        this.bCol = ARGB.blueFloat(look.rgb());
    }

    @Override
    public Layer getLayer() {
        return layer;
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

    /** Applies gravity, movement, drag, and delegates to pre/post move hooks. */
    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        this.preMoveUpdate();
        if (!this.removed) {
            applyPhysics();
        }
    }

    /** Applies gravity, moves the particle, runs post-move hooks, and damps velocity. */
    private void applyPhysics() {
        this.yd -= this.gravity;
        this.move(this.xd, this.yd, this.zd);
        this.postMoveUpdate();
        if (!this.removed) {
            this.xd *= DRAG;
            this.yd *= DRAG;
            this.zd *= DRAG;
        }
    }

    /** Counts down lifetime; subclasses override for phase transitions. */
    protected void preMoveUpdate() {
        this.lifetime--;
        if (this.lifetime <= 0) {
            this.remove();
        }
    }

    /** Ground-contact behavior after each move. */
    protected abstract void postMoveUpdate();

    /**
     * The falling drip - falls under gravity, spawns its land splat on ground contact.
     */
    private static final class FallParticle extends DripParticle {

        private final ParticleOptions landOption;

        FallParticle(ClientLevel level, double x, double y, double z, Vec3 velocity,
                DripLook look, Layer layer, ParticleOptions landOption) {
            super(level, x, y, z, look, layer);
            this.landOption = landOption;
            this.xd = velocity.x;
            this.yd = velocity.y;
            this.zd = velocity.z;
            this.lifetime = (int) (LIFETIME_DIVISOR / (level.getRandom().nextFloat() * LIFETIME_RANGE + LIFETIME_MIN_FACTOR));
        }

        /**
         * Draws the camera-facing quad lifted clear of the surface its
         * collision box lands on (decision diagnose-then-fix-drip-z-fighting).
         *
         * @param reusedState the reusable render state for quad particles
         * @param camera      the active camera for view transform
         * @param rotation    the camera-facing rotation
         * @param partialTick the partial tick for interpolation
         */
        @Override
        protected void extractRotatedQuad(QuadParticleRenderState reusedState, Camera camera,
                Quaternionf rotation, float partialTick) {
            Vec3 cameraPos = camera.position();
            double particleY = Mth.lerp(partialTick, this.yo, this.y);
            double quadY = DripQuadPlacement.fallQuadCenterY(particleY, this.getQuadSize(partialTick));
            this.extractRotatedQuad(reusedState, rotation,
                    (float) (Mth.lerp(partialTick, this.xo, this.x) - cameraPos.x()),
                    (float) (quadY - cameraPos.y()),
                    (float) (Mth.lerp(partialTick, this.zo, this.z) - cameraPos.z()),
                    partialTick);
        }

        /** Spawns a landing splat on ground contact. */
        @Override
        protected void postMoveUpdate() {
            if (this.onGround) {
                this.remove();
                this.level.addParticle(landOption, this.x, this.y, this.z, 0.0, 0.0, 0.0);
            }
        }
    }

    /**
     * Ground splat that lies flat and spreads out while fading.
     * Overrides the billboard orientation so the quad faces upward,
     * giving the visual impression of a drip flattening on impact.
     */
    private static final class LandParticle extends DripParticle {

        /** Quaternion that lays the quad flat on the XZ plane (normal facing +Y). */
        private static final Quaternionf FLAT_ROTATION =
                new Quaternionf().rotateX((float) (-Math.PI / 2.0));

        private final int maxLifetime;

        LandParticle(ClientLevel level, double x, double y, double z, DripLook look, Layer layer) {
            super(level, x, y, z, look, layer);
            this.y = DripQuadPlacement.landQuadY(this.y);
            this.yo = this.y;
            this.quadSize *= LAND_QUAD_SCALE;
            this.lifetime = (int) (LAND_LIFETIME_DIVISOR / (level.getRandom().nextFloat() * LIFETIME_RANGE + LIFETIME_MIN_FACTOR));
            this.maxLifetime = this.lifetime;
            this.gravity = 0.0f;
        }

        /** A splat already lies on the ground, so contact changes nothing. */
        @Override
        protected void postMoveUpdate() {
            // Contact is the splat's resting state.
        }

        /**
         * Renders as a flat, ground-facing quad instead of a camera billboard.
         *
         * @param reusedState the reusable render state for quad particles
         * @param camera the active camera for view transform
         * @param partialTick the partial tick for interpolation
         */
        @Override
        public void extract(QuadParticleRenderState reusedState, Camera camera,
                float partialTick) {
            this.extractRotatedQuad(reusedState, camera,
                    new Quaternionf(FLAT_ROTATION), partialTick);
        }

        /**
         * Grows wider over lifetime to simulate the drip spreading on impact.
         *
         * @param partialTick the partial tick for interpolation
         * @return the scaled quad size for this frame
         */
        @Override
        public float getQuadSize(float partialTick) {
            float progress = 1.0f - (float) this.lifetime / this.maxLifetime;
            return this.quadSize * (1.0f + progress * 1.0f);
        }

        /** Fades out as the splat spreads. */
        @Override
        protected void preMoveUpdate() {
            this.alpha = (float) this.lifetime / this.maxLifetime;
            super.preMoveUpdate();
        }
    }

    /**
     * Provider for a falling drip: the subclass names the look an option
     * draws, the layer it draws on and the splat it lands as.
     *
     * @param <T> the particle option the drip is sent with
     */
    public abstract static class FallProvider<T extends ParticleOptions> implements ParticleProvider<T> {

        /**
         * @param options the option the drip was sent with
         * @param random  the random source
         * @return the sprite, UVs and color the drip draws
         */
        protected abstract DripLook look(T options, RandomSource random);

        /**
         * @return the particle layer bound to the look's atlas
         */
        protected abstract Layer layer();

        /**
         * @param options the option the drip was sent with
         * @return the splat option the drip spawns on ground contact
         */
        protected abstract ParticleOptions landOption(T options);

        /**
         * Creates a falling goo drip particle.
         *
         * @param options the option the drip was sent with
         * @param level the client level to spawn in
         * @param x the x spawn coordinate
         * @param y the y spawn coordinate
         * @param z the z spawn coordinate
         * @param xSpeed the x velocity for the falling drip
         * @param ySpeed the y velocity for the falling drip
         * @param zSpeed the z velocity for the falling drip
         * @param random the random source
         * @return the new falling drip particle
         */
        @Override
        public @Nullable Particle createParticle(
                T options, ClientLevel level,
                double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed,
                RandomSource random) {
            return new FallParticle(level, x, y, z, new Vec3(xSpeed, ySpeed, zSpeed),
                    look(options, random), layer(), landOption(options));
        }
    }

    /**
     * Provider for the ground splat spawned by a falling drip on impact: the
     * subclass names the look an option draws and the layer it draws on.
     *
     * @param <T> the particle option the splat is spawned with
     */
    public abstract static class LandProvider<T extends ParticleOptions> implements ParticleProvider<T> {

        /**
         * @param options the option the splat was spawned with
         * @param random  the random source
         * @return the sprite, UVs and color the splat draws
         */
        protected abstract DripLook look(T options, RandomSource random);

        /**
         * @return the particle layer bound to the look's atlas
         */
        protected abstract Layer layer();

        /**
         * Creates a ground splat particle.
         *
         * @param options the option the splat was spawned with
         * @param level the client level to spawn in
         * @param x the x spawn coordinate
         * @param y the y spawn coordinate
         * @param z the z spawn coordinate
         * @param xSpeed the x velocity (unused for land splats)
         * @param ySpeed the y velocity (unused for land splats)
         * @param zSpeed the z velocity (unused for land splats)
         * @param random the random source
         * @return the new land splat particle
         */
        @Override
        public @Nullable Particle createParticle(
                T options, ClientLevel level,
                double x, double y, double z,
                double xSpeed, double ySpeed, double zSpeed,
                RandomSource random) {
            return new LandParticle(level, x, y, z, look(options, random), layer());
        }
    }
}
