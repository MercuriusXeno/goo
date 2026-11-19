package com.mercuriusxeno.goo.client.ability;

import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;

/**
 * Where a player's glove hand stands before this client has drawn their
 * model, the place DrawnPalm falls back to: before the chest, on the glove
 * side. A forming knife
 * sounds from the server's count (HoveringKnives), not from this client's hold.
 * decision knives-and-javelins-sound-as-each-forms-and-is-thrown
 */
public final class KnivesCharge {

    /** The hand's place in the world: chest height, ahead of the body, toward the glove side. */
    private static final double CHEST_HEIGHT = 1.05;
    private static final double CROUCH_DROP = 0.3;
    private static final double AHEAD = 0.42;
    private static final double GLOVE_SIDE = 0.35;
    private static final float RIGHT_ANGLE = 90f;

    private KnivesCharge() {
    }

    /**
     * Where a player's glove hand is: before the chest, on the glove side.
     *
     * @param player     the player
     * @param gloveRight whether the glove is in the right hand
     * @param partial    the partial tick
     * @return the hand's place in the world
     */
    static Vec3 handAt(Player player, boolean gloveRight, float partial) {
        float bodyYaw = Mth.rotLerp(partial, player.yBodyRotO, player.yBodyRot);
        Vec3 ahead = Vec3.directionFromRotation(0f, bodyYaw);
        Vec3 glove = Vec3.directionFromRotation(0f, bodyYaw + (gloveRight ? RIGHT_ANGLE : -RIGHT_ANGLE));
        return player.getPosition(partial).add(0, CHEST_HEIGHT - (player.isCrouching() ? CROUCH_DROP : 0), 0)
                .add(ahead.scale(AHEAD)).add(glove.scale(GLOVE_SIDE));
    }
}
