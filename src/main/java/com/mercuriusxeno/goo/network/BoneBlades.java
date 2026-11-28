package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.program.BladeStep;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * The bone shotels standing on the server, one at most to a caster: a shotel latches once a hold of Blade's right
 * click is carried through its whole charge, the caster's hand casts nothing else while it stands, and the next
 * right click dissolves it, as a strike finding too little Osseous does.
 * decision blade-forms-a-bone-shotel-in-hand
 */
public final class BoneBlades {

    private final Map<UUID, Blade> standing = new HashMap<>();

    /**
     * One latched shotel.
     *
     * @param rightHand whether it stands in the right hand
     * @param damage    the damage each strike deals
     * @param drain     the whole mB of Osseous each strike drains
     */
    public record Blade(boolean rightHand, float damage, int drain) {

        /**
         * The shotel a Blade step lays.
         *
         * @param step      the Blade step
         * @param rightHand whether the casting hand is the right
         * @return the shotel
         */
        public static Blade of(BladeStep step, boolean rightHand) {
            return new Blade(rightHand, step.damage(), step.drain());
        }
    }

    /**
     * Whether a hold reached far enough to form the shotel: its whole charge.
     *
     * @param chargeShare the share of a full charge the hold reached, 0 to 1
     * @return true where the shotel forms
     */
    public static boolean formsAt(float chargeShare) {
        return chargeShare >= 1f;
    }

    /**
     * Latches a shotel in a caster's hand, replacing any it held.
     *
     * @param caster the caster's id
     * @param blade  the shotel
     */
    public void latch(UUID caster, Blade blade) {
        standing.put(caster, blade);
    }

    /**
     * @param caster the caster's id
     * @return the shotel the caster holds, empty for none
     */
    public Optional<Blade> bladeOf(UUID caster) {
        return Optional.ofNullable(standing.get(caster));
    }

    /**
     * Whether the caster's hand refuses a cast: it holds a shotel.
     *
     * @param caster the caster's id
     * @return true while a shotel stands in the caster's hand
     */
    public boolean refusesCast(UUID caster) {
        return standing.containsKey(caster);
    }

    /**
     * Dissolves a caster's shotel.
     *
     * @param caster the caster's id
     * @return whether a shotel stood to dissolve
     */
    public boolean dissolve(UUID caster) {
        return standing.remove(caster) != null;
    }

    /** Dissolves every shotel, as a server stop does. */
    public void clear() {
        standing.clear();
    }
}
