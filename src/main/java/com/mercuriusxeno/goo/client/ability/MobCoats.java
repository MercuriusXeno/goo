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
        private Vec3 hitPoint;
        private Vec3 hitFromFeet;
        private float bodyYawAtHit;
        private ModelRay.@Nullable PartHit pin;

        /**
         * @param gooType the goo type it is splatted in
         * @param hitTick the game tick of its hit
         * @param strike  where and how the goo struck
         */
        Coat(ResourceKey<GooTypeDefinition> gooType, long hitTick, Strike strike) {
            this.gooType = gooType;
            this.hitTick = hitTick;
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
        dropDissolved(hitTick);
        Deque<Coat> splats = coats.computeIfAbsent(entityId, id -> new ArrayDeque<>());
        splats.addLast(new Coat(gooType, hitTick, strike));
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
