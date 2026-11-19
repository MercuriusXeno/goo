package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.nether.Invert;
import com.mercuriusxeno.goo.ability.nether.InvertPresentation;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ViewportEvent;

/**
 * An inverted player's own view, read through InvertPresentation: its first
 * person camera rolls half a turn with its body, so the world reads as if it
 * stood right side up on its ceiling, and its mouse and strafe keys turn and
 * move it toward the sides it sees. The hands, the held items and the HUD
 * draw in the screen's own frame, which the roll leaves as it was, so they
 * stand where they stand for an upright player.
 * invert-flips-your-gravity-and-lets-you-walk-on-ceilings
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class InvertView {

    private InvertView() {
    }

    /**
     * Rolls the camera of an inverted player seen through its own eyes, after
     * any tilt has turned it.
     *
     * @param event the camera's angles for this frame
     */
    @SubscribeEvent(priority = EventPriority.LOW)
    public static void onComputeCameraAngles(ViewportEvent.ComputeCameraAngles event) {
        Entity viewer = event.getCamera().entity();
        if (viewer instanceof Player player) {
            event.setRoll(event.getRoll() + presentationOf(player).cameraRoll());
        }
    }

    /**
     * The sign the mouse's turn and the strafe keys take for a player.
     *
     * @param player the local player
     * @return -1 while its camera is rolled, else 1
     */
    public static float steerSign(Player player) {
        return presentationOf(player).steerSign();
    }

    private static InvertPresentation presentationOf(Player player) {
        boolean firstPerson = Minecraft.getInstance().options.getCameraType().isFirstPerson();
        return InvertPresentation.of(Invert.isInverted(player), firstPerson);
    }
}
