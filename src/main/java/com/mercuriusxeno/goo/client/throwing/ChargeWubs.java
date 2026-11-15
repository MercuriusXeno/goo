package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.Charge;
import com.mercuriusxeno.goo.registry.GooSoundIds;
import net.minecraft.resources.Identifier;
import java.util.Optional;

/**
 * The sound a charge-sized cast's charge makes as it grows: the outsider
 * wub once each time the hold reaches a stage, at pitch 1.0 for the stage it
 * opens at and 0.125 higher for each stage after, so Blast wubs 1.0 at 2
 * blocks up to 1.625 at 7; a stage the caster's goo cannot pay for, and a
 * hold resting on a stage, sound nothing more.
 * decision blast-charge-wubs-higher-at-each-stage
 */
final class ChargeWubs {

    /** The pitch the opening stage wubs at. */
    static final float OPENING_PITCH = 1.0f;
    /** How much higher each stage after the opening wubs. */
    static final float PITCH_PER_STAGE = 0.125f;
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
        float pitch = OPENING_PITCH + (float) (stage - charge.openingSize()) * PITCH_PER_STAGE;
        return Optional.of(new Cue(GooSoundIds.ZONE_WUB, pitch));
    }

    /** Lets the next charge sound its stages afresh. */
    void reset() {
        soundedStage = NONE_SOUNDED;
    }
}
