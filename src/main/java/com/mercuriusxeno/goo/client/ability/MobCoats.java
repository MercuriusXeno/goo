package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.jspecify.annotations.Nullable;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * The goo splats standing on struck mobs, keyed by entity id: every hit
 * adds its own splat, which holds whole for the hold, then dissolves; a mob
 * carries at most MAX_SPLATS_PER_MOB, its oldest dropping first.
 * Decision shader-coat-on-every-mob-landing.
 * Decision splat-holds-then-dissolves-dripping.
 */
public final class MobCoats {

    /** Splats one mob carries at most; a hit past it drops the oldest. */
    public static final int MAX_SPLATS_PER_MOB = 8;

    /** Game ticks a splat holds whole after its latest hit, about six seconds. */
    public static final int COAT_HOLD_TICKS = 120;

    /** Game ticks the splat takes to dissolve after its hold, about three seconds. */
    public static final int COAT_DISSOLVE_TICKS = 60;

    /** Game ticks a splat stands in all, from its hit until it has dissolved. */
    public static final int COAT_LIFE_TICKS = COAT_HOLD_TICKS + COAT_DISSOLVE_TICKS;

    /**
     * Game ticks an envelop takes to spread from the mob's middle over its
     * whole body, a quarter second, as the darting blob bursts over it.
     * charm-like-abilities-lock-the-highlighted-mob-on-right-click
     */
    public static final int ENVELOP_SPREAD_TICKS = 5;

    /**
     * The reach in blocks a splat covers whole, its noise never breaking the
     * edge inside it: SPLAT_RADIUS less EDGE_NOISE of it, as goo_mob_coat.fsh names them.
     */
    static final double WHOLE_REACH_BLOCKS = 0.25 * (1 - 0.45);

    /** Half of a length, the box's middle to its corner being half its diagonal. */
    private static final double HALF = 0.5;

    /** The reach scale of a splat, which covers the half block around its hit point. */
    public static final float SPLAT_REACH = 1f;

    /** The client's splats. */
    public static final MobCoats CLIENT = new MobCoats();

    /** Splats by struck entity id, oldest first. */
    private final Map<Integer, Deque<Coat>> coats = new HashMap<>();

    /**
     * One mob's splat.
     */
    public static final class Coat {

        private final ResourceKey<GooTypeDefinition> gooType;
        private final long hitTick;
        private final Vec3 aimDirection;
        private final float reachScale;
        private Vec3 hitPoint;
        private Vec3 hitFromFeet;
        private float bodyYawAtHit;
        private ModelRay.@Nullable PartHit pin;

        /**
         * @param gooType    the goo type it is splatted in
         * @param hitTick    the game tick of its hit
         * @param strike     where and how the goo struck
         * @param reachScale how many times a splat's reach it covers, SPLAT_REACH for a splat
         */
        Coat(ResourceKey<GooTypeDefinition> gooType, long hitTick, Strike strike, float reachScale) {
            this.gooType = gooType;
            this.hitTick = hitTick;
            this.reachScale = reachScale;
            this.aimDirection = strike.aimDirection();
            this.hitPoint = strike.hitPoint();
            this.hitFromFeet = strike.hitPoint().subtract(strike.struck().feet());
            this.bodyYawAtHit = strike.struck().bodyYaw();
        }

        /** @return the aim's unit direction, zero where the strike carried none */
        public Vec3 aimDirection() {
            return aimDirection;
        }

        /**
         * The world point the splat sits on now, carried with the mob from
         * where it stood when hit: the hit's offset from its feet, turned by
         * as far as its body has turned since.
         *
         * @param now where the mob stands now
         * @return the splat's world point
         */
        public Vec3 hitPointOn(Stance now) {
            float turned = now.bodyYaw() - bodyYawAtHit;
            return now.feet().add(hitFromFeet.yRot((float) -Math.toRadians(turned)));
        }

        /**
         * How far the splat has dissolved at an age.
         *
         * @param ageTicks game ticks since its hit, with the partial tick
         * @return 0 through the hold, rising to 1 across the dissolve
         */
        public static float dissolveProgress(float ageTicks) {
            return Math.clamp((ageTicks - COAT_HOLD_TICKS) / COAT_DISSOLVE_TICKS, 0f, 1f);
        }

        /**
         * How far the coat has dissolved at an age: a splat by its dissolve,
         * an envelop besides spreading out from the mob's middle over its
         * first ticks, so it reads as the goo bursting over the mob.
         * charm-like-abilities-lock-the-highlighted-mob-on-right-click
         *
         * @param ageTicks game ticks since its hit, with the partial tick
         * @return 0 where it stands whole, 1 where nothing of it shows
         */
        public float dissolveAt(float ageTicks) {
            float kept = 1f - dissolveProgress(ageTicks);
            float spread = envelops() ? Math.clamp(ageTicks / ENVELOP_SPREAD_TICKS, 0f, 1f) : 1f;
            return 1f - kept * spread;
        }

        /** @return how many times a splat's reach it covers, SPLAT_REACH for a splat */
        public float reachScale() {
            return reachScale;
        }

        /** @return whether it envelops the whole mob rather than splatting around its hit point */
        public boolean envelops() {
            return reachScale > SPLAT_REACH;
        }

        /** @return the goo type it is splatted in */
        public ResourceKey<GooTypeDefinition> gooType() {
            return gooType;
        }

        /** @return the game tick of its hit */
        public long hitTick() {
            return hitTick;
        }

        /** @return the world point the goo struck */
        public Vec3 hitPoint() {
            return hitPoint;
        }

        /**
         * Where the splat is pinned on the mob's model: the part the aim
         * struck and the point in that part's own space, fixed on the splat's
         * first draw so it turns with the part afterwards.
         *
         * @return the pin, or null before the first draw
         */
        ModelRay.@Nullable PartHit pin() {
            return pin;
        }

        /**
         * Pins the splat on the part the aim struck, and moves the world hit
         * point the drips leave from onto it.
         *
         * @param partHit  the struck part and the point in its space
         * @param worldHit the same point in the world
         * @param stance   where the mob stands as it is pinned
         */
        void pin(ModelRay.PartHit partHit, Vec3 worldHit, Stance stance) {
            this.pin = partHit;
            this.hitPoint = worldHit;
            this.hitFromFeet = worldHit.subtract(stance.feet());
            this.bodyYawAtHit = stance.bodyYaw();
        }
    }

    /**
     * Where and how a goo struck a mob.
     *
     * @param hitPoint     the world point the aim entered the mob's box
     * @param aimDirection the aim's unit direction, zero where none
     * @param struck       where the struck mob stood when hit
     */
    public record Strike(Vec3 hitPoint, Vec3 aimDirection, Stance struck) {
    }

    /**
     * Where a mob stands: its feet and its body's yaw in degrees.
     *
     * @param feet    its feet's world position
     * @param bodyYaw its body's yaw in degrees
     */
    public record Stance(Vec3 feet, float bodyYaw) {
    }

    /**
     * Adds a hit's splat to the mob beside those it wears, dropping its
     * oldest past the cap and every splat that has dissolved.
     *
     * @param entityId the struck entity's id
     * @param gooType  the goo type it was struck with
     * @param hitTick  the game tick of the hit
     * @param strike   where and how the goo struck
     */
    public void coat(int entityId, ResourceKey<GooTypeDefinition> gooType, long hitTick, Strike strike) {
        add(entityId, new Coat(gooType, hitTick, strike, SPLAT_REACH));
    }

    /**
     * Envelops a mob whole in the goo, the coat struck at its middle and
     * reaching every corner of its box, beside the splats it wears.
     * charm-like-abilities-lock-the-highlighted-mob-on-right-click
     *
     * @param entityId the enveloped entity's id
     * @param gooType  the goo type it is enveloped in
     * @param hitTick  the game tick the dart landed
     * @param middle   the mob's middle, where the coat is struck, carrying no aim
     * @param width    the mob's box width in blocks
     * @param height   the mob's box height in blocks
     */
    public void envelop(int entityId, ResourceKey<GooTypeDefinition> gooType, long hitTick, Strike middle,
                        double width, double height) {
        add(entityId, new Coat(gooType, hitTick, middle, envelopReach(width, height)));
    }

    /**
     * The reach scale that covers a mob's box whole from its middle: the
     * half diagonal of the box over the reach a splat covers whole.
     *
     * @param width  the box width in blocks
     * @param height the box height in blocks
     * @return the scale, never below a splat's own
     */
    static float envelopReach(double width, double height) {
        double halfDiagonal = Math.sqrt(width * width + width * width + height * height) * HALF;
        return (float) Math.max(SPLAT_REACH, halfDiagonal / WHOLE_REACH_BLOCKS);
    }

    private void add(int entityId, Coat coat) {
        dropDissolved(coat.hitTick());
        Deque<Coat> splats = coats.computeIfAbsent(entityId, id -> new ArrayDeque<>());
        splats.addLast(coat);
        while (splats.size() > MAX_SPLATS_PER_MOB) {
            splats.removeFirst();
        }
    }

    /**
     * Visits every splat still standing at a tick, dropping those that have
     * dissolved.
     *
     * @param tick    the game tick
     * @param visitor takes each standing splat's entity id and splat
     */
    public void forEachStanding(long tick, BiConsumer<Integer, Coat> visitor) {
        dropDissolved(tick);
        coats.forEach((entityId, splats) -> splats.forEach(coat -> visitor.accept(entityId, coat)));
    }

    /**
     * The splats a mob wears at a tick, oldest first; empty where it wears
     * none.
     *
     * @param entityId the entity's id
     * @param tick     the game tick
     * @return the standing splats
     */
    public List<Coat> coatsOf(int entityId, long tick) {
        Deque<Coat> splats = coats.get(entityId);
        if (splats == null) {
            return List.of();
        }
        return splats.stream().filter(coat -> !ended(coat, tick)).toList();
    }

    private void dropDissolved(long tick) {
        coats.values().forEach(splats -> splats.removeIf(coat -> ended(coat, tick)));
        coats.values().removeIf(Deque::isEmpty);
    }

    /**
     * Drops every splat, as a disconnect does.
     */
    public void clear() {
        coats.clear();
    }

    private static boolean ended(Coat coat, long tick) {
        return tick - coat.hitTick() >= COAT_LIFE_TICKS;
    }
}
