package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.ArmPoseKind;
import com.mercuriusxeno.goo.ability.Charge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.client.throwing.GloveThrowSender;
import org.mockito.MockedStatic;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Every player's glove selecting Knives, its charge Knives' own, the local
 * hold reaching a set share of a full charge, and the caster of a
 * {@link SprayingClient} winding up and letting it go tick by tick, each tick
 * holding the floating blob as the client tick does. Close it to restore the
 * statics it swaps.
 */
public final class KnivesCharging implements AutoCloseable {

    /** Knives' charge, as crystal_knives.json gives it: 5 stages of 12 ticks. */
    public static final Charge KNIVES = new Charge(60, 5, 12);

    private final MockedStatic<GloveThrowSender> sender;

    private KnivesCharging(float share) {
        Delivery knives = mock(Delivery.class);
        when(knives.charge()).thenReturn(KNIVES);
        when(knives.chargeShare(anyInt())).thenReturn(share);
        sender = mockStatic(GloveThrowSender.class);
        sender.when(() -> GloveThrowSender.heldSelection(any()))
                .thenReturn(new GloveSelection("goo:crystal", "goo:crystal_knives"));
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
     * Winds the caster's arm up at a tick and holds its charge every tick through another.
     *
     * @param client the client
     * @param from   the tick the wind-up begins
     * @param last   the last tick held
     */
    public void windUp(SprayingClient client, long from, long last) {
        client.at(from);
        AbilityPoses.pose(SprayingClient.CASTER, ArmPoseKind.WIND_UP, true);
        holdThrough(client, from, last);
    }

    /**
     * Lets the caster's hold go at a tick, dropping the arm as letting go of
     * right click does, and runs the client's ticks through another.
     * decision knives-form-on-right-click-and-throw-on-left-click
     *
     * @param client the client
     * @param letGo  the tick the hold is let go
     * @param last   the last tick run
     */
    public void letGo(SprayingClient client, long letGo, long last) {
        client.at(letGo);
        AbilityPoses.pose(SprayingClient.CASTER, ArmPoseKind.NONE, true);
        holdThrough(client, letGo, last);
    }

    /**
     * Runs the client's ticks, each holding every floating blob as the client tick does.
     *
     * @param client the client
     * @param from   the first tick
     * @param last   the last tick
     */
    public void holdThrough(SprayingClient client, long from, long last) {
        for (long tick = from; tick <= last; tick++) {
            client.at(tick);
            KnivesCharge.holdTheFloatingBlobs(tick);
        }
    }

    @Override
    public void close() {
        sender.close();
        AbilityPoses.clear();
    }
}
