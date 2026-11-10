package com.mercuriusxeno.goo.ability.reap;

import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import java.util.Map;
import java.util.WeakHashMap;

/**
 * The cadence Reap's held channel slashes at, uneven so the slashes come as a
 * flurry rather than a beat: a slash on the hold's first tick, then each next
 * a random few ticks after the last, between the fewest and the most the JSON
 * names. Each slasher's next slash is kept here for as long as the slasher
 * stands, and never saved.
 * reap-is-crissaegrim-slashes-that-harvest-and-cut
 */
public final class SwingCadence {

    private static final Map<Entity, Long> NEXT_SWING = new WeakHashMap<>();

    private SwingCadence() {
    }

    /**
     * Whether a slasher's held tick slashes, setting the tick of the next
     * slash when it does.
     *
     * @param slasher the entity slashing
     * @param held    the hold's tick count, 1 on its first tick
     * @param now     the game tick
     * @param fewest  the fewest ticks from one slash to the next
     * @param most    the most ticks from one slash to the next
     * @return true on a slashing tick
     */
    public static boolean dueToSwing(Entity slasher, int held, long now, int fewest, int most) {
        if (!swingsNow(held, now, NEXT_SWING.getOrDefault(slasher, now))) {
            return false;
        }
        NEXT_SWING.put(slasher, nextSwingAt(now, fewest, most, slasher.getRandom()));
        return true;
    }

    /**
     * The tick of the next slash: a random gap after this one, from the
     * fewest ticks to the most, both included.
     *
     * @param now    the game tick of this slash
     * @param fewest the fewest ticks of the gap
     * @param most   the most ticks of the gap
     * @param random the random source
     * @return the next slash's game tick
     */
    static long nextSwingAt(long now, int fewest, int most, RandomSource random) {
        int least = Math.max(1, fewest);
        return now + least + random.nextInt(Math.max(least, most) - least + 1);
    }

    /**
     * Whether a held tick slashes: the hold's first does, and so does any
     * tick the next slash has come due on.
     *
     * @param held the hold's tick count, 1 on its first tick
     * @param now  the game tick
     * @param due  the game tick the next slash comes due
     * @return true on a slashing tick
     */
    static boolean swingsNow(int held, long now, long due) {
        return held <= 1 || now >= due;
    }
}
