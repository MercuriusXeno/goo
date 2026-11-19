package com.mercuriusxeno.goo.lunar;

import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

/**
 * Lunar's gravity: one sixth of the overworld's, the real moon's, for everything that falls there, the overworld's
 * everywhere else, and lunar's for whoever holds Moonrock. Every gravity the game or goo applies to an entity is
 * scaled by {@link #scaleOf}, every other gravity applied in a dimension by {@link #scaleIn}, and a thrown goo blob,
 * which flies a scripted arc to its aim point with no gravity of its own, floats there for {@link #flightTicksIn},
 * the time one-sixth gravity takes to draw the same peak.
 * decision lunar-gravity-is-one-sixth-of-the-overworlds
 * decision lunar-stone-carries-one-sixth-gravity-while-held
 */
public final class LunarGravity {

    /** Lunar's gravity as a share of the overworld's: the real moon's, one sixth. */
    private static final double LUNAR_SCALE = 1.0 / 6.0;
    private static final double OVERWORLD_SCALE = 1.0;

    private LunarGravity() {
    }

    /**
     * The share of the overworld's gravity a dimension pulls with.
     *
     * @param dimension the dimension
     * @return one sixth in lunar, one everywhere else
     */
    public static double scaleIn(ResourceKey<Level> dimension) {
        return Lunar.LEVEL.equals(dimension) ? LUNAR_SCALE : OVERWORLD_SCALE;
    }

    /**
     * The share of the overworld's gravity an entity falls under: lunar's sixth for a living entity holding Moonrock
     * in either hand, in any dimension, and its dimension's share otherwise, so the stone changes nothing in lunar and
     * nothing from the tick it leaves the hand. Every gravity scaled for one entity reads this.
     * decision lunar-stone-carries-one-sixth-gravity-while-held
     *
     * @param entity the entity
     * @return one sixth while it holds Moonrock or stands in lunar, one otherwise
     */
    public static double scaleOf(Entity entity) {
        return holdsMoonrock(entity) ? LUNAR_SCALE : scaleIn(entity.level().dimension());
    }

    private static boolean holdsMoonrock(Entity entity) {
        return entity instanceof LivingEntity living && (living.getMainHandItem().is(GooItems.MOONROCK.get())
                || living.getOffhandItem().is(GooItems.MOONROCK.get()));
    }

    /**
     * A thrown goo blob's flight time in a dimension: an arc's peak is gravity times the flight time squared over
     * eight, so the same peak under a weaker pull takes the flight time over the square root of the scale.
     *
     * @param dimension      the dimension the blob is thrown in
     * @param overworldTicks the ticks the same throw flies in the overworld
     * @return the ticks it flies in that dimension
     */
    public static int flightTicksIn(ResourceKey<Level> dimension, int overworldTicks) {
        return (int) Math.round(overworldTicks / Math.sqrt(scaleIn(dimension)));
    }
}
