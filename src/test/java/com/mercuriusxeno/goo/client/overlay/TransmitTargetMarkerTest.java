package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.IndicatorShowing;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.TransmitStep;
import com.mercuriusxeno.goo.ability.program.Step;
import com.mercuriusxeno.goo.ability.program.TeleportMode;
import com.mercuriusxeno.goo.ability.program.TeleportStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Transmit's marker shows while its press is held and for no other
 * ability, and its column motes climb within the column
 * (decision fungal-shift-blinks-to-the-aimed-fungus).
 */
class TransmitTargetMarkerTest {

    private static ClientAbility ability(List<Step> behaviors) {
        return new ClientAbility(Identifier.parse("goo:shroom_transmit"), "transmit", "", 0, List.of(), behaviors,
                0, Delivery.of(DeliveryKind.SELF), AbilityBadge.SELF, List.of(), AbilityArea.NONE,
                IndicatorShowing.HELD);
    }

    @Test
    void aHeldTransmitShowsTheMarker() {
        ClientAbility transmit = ability(List.of(new TransmitStep(Expr.literal(64))));
        assertTrue(TransmitTargetMarker.showsMarker(transmit, true));
        assertFalse(TransmitTargetMarker.showsMarker(transmit, false));
    }

    @Test
    void aBlinkOrNoAbilityShowsNoMarker() {
        ClientAbility blink = ability(List.of(new TeleportStep(TeleportMode.THROWER_LOOK, Expr.literal(8))));
        assertFalse(TransmitTargetMarker.showsMarker(blink, true));
        assertFalse(TransmitTargetMarker.showsMarker(null, true));
    }

    @Test
    void columnMotesClimbWithinTheColumn() {
        for (int i = 0; i < TransmitTargetMarker.COLUMN_MOTES; i++) {
            double height = TransmitTargetMarker.columnMote(i, 3.7)[1];
            assertTrue(height >= 0 && height < 2.2, String.valueOf(height));
        }
    }
}
