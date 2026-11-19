package com.mercuriusxeno.goo.ability.yarn;

import com.mercuriusxeno.goo.ability.root.RootStrike;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;

/**
 * The strands Stitch tangles a hostile in: they hold while the hostile
 * stands in the goo and a short linger after it leaves, fire on a tangled
 * hostile that is not fire immune lands at the multiple Vines' root gives,
 * and fire ends the tangle until the hostile has been out of the goo past
 * the linger.
 * passives-move-through-stitch-and-hostiles-tangle-in-it
 *
 * @param fireFactor what fire damage to a tangled thing that is not fire immune is multiplied by
 * @param until      the game time the strands let go at unless the goo touches the thing again
 * @param burnt      whether fire has ended the tangle
 */
public record Tangled(float fireFactor, long until, boolean burnt) {

    /** The strands on a thing Stitch never touched. */
    public static final Tangled NONE = new Tangled(1f, 0L, false);

    /** Ticks the strands hold past the goo's last touch. */
    static final int LINGER_TICKS = 10;
    /**
     * Ticks left on the strands under which a touch renews them, so a thing
     * standing in the goo rewrites its strands every few ticks rather than
     * every tick; it is also the fade the strands fall away over.
     */
    static final int RENEW_UNDER = 5;

    /** Codec for the strands synced to the clients drawing the thing. */
    public static final StreamCodec<ByteBuf, Tangled> STREAM_CODEC = StreamCodec.composite(
            ByteBufCodecs.FLOAT, Tangled::fireFactor,
            ByteBufCodecs.VAR_LONG, Tangled::until,
            ByteBufCodecs.BOOL, Tangled::burnt,
            Tangled::new);

    /**
     * The strands after the goo touched the thing: strands standing hold on,
     * renewed once they run low, burnt ones staying burnt; otherwise fresh
     * strands take hold.
     *
     * @param factor the fire factor the goo's laying step names
     * @param now    the game time
     * @return the strands after the touch, this same value where nothing changed
     */
    public Tangled touched(float factor, long now) {
        if (now < until) {
            return until - now > RENEW_UNDER ? this : new Tangled(fireFactor, now + LINGER_TICKS, burnt);
        }
        return new Tangled(factor, now + LINGER_TICKS, false);
    }

    /**
     * Answers whether the strands tangle the thing now.
     *
     * @param now the game time
     * @return true while unburnt strands have not let go
     */
    public boolean holds(long now) {
        return !burnt && now < until;
    }

    /**
     * The strands once fire has ended the tangle.
     *
     * @return the burnt strands
     */
    public Tangled burn() {
        return new Tangled(fireFactor, until, true);
    }

    /**
     * The damage a hit deals the thing: fire on a tangled thing that is not
     * fire immune lands at the fire factor, as on a mob Vines roots; any
     * other hit lands as it is.
     *
     * @param now        the game time
     * @param amount     the hit's damage
     * @param fire       whether the hit is fire
     * @param fireImmune whether the thing is fire immune
     * @return the damage dealt
     */
    public float damageAt(long now, float amount, boolean fire, boolean fireImmune) {
        return holds(now) ? RootStrike.fireDealt(amount, fire && !fireImmune, fireFactor) : amount;
    }

    /**
     * How much of the thing the strands cover at a time: all of it while the
     * goo touches it, falling away across the fade once it leaves, and none
     * once burnt.
     *
     * @param time the game time with the partial tick
     * @return the share covered, 0 to 1
     */
    public float coverAt(float time) {
        return burnt ? 0f : Math.clamp((until - time) / RENEW_UNDER, 0f, 1f);
    }
}
