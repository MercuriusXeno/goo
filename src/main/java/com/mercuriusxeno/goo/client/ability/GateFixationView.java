package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.gate.GateFixation;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * The caster's hold while Astral's gate takes them: the gaze turns onto the moon or the blue star and cannot look
 * away, and the body takes no step. The field of view stays as it stands; the moon comes to the caster
 * (GateTripView) rather than the view closing in on it.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GateFixationView {

    /** The share of the remaining turn the gaze makes each tick, so it settles onto its target in a few ticks. */
    private static final float TURN_SHARE = 0.35F;

    private GateFixationView() {
    }

    /**
     * Takes every step and jump from the held caster's input.
     *
     * @param event the movement input event
     */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (event.getEntity().getData(GooAttachments.GATE_FIXATION).stands()) {
            event.getInput().keyPresses = Input.EMPTY;
            event.getInput().moveVector = Vec2.ZERO;
        }
    }

    /**
     * Turns the held caster's gaze onto the fixation, whatever the mouse does.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        GateFixation fixation = player.getData(GooAttachments.GATE_FIXATION);
        if (!fixation.stands()) {
            return;
        }
        Vec2 facing = fixation.direction().rotation();
        player.setXRot(Mth.rotLerp(TURN_SHARE, player.getXRot(), facing.x));
        player.setYRot(Mth.rotLerp(TURN_SHARE, player.getYRot(), facing.y));
        player.setYHeadRot(player.getYRot());
    }
}
