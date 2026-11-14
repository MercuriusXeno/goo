package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.program.BranchStep;
import com.mercuriusxeno.goo.ability.program.LeafSteps;
import com.mercuriusxeno.goo.ability.program.ParticlesStep;
import com.mercuriusxeno.goo.ability.program.SoundStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.TargetStep;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers the shipped mob abilities cast like Charm: each names the envelop
 * delivery with no charge holding back the release, and Rewind wears the mob badge
 * (decision charm-like-abilities-lock-the-highlighted-mob-on-right-click).
 */
class CharmLikeAbilitiesTest {

    @ParameterizedTest
    @ValueSource(strings = {"hex_charm", "quantum_phase_other", "zoo_rally", "zoo_shape", "zoo_spawn", "typhoon_shulk",
            "yore_rewind"})
    void namesTheEnvelopDeliveryWithNoWindUp(String ability) {
        Delivery delivery = AbilityJson.decode(ability).delivery();
        assertTrue(delivery.envelops() && !delivery.charges(),
                ability + " delivers by " + delivery.kind() + " winding up " + delivery.chargeTicks() + " ticks");
    }

    /** Rewind leaves the channeled badge for the mob badge the other charm-like casts wear. */
    @Test
    void rewindWearsTheMobBadge() {
        assertEquals(AbilityBadge.MOB, AbilityJson.decode("yore_rewind").badge());
    }

    /**
     * Charm's missed roll fizzles at the mob, smoke and the extinguishing
     * hiss, so a failed attempt never reads as no attempt (operator: "the
     * attempt needs a fanfare, and a failure").
     */
    @Test
    void charmsMissedRollFizzlesAtTheMob() {
        List<Step> missed = AbilityJson.decode("hex_charm").behaviors().stream()
                .filter(TargetStep.class::isInstance).map(TargetStep.class::cast)
                .flatMap(target -> target.steps().stream())
                .filter(BranchStep.class::isInstance).map(BranchStep.class::cast)
                .flatMap(roll -> roll.otherwise().stream())
                .toList();
        assertEquals(List.of(FIZZLE_SOUND), missed.stream().filter(SoundStep.class::isInstance)
                .map(step -> ((SoundStep) step).sound()).toList());
        assertEquals(List.of(FIZZLE_SMOKE), missed.stream().filter(ParticlesStep.class::isInstance)
                .map(step -> ((ParticlesStep) step).particle()).toList());
        assertEquals(1, missed.stream().filter(step -> step.type() == LeafSteps.ENVELOP_BURST.type()).count(),
                "the missed roll bursts the slime shell off the mob");
    }

    private static final Identifier FIZZLE_SOUND = Identifier.withDefaultNamespace("block.fire.extinguish");
    private static final Identifier FIZZLE_SMOKE = Identifier.withDefaultNamespace("smoke");
}
