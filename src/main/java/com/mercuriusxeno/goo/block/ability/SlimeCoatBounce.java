package com.mercuriusxeno.goo.block.ability;

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
 * ramp up bounce on bounce to a ceiling; without jump held the bounce
 * decays, and sneaking decays it far faster. Each bounce sounds a short boing.
 * bounce-ramps-jump-height-while-jump-is-held
 */
public final class SlimeCoatBounce {

    /** How much higher each held-jump bounce launches than the last or than the fall it caught. */
    static final double RAMP_PER_BOUNCE = 1.25;
    /** The share of its fall a lander keeps on the way back up without jump held. */
    static final double RELEASED_DECAY = 0.7;
    /** The share of its fall a sneaking lander keeps, a far faster decay. */
    static final double SNEAK_DECAY = 0.25;
    /** The fastest launch a ramp reaches, in blocks a tick: a climb of about fourteen blocks. */
    static final double HIGHEST_LAUNCH = 1.8;
    /** A launch below this settles the lander on the coat rather than hopping it. */
    static final double SETTLED_LAUNCH = 0.1;
    /** Ticks without a bounce after which a lander's ramp starts afresh, two seconds. */
    static final long FORGET_TICKS = 40;
    private static final float BOING_VOLUME = 0.6f;
    private static final float BOING_PITCH = 1.0f;
    private static final float BOING_PITCH_PER_LAUNCH = 0.35f;

    /**
     * The last launch each lander took off the coat and when. The client and
     * the integrated server both bounce landers, each on its own thread, so
     * every touch of it is synchronized.
     */
    private static final Map<Object, Launched> LAST_LAUNCH = Collections.synchronizedMap(new WeakHashMap<>());

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
     * How fast a lander leaves the coat.
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
        Launched before = LAST_LAUNCH.get(lander.identity());
        // bounce-ramps-jump-height-while-jump-is-held: a lander back after a pause starts its ramp afresh
        double previous = before == null || lander.gameTime() - before.at() > FORGET_TICKS ? 0 : before.speed();
        double launch = launch(lander.fallSpeed(), previous, lander.jumpHeld(), lander.sneaking());
        lander.launch(launch);
        LAST_LAUNCH.put(lander.identity(), new Launched(launch, lander.gameTime()));
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
