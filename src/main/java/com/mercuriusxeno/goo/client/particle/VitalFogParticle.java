package com.mercuriusxeno.goo.client.particle;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;

/**
 * A soft puff of jelly fog: a wide, faint radial glow that drifts with the
 * velocity it was given, swelling as it goes and fading in then out, so many
 * of them overlapping read as one fog rather than as dots. Jelly heal fills
 * its cone with them while held.
 * nourish-and-healing-ship-on-jelly
 */
public final class VitalFogParticle extends DriftingGlowParticle {

    /** The share a puff swells by over its life. */
    private static final float SWELL = 1.5f;
    /** Peak opacity, faint so overlapping puffs build to a fog. */
    private static final float PEAK_ALPHA = 0.12f;
    /** The share of its speed a puff keeps each tick. */
    public static final float FRICTION = 0.92f;
    private static final int BASE_LIFETIME = 18;
    private static final int LIFETIME_VARIANCE = 10;
    /** The most ticks a puff lives. */
    public static final int LONGEST_LIFE = BASE_LIFETIME + LIFETIME_VARIANCE - 1;
    /** A wide puff in jelly amber, opening unseen and fading in. */
    private static final Look PUFF = new Look(FRICTION, 0.01f, BASE_LIFETIME, LIFETIME_VARIANCE, 0.35f, 0.2f,
            new float[] {1.0f, 0.7f, 0.3f}, 0f);

    private final Vec3 spawn;
    private final Vec3 launch;

    private VitalFogParticle(ClientLevel level, double[] position, double[] velocity, SpriteSet sprites) {
        super(level, position, velocity, sprites, PUFF);
        this.spawn = new Vec3(this.x, this.y, this.z);
        this.launch = new Vec3(this.xd, this.yd, this.zd);
    }

    /**
     * Where a puff stands a number of ticks after it spawned: carried by its launch speed, which the fog's friction
     * sheds each tick, so its whole drift stays short of its launch speed over one less the friction.
     *
     * @param spawn    where it spawned
     * @param velocity its launch speed a tick
     * @param ticks    ticks since it spawned
     * @return where it stands
     */
    public static Vec3 driftedTo(Vec3 spawn, Vec3 velocity, int ticks) {
        return spawn.add(velocity.scale((1 - Math.pow(FRICTION, ticks)) / (1 - FRICTION)));
    }

    /** Drifts the puff and fades it in over the first half of its life and out over the second. */
    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        Vec3 at = driftedTo(spawn, launch, age);
        this.setPos(at.x, at.y, at.z);
        float progress = lifeProgress();
        this.alpha = PEAK_ALPHA * (float) Math.sin(Math.PI * Math.min(1f, progress));
    }

    /**
     * Swells over its life.
     *
     * @param partialTick the partial tick for interpolation
     * @return the scaled quad size for this frame
     */
    @Override
    public float getQuadSize(float partialTick) {
        float progress = Math.min(1f, (this.age + partialTick) / this.lifetime);
        return this.quadSize * (1f + SWELL * progress);
    }

    /**
     * Creates fog puffs, keeping the velocity the spawner gave each one.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        /**
         * Creates a provider over the fog's sprite set.
         *
         * @param sprites the sprite set from vital_fog.json
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable VitalFogParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                         double x, double y, double z,
                                                         double xSpeed, double ySpeed, double zSpeed,
                                                         RandomSource random) {
            return new VitalFogParticle(level, new double[] {x, y, z}, new double[] {xSpeed, ySpeed, zSpeed},
                    sprites);
        }
    }
}
