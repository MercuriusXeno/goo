package com.mercuriusxeno.goo.client.hole;

import com.mercuriusxeno.goo.Goo;
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
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;

/**
 * Draws the items a hole's owner has stored swimming sunk inside it: each
 * stored kind a small item drifting on its own slow path through the pit
 * behind the mouth, turning lazily as it goes, sunk up into a ceiling's pit as
 * down into a floor's. The items lie wholly behind the face, so the world's
 * blocks hide them everywhere but through the mouth HolePits carves.
 * decision hole-is-an-in-world-portable-hole-for-items
 * decision hole-items-are-a-three-dimensional-soup-sunk-in-the-hole
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

    private static final ItemStackRenderState[] SHOWN = newShown();

    private HoleContents() {
    }

    private static ItemStackRenderState[] newShown() {
        ItemStackRenderState[] shown = new ItemStackRenderState[HoleDrift.MOST_SHOWN];
        for (int index = 0; index < shown.length; index++) {
            shown[index] = new ItemStackRenderState();
        }
        return shown;
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
        // decision hole-animates-open-and-closed: the soup grows and shrinks with its mouth
        for (PortableHolesSyncPayload.Mouth mouth : ClientPortableHoles.drawn(time)) {
            float open = ClientPortableHoles.openShare(mouth, time);
            if (open > 0F) {
                submitMouth(mc, event, mouth, camera, open, time);
            }
        }
    }

    private static void submitMouth(Minecraft mc, SubmitCustomGeometryEvent event,
                                    PortableHolesSyncPayload.Mouth mouth, Vec3 camera, float open, float time) {
        Direction face = mouth.face();
        Vec3 normal = Vec3.atLowerCornerOf(face.getUnitVec3i());
        AABB opening = HoleMouths.openingOn(mouth.footprint(), face);
        Vec3 centre = opening.getCenter();
        Vec3 across = face.getAxis() == Direction.Axis.X ? new Vec3(0, 0, 1) : new Vec3(1, 0, 0);
        Vec3 along = normal.cross(across);
        // the items keep within the pit's walls, which stand inside the footprint's outer faces
        AABB pitRim = HolePits.pitOf(mouth.footprint(), face).rim();
        double halfAcross = HoleMouths.halfSpan(pitRim, across);
        double halfAlong = HoleMouths.halfSpan(pitRim, along);
        int light = LevelRenderer.getLightCoords(mc.level, BlockPos.containing(centre.add(normal.scale(FACE_OFFSET))));
        int shown = Math.min(mouth.stored().size(), SHOWN.length);
        for (int index = 0; index < shown; index++) {
            ItemStackRenderState item = SHOWN[index];
            ItemStack stack = BuiltInRegistries.ITEM.getValue(mouth.stored().get(index).item()).getDefaultInstance();
            mc.getItemModelResolver().updateForTopItem(item, stack, ItemDisplayContext.GROUND, mc.level, null, index);
            if (stack.isEmpty() || item.isEmpty()) {
                continue;
            }
            Vec3 drift = HoleDrift.offset(index, time, halfAcross, halfAlong).scale(open);
            Vec3 at = centre.add(across.scale(drift.x())).add(normal.scale(drift.y())).add(along.scale(drift.z()));
            submitItem(event, item, at.subtract(camera), face, open, TURN * time + index * TURN_PHASE, light);
        }
    }

    private static void submitItem(SubmitCustomGeometryEvent event, ItemStackRenderState item, Vec3 fromCamera,
                                   Direction face, float open, float turn, int light) {
        PoseStack poseStack = event.getPoseStack();
        poseStack.pushPose();
        poseStack.translate(fromCamera.x(), fromCamera.y(), fromCamera.z());
        if (face == Direction.DOWN) {
            poseStack.mulPose(Axis.XP.rotationDegrees(HALF_TURN_DEGREES));
        }
        poseStack.mulPose(Axis.YP.rotation(turn));
        // every model draws HoleDrift.ITEM_SIZE across at its largest span, so its drift keeps it in the pit
        AABB model = item.getModelBoundingBox();
        double largest = Math.max(model.getXsize(), Math.max(model.getYsize(), model.getZsize()));
        float scale = (float) (HoleDrift.ITEM_SIZE / Math.max(largest, MIN_MODEL_SPAN)) * open;
        poseStack.scale(scale, scale, scale);
        Vec3 middle = model.getCenter();
        poseStack.translate(-middle.x(), -middle.y(), -middle.z());
        item.submit(poseStack, event.getSubmitNodeCollector(), light, OverlayTexture.NO_OVERLAY, 0);
        poseStack.popPose();
    }
}
