package com.mercuriusxeno.goo.ability.root;

import com.mojang.serialization.Codec;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.world.phys.Vec3;

/**
 * The vines rooting a mob to the point they latched it at: they hold it for
 * a few seconds or a number of hits, whichever ends first, thorn it for
 * straining past the leash and feed fire damage; a throw landing on a mob
 * still held stacks its hits and its thorns and holds it afresh from that
 * throw, the root staying where it latched. Once released the vines stand a
 * short fade before they go, so the client can draw them falling away.
 * vines-unpack-root-and-thorn
 *
 * @param anchor     the world point the vines latched the mob at
 * @param hitsLeft   the hits the vines take before they break; zero once released
 * @param thorns     the damage the vines deal each time the mob strains past the leash
 * @param fireFactor what fire damage to a mob that is not fire immune is multiplied by
 * @param rootedAt   the game time the vines first latched, which the unpack runs from
 * @param endsAt     the game time the vines release at, or released at once they have
 */
public record Rooted(Vec3 anchor, int hitsLeft, float thorns, float fireFactor, long rootedAt, long endsAt) {

    /** The vines a mob never rooted holds. */
    public static final Rooted NONE = new Rooted(Vec3.ZERO, 0, 0f, 1f, 0L, 0L);

    /** Ticks the blob takes to unpack into vines over the mob. */
    public static final int UNPACK_TICKS = 6;

    /** Ticks the vines take to fall away once released, before the attachment goes. */
    public static final int FADE_TICKS = 10;

    private static final String FIELD_ANCHOR = "anchor";
    private static final String FIELD_HITS_LEFT = "hits_left";
    private static final String FIELD_THORNS = "thorns";
    private static final String FIELD_FIRE_FACTOR = "fire_factor";
    private static final String FIELD_ROOTED_AT = "rooted_at";
    private static final String FIELD_ENDS_AT = "ends_at";

    /** Codec for the saved vines. */
    public static final MapCodec<Rooted> CODEC = RecordCodecBuilder.mapCodec(inst -> inst.group(
            Vec3.CODEC.fieldOf(FIELD_ANCHOR).forGetter(Rooted::anchor),
            Codec.INT.fieldOf(FIELD_HITS_LEFT).forGetter(Rooted::hitsLeft),
            Codec.FLOAT.fieldOf(FIELD_THORNS).forGetter(Rooted::thorns),
            Codec.FLOAT.fieldOf(FIELD_FIRE_FACTOR).forGetter(Rooted::fireFactor),
            Codec.LONG.fieldOf(FIELD_ROOTED_AT).forGetter(Rooted::rootedAt),
            Codec.LONG.fieldOf(FIELD_ENDS_AT).forGetter(Rooted::endsAt)
    ).apply(inst, Rooted::new));

    /** Codec for the vines synced to the clients drawing the mob. */
    public static final StreamCodec<ByteBuf, Rooted> STREAM_CODEC = StreamCodec.composite(
            Vec3.STREAM_CODEC, Rooted::anchor,
            ByteBufCodecs.VAR_INT, Rooted::hitsLeft,
            ByteBufCodecs.FLOAT, Rooted::thorns,
            ByteBufCodecs.FLOAT, Rooted::fireFactor,
            ByteBufCodecs.VAR_LONG, Rooted::rootedAt,
            ByteBufCodecs.VAR_LONG, Rooted::endsAt,
            Rooted::new);

    /**
     * The strength of one throw of vines.
     *
     * @param hits       the hits the throw's vines take before they break
     * @param thorns     the thorn damage the throw adds
     * @param fireFactor what fire damage is multiplied by while they hold
     * @param duration   the ticks the throw holds the mob for
     */
    public record Throw(int hits, float thorns, float fireFactor, int duration) {
    }

    /**
     * Answers whether the vines hold the mob now.
     *
     * @param now the game time
     * @return true while hits are left and the hold has not run out
     */
    public boolean holds(long now) {
        return hitsLeft > 0 && now < endsAt;
    }

    /**
     * Answers whether the vines stand on the mob at all, holding or falling away.
     *
     * @return true from the first latch until the attachment goes
     */
    public boolean stands() {
        return endsAt > 0L;
    }

    /**
     * Latches a throw of vines onto the mob: vines still holding stack the
     * throw's hits and thorns, keep their anchor and unpack, and hold afresh
     * from now; otherwise the throw latches new vines at the point given.
     *
     * @param thrown the throw's strength
     * @param at     where the vines latch when none hold the mob
     * @param now    the game time
     * @return the vines after the throw
     */
    public Rooted latch(Throw thrown, Vec3 at, long now) {
        long endsAfter = now + thrown.duration();
        if (holds(now)) {
            return new Rooted(anchor, hitsLeft + thrown.hits(), thorns + thrown.thorns(),
                    Math.max(fireFactor, thrown.fireFactor()), rootedAt, endsAfter);
        }
        return new Rooted(at, thrown.hits(), thrown.thorns(), thrown.fireFactor(), now, endsAfter);
    }

    /**
     * Spends one of the vines' hits; the last one releases the mob now.
     *
     * @param now the game time
     * @return the vines after the hit
     */
    public Rooted struck(long now) {
        int left = hitsLeft - 1;
        return left > 0 ? new Rooted(anchor, left, thorns, fireFactor, rootedAt, endsAt) : releasedAt(now);
    }

    /**
     * Releases the mob, the vines starting to fall away.
     *
     * @param now the game time the vines let go
     * @return the released vines
     */
    public Rooted releasedAt(long now) {
        return new Rooted(anchor, 0, thorns, fireFactor, rootedAt, Math.min(endsAt, now));
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
     * How much of the mob the vines cover at a time: they spread over it as
     * the blob unpacks, and fall away across the fade once released.
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
