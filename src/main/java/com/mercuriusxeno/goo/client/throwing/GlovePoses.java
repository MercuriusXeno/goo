package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.ArmPose;
import com.mercuriusxeno.goo.ability.ArmPoseKind;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.client.ability.AbilityPoses;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.network.GlovePosePayload;
import com.mercuriusxeno.goo.network.KnifeRainSling;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket;
import net.minecraft.world.InteractionHand;
import org.jspecify.annotations.Nullable;

/**
 * Poses the local player's glove arm from the selected ability's pose as a
 * press holds, lets go or drops, and tells the server, which shows the
 * players watching the same frame; a blob thrown swings the arm as vanilla
 * does instead, which vanilla shows the players watching.
 * decision ability-json-names-its-arm-pose
 * decision use-key-throw-swings-no-more
 */
public final class GlovePoses {

    private GlovePoses() {
    }

    /**
     * Holds the arm in the selection's pose while the press is held.
     *
     * @param player the local player
     */
    public static void hold(LocalPlayer player) {
        ArmPose pose = selectedPose(player);
        if (pose != null) {
            show(player, pose.holdFrame());
        }
    }

    /**
     * Plays the selection's release as a sent throw lets go: a blob thrown
     * swings the arm, and a pose with no release of its own eases it down.
     * decision use-key-throw-swings-no-more
     *
     * @param player the local player
     * @param hand   the hand the press came from, which a swing swings
     */
    public static void release(LocalPlayer player, InteractionHand hand) {
        ArmPose pose = selectedPose(player);
        show(player, pose == null ? ArmPoseKind.NONE : pose.releaseFrame());
        if (pose != null && pose.swingsOnRelease()) {
            player.swing(hand);
        }
    }

    /**
     * Eases the arm back down from whatever it held.
     *
     * @param player the local player
     */
    public static void drop(LocalPlayer player) {
        show(player, ArmPoseKind.NONE);
    }

    private static void show(LocalPlayer player, ArmPoseKind frame) {
        AbilityPoses.pose(player.getId(), frame, KnifeRainSling.gloveOnTheRight(player));
        player.connection.send(new ServerboundCustomPayloadPacket(new GlovePosePayload(frame)));
    }

    /**
     * The pose of the ability the held glove selects.
     *
     * @param player the local player
     * @return the pose, or null with no selection or no synced copy
     */
    private static @Nullable ArmPose selectedPose(LocalPlayer player) {
        GloveSelection selection = GloveThrowSender.heldSelection(player);
        ClientAbility ability = selection == null ? null : AbilitySyncHandler.findAbility(selection.abilityId());
        return ability == null ? null : ability.pose();
    }
}
