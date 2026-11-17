package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.item.GooGloveItem;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.entity.player.Player;

/**
 * The side of the body Knives' knives leave from: the glove hand's, which
 * every cast drawn from the lifted palm blob reads too.
 * decision knives-blob-floats-ahead-of-the-hand-and-spews-the-knives
 */
public final class KnifeRainSling {

    private KnifeRainSling() {
    }

    /**
     * Whether the glove is in the player's right hand: the main hand's arm
     * when the glove is there, the other arm when it is in the off hand.
     *
     * @param player the player
     * @return true for a glove in the right hand
     */
    public static boolean gloveOnTheRight(Player player) {
        boolean inMain = player.getMainHandItem().getItem() instanceof GooGloveItem;
        HumanoidArm gloveArm = inMain ? player.getMainArm() : player.getMainArm().getOpposite();
        return gloveArm == HumanoidArm.RIGHT;
    }
}
