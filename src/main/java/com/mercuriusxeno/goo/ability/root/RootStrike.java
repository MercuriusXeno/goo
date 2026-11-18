package com.mercuriusxeno.goo.ability.root;

/**
 * What a hit on a thing does to the vines holding it. The vines deal no
 * damage of their own; any damage the held thing takes ends every vine at
 * once, the thing taking that damage, and the Vines cell that last gave it
 * vines keeps one layer fewer where it still stands. Fire to a held thing
 * that burns lands at the fire factor.
 * vines-deal-no-thorn-damage-and-attacking-the-mob-frees-it
 * entangled-mob-taking-damage-eats-a-layer-of-vines-goo
 * vines-unpack-root-and-thorn
 *
 * @param vinesLeft  the vines the thing holds after the hit
 * @param damage     the damage the hit deals
 * @param layersKept the layers the cell that last gave vines keeps, none where it is gone
 */
public record RootStrike(int vinesLeft, float damage, int layersKept) {

    /**
     * The hit's outcome.
     *
     * @param vinesHeld  the vines holding the thing as the hit lands
     * @param amount     the hit's damage
     * @param feedsFire  whether the hit is fire the thing burns from
     * @param fireFactor what fire damage to a held thing is multiplied by
     * @param cellLayers the layers the cell that last gave vines holds, none where it is gone
     * @return the vines left, the damage dealt and the layers the cell keeps
     */
    public static RootStrike of(int vinesHeld, float amount, boolean feedsFire, float fireFactor, int cellLayers) {
        if (vinesHeld <= 0 || amount <= 0f) {
            return new RootStrike(vinesHeld, amount, cellLayers);
        }
        float dealt = feedsFire ? amount * fireFactor : amount;
        return new RootStrike(0, dealt, Math.max(0, cellLayers - 1));
    }
}
