package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.Charge;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import net.minecraft.resources.Identifier;
import java.util.Optional;

/**
 * The sound a charge-sized cast's charge makes as it grows: the outsider
 * wub's one take, so every stage pitches one base tone, once each time the
 * hold reaches a stage, up the major scale from the stage it opens at, so
 * Blast wubs do at 2 blocks up to the octave at 9; a stage the caster's goo
 * cannot pay for, and a hold resting on a stage, sound nothing more.
 * decision blast-charge-wubs-higher-at-each-stage
 */
final class ChargeWubs {

    /**
     * The major scale in just intonation, do re mi fa sol la ti do', each a
     * pitch over the opening stage's; a stage past the octave holds it.
     */
    static final float[] MAJOR_SCALE = {1f, 9f / 8, 5f / 4, 4f / 3, 3f / 2, 5f / 3, 15f / 8, 2f};
    /** The stage sounded before any, so the opening stage sounds. */
    private static final double NONE_SOUNDED = Double.NEGATIVE_INFINITY;

    private double soundedStage = NONE_SOUNDED;

    /**
     * A sound the charge plays as it reaches a stage.
     *
     * @param sound the sound's id
     * @param pitch its pitch
     */
    record Cue(Identifier sound, float pitch) {
    }

    /**
     * The wub a tick of the hold sounds, when it reaches a stage not sounded
     * yet that the goo pays for.
     *
     * @param charge    the cast's sizing charge
     * @param heldTicks the ticks the hold has run
     * @param paidFor   the largest stage the caster's goo pays for, in blocks
     * @return the cue, empty on a tick reaching no new stage
     */
    Optional<Cue> tick(Charge charge, int heldTicks, double paidFor) {
        double stage = Math.min(charge.stageAt(heldTicks), paidFor);
        if (stage <= soundedStage) {
            return Optional.empty();
        }
        soundedStage = stage;
        int degree = Math.clamp((long) (stage - charge.openingSize()), 0, MAJOR_SCALE.length - 1);
        return Optional.of(new Cue(GooSoundIds.CHARGE_WUB, MAJOR_SCALE[degree]));
    }

    /** Lets the next charge sound its stages afresh. */
    void reset() {
        soundedStage = NONE_SOUNDED;
    }
}
