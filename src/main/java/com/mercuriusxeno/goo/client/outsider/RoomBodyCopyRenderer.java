package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import com.mercuriusxeno.goo.client.ability.ClientRoomEdges;
import com.mercuriusxeno.goo.client.ability.PortalView;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import java.util.List;
import java.util.Optional;

/**
 * Submits every body standing in the Outsider room the player stands in once
 * in each of the room's copies the camera sees, at its drawn place moved by
 * that copy's offset, the local player among them in first person too, so
 * each copy shows the bodies the room holds and the player looking across
 * the room sees its own back.
 * decision room-tiles-to-render-distance-held-to-the-picture
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class RoomBodyCopyRenderer {

    /** How far round a body's box its copy still counts as seen, for what its model draws past the box. */
    private static final double MODEL_MARGIN = 1.0;

    private static boolean drawingCopies;

    private RoomBodyCopyRenderer() {
    }

    /**
     * Whether a body's copy is being submitted now, so what draws on a body
     * and reads its place, as the glove reads the hand the aim line starts
     * from, knows the place is a copy's and not the body's own.
     *
     * @return true while a copy is submitted
     */
    public static boolean drawingCopies() {
        return drawingCopies;
    }

    /**
     * Runs a submit as a body's copy.
     *
     * @param submit the submit
     */
    public static void whileDrawingCopies(Runnable submit) {
        boolean was = drawingCopies;
        drawingCopies = true;
        try {
            submit.run();
        } finally {
            drawingCopies = was;
        }
    }

    /**
     * Submits the room's bodies in every copy the pass drawing now sees.
     *
     * @param event the custom geometry submit event
     */
    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        ClientLevel level = mc.level;
        Optional<RoomExtent.Bounds> bounds = ClientRoomEdges.bounds();
        if (level == null || level.dimension() != OutsiderRooms.POCKET || bounds.isEmpty()) {
            return;
        }
        List<RoomCopies.Offset> offsets = RoomCopies.offsets(bounds.get(),
                RoomCopies.reachOf(mc.options.getEffectiveRenderDistance()));
        Frustum frustum = PortalView.drawing() ? PortalView.camera().getCullFrustum()
                : mc.gameRenderer.getMainCamera().getCullFrustum();
        for (Entity body : level.entitiesForRendering()) {
            float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(
                    !level.tickRateManager().isEntityFrozen(body));
            Vec3 drawn = body.getPosition(partial);
            if (RoomBodyCopies.standsIn(bounds.get(), drawn)) {
                submitCopies(event, body, drawn, partial, offsets, frustum);
            }
        }
    }

    /**
     * Submits one body in each copy the camera sees.
     *
     * @param event   the custom geometry submit event
     * @param body    the body
     * @param drawn   its drawn place
     * @param partial its partial tick
     * @param offsets the room's copies
     * @param frustum the pass's frustum
     */
    private static void submitCopies(SubmitCustomGeometryEvent event, Entity body, Vec3 drawn, float partial,
            List<RoomCopies.Offset> offsets, Frustum frustum) {
        EntityRenderDispatcher dispatcher = Minecraft.getInstance().getEntityRenderDispatcher();
        LevelRenderState level = event.getLevelRenderState();
        AABB box = body.getBoundingBox().move(drawn.subtract(body.position())).inflate(MODEL_MARGIN);
        EntityRenderState state = null;
        Vec3 camera = level.cameraRenderState.pos;
        for (Vec3 place : RoomBodyCopies.placesOf(drawn, offsets)) {
            if (!frustum.isVisible(box.move(place.subtract(drawn)))) {
                continue;
            }
            if (state == null) {
                state = dispatcher.extractEntity(body, partial);
                state.outlineColor = level.haveGlowingEntities ? state.outlineColor : 0;
            }
            Vec3 at = place.add(state.x - drawn.x(), state.y - drawn.y(), state.z - drawn.z());
            EntityRenderState copy = state;
            whileDrawingCopies(() -> dispatcher.submit(copy, level.cameraRenderState, at.x() - camera.x(),
                    at.y() - camera.y(), at.z() - camera.z(), event.getPoseStack(), event.getSubmitNodeCollector()));
        }
    }
}
