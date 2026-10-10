package com.mercuriusxeno.goo.ability.bio;

/**
 * The health one second of Bio's toxin takes: the share of max health the
 * throw named, times the toxin's amplitude.
 * bio-toxin-stacks-to-amplitude-two
 */
public final class ToxinDrain {

    private ToxinDrain() {
    }

    /**
     * The health one second of toxin takes.
     *
     * @param share     the share of max health a second at amplitude one
     * @param maxHealth the mob's max health
     * @param amplifier the effect's amplifier, amplitude less one
     * @return the damage the second deals
     */
    public static float perSecond(double share, float maxHealth, int amplifier) {
        return (float) (share * maxHealth * (amplifier + 1));
    }
}
