package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.client.GooSubmitter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.function.Supplier;

/**
 * A mote of vital goo that homes: it bends out from where it spawned and
 * lands on a target read every tick, then winks out. Reserve draws the
 * player's life from the chest into the glove with it.
 * reserve-hearts-sit-behind-the-bar
 */
public final class VitalMoteParticle extends SingleQuadParticle {

    private static final float COLLISION_SIZE = 0.02f;
    private static final int BASE_LIFETIME = 10;
    private static final int LIFETIME_VARIANCE = 5;
    private static final float BASE_QUAD_SIZE = 0.05f;
    private static final float QUAD_SIZE_VARIANCE = 0.03f;
    private static final float ALPHA = 0.9f;
    /**
     * Jelly goo's bright amber, lighter than its wheel color so the mote reads as food.
     * nourish-and-healing-ship-on-jelly
     */
    private static final float RED = 1.0f;
    private static final float GREEN = 0.7f;
    private static final float BLUE = 0.22f;

    private HomingPath path;
    private Supplier<Vec3> target;

    private VitalMoteParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(0, 1));
        Vec3 spawn = new Vec3(x, y, z);
        this.path = new HomingPath(spawn, spawn);
        this.target = () -> spawn;
        this.setSize(COLLISION_SIZE, COLLISION_SIZE);
        this.gravity = 0f;
        this.hasPhysics = false;
        RandomSource random = level.getRandom();
        this.lifetime = BASE_LIFETIME + random.nextInt(LIFETIME_VARIANCE);
        this.quadSize = BASE_QUAD_SIZE + random.nextFloat() * QUAD_SIZE_VARIANCE;
        this.rCol = RED;
        this.gCol = GREEN;
        this.bCol = BLUE;
        this.alpha = ALPHA;
    }

    /**
     * Sends the mote home: it bends out through the control point and lands
     * on the target, which it reads again each tick.
     *
     * @param control the point the flight bends out through
     * @param home    where the mote lands
     */
    public void homeTo(Vec3 control, Supplier<Vec3> home) {
        this.path = new HomingPath(path.start(), control);
        this.target = home;
    }

    /** Flies one tick further along the curve, and ends on landing. */
    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        Vec3 at = path.at(target.get(), (double) this.age / this.lifetime);
        this.setPos(at.x, at.y, at.z);
    }

    /**
     * Motes blend over what lies behind them.
     *
     * @return the translucent particle render layer
     */
    @Override
    public Layer getLayer() {
        return Layer.TRANSLUCENT;
    }

    /**
     * Glows at full brightness whatever the light where it flies.
     *
     * @param partialTick the partial tick
     * @return full block and sky light
     */
    @Override
    protected int getLightCoords(float partialTick) {
        return GooSubmitter.fullbrightLight();
    }

    /**
     * Creates vital motes; the spawner sends each one home after creating it.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        /**
         * Creates a provider over the mote's sprite set.
         *
         * @param sprites the sprite set from vital_mote.json
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable VitalMoteParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                          double x, double y, double z,
                                                          double xSpeed, double ySpeed, double zSpeed,
                                                          RandomSource random) {
            return new VitalMoteParticle(level, x, y, z, sprites);
        }
    }
}
