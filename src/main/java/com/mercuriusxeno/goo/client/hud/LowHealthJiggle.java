package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.ability.hearts.HeartOverlay;
import com.mercuriusxeno.goo.ability.reserve.Reserve;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

/**
 * Decides whether the health bar shakes for low health: vanilla shakes it at
 * two hearts or under of health and absorption, and Goo counts the half
 * hearts other abilities fill beside them, so a bar backed by stone, nether,
 * ember, bark, frost, crystal or reserve hearts stays steady. Vanilla's own
 * hearts and Goo's overlay both read this one answer.
 * health-bar-stays-steady-while-filled-hearts-stand
 */
public final class LowHealthJiggle {

    /** Vanilla jiggles the bar at or under two hearts of health and absorption. */
    static final int JIGGLE_HEALTH = 4;

    private LowHealthJiggle() {
    }

    /**
     * Answers whether the bar shakes.
     *
     * @param health       the real health, rounded up
     * @param absorption   the absorption points, rounded up
     * @param filledHalves the half hearts other abilities fill
     * @return true when the bar is low enough to jiggle
     */
    public static boolean jiggles(int health, int absorption, int filledHalves) {
        return health + absorption + filledHalves <= JIGGLE_HEALTH;
    }

    /**
     * The half hearts other abilities fill: every shield half a standing
     * overlay holds, over missing hearts or present ones, and every reserve
     * half banked behind the bar.
     *
     * @param overlay the player's heart overlay
     * @param reserve the player's reserve
     * @return the filled halves
     */
    public static int filledHalves(HeartOverlay overlay, Reserve reserve) {
        int shieldHalves = overlay.stands() ? overlay.shieldHalves() : 0;
        return shieldHalves + reserve.heartHalves();
    }

    /**
     * Answers whether a player's health bar shakes, read from the player's
     * health, absorption, heart overlay and reserve.
     *
     * @param player the player whose bar draws
     * @return true when the bar is low enough to jiggle
     */
    public static boolean jiggles(Player player) {
        return jiggles(Mth.ceil(player.getHealth()), Mth.ceil(player.getAbsorptionAmount()),
                filledHalves(player.getData(GooAttachments.HEART_OVERLAY), player.getData(GooAttachments.RESERVE)));
    }
}
