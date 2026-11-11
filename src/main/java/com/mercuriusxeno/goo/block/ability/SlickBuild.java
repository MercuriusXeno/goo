package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.SlickStep;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec3;
import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * Slick's build: a thing trying to move on the slick is ramped each tick by
 * a ramp that grows the longer it keeps trying, and a thing not trying, an
 * item, a projectile or a player who let go of the keys, is left to slide on
 * the film unmultiplied. The build follows no heading, since the ramp
 * multiplies whatever the velocity is, so a turn keeps it. A thing trying to
 * move that steps on from other ground slower than the start speed is raised
 * to it that tick.
 * slick-coats-the-ground-with-kinetic-speed
 * slick-ramp-grows-while-a-thing-keeps-moving
 * slick-raises-a-slow-entry-to-a-start-speed
 */
public final class SlickBuild {

    /**
     * Ticks without trying on the slick after which a slider's build starts
     * afresh, one second: a hop across the slick keeps the build, a stop
     * and a later start does not.
     */
    static final long FORGET_TICKS = 20;

    /**
     * Each slider's build, the client's apart from the server's: in
     * singleplayer a player's client and server twins share an entity id,
     * which Entity.equals compares, so one shared memory would count both
     * twins' ticks into one build. Each is synchronized, the client and the
     * integrated server sliding on their own threads.
     */
    private static final Map<Object, Build> CLIENT_BUILDS = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<Object, Build> SERVER_BUILDS = Collections.synchronizedMap(new WeakHashMap<>());

    /** The game time a slider that never tried last tried at, far enough back to have been forgotten. */
    private static final long NEVER_TRIED = -FORGET_TICKS - 1;

    /**
     * A slider's build, and when it last stood on the slick, which tells a
     * tick stepping on from other ground from one sliding on.
     *
     * @param tried         the ticks it has kept trying
     * @param lastTriedAt   the game time of the last tick it tried
     * @param lastOnSlickAt the game time of the last tick it stood on the slick
     */
    private record Build(int tried, long lastTriedAt, long lastOnSlickAt) {
    }

    /**
     * What slides on the slick: whether it tries to move, and the velocity
     * the slick ramps. An entity is one; a test fakes one.
     */
    interface Slider {

        /**
         * @return what the slider is, the key its build is remembered by
         */
        Object identity();

        /**
         * @return whether it slides on the client
         */
        boolean clientSide();

        /**
         * @return whether it is trying to move
         */
        boolean tryingToMove();

        /**
         * @return the game time it slides at
         */
        long gameTime();

        /**
         * @return its velocity, in blocks a tick
         */
        Vec3 velocity();

        /**
         * Sets its velocity.
         *
         * @param velocity the velocity the slick hands back
         */
        void slide(Vec3 velocity);
    }

    private SlickBuild() {
    }

    /**
     * Ramps an entity on the slick where it tries to move.
     *
     * @param entity the entity standing on the slick
     * @param slick  the slick step naming the ramp
     */
    static void slide(Entity entity, SlickStep slick) {
        slide(new EntitySlider(entity), slick);
    }

    /**
     * Raises a slider trying to move that steps on slowly, else ramps it by
     * its build's ramp, and grows the build; a slider not trying keeps its
     * velocity. Every tick on the slick is remembered, so stepping from one
     * slick block to the next, or starting to move where it stood still on
     * the slick, is no entry.
     *
     * @param slider the slider
     * @param slick  the slick step naming the ramp and the start speed
     */
    static void slide(Slider slider, SlickStep slick) {
        Map<Object, Build> builds = slider.clientSide() ? CLIENT_BUILDS : SERVER_BUILDS;
        Build before = builds.get(slider.identity());
        long now = slider.gameTime();
        if (before != null && before.lastOnSlickAt() == now) {
            // a second step on the slick within one tick ramps nothing more
            return;
        }
        boolean trying = slider.tryingToMove();
        int tried = triedBefore(before, now);
        slider.slide(SlickRamp.slid(slider.velocity(), stoodOnSlickBefore(before, now), trying, tried, slick));
        builds.put(slider.identity(),
                trying ? new Build(tried + 1, now, now) : new Build(tried, lastTriedAt(before), now));
    }

    /**
     * @param before the slider's build, null where it has none
     * @param now    the game time it slides at
     * @return whether it stood on the slick the tick before
     */
    private static boolean stoodOnSlickBefore(Build before, long now) {
        return before != null && before.lastOnSlickAt() == now - 1;
    }

    /**
     * @param before the slider's build, null where it has none
     * @return the game time it last tried at
     */
    private static long lastTriedAt(Build before) {
        return before == null ? NEVER_TRIED : before.lastTriedAt();
    }

    /**
     * The ticks a slider's build holds, none where it has none or has paused past FORGET_TICKS.
     *
     * @param before the slider's build, null where it has none
     * @param now    the game time it slides at
     * @return the ticks it has kept trying
     */
    private static int triedBefore(Build before, long now) {
        return before == null || now - before.lastTriedAt() > FORGET_TICKS ? 0 : before.tried();
    }

    /**
     * Whether an entity is trying to move: a server's player by the movement
     * keys its client last sent, since the server never runs a player's
     * input; a mob, and the client's own player, by the movement driving it
     * this tick. An entity that is no LivingEntity never tries.
     *
     * @param entity the entity
     * @return whether it tries to move
     */
    static boolean tryingToMove(Entity entity) {
        if (entity instanceof ServerPlayer player) {
            return holdsAMovementKey(player.getLastClientInput());
        }
        return entity instanceof LivingEntity living && (living.xxa != 0 || living.zza != 0);
    }

    /**
     * @param keys the keys a client last sent
     * @return whether they hold forward, back, left or right
     */
    private static boolean holdsAMovementKey(Input keys) {
        return keys.forward() || keys.backward() || keys.left() || keys.right();
    }

    /**
     * An entity on the slick.
     *
     * @param entity the entity
     */
    private record EntitySlider(Entity entity) implements Slider {

        @Override
        public Object identity() {
            return entity;
        }

        @Override
        public boolean clientSide() {
            return entity.level().isClientSide();
        }

        @Override
        public boolean tryingToMove() {
            return SlickBuild.tryingToMove(entity);
        }

        @Override
        public long gameTime() {
            return entity.level().getGameTime();
        }

        @Override
        public Vec3 velocity() {
            return entity.getDeltaMovement();
        }

        @Override
        public void slide(Vec3 velocity) {
            entity.setDeltaMovement(velocity);
        }
    }
}
