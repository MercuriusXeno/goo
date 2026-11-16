package com.mercuriusxeno.goo.block.ability;

/**
 * The two choices a sculk charge makes crossing a Host cell: the charge it
 * carries on, and the layers the cell spends turning the block under it to
 * sculk.
 * sculk-charge-runs-along-host-goo-without-weakening
 */
public final class HostSpread {

    private HostSpread() {
    }

    /**
     * The charge a cursor carries out of a Host cell: all it carried in, where
     * on ordinary sculk it would decay.
     *
     * @param chargeIn the charge the cursor carried into the cell
     * @return the charge it carries on
     */
    public static int chargeOut(int chargeIn) {
        return chargeIn;
    }

    /**
     * The layers a Host cell spends turning the block under it to sculk: a
     * block of sculk's worth where that block is ground sculk replaces and the
     * cell holds that many, none otherwise.
     *
     * @param layers       the layers the cell holds
     * @param turnsToSculk whether the block under the cell is ground sculk replaces
     * @param sculkLayers  the layers a block of sculk spends, the Host step's tunable
     * @return the layers spent, 0 where the block under the cell stays
     */
    public static int layersSpent(int layers, boolean turnsToSculk, int sculkLayers) {
        return turnsToSculk && layers >= sculkLayers ? sculkLayers : 0;
    }
}
