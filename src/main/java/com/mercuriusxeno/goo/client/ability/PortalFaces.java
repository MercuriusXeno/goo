package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.QuantumPortals;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Draws the local player's portals on the faces they cover: the entry portal
 * blue, its partner orange, and an unlinked portal its idle face. A linked
 * portal whose view drew this frame shows that view, the other side flush
 * as though right there, inside a rim of its color. While the player
 * chooses a partner, every portal of theirs in this level draws through
 * walls (portal vision), the one the look picks brightest.
 * portals-link-by-looking-through-portal-vision
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PortalFaces {

    /** The blue entry portal's color. */
    static final int ENTRY_RGB = 0x3A7BFF;
    /** The orange exit portal's color. */
    static final int EXIT_RGB = 0xFF8A1E;
    /** An unlinked portal's idle color, Quantum's violet. */
    static final int IDLE_RGB = 0x9B6BFF;
    private static final int FACE_ALPHA = 190;
    private static final int VISION_ALPHA = 120;
    private static final int PICKED_ALPHA = 235;
    private static final int OPAQUE = 255;
    /** The idle swirl's alpha: nearly solid, a little of the wall showing through its dark heart. */
    private static final int SWIRL_ALPHA = 235;
    /** The width of the colored rim around a portal's view, in blocks. */
    private static final double RIM = 0.06;
    /** How far off its blocks a portal's face, or its view, stands. */
    private static final double FACE_LIFT = 0.01;
    /** How far off its blocks the rim around a view stands, clear of the view beneath it. */
    private static final double RIM_LIFT = 0.02;

    private PortalFaces() {
    }

    /**
     * The color a portal's face draws in: blue for the entry, orange for its
     * partner, violet for an unlinked portal.
     *
     * @param portal the portal
     * @param link   the player's linked pair, empty for none
     * @return 0xRRGGBB
     */
    static int colorOf(Portal portal, Optional<QuantumPortals.Link> link) {
        if (link.isEmpty()) {
            return IDLE_RGB;
        }
        if (link.get().entry().equals(portal)) {
            return ENTRY_RGB;
        }
        return link.get().exit().equals(portal) ? EXIT_RGB : IDLE_RGB;
    }

    /**
     * Draws the portals once the world has drawn.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || nothingToDraw(mc)) {
            return;
        }
        Optional<Portal> choosing = ClientPortals.choosing();
        Optional<Portal> picked = choosing.flatMap(from -> ClientPortals.pickedOnLook(ClientPortals.portals(), from,
                player.getEyePosition(), player.getLookAngle()));
        Vec3 camera = mc.gameRenderer.getMainCamera().position();
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        if (choosing.isEmpty()) {
            List<Drawn> gates = gatesIn(mc.level.dimension());
            drawViews(pose, buffers, camera, gates);
            drawIdle(pose, buffers, camera, gates);
        }
        RenderType type = choosing.isPresent() ? GooRenderTypes.SPORE_SHELL_THROUGH_BLOCKS_TYPE
                : GooRenderTypes.SPORE_SHELL_TYPE;
        drawFaces(pose, buffers.getBuffer(type), camera, mc.level.dimension(), choosing, picked);
        buffers.endBatch(type);
    }

    /**
     * A gate the frame draws and the color it wears.
     *
     * @param gate a portal's frame or a hole's mouth
     * @param rgb  its color, 0xRRGGBB
     */
    record Drawn(Gate gate, int rgb) {
    }

    /**
     * Every gate in a level the frame draws: the local player's portals, each
     * in its color, and each open hole's two mouths in Quantum's violet.
     *
     * @param dimension the level drawn
     * @return the gates
     */
    private static List<Drawn> gatesIn(ResourceKey<Level> dimension) {
        List<Drawn> gates = new ArrayList<>();
        ClientPortals.portals().forEach(portal -> gates.add(new Drawn(portal.gate(),
                colorOf(portal, ClientPortals.link()))));
        ClientHoles.holes().forEach(hole -> {
            gates.add(new Drawn(hole.entry(), IDLE_RGB));
            gates.add(new Drawn(hole.exit(), IDLE_RGB));
        });
        gates.removeIf(drawn -> !drawn.gate().dimension().equals(dimension));
        return gates;
    }

    /**
     * Draws each gate with no view to show as its idle swirl.
     *
     * @param pose    the pose
     * @param buffers the frame's buffers
     * @param camera  the main camera's position
     * @param gates   the gates the frame draws
     */
    private static void drawIdle(PoseStack.Pose pose, MultiBufferSource.BufferSource buffers, Vec3 camera,
            List<Drawn> gates) {
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.GOO_PORTAL_IDLE_TYPE);
        for (Drawn drawn : gates) {
            if (PortalView.viewOf(drawn.gate()).isEmpty()) {
                emitSwirl(pose, consumer, drawn.gate().blocks().move(camera.reverse()), drawn.gate().face(),
                        ARGB.color(SWIRL_ALPHA, drawn.rgb()));
            }
        }
        buffers.endBatch(GooRenderTypes.GOO_PORTAL_IDLE_TYPE);
    }

    /**
     * Emits a frame's face as a quad standing a lift off it, its corners
     * mapped zero to one across each way, for the swirl to fill as an oval.
     *
     * @param pose     the pose
     * @param consumer the vertex consumer
     * @param box      the frame's blocks, camera-relative
     * @param side     the face
     * @param color    the packed ARGB color
     */
    static void emitSwirl(PoseStack.Pose pose, VertexConsumer consumer, AABB box, Direction side, int color) {
        int[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int[] corner : corners) {
            Vec3 at = cornerOf(box, side, corner[0], corner[1]);
            consumer.addVertex(pose, (float) at.x(), (float) at.y(), (float) at.z()).setUv(corner[0], corner[1])
                    .setColor(color);
        }
    }

    /**
     * A corner of a box's side, standing the face lift off it.
     *
     * @param box   the box
     * @param side  the side
     * @param first zero for the low end across the side's first axis, one for the high
     * @param second zero for the low end across its second axis, one for the high
     * @return the corner
     */
    private static Vec3 cornerOf(AABB box, Direction side, int first, int second) {
        Direction.Axis axis = side.getAxis();
        Direction.Axis firstAxis = axis == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
        Direction.Axis secondAxis = axis == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
        double plane = side.getAxisDirection() == Direction.AxisDirection.POSITIVE
                ? box.max(axis) + FACE_LIFT : box.min(axis) - FACE_LIFT;
        double[] xyz = new double[Direction.Axis.values().length];
        xyz[axis.ordinal()] = plane;
        xyz[firstAxis.ordinal()] = first == 0 ? box.min(firstAxis) : box.max(firstAxis);
        xyz[secondAxis.ordinal()] = second == 0 ? box.min(secondAxis) : box.max(secondAxis);
        return new Vec3(xyz[Direction.Axis.X.ordinal()], xyz[Direction.Axis.Y.ordinal()],
                xyz[Direction.Axis.Z.ordinal()]);
    }

    /**
     * Whether the frame draws no portal: no level, no portal placed, or a
     * portal view drawing now, which samples no view again.
     *
     * @param mc the client
     * @return true where nothing draws
     */
    private static boolean nothingToDraw(Minecraft mc) {
        return mc.level == null || ClientPortals.portals().isEmpty() && ClientHoles.holes().isEmpty()
                || PortalView.drawing();
    }

    /**
     * Draws the view through each gate that drew one this frame on its face.
     * portals-link-by-looking-through-portal-vision
     * hole-closes-the-space-to-an-opening-ahead
     *
     * @param pose    the pose
     * @param buffers the frame's buffers
     * @param camera  the main camera's position
     * @param gates   the gates the frame draws
     */
    private static void drawViews(PoseStack.Pose pose, MultiBufferSource.BufferSource buffers, Vec3 camera,
            List<Drawn> gates) {
        for (Drawn drawn : gates) {
            PortalView.viewOf(drawn.gate()).ifPresent(view -> {
                RenderType type = GooRenderTypes.gooPortalView(view);
                emitFace(pose, buffers.getBuffer(type), drawn.gate().blocks().move(camera.reverse()),
                        drawn.gate().face(), FACE_LIFT, ARGB.color(OPAQUE, drawn.rgb()));
                buffers.endBatch(type);
            });
        }
    }

    private static void drawFaces(PoseStack.Pose pose, VertexConsumer consumer, Vec3 camera,
            ResourceKey<Level> dimension, Optional<Portal> choosing, Optional<Portal> picked) {
        for (Portal portal : ClientPortals.portals()) {
            if (portal.block().dimension().equals(dimension)) {
                AABB frame = portal.blocks().move(camera.reverse());
                int color = ARGB.color(alphaOf(portal, choosing, picked), colorOf(portal, ClientPortals.link()));
                if (choosing.isPresent()) {
                    emitFace(pose, consumer, frame, portal.face(), FACE_LIFT, color);
                } else if (PortalView.viewOf(portal.gate()).isPresent()) {
                    rimOf(frame, portal.face().getAxis()).forEach(strip -> emitFace(pose, consumer, strip,
                            portal.face(), RIM_LIFT, color));
                }
            }
        }
    }

    /**
     * Emits one side of a box as a quad standing a lift off it, far enough
     * off the block that the two never fight for depth.
     *
     * @param pose     the pose
     * @param consumer the vertex consumer
     * @param box      the box, camera-relative
     * @param side     the side to draw
     * @param lift     how far off the side the quad stands
     * @param color    the packed ARGB color
     */
    static void emitFace(PoseStack.Pose pose, VertexConsumer consumer, AABB box, Direction side, double lift,
            int color) {
        ScrySweep.emitBoxFace(pose, consumer, box, side, lift, color);
    }

    /**
     * The four strips along a face's edges, the colored rim around a view.
     *
     * @param block the block's box
     * @param axis  the axis the face stands across
     * @return the strips' boxes
     */
    static List<AABB> rimOf(AABB block, Direction.Axis axis) {
        List<AABB> strips = new ArrayList<>();
        for (Direction.Axis across : Direction.Axis.values()) {
            if (across != axis) {
                double min = block.min(across);
                double max = block.max(across);
                strips.add(along(block, across, min, min + RIM));
                strips.add(along(block, across, max - RIM, max));
            }
        }
        return strips;
    }

    private static AABB along(AABB box, Direction.Axis axis, double from, double to) {
        return switch (axis) {
            case X -> new AABB(from, box.minY, box.minZ, to, box.maxY, box.maxZ);
            case Y -> new AABB(box.minX, from, box.minZ, box.maxX, to, box.maxZ);
            case Z -> new AABB(box.minX, box.minY, from, box.maxX, box.maxY, to);
        };
    }

    private static int alphaOf(Portal portal, Optional<Portal> choosing, Optional<Portal> picked) {
        if (choosing.isEmpty() || choosing.get().equals(portal)) {
            return FACE_ALPHA;
        }
        return picked.isPresent() && picked.get().equals(portal) ? PICKED_ALPHA : VISION_ALPHA;
    }
}
