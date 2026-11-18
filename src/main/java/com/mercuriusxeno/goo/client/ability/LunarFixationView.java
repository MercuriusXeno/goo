package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.astral.LunarFixation;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.phys.Vec2;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.MovementInputUpdateEvent;

/**
 * The caster's hold while Lunar takes them: the look stays where the caster held it on the moon or the blue planet
 * as the fixation began and cannot look away, and the body takes no step and no turn. The field of view stays as it
 * stands; the moon comes to the caster (LunarTripView) rather than the view closing in on it.
 * decision gate-fixates-your-view-on-the-moon-until-it-takes-you
 * decision lunar-just-looks-at-the-moon-without-turning-your-body
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class LunarFixationView {

    /** The look the local caster held as their standing fixation began. */
    private static HeldLook heldLook = HeldLook.NONE;

    private LunarFixationView() {
    }

    /**
     * Takes every step and jump from the held caster's input.
     *
     * @param event the movement input event
     */
    @SubscribeEvent
    public static void onMovementInput(MovementInputUpdateEvent event) {
        if (event.getEntity().getData(GooAttachments.LUNAR_FIXATION).stands()) {
            event.getInput().keyPresses = Input.EMPTY;
            event.getInput().moveVector = Vec2.ZERO;
        }
    }

    /**
     * Holds the held caster's look where it stood as the fixation began, whatever the mouse does.
     *
     * @param event the client tick event
     */
    @SubscribeEvent
    public static void onClientTick(ClientTickEvent.Post event) {
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null) {
            return;
        }
        LunarFixation fixation = player.getData(GooAttachments.LUNAR_FIXATION);
        if (!fixation.stands()) {
            return;
        }
        heldLook = lookOnTick(heldLook, fixation, player.getXRot(), player.getYRot());
        player.setXRot(heldLook.pitch());
        player.setYRot(heldLook.yaw());
        player.setYHeadRot(heldLook.yaw());
    }

    /**
     * The look a held caster keeps, and the fixation it was taken for.
     *
     * @param startedAt the game time of the fixation the look was taken for
     * @param pitch     the pitch held, in degrees
     * @param yaw       the yaw held, in degrees
     */
    record HeldLook(long startedAt, float pitch, float yaw) {
        /** No look held yet. */
        static final HeldLook NONE = new HeldLook(Long.MIN_VALUE, 0F, 0F);
    }

    /**
     * The look a held caster ends a tick on: the one they held on the fixation's first tick, so Lunar turns
     * neither the view nor the body.
     * decision lunar-just-looks-at-the-moon-without-turning-your-body
     *
     * @param kept     the look kept from the tick before
     * @param fixation the fixation that holds the caster
     * @param pitch    the caster's pitch this tick, in degrees
     * @param yaw      the caster's yaw this tick, in degrees
     * @return the look to set
     */
    static HeldLook lookOnTick(HeldLook kept, LunarFixation fixation, float pitch, float yaw) {
        if (kept.startedAt() == fixation.startedAt()) {
            return kept;
        }
        return new HeldLook(fixation.startedAt(), pitch, yaw);
    }
}
