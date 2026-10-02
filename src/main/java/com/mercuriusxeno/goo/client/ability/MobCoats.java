package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.Map;

/**
 * The goo splats standing on struck mobs, keyed by entity id: each holds
 * from its latest hit for the hold, and a repeat hit replaces it at the new
 * hit point and restarts it.
 * Decision shader-coat-on-every-mob-landing.
 * Decision splat-holds-then-dissolves-dripping.
 */
public final class MobCoats {

    /** Game ticks a splat holds whole after its latest hit, about three seconds. */
    public static final int COAT_HOLD_TICKS = 60;

    /** The client's splats. */
    public static final MobCoats CLIENT = new MobCoats();

    /** Splats by struck entity id. */
    private final Map<Integer, Coat> coats = new HashMap<>();

    /**
     * One mob's splat.
     */
    public static final class Coat {

        private final ResourceKey<GooTypeDefinition> gooType;
        private final long hitTick;
        private final Vec3 hitPoint;
        private @Nullable Vector3f pinnedHit;

        /**
         * @param gooType  the goo type it is splatted in
         * @param hitTick  the game tick of its hit
         * @param hitPoint the world point the goo struck
         */
        Coat(ResourceKey<GooTypeDefinition> gooType, long hitTick, Vec3 hitPoint) {
            this.gooType = gooType;
            this.hitTick = hitTick;
            this.hitPoint = hitPoint;
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
         * The hit point in the mob model's root space, fixed on the splat's
         * first draw so the splat rides the body afterwards.
         *
         * @return the pinned hit point, or null before the first draw
         */
        public @Nullable Vector3f pinnedHit() {
            return pinnedHit;
        }

        /**
         * Fixes the hit point in the mob model's root space.
         *
         * @param modelSpaceHit the hit point in the model's root space
         */
        public void pin(Vector3f modelSpaceHit) {
            this.pinnedHit = modelSpaceHit;
        }
    }

    /**
     * Splats a mob from a hit, replacing any splat it wears, and drops every
     * splat whose hold has ended.
     *
     * @param entityId the struck entity's id
     * @param gooType  the goo type it was struck with
     * @param hitTick  the game tick of the hit
     * @param hitPoint the world point the goo struck
     */
    public void coat(int entityId, ResourceKey<GooTypeDefinition> gooType, long hitTick, Vec3 hitPoint) {
        coats.values().removeIf(coat -> ended(coat, hitTick));
        coats.put(entityId, new Coat(gooType, hitTick, hitPoint));
    }

    /**
     * The splat a mob wears at a tick, or null where it wears none or its
     * hold has ended.
     *
     * @param entityId the entity's id
     * @param tick     the game tick
     * @return the splat, or null
     */
    public @Nullable Coat coatOf(int entityId, long tick) {
        Coat coat = coats.get(entityId);
        if (coat == null || ended(coat, tick)) {
            return null;
        }
        return coat;
    }

    /**
     * Drops every splat, as a disconnect does.
     */
    public void clear() {
        coats.clear();
    }

    private static boolean ended(Coat coat, long tick) {
        return tick - coat.hitTick() >= COAT_HOLD_TICKS;
    }
}
