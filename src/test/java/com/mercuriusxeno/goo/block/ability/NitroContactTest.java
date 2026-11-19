package com.mercuriusxeno.goo.block.ability;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What each kind of thing acting on a Nitro cell answers: touch, heat,
 * shock, pulse and strike set it off, and Vacuum and an unstable goo item
 * leave it be, so the goo can be reclaimed safely.
 * nitro-explodes-when-touched-heated-shocked-pulsed-or-struck
 * vacuum-and-unstable-goo-items-do-not-set-nitro-off
 */
class NitroContactTest {

    @ParameterizedTest
    @EnumSource(value = NitroContact.class, names = {"TOUCH", "HEAT", "SHOCK", "PULSE", "STRIKE"})
    void touchHeatShockPulseAndStrikeEachDetonate(NitroContact contact) {
        assertEquals(NitroReaction.DETONATE, contact.reaction(), contact.name());
    }

    @ParameterizedTest
    @EnumSource(value = NitroContact.class, names = {"VACUUM", "UNSTABLE_GOO_ITEM"})
    void vacuumAndAnUnstableGooItemEachHold(NitroContact contact) {
        assertEquals(NitroReaction.HOLD, contact.reaction(), contact.name());
    }
}
