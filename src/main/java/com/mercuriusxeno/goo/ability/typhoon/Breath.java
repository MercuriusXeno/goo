package com.mercuriusxeno.goo.ability.typhoon;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The air Pneuma banks for a player past the vanilla air meter, and the game
 * time its airless immunity ends at. A held tick of Pneuma refills the vanilla
 * air first and spills what is left into the reserve up to its cap; while the
 * player cannot breathe, the immunity window lets no air deplete, then the
 * reserve drains before the vanilla air does. Saved with the player and synced
 * to its client, which draws the reserve above the air bubbles.
 * pneuma-overfills-the-air-meter-with-a-brief-airless-immunity
 *
 * @param reserve     the air banked past the vanilla meter, in ticks of breath
 * @param reserveCap  the most air the reserve banks, in ticks of breath, set by the Pneuma that filled it
 * @param immuneUntil the game time the airless immunity ends at; zero when none was opened
 */
public record Breath(int reserve, int reserveCap, long immuneUntil) {

    /** No Pneuma. */
    public static final Breath NONE = new Breath(0, 0, 0L);

    private static final String FIELD_RESERVE = "reserve";
    private static final String FIELD_RESERVE_CAP = "reserve_cap";
    private static final String FIELD_IMMUNE_UNTIL = "immune_until";

    /** Saves the breath with the player. */
    public static final MapCodec<Breath> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Codec.INT.fieldOf(FIELD_RESERVE).forGetter(Breath::reserve),
            Codec.INT.fieldOf(FIELD_RESERVE_CAP).forGetter(Breath::reserveCap),
            Codec.LONG.fieldOf(FIELD_IMMUNE_UNTIL).forGetter(Breath::immuneUntil)
    ).apply(inst, Breath::new));

    /** Syncs the breath to the owning client, which draws the reserve. */
    public static final StreamCodec<ByteBuf, Breath> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.VAR_INT, Breath::reserve,
            ByteBufCodecs.VAR_INT, Breath::reserveCap,
            ByteBufCodecs.VAR_LONG, Breath::immuneUntil,
            Breath::new);

    /**
     * One held tick of Pneuma: the vanilla air rises by the refill up to its
     * meter, the rest spills into the reserve up to the cap, and the immunity
     * window opens for its length from now.
     *
     * @param air      the player's vanilla air now, in ticks
     * @param maxAir   the vanilla meter's full air, in ticks
     * @param refill   the air one held tick refills, in ticks
     * @param cap      the most air the reserve banks, in ticks
     * @param immunity the ticks the immunity window stands after this held tick
     * @param now      the game time of the held tick
     * @return the vanilla air after the tick and the breath it leaves
     */
    public Refilled refilled(int air, int maxAir, int refill, int cap, int immunity, long now) {
        int filled = Math.max(air, 0) + refill;
        int spilled = Math.max(filled - maxAir, 0);
        return new Refilled(Math.min(filled, maxAir), new Breath(Math.min(reserve + spilled, cap), cap, now + immunity));
    }

    /**
     * One tick of breath taken while the player cannot breathe: nothing inside
     * the immunity window, then from the reserve, then what the reserve could
     * not cover from the vanilla air.
     *
     * @param consume the air the tick takes, in ticks
     * @param now     the game time
     * @return the air left for the vanilla meter to lose and the breath left
     */
    public Spent spent(int consume, long now) {
        if (now < immuneUntil) {
            return new Spent(0, this);
        }
        int fromReserve = Math.min(reserve, consume);
        return new Spent(consume - fromReserve, new Breath(reserve - fromReserve, reserveCap, immuneUntil));
    }

    /**
     * Whether the breath holds anything worth saving: a reserve or an immunity window opened.
     *
     * @return true when the reserve or the window stands
     */
    public boolean stands() {
        return reserve > 0 || immuneUntil > 0L;
    }

    /**
     * The vanilla air a held tick of Pneuma leaves, and the breath beside it.
     *
     * @param air    the vanilla air, in ticks
     * @param breath the breath
     */
    public record Refilled(int air, Breath breath) {
    }

    /**
     * What a tick of breath leaves for the vanilla air to lose, and the breath beside it.
     *
     * @param fromAir the air the vanilla meter loses, in ticks
     * @param breath  the breath
     */
    public record Spent(int fromAir, Breath breath) {
    }
}
