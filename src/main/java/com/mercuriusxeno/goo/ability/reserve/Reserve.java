package com.mercuriusxeno.goo.ability.reserve;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The reserve banked behind a player's bars: reserve hearts behind the health
 * bar, spent before real health, and reserve shanks behind the hunger bar,
 * spent before hunger drops. It stands apart from any heart overlay, so it
 * neither ends one nor is ended by one (decisions reserve-channels-on-jelly
 * and reserve-hearts-sit-behind-the-bar). Halves fill from the bar's first
 * slot and spend from its last.
 *
 * @param heartHalves the reserve half hearts banked
 * @param heartCarry  the health points drained that have not yet banked a half heart
 * @param shankHalves the reserve half shanks banked
 * @param shankCarry  the food points drained that have not yet banked a half shank
 * @param foodOwed    the food points the drain has run up and not yet taken from the bar
 * @param lastFood    the hunger the bar stood at when the reserve last read it
 */
public record Reserve(int heartHalves, float heartCarry, int shankHalves, float shankCarry, float foodOwed,
                      int lastFood) {

    /** The reserve a player who never held Reserve holds. */
    public static final Reserve NONE = new Reserve(0, 0f, 0, 0f, 0f, 0);

    /** Half hearts in a heart, and half shanks in a shank. */
    public static final int HALVES_PER_SLOT = 2;
    /** Health points in a heart, and food points in a shank. */
    public static final float POINTS_PER_SLOT = 2f;
    /** The slack a banked half forgives in the float sum of a drain's ticks. */
    private static final float BANK_TOLERANCE = 1e-4f;

    /** Codec for the saved reserve. */
    public static final MapCodec<Reserve> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.INT.fieldOf("heart_halves").forGetter(Reserve::heartHalves),
            Codec.FLOAT.fieldOf("heart_carry").forGetter(Reserve::heartCarry),
            Codec.INT.fieldOf("shank_halves").forGetter(Reserve::shankHalves),
            Codec.FLOAT.fieldOf("shank_carry").forGetter(Reserve::shankCarry),
            Codec.FLOAT.fieldOf("food_owed").forGetter(Reserve::foodOwed),
            Codec.INT.fieldOf("last_food").forGetter(Reserve::lastFood)
    ).apply(inst, Reserve::new));

    /** Codec for the reserve synced to the owning client, which draws its rows. */
    public static final StreamCodec<ByteBuf, Reserve> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Reserve::heartHalves,
            ByteBufCodecs.FLOAT, Reserve::heartCarry,
            ByteBufCodecs.VAR_INT, Reserve::shankHalves,
            ByteBufCodecs.FLOAT, Reserve::shankCarry,
            ByteBufCodecs.FLOAT, Reserve::foodOwed,
            ByteBufCodecs.VAR_INT, Reserve::lastFood,
            Reserve::new);

    /**
     * What a hit leaves: the reserve after it and the damage that reaches real health.
     *
     * @param reserve   the reserve after the hit
     * @param remainder the damage real health takes
     */
    public record Spent(Reserve reserve, float remainder) {
    }

    /**
     * Answers whether a reserve stands: halves banked or a drain under way.
     *
     * @return true while Reserve holds something behind a bar
     */
    public boolean stands() {
        return banks() || draining();
    }

    private boolean banks() {
        return heartHalves > 0 || shankHalves > 0;
    }

    private boolean draining() {
        return heartCarry > 0f || shankCarry > 0f || foodOwed > 0f;
    }

    /**
     * The half hearts or half shanks banked in a slot of a row, the first slot filling first.
     *
     * @param halves the halves banked in the row
     * @param slot   the slot, from the row's start
     * @return zero to two halves
     */
    public static int halvesAt(int halves, int slot) {
        return Math.clamp(halves - slot * HALVES_PER_SLOT, 0, HALVES_PER_SLOT);
    }

    /**
     * Banks drained health: the points add to the carry, and each time the
     * carry covers a half at the ratio a half heart banks, up to the cap,
     * which drops what the carry still held.
     *
     * @param drained   the health points drained this tick
     * @param ratio     the reserve halves one drained point banks
     * @param capHalves the most half hearts that may stand
     * @return the reserve after the banking
     */
    public Reserve bankHealth(float drained, float ratio, int capHalves) {
        Banked banked = bank(heartHalves, heartCarry + drained, ratio, capHalves);
        return new Reserve(banked.halves, banked.carry, shankHalves, shankCarry, foodOwed, lastFood);
    }

    /**
     * Banks drained food the way health banks, into half shanks.
     *
     * @param drained   the food points taken from the bar this tick
     * @param ratio     the reserve halves one drained point banks
     * @param capHalves the most half shanks that may stand
     * @return the reserve after the banking
     */
    public Reserve bankFood(float drained, float ratio, int capHalves) {
        Banked banked = bank(shankHalves, shankCarry + drained, ratio, capHalves);
        return new Reserve(heartHalves, heartCarry, banked.halves, banked.carry, foodOwed, lastFood);
    }

    private record Banked(int halves, float carry) {
    }

    private static Banked bank(int standing, float carry, float ratio, int capHalves) {
        float pointsPerHalf = 1f / ratio;
        int halves = standing;
        float left = carry;
        while (halves < capHalves && left + BANK_TOLERANCE >= pointsPerHalf) {
            halves++;
            left -= pointsPerHalf;
        }
        return new Banked(halves, halves >= capHalves ? 0f : Math.max(0f, left));
    }

    /**
     * The reserve with the food the drain owes and the hunger it left the bar at.
     *
     * @param owed the food points owed and not yet taken
     * @param food the hunger the bar stands at
     * @return the reserve after the drain's tick
     */
    public Reserve owing(float owed, int food) {
        return new Reserve(heartHalves, heartCarry, shankHalves, shankCarry, owed, food);
    }

    /**
     * Runs a hit through the reserve hearts: each half absorbs up to a health
     * point and spends, and what passes every half reaches real health.
     * reserve-hearts-sit-behind-the-bar
     *
     * @param damage the hit's damage
     * @return the reserve after the hit and the damage real health takes
     */
    public Spent spend(float damage) {
        if (heartHalves == 0 || damage <= 0f) {
            return new Spent(this, damage);
        }
        int used = Math.min(heartHalves, (int) Math.ceil(damage));
        return new Spent(settled(heartHalves - used, shankHalves, lastFood), Math.max(0f, damage - heartHalves));
    }

    /**
     * The half shanks that refill hunger the bar lost since the reserve last
     * read it: one a food point, up to what stands banked.
     *
     * @param food the hunger the bar stands at
     * @return the half shanks to spend into the bar
     */
    public int refillFor(int food) {
        return food < lastFood ? Math.min(lastFood - food, shankHalves) : 0;
    }

    /**
     * The reserve after the bar read the hunger it stands at, with the half
     * shanks it spent refilling the bar's loss.
     *
     * @param spent the half shanks spent into the bar
     * @param food  the hunger the bar stands at after the refill
     * @return the reserve after the read
     */
    public Reserve read(int spent, int food) {
        return settled(heartHalves, shankHalves - spent, food);
    }

    private Reserve settled(int hearts, int shanks, int food) {
        Reserve after = new Reserve(hearts, heartCarry, shanks, shankCarry, foodOwed, food);
        return after.stands() ? after : NONE;
    }
}
