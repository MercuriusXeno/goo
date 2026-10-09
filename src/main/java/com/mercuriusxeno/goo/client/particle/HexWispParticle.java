package com.mercuriusxeno.goo.client.particle;

import com.mercuriusxeno.goo.client.GooSubmitter;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.SingleQuadParticle;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.function.Supplier;

/**
 * A wisp of drawn life in hex purple: after its delay it scatters from where
 * it spawned, darting and slowing as the nether's gnats do, then curls toward
 * a target it reads again every tick along a bent path, swaying across it,
 * its sway dying out as it lands. Each wisp draws its own shade, size, pace
 * and sway, so a cloud of them never moves as one.
 * lifetap-trades-regen-for-leech
 * drain-field-heals-with-the-lifetap-visuals
 */
public final class HexWispParticle extends SingleQuadParticle {

    private static final float COLLISION_SIZE = 0.02f;
    private static final float BASE_QUAD_SIZE = 0.035f;
    private static final float QUAD_SIZE_VARIANCE = 0.045f;
    private static final float ALPHA = 0.9f;
    /** Ticks a wisp takes to fade in once it shows. */
    private static final float FADE_IN_TICKS = 3f;
    /** The share of the flight the wisp fades out over as it lands. */
    private static final float FADE_OUT_SHARE = 0.25f;
    /** Each tick of the scatter keeps this share of its speed. */
    private static final double SCATTER_DRAG = 0.78;
    /** How far a dart may change the wisp's speed each tick, on each axis. */
    private static final double DART_SPAN = 0.03;
    /** The darkest and brightest hex purple a wisp may take. */
    private static final float[] DEEP = {0.36f, 0.10f, 0.62f};
    private static final float[] BRIGHT = {0.78f, 0.52f, 0.98f};
    /** How far the flight's bend may reach off the straight line, in blocks. */
    private static final double BEND_REACH = 0.9;
    /** How high the bend lifts, at most, in blocks. */
    private static final double BEND_LIFT = 0.7;
    private static final double BASE_SWAY = 0.12;
    private static final double SWAY_VARIANCE = 0.18;
    private static final double BASE_SWAYS = 1.0;
    private static final double SWAYS_VARIANCE = 2.0;
    private static final double FULL_TURN = Math.PI * 2;
    private static final double HALF = 0.5;
    private static final int RED = 0;
    private static final int GREEN = 1;
    private static final int BLUE = 2;
    private static final double BOTH_WAYS = 2;
    /** Below this squared length a heading has no direction to sway across. */
    private static final double NO_HEADING = 1e-6;

    private final RandomSource random;
    private final double swayPhase;
    private final double swayReach;
    private final double sways;
    private int delayTicks;
    private int scatterTicks;
    private int flightTicks;
    private Supplier<Vec3> target;
    private @Nullable HomingPath path;
    private int flightAge;

    private HexWispParticle(ClientLevel level, double x, double y, double z, SpriteSet sprites) {
        super(level, x, y, z, sprites.get(0, 1));
        Vec3 spawn = new Vec3(x, y, z);
        this.target = () -> spawn;
        this.setSize(COLLISION_SIZE, COLLISION_SIZE);
        this.gravity = 0f;
        this.hasPhysics = false;
        this.random = level.getRandom();
        this.quadSize = BASE_QUAD_SIZE + random.nextFloat() * QUAD_SIZE_VARIANCE;
        float shade = random.nextFloat();
        this.rCol = Mth.lerp(shade, DEEP[RED], BRIGHT[RED]);
        this.gCol = Mth.lerp(shade, DEEP[GREEN], BRIGHT[GREEN]);
        this.bCol = Mth.lerp(shade, DEEP[BLUE], BRIGHT[BLUE]);
        this.alpha = 0f;
        this.swayPhase = random.nextDouble() * FULL_TURN;
        this.swayReach = BASE_SWAY + random.nextDouble() * SWAY_VARIANCE;
        this.sways = BASE_SWAYS + random.nextDouble() * SWAYS_VARIANCE;
        this.lifetime = Integer.MAX_VALUE;
    }

    /**
     * Sends the wisp off: it waits out its delay unseen, scatters with the
     * velocity given for its scatter ticks, then flies home to the target
     * over its flight ticks.
     *
     * @param delay       ticks it waits unseen before it shows
     * @param scatter     the velocity it scatters with, blocks per tick
     * @param scatterFor  ticks it scatters before it turns home
     * @param flightFor   ticks its flight home takes
     * @param home        where it lands, read every tick
     */
    public void launch(int delay, Vec3 scatter, int scatterFor, int flightFor, Supplier<Vec3> home) {
        this.delayTicks = delay;
        this.scatterTicks = scatterFor;
        this.flightTicks = Math.max(1, flightFor);
        this.target = home;
        this.xd = scatter.x;
        this.yd = scatter.y;
        this.zd = scatter.z;
        this.lifetime = delay + scatterFor + this.flightTicks;
    }

    @Override
    public void tick() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
            return;
        }
        if (this.age <= delayTicks) {
            return;
        }
        int shown = this.age - delayTicks;
        if (shown <= scatterTicks) {
            scatterTick();
        } else {
            flyHomeTick();
        }
        this.alpha = ALPHA * Math.min(1f, shown / FADE_IN_TICKS) * fadeOut();
    }

    private void scatterTick() {
        this.xd = this.xd * SCATTER_DRAG + dart();
        this.yd = this.yd * SCATTER_DRAG + dart();
        this.zd = this.zd * SCATTER_DRAG + dart();
        this.setPos(this.x + this.xd, this.y + this.yd, this.z + this.zd);
    }

    private double dart() {
        return (random.nextDouble() - HALF) * DART_SPAN;
    }

    private void flyHomeTick() {
        Vec3 here = new Vec3(this.x, this.y, this.z);
        Vec3 home = target.get();
        if (path == null) {
            path = new HomingPath(here, bendPoint(here, home));
        }
        flightAge++;
        double progress = Math.min(1.0, (double) flightAge / flightTicks);
        Vec3 along = path.at(home, progress);
        Vec3 across = acrossOf(home.subtract(path.start()));
        double sway = Math.sin(progress * Math.PI * sways + swayPhase) * swayReach * (1 - progress);
        Vec3 at = along.add(across.scale(sway)).add(dart(), dart(), dart());
        this.setPos(at.x, at.y, at.z);
    }

    /**
     * The point a flight bends through: the midpoint lifted and pushed off to
     * a side drawn at random, so no two wisps take one line.
     *
     * @param from where the flight starts
     * @param to   where it lands as the flight starts
     * @return the bend point
     */
    private Vec3 bendPoint(Vec3 from, Vec3 to) {
        Vec3 mid = from.add(to).scale(HALF);
        Vec3 side = acrossOf(to.subtract(from)).scale((random.nextDouble() * BOTH_WAYS - 1) * BEND_REACH);
        return mid.add(side).add(0, random.nextDouble() * BEND_LIFT, 0);
    }

    private static Vec3 acrossOf(Vec3 heading) {
        Vec3 across = heading.cross(new Vec3(0, 1, 0));
        return across.lengthSqr() < NO_HEADING ? new Vec3(1, 0, 0) : across.normalize();
    }

    private float fadeOut() {
        if (path == null) {
            return 1f;
        }
        float progress = (float) flightAge / flightTicks;
        return Mth.clamp((1f - progress) / FADE_OUT_SHARE, 0f, 1f);
    }

    /**
     * Wisps blend over what lies behind them.
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
     * Creates hex wisps; the spawner launches each one after creating it.
     */
    public static class Provider implements ParticleProvider<SimpleParticleType> {

        private final SpriteSet sprites;

        /**
         * Creates a provider over the wisp's sprite set.
         *
         * @param sprites the sprite set from hex_wisp.json
         */
        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        @Override
        public @Nullable HexWispParticle createParticle(SimpleParticleType type, ClientLevel level,
                                                        double x, double y, double z,
                                                        double xSpeed, double ySpeed, double zSpeed,
                                                        RandomSource random) {
            return new HexWispParticle(level, x, y, z, sprites);
        }
    }
}
