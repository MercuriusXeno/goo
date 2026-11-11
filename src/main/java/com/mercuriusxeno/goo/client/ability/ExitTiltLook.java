package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.quantum.ExitTilt;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Quaternionf;

/**
 * Draws a body come out of a linked pair tilted the way it went in, easing
 * upright: a body dropping feet first into a floor portal comes out of a
 * wall portal feet first and swings down to standing over about half a
 * second, turned about the point it was carried by, its eye for a player
 * and its middle for any other body. The hitbox stands upright throughout;
 * only the drawn body turns.
 * bodies-emerge-along-their-travel-and-ease-upright
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class ExitTiltLook {

    /** The render data holding how far the body is still turned, and about what. */
    public static final ContextKey<Turned> TILT =
            new ContextKey<>(Identifier.fromNamespaceAndPath(Goo.MODID, "exit_tilt"));

    private static final float HALF = 0.5f;

    /**
     * The turn a frame draws a body with.
     *
     * @param turn  the turn
     * @param pivot how high over the feet the body turns about
     */
    public record Turned(Quaternionf turn, float pivot) {
    }

    private ExitTiltLook() {
    }

    /**
     * Stamps the tilt a body still holds onto its render state.
     *
     * @param entity the body
     * @param state  its render state
     */
    public static void stampTilt(Entity entity, EntityRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        double now = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        ClientExitTilts.tiltOf(entity.getId(), now).ifPresent(tilt -> state.setRenderData(TILT,
                new Turned(turnOf(tilt), entity instanceof Player ? entity.getEyeHeight()
                        : entity.getBbHeight() * HALF)));
    }

    private static Quaternionf turnOf(ExitTilt tilt) {
        return new Quaternionf().rotationAxis((float) Math.toRadians(tilt.degrees()), (float) tilt.axis().x(),
                (float) tilt.axis().y(), (float) tilt.axis().z());
    }

    /**
     * Turns a tilted body before it draws, about its pivot.
     *
     * @param event the living render event, before the body draws
     */
    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?, ?> event) {
        Turned turned = event.getRenderState().getRenderData(TILT);
        if (turned != null) {
            event.getPoseStack().pushPose();
            event.getPoseStack().translate(0, turned.pivot(), 0);
            event.getPoseStack().mulPose(turned.turn());
            event.getPoseStack().translate(0, -turned.pivot(), 0);
        }
    }

    /**
     * Closes the turn opened around a tilted body.
     *
     * @param event the living render event, after the body drew
     */
    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?, ?> event) {
        if (event.getRenderState().getRenderData(TILT) != null) {
            event.getPoseStack().popPose();
        }
    }
}
