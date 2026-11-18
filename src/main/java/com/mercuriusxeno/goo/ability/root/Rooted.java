package com.mercuriusxeno.goo.ability.root;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.BlockPos;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * The vines Vines goo holds a living thing with: the count its cells gave,
 * which slows it, the time they hold until, and the cell that last gave it
 * vines, which gives none again until the thing has left it. Once released
 * the vines stand a short fade before they go, so the client can draw them
 * falling away.
 * vines-is-sprayed-slime-that-entangles-what-tries-to-cross
 * vines-root-is-volume-based-one-to-five-vines-by-layers
 * vines-roots-last-three-seconds-plus-one-per-stack-and-each-vine-refreshes
 *
 * @param anchor     where the thing stood when a cell last gave it vines, which the strands reach down to
 * @param vines      the vines held, none once released
 * @param fireFactor what fire damage to a thing that is not fire immune is multiplied by
 * @param lastCell   the cell that last gave the thing vines
 * @param rootedAt   the game time the vines first latched, which the unpack runs from
 * @param endsAt     the game time the vines release at, or released at once they have
 */
public record Rooted(Vec3 anchor, int vines, float fireFactor, BlockPos lastCell, long rootedAt, long endsAt) {

    /** The vines a thing never rooted holds. */
    public static final Rooted NONE = new Rooted(Vec3.ZERO, 0, 1f, BlockPos.ZERO, 0L, 0L);

    /** Ticks the vines take to spread over the thing. */
    public static final int UNPACK_TICKS = 6;

    /** Ticks the vines take to fall away once released, before the attachment goes. */
    public static final int FADE_TICKS = 10;

    private static final String FIELD_ANCHOR = "anchor";
    private static final String FIELD_VINES = "vines";
    private static final String FIELD_FIRE_FACTOR = "fire_factor";
    private static final String FIELD_LAST_CELL = "last_cell";
    private static final String FIELD_ROOTED_AT = "rooted_at";
    private static final String FIELD_ENDS_AT = "ends_at";

    /** Codec for the saved vines. */
    public static final MapCodec<Rooted> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Vec3.CODEC.fieldOf(FIELD_ANCHOR).forGetter(Rooted::anchor),
            Codec.INT.fieldOf(FIELD_VINES).forGetter(Rooted::vines),
            Codec.FLOAT.fieldOf(FIELD_FIRE_FACTOR).forGetter(Rooted::fireFactor),
            BlockPos.CODEC.fieldOf(FIELD_LAST_CELL).forGetter(Rooted::lastCell),
            Codec.LONG.fieldOf(FIELD_ROOTED_AT).forGetter(Rooted::rootedAt),
            Codec.LONG.fieldOf(FIELD_ENDS_AT).forGetter(Rooted::endsAt)
    ).apply(inst, Rooted::new));

    /** Codec for the vines synced to the clients drawing the thing. */
    public static final StreamCodec<ByteBuf, Rooted> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, Rooted::anchor,
            ByteBufCodecs.VAR_INT, Rooted::vines,
            ByteBufCodecs.FLOAT, Rooted::fireFactor,
            BlockPos.STREAM_CODEC, Rooted::lastCell,
            ByteBufCodecs.VAR_LONG, Rooted::rootedAt,
            ByteBufCodecs.VAR_LONG, Rooted::endsAt,
            Rooted::new);

    /**
     * What the Vines goo a thing touched gives it.
     *
     * @param cell       the cell touched
     * @param layers     the layers it holds
     * @param fireFactor the fire factor its laying step names
     */
    public record Touch(BlockPos cell, int layers, float fireFactor) {
    }

    /**
     * Answers whether the vines hold the thing now.
     *
     * @param now the game time
     * @return true while vines are held and the hold has not run out
     */
    public boolean holds(long now) {
        return vines > 0 && now < endsAt;
    }

    /**
     * Answers whether the vines stand on the thing at all, holding or falling away.
     *
     * @return true from the first latch until the attachment goes
     */
    public boolean stands() {
        return endsAt > 0L;
    }

    /**
     * The vines after the thing touched a Vines cell: the cell that last
     * gave it vines gives none again; vines still holding take the cell's
     * vines and refresh to its threshold; otherwise the cell latches fresh
     * vines where the thing stands.
     *
     * @param touch what the cell gives
     * @param at    where the thing stands
     * @param now   the game time
     * @return the vines after the touch
     */
    public Rooted touched(Touch touch, Vec3 at, long now) {
        if (stands() && touch.cell().equals(lastCell)) {
            return this;
        }
        boolean holding = holds(now);
        VineHold held = holding ? new VineHold(vines, (int) (endsAt - now)) : VineHold.NONE;
        VineHold after = held.touched(touch.layers());
        if (after.vines() == 0) {
            return this;
        }
        return new Rooted(at, after.vines(), touch.fireFactor(), touch.cell().immutable(),
                holding ? rootedAt : now, now + after.ticksLeft());
    }

    /**
     * The share of its speed the thing keeps while the vines hold.
     *
     * @param now the game time
     * @return the share, 1 once released
     */
    public double speedAt(long now) {
        return holds(now) ? VineHold.speedOf(vines) : 1.0;
    }

    /**
     * Releases the thing, the vines starting to fall away.
     *
     * @param now the game time the vines let go
     * @return the released vines
     */
    public Rooted releasedAt(long now) {
        return new Rooted(anchor, 0, fireFactor, lastCell, rootedAt, Math.min(endsAt, now));
    }

    /**
     * Answers whether released vines have finished falling away.
     *
     * @param now the game time
     * @return true once the fade after the release has run
     */
    public boolean fadedBy(long now) {
        return now >= endsAt + FADE_TICKS;
    }

    /**
     * How much of the thing the vines cover at a time: they spread over it
     * as they latch, and fall away across the fade once released.
     *
     * @param time the game time with the partial tick
     * @return the share covered, 0 to 1
     */
    public float coverAt(float time) {
        float unpacked = Math.clamp((time - rootedAt) / UNPACK_TICKS, 0f, 1f);
        float fading = Math.clamp((time - endsAt) / FADE_TICKS, 0f, 1f);
        return unpacked * (1f - fading);
    }
}
