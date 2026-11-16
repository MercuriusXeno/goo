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
import net.neoforged.neoforge.client.event.ComputeFovModifierEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * The caster's view while Astral's gate holds them: the gaze turns onto the moon or the blue star and cannot look
 * away, the body takes no step, and the field of view closes in on it until the gate carries them.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class GateFixationView {

    /** The share of the normal field of view left at the fixation's end. */
    static final float CLOSEST_FOV = 0.15F;
    /** The share of the remaining turn the gaze makes each tick, so it settles onto its target in a few ticks. */
    private static final float TURN_SHARE = 0.35F;

    private GateFixationView() {
    }

    /**
     * The field of view's scale as the fixation runs: unchanged at its start, closing in faster as it ends.
     *
     * @param progress the share of the fixation run, zero to one
     * @return the scale on the field of view
     */
    static float fovScale(float progress) {
        return 1F - (1F - CLOSEST_FOV) * progress * progress;
    }

    /**
     * Closes the field of view in on the fixation.
     *
     * @param event the field of view event
     */
    @SubscribeEvent
    public static void onComputeFov(ComputeFovModifierEvent event) {
        GateFixation fixation = event.getPlayer().getData(GooAttachments.GATE_FIXATION);
        if (fixation.stands()) {
            float progress = fixation.progress(event.getPlayer().level().getGameTime());
            event.setNewFovModifier(event.getNewFovModifier() * fovScale(progress));
        }
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
