package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.ability.program.FungusAim;
import com.mercuriusxeno.goo.ability.program.ShiftStep;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;

/**
 * Whether the local player stands near enough a fungus to start a Fungal
 * Shift, read through the check the server's shift makes and kept for the
 * tick and block it was read at, so the glove's glow and Sight's eye read it
 * every frame for nothing (decision fungal-shift-blinks-to-the-aimed-fungus).
 */
public final class FungusNearby {

    private static final String FUNGAL_SHIFT = "goo:shroom_fungal_shift";
    /** Fungal Shift's reach to a fungus when the player holds no synced copy of it. */
    private static final double FALLBACK_NEAR = 3;

    private static boolean near;
    private static long readAt = Long.MIN_VALUE;
    private static BlockPos readFrom = BlockPos.ZERO;

    private FungusNearby() {
    }

    /**
     * Whether the local player stands within Fungal Shift's reach of a fungus.
     *
     * @return true near a fungus, false with no player or none near
     */
    public static boolean isNear() {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return false;
        }
        long tick = player.level().getGameTime();
        BlockPos at = player.blockPosition();
        if (tick != readAt || !at.equals(readFrom)) {
            near = FungusAim.standsNearFungus(player.level(), player, nearReach());
            readAt = tick;
            readFrom = at;
        }
        return near;
    }

    private static double nearReach() {
        ClientAbility shift = AbilitySyncHandler.findAbility(FUNGAL_SHIFT);
        return shift == null ? FALLBACK_NEAR : ShiftStep.fungusNear(shift.behaviors()).orElse(FALLBACK_NEAR);
    }
}
