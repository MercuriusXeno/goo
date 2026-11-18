package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.lunar.LunarGravity;
import com.mercuriusxeno.goo.registry.GooSounds;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The slime coat's bounce: a landing with jump held launches the lander
 * higher than it arrived and higher than its bounce before, so held jumps
 * ramp up bounce on bounce to a ceiling reached on the fourth held bounce,
 * fourteen blocks up; without jump held the bounce is near-lossless, a small
 * decay a bounce so it takes a long time to die out, and sneaking dampens it
 * as the neutral bounce once did. Each bounce sounds a short boing. Every
 * entity bounces: players, mobs, items and projectiles. Under a weaker
 * gravity every bounce peaks as much higher as the pull is weaker, the
 * ceiling six times as high in lunar.
 * bounce-ramps-jump-height-while-jump-is-held
 * goop-bounce-height-is-proportionate-to-gravity
 * ramp-tops-out-on-the-fourth-held-bounce-at-fourteen-blocks
 * neutral-bounce-is-near-lossless-and-bounces-every-entity
 * sneak-bounce-dampens-as-the-neutral-bounce-did
 */
public final class SlimeCoatBounce {

    /**
     * How much higher each held-jump bounce launches than the last or than the
     * fall it caught: a standing jump's 0.42 held reaches HIGHEST_LAUNCH on
     * the fourth bounce, the third still short of it.
     * ramp-tops-out-on-the-fourth-held-bounce-at-fourteen-blocks
     */
    static final double RAMP_PER_BOUNCE = 1.5;
    /**
     * The share of its fall a lander keeps on the way back up without jump held:
     * twenty bounces still keep more than half the first.
     * neutral-bounce-is-near-lossless-and-bounces-every-entity
     */
    static final double RELEASED_DECAY = 0.97;
    /**
     * The share of its fall a sneaking lander keeps, the neutral bounce's share
     * before it went near-lossless.
     * sneak-bounce-dampens-as-the-neutral-bounce-did
     */
    static final double SNEAK_DECAY = 0.7;
    /**
     * The fastest launch a ramp reaches, in blocks a tick, set for a peak
     * fourteen blocks over the coat: the launch tick takes gravity's 0.08
     * and the 0.98 drag before the lander first moves, as every tick after
     * it does, so 1.74 climbs about fourteen blocks.
     * ramp-tops-out-on-the-fourth-held-bounce-at-fourteen-blocks
     */
    static final double HIGHEST_LAUNCH = 1.74;
    /** A launch below this settles the lander on the coat rather than hopping it. */
    static final double SETTLED_LAUNCH = 0.1;
    /** Ticks without a bounce after which a lander's ramp starts afresh in the overworld, two seconds. */
    static final long FORGET_TICKS = 40;
    /** The overworld's share of its own gravity, under which the bounce reads its overworld numbers unconverted. */
    private static final double OVERWORLD_SCALE = 1;
    private static final float BOING_VOLUME = 0.6f;
    private static final float BOING_PITCH = 1.0f;
    private static final float BOING_PITCH_PER_LAUNCH = 0.35f;

    /**
     * The last launch each lander took off the coat and when, the client's
     * apart from the server's: in singleplayer a player's client and server
     * twins share an entity id, which Entity.equals compares, and the server
     * never sees jump held, so one shared memory hands the client the
     * server's decayed launch each bounce and stalls the ramp. Each is
     * synchronized, the client and the integrated server bouncing on their
     * own threads.
     */
    private static final Map<Object, Launched> CLIENT_LAUNCHES = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Launched> SERVER_LAUNCHES = Collections.synchronizedMap(new WeakHashMap<>());

    /**
     * A launch off the coat.
     *
     * @param speed the upward speed it left at
     * @param at    the game time it left
     */
    private record Launched(double speed, long at) {
    }

    /**
     * What lands on the coat: how fast it falls, what it holds, and how the
     * bounce reaches it. An entity is one; a test fakes one.
     */
    interface Lander {

        /**
         * @return what the lander is, the key its launches are remembered by
         */
        Object identity();

        /**
         * @return whether it lands on the client
         */
        boolean clientSide();

        /**
         * @return how fast it fell onto the coat, in blocks a tick
         */
        double fallSpeed();

        /**
         * @return whether it holds jump
         */
        boolean jumpHeld();

        /**
         * @return whether it sneaks
         */
        boolean sneaking();

        /**
         * @return the game time it lands at
         */
        long gameTime();

        /**
         * @return the share of the overworld's gravity it falls under
         */
        double gravityScale();

        /**
         * Sends it up off the coat.
         *
         * @param speed the upward speed, 0 where it settles
         */
        void launch(double speed);

        /**
         * Sounds the boing of a bounce.
         *
         * @param launch the upward speed the bounce sent it at
         */
        void boing(double launch);
    }

    private SlimeCoatBounce() {
    }

    /**
     * How fast a lander leaves the coat under a share of the overworld's gravity: the overworld's bounce, taken on
     * the overworld launches the fall and the launch before stand for, and sent up at the launch that climbs that
     * bounce's height over the share, so every bounce, and the ceiling, peaks the share's inverse as high.
     * goop-bounce-height-is-proportionate-to-gravity
     *
     * @param arrivingFall   how fast it fell onto the coat, in blocks a tick
     * @param previousLaunch how fast it left the coat the bounce before, 0 for none
     * @param jumpHeld       whether the lander holds jump
     * @param sneaking       whether the lander sneaks, which decays the bounce even with jump held
     * @param gravityScale   the share of the overworld's gravity the lander falls under
     * @return the upward speed it leaves at, 0 where it settles
     */
    static double launch(double arrivingFall, double previousLaunch, boolean jumpHeld, boolean sneaking,
            double gravityScale) {
        if (gravityScale == OVERWORLD_SCALE) {
            return launch(arrivingFall, previousLaunch, jumpHeld, sneaking);
        }
        double overworldLaunch = launch(LaunchUnderGravity.overworldFallOf(arrivingFall, gravityScale),
                LaunchUnderGravity.overworldLaunchOf(previousLaunch, gravityScale), jumpHeld, sneaking);
        return LaunchUnderGravity.launchIn(overworldLaunch, gravityScale);
    }

    /**
     * The height a ramp tops out at over the coat under a share of the overworld's gravity, the overworld's
     * fourteen blocks over that share.
     * goop-bounce-height-is-proportionate-to-gravity
     *
     * @param gravityScale the share of the overworld's gravity the lander falls under
     * @return the blocks the highest launch climbs
     */
    static double ceilingIn(double gravityScale) {
        return LaunchUnderGravity.peakOf(launch(HIGHEST_LAUNCH, HIGHEST_LAUNCH, true, false, gravityScale),
                gravityScale);
    }

    /**
     * The ticks without a bounce after which a ramp starts afresh under a share of the overworld's gravity: the
     * overworld's two seconds, stretched as a flight to the ceiling stretches, so a lander in flight between two
     * bounces keeps its ramp.
     * goop-bounce-height-is-proportionate-to-gravity
     *
     * @param gravityScale the share of the overworld's gravity the lander falls under
     * @return the ticks
     */
    static long forgetTicksIn(double gravityScale) {
        if (gravityScale == OVERWORLD_SCALE) {
            return FORGET_TICKS;
        }
        double ceilingFlight = LaunchUnderGravity.flightTicksOf(
                LaunchUnderGravity.launchIn(HIGHEST_LAUNCH, gravityScale), gravityScale);
        return Math.round(FORGET_TICKS * ceilingFlight / LaunchUnderGravity.flightTicksOf(HIGHEST_LAUNCH, 1));
    }

    /**
     * How fast a lander leaves the coat in the overworld.
     *
     * @param arrivingFall   how fast it fell onto the coat, in blocks a tick
     * @param previousLaunch how fast it left the coat the bounce before, 0 for none
     * @param jumpHeld       whether the lander holds jump
     * @param sneaking       whether the lander sneaks, which decays the bounce even with jump held
     * @return the upward speed it leaves at, 0 where it settles
     */
    static double launch(double arrivingFall, double previousLaunch, boolean jumpHeld, boolean sneaking) {
        double launch;
        if (sneaking) {
            launch = arrivingFall * SNEAK_DECAY;
        } else if (jumpHeld) {
            launch = Math.min(HIGHEST_LAUNCH, Math.max(arrivingFall, previousLaunch) * RAMP_PER_BOUNCE);
        } else {
            launch = arrivingFall * RELEASED_DECAY;
        }
        return launch < SETTLED_LAUNCH ? 0 : launch;
    }

    /**
     * Bounces an entity that has just landed on the coat, and sounds the boing.
     *
     * @param entity the lander, still moving at the speed it landed with
     */
    static void bounce(Entity entity) {
        bounce(new EntityLander(entity));
    }

    /**
     * Bounces a lander off the coat, ramping off its launch before, and
     * sounds the boing unless it settles.
     *
     * @param lander the lander
     */
    static void bounce(Lander lander) {
        Map<Object, Launched> launches = lander.clientSide() ? CLIENT_LAUNCHES : SERVER_LAUNCHES;
        Launched before = launches.get(lander.identity());
        // bounce-ramps-jump-height-while-jump-is-held: a lander back after a pause starts its ramp afresh
        double gravityScale = lander.gravityScale();
        boolean forgotten = before == null || lander.gameTime() - before.at() > forgetTicksIn(gravityScale);
        double previous = forgotten ? 0 : before.speed();
        double launch = launch(lander.fallSpeed(), previous, lander.jumpHeld(), lander.sneaking(), gravityScale);
        lander.launch(launch);
        launches.put(lander.identity(), new Launched(launch, lander.gameTime()));
        if (launch > 0) {
            lander.boing(launch);
        }
    }

    /**
     * An entity landing on the coat.
     *
     * @param entity the entity
     */
    private record EntityLander(Entity entity) implements Lander {

        @Override
        public Object identity() {
            return entity;
        }

        @Override
        public boolean clientSide() {
            return entity.level().isClientSide();
        }

        @Override
        public double fallSpeed() {
            return -entity.getDeltaMovement().y;
        }

        @Override
        public boolean jumpHeld() {
            return entity instanceof LivingEntity living && living.jumping;
        }

        @Override
        public boolean sneaking() {
            return entity.isShiftKeyDown();
        }

        @Override
        public long gameTime() {
            return entity.level().getGameTime();
        }

        @Override
        public double gravityScale() {
            return LunarGravity.scaleIn(entity.level().dimension());
        }

        @Override
        public void launch(double speed) {
            Vec3 motion = entity.getDeltaMovement();
            entity.setDeltaMovement(motion.x, speed, motion.z);
        }

        @Override
        public void boing(double launch) {
            // the local player hears its own boing; the server plays it to everyone else
            entity.level().playSound(entity instanceof Player player ? player : null, entity.getX(), entity.getY(),
                    entity.getZ(), GooSounds.BOING.get(), SoundSource.BLOCKS, BOING_VOLUME,
                    BOING_PITCH + (float) launch * BOING_PITCH_PER_LAUNCH);
        }
    }
}
