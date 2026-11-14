package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.resources.Identifier;
import net.minecraft.util.context.ContextKey;
import net.minecraft.world.entity.Entity;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLivingEvent;
import org.joml.Quaternionf;

/**
 * Lays a downed mob on its side for as long as it lies downed: the body
 * tipped a quarter turn about the line it faces along, as vanilla's death
 * fall tips it, and its walk frozen. The entity's own death time is left
 * alone, since vanilla removes an entity whose death time runs out; only the
 * drawn body turns.
 * rallied-mob-falls-downed-instead-of-dying
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class DownedPose {

    /** The render data holding the turn that lays a downed body on its side. */
    public static final ContextKey<Quaternionf> FALLEN =
            new ContextKey<>(Identifier.fromNamespaceAndPath(Goo.MODID, "downed_fallen"));

    /** A quarter turn, the tip from standing onto the side. */
    private static final float ON_ITS_SIDE_DEGREES = 90f;
    /** The yaw the renderer turns a body by before it draws: 180 less the body's facing. */
    private static final float RENDERER_YAW_OFFSET = 180f;

    private DownedPose() {
    }

    /**
     * Stamps the fallen pose onto a downed mob's render state: the turn onto
     * its side and a zeroed walk. Registered via RegisterRenderStateModifiersEvent in GooClientSetup.
     *
     * @param entity the entity being rendered
     * @param state  its render state
     */
    public static void stampDownedPose(Entity entity, EntityRenderState state) {
        if (state instanceof LivingEntityRenderState living && entity.hasData(GooAttachments.DOWNED)) {
            living.walkAnimationPos = 0f;
            living.walkAnimationSpeed = 0f;
            living.setRenderData(FALLEN, onItsSide(living.bodyRot));
        }
    }

    /**
     * The turn that tips a body facing a yaw onto its side, about the line
     * it faces along.
     *
     * @param bodyRot the body's facing, in degrees
     * @return the turn
     */
    static Quaternionf onItsSide(float bodyRot) {
        float facing = (float) Math.toRadians(RENDERER_YAW_OFFSET - bodyRot);
        return new Quaternionf().rotationY(facing).rotateZ((float) Math.toRadians(ON_ITS_SIDE_DEGREES))
                .rotateY(-facing);
    }

    /**
     * Turns a downed body onto its side before it draws, about its feet.
     *
     * @param event the living render event, before the body draws
     */
    @SubscribeEvent
    public static void onRenderLivingPre(RenderLivingEvent.Pre<?, ?, ?> event) {
        Quaternionf fallen = event.getRenderState().getRenderData(FALLEN);
        if (fallen != null) {
            event.getPoseStack().pushPose();
            event.getPoseStack().mulPose(fallen);
        }
    }

    /**
     * Closes the turn opened around a downed body.
     *
     * @param event the living render event, after the body drew
     */
    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?, ?> event) {
        if (event.getRenderState().getRenderData(FALLEN) != null) {
            event.getPoseStack().popPose();
        }
    }
}
