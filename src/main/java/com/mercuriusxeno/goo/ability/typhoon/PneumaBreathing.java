package com.mercuriusxeno.goo.ability.typhoon;

import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.world.entity.player.Player;

/**
 * The server's half of Pneuma: each tick a player who cannot breathe takes a
 * breath, the breath Pneuma banked answers first. NeoForge's breathe event
 * hands LunarBreathing that breath, underwater and wherever else air drains
 * as underwater, so every airless place reads the reserve and the immunity
 * through the one path.
 * pneuma-overfills-the-air-meter-with-a-brief-airless-immunity
 */
public final class PneumaBreathing {

    /** The air vanilla takes from a player who cannot breathe each tick. */
    private static final int ONE_BREATH = 1;

    private PneumaBreathing() {
    }

    /**
     * Takes one breath from a player's Pneuma: none inside the immunity
     * window, else from the reserve while it holds air.
     *
     * @param player the player who cannot breathe
     * @return true when Pneuma covered the breath, so the vanilla air stays
     */
    public static boolean coversBreath(Player player) {
        Breath breath = player.getData(GooAttachments.BREATH);
        if (!breath.stands()) {
            return false;
        }
        Breath.Spent spent = breath.spent(ONE_BREATH, player.level().getGameTime());
        if (!spent.breath().equals(breath)) {
            player.setData(GooAttachments.BREATH, spent.breath());
        }
        return spent.fromAir() == 0;
    }
}
