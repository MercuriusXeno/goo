package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.item.SprayerNozzle;
import com.mercuriusxeno.goo.throwing.ConeShape;

/**
 * What a held spray carries beside its ability: where it pays from, how much it throws a tick and the cone it
 * throws in, the glove's at the inventory, its residual's own rate and its residual's own round cone, the
 * sprayer's at its canister, its wheel's flow and its nozzle's shape.
 * decision sprayer-holds-a-canister-sideways-like-a-caulk-gun
 * decision spray-item-sprays-more-or-less-per-tick
 * decision spray-item-takes-different-nozzles-doing-different-things
 * decision mister-draws-mist-and-lays-one-layer-on-bare-spots
 *
 * @param paysFrom where what the spray lays pays from
 * @param flowRate the multiple of the residual's drips a tick the spray throws
 * @param nozzle   the nozzle, which shapes the cone, scales the drips and the reach, or mists
 */
public record HeldSpray(PaysFrom paysFrom, double flowRate, SprayerNozzle nozzle) {

    /** The glove's spray: the inventory pays and the residual throws its own drips a tick in its own cone. */
    public static final HeldSpray GLOVE = new HeldSpray(PaysFrom.INVENTORY, 1.0, SprayerNozzle.SPRAY);

    /**
     * @param delivery the residual's delivery
     * @return the cone this spray throws the residual's drips in
     */
    public ConeShape shapeOf(Delivery delivery) {
        return nozzle.shape(delivery.coneDegrees());
    }

    /**
     * @return the multiple of the residual's drips a tick the spray throws: the flow's times the nozzle's
     */
    public double dripRate() {
        return flowRate * nozzle.dripScale();
    }

    /**
     * @return the multiple of the residual's exit speed the spray throws at
     */
    public double reachScale() {
        return nozzle.reachScale();
    }

    /**
     * @return whether the spray mists, laying a layer on each bare spot its cone covers, in place of drips
     */
    public boolean mists() {
        return nozzle.mists();
    }
}
