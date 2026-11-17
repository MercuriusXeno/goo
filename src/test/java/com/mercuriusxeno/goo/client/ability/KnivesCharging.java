package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.ArmPoseKind;
import com.mercuriusxeno.goo.ability.Charge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import com.mercuriusxeno.goo.item.GooCasters;
import org.mockito.MockedStatic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Every player's glove selecting Knives, its charge Knives' own, the local
 * hold reaching a set share of a full charge, and the caster of a
 * {@link SprayingClient} holding it tick by tick. Close it to restore the
 * statics it swaps.
 */
public final class KnivesCharging implements AutoCloseable {

    /** Knives' charge, as crystal_knives.json gives it: one knife per 3 ticks up to 10. */
    public static final Charge KNIVES = new Charge(30, 10, 3);

    private final MockedStatic<GloveThrowSender> sender;
    private final MockedStatic<GooCasters> casters;

    private KnivesCharging(float share) {
        Delivery knives = mock(Delivery.class);
        when(knives.charge()).thenReturn(KNIVES);
        when(knives.formsKnives()).thenReturn(true);
        when(knives.chargeShare(anyInt())).thenReturn(share);
        casters = mockStatic(GooCasters.class, CALLS_REAL_METHODS);
        casters.when(() -> GooCasters.heldSelection(any()))
                .thenReturn(new GloveSelection("goo:crystal", "goo:crystal_knives"));
        sender = mockStatic(GloveThrowSender.class);
        sender.when(() -> GloveThrowSender.selectedDelivery(any())).thenReturn(knives);
    }

    /**
     * Knives selected, the local hold at a share of a full charge.
     *
     * @param share the share the local hold reaches, 0 to 1
     * @return the selection
     */
    public static KnivesCharging heldTo(float share) {
        return new KnivesCharging(share);
    }

    /**
     * Holds the caster's Knives press in an arm frame tick by tick: the arm
     * posed in it, the press lifting the blob each tick as the glove press does.
     *
     * @param client the client
     * @param frame  the arm frame the hold poses
     * @param from   the first tick held
     * @param last   the last tick held
     */
    public void holdIn(SprayingClient client, ArmPoseKind frame, long from, long last) {
        client.at(from);
        AbilityPoses.pose(SprayingClient.CASTER, frame, true);
        for (long tick = from; tick <= last; tick++) {
            client.at(tick);
            LiftedPalmBlob.held(SprayingClient.CASTER, SprayingClient.SENT_GLOVE, tick);
        }
    }

    @Override
    public void close() {
        sender.close();
        casters.close();
        AbilityPoses.clear();
    }
}
