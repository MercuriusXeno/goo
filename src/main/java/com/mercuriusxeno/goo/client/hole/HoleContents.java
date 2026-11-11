package com.mercuriusxeno.goo.client.hole;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.hole.HoleDrift;
import com.mercuriusxeno.goo.ability.hole.HoleFrame;
import com.mercuriusxeno.goo.network.PortableHolesSyncPayload;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.jspecify.annotations.Nullable;
import java.util.ArrayList;
import java.util.List;

/**
 * Draws the items a hole's owner has stored swimming sunk inside it: each
 * stored kind a small item drifting on its own slow path through the pit
 * behind the mouth, turning lazily as it goes, sunk up into a ceiling's pit as
 * down into a floor's. The items lie wholly behind the face, so the world's
 * blocks hide them everywhere but through the mouth HolePits carves. Each
 * item the hole swallows glides in from where it stood.
 * decision hole-is-an-in-world-portable-hole-for-items
 * decision hole-items-are-a-three-dimensional-soup-sunk-in-the-hole
 * decision swallowed-item-transitions-into-the-hole-smoothly
 * decision hole-shows-every-kind-stored
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class HoleContents {

    /** The radians an item turns each tick. */
    private static final float TURN = 0.035F;
    /** The turn between one item's facing and the next's. */
    private static final float TURN_PHASE = 1.7F;
    /** A block face's distance from its block's center. */
    private static final double FACE_OFFSET = 0.5;
    private static final float HALF_TURN_DEGREES = 180F;
    /** The least span a model is taken to have, so an empty model never scales without bound. */
    private static final double MIN_MODEL_SPAN = 0.01;

    private static final double HALF = 0.5;

    /**
     * The render states the soup draws through, one per stored kind, grown to the most kinds a mouth stores.
     * decision hole-shows-every-kind-stored
     */
    private static final List<ItemStackRenderState> SHOWN = new ArrayList<>();
    /** The render states the items easing in draw through, as many as one swallow tells of. */
    private static final ItemStackRenderState[] EASING = newEasing();

    private HoleContents() {
    }

    private static ItemStackRenderState[] newEasing() {
        ItemStackRenderState[] easing = new ItemStackRenderState[PortableHolesSyncPayload.MOST_SWALLOWS_TOLD];
        for (int slot = 0; slot < easing.length; slot++) {
            easing[slot] = new ItemStackRenderState();
        }
        return easing;
    }

    private static ItemStackRenderState shownState(int index) {
        while (SHOWN.size() <= index) {
            SHOWN.add(new ItemStackRenderState());
        }
        return SHOWN.get(index);
    }

    /**
     * Submits every open mouth's swimming items.
     *
     * @param event the custom geometry submit event
     */
    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) {
            return;
        }
        float time = mc.level.getGameTime() + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = event.getLevelRenderState().cameraRenderState.pos;
        // decision hole-seals-over-items-of-fixed-scale: the soup keeps its size while the mouth's oval
        // opens and closes over it, and draws nothing once the oval is shut
        for (PortableHolesSyncPayload.Mouth mouth : ClientPortableHoles.drawn(time)) {
            float open = ClientPortableHoles.openShare(mouth, time);
            if (open > 0F) {
                submitMouth(mc, event, mouth, camera, open, time);
            }
        }
    }

    private static void submitMouth(Minecraft mc, SubmitCustomGeometryEvent event,
                                    PortableHolesSyncPayload.Mouth mouth, Vec3 camera, float open, float time) {
        MouthFrame frame = MouthFrame.of(mc, mouth);
        float kinds = ClientPortableHoles.kindsShownAt(mouth, time);
        // decision swallowed-item-transitions-into-the-hole-smoothly: a kind easing in first waits out its transit
        for (int index : ClientPortableHoles.soupDrawn(mouth, time)) {
            ItemStackRenderState item = modelOf(mc, shownState(index), mouth.stored().get(index).item(), index);
            if (item != null) {
                HoleDrift.Placement placement = HoleDrift.placementAt(index, kinds, time, frame.halfAcross(),
                        frame.halfAlong(), open);
                submitItem(event, item, frame.toWorld(placement.offset()).subtract(camera), frame, placement.size(),
                        TURN * time + index * TURN_PHASE);
            }
        }
        List<HoleSwallows.Transit> transits = ClientPortableHoles.transitsInto(mouth, time);
        for (int slot = 0; slot < Math.min(transits.size(), EASING.length); slot++) {
            submitTransit(mc, event, mouth, frame, transits.get(slot).swallowed(), slot, camera, kinds, time);
        }
    }

    /**
     * Submits one swallowed item partway along its glide from where it stood
     * into its kind's place in the soup.
     * decision swallowed-item-transitions-into-the-hole-smoothly
     *
     * @param mc        the client
     * @param event     the custom geometry submit event
     * @param mouth     the mouth it eases into
     * @param frame     the mouth's own frame
     * @param swallowed the item as the server told of it
     * @param slot      the render state slot it draws through
     * @param camera    the camera's world position
     * @param kinds     the count of kinds the soup spreads over
     * @param time      the client's game time, partial tick included
     */
    private static void submitTransit(Minecraft mc, SubmitCustomGeometryEvent event,
                                      PortableHolesSyncPayload.Mouth mouth, MouthFrame frame,
                                      PortableHolesSyncPayload.Swallowed swallowed, int slot, Vec3 camera,
                                      float kinds, float time) {
        int index = HoleSwallows.shownIndexOf(mouth, swallowed.item());
        ItemStackRenderState item = modelOf(mc, EASING[slot], swallowed.item(), slot);
        // a swallow whose kind the mouth no longer stores has no place in the soup to ease to
        if (item == null || index == HoleSwallows.NOT_SHOWN) {
            return;
        }
        // a dropped item draws its ground model unscaled, its middle about half its span over where it stands
        double droppedSize = largestSpanOf(item);
        Vec3 from = frame.toFrame(swallowed.at().add(0, droppedSize * HALF, 0));
        HoleDrift.Placement end = HoleSwallows.endOf(index, kinds, time, frame.halfAcross(), frame.halfAlong());
        HoleDrift.Placement placement = HoleSwallows.transitAt(from, droppedSize, end, time - swallowed.time());
        submitItem(event, item, frame.toWorld(placement.offset()).subtract(camera), frame, placement.size(),
                TURN * time + index * TURN_PHASE);
    }

    /**
     * Resolves a kind's ground model into a render state.
     *
     * @param mc    the client
     * @param state the render state to fill
     * @param kind  the item's id
     * @param seed  the model's seed
     * @return the filled state, or null where the kind draws nothing
     */
    private static @Nullable ItemStackRenderState modelOf(Minecraft mc, ItemStackRenderState state, Identifier kind,
                                                          int seed) {
        ItemStack stack = BuiltInRegistries.ITEM.getValue(kind).getDefaultInstance();
        mc.getItemModelResolver().updateForTopItem(state, stack, ItemDisplayContext.GROUND, mc.level, null, seed);
        return stack.isEmpty() || state.isEmpty() ? null : state;
    }

    private static double largestSpanOf(ItemStackRenderState item) {
        AABB model = item.getModelBoundingBox();
        return Math.max(model.getXsize(), Math.max(model.getYsize(), model.getZsize()));
    }

    /**
     * A mouth's own frame, the one the server pours from, with the face it
     * opens on and its light.
     *
     * @param hole  the mouth's frame
     * @param face  the face it opens on
     * @param light the packed light just off the face
     */
    private record MouthFrame(HoleFrame hole, Direction face, int light) {

        static MouthFrame of(Minecraft mc, PortableHolesSyncPayload.Mouth mouth) {
            HoleFrame hole = HoleFrame.of(mouth.footprint(), mouth.face());
            int light = LevelRenderer.getLightCoords(mc.level,
                    BlockPos.containing(hole.centre().add(hole.normal().scale(FACE_OFFSET))));
            return new MouthFrame(hole, mouth.face(), light);
        }

        double halfAcross() {
            return hole.halfAcross();
        }

        double halfAlong() {
            return hole.halfAlong();
        }

        Vec3 toWorld(Vec3 offset) {
            return hole.toWorld(offset);
        }

        Vec3 toFrame(Vec3 world) {
            return hole.toFrame(world);
        }
    }

    private static void submitItem(SubmitCustomGeometryEvent event, ItemStackRenderState item, Vec3 fromCamera,
                                   MouthFrame frame, double size, float turn) {
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(fromCamera.x(), fromCamera.y(), fromCamera.z());
        if (frame.face() == Direction.DOWN) {
            poseStack.mulPose(Axis.XP.rotationDegrees(HALF_TURN_DEGREES));
        }
        poseStack.mulPose(Axis.YP.rotation(turn));
        // every model draws the placement's size across at its largest span, so its drift keeps it in the pit
        AABB model = item.getModelBoundingBox();
        float scale = (float) (size / Math.max(largestSpanOf(item), MIN_MODEL_SPAN));
        poseStack.scale(scale, scale, scale);
        Vec3 middle = model.getCenter();
        poseStack.translate(-middle.x(), -middle.y(), -middle.z());
        item.submit(poseStack, event.getSubmitNodeCollector(), frame.light(), OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
