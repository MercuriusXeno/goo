package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.GatePassage;
import com.mercuriusxeno.goo.ability.quantum.Portal;
import com.mercuriusxeno.goo.ability.quantum.QuantumPortals;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.throwing.GloveAim;
import com.mercuriusxeno.goo.client.throwing.PortalAim;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
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
 * Draws every gate as an oval on the face it covers, blue or orange: the
 * local player's portals, the blue entry and unlinked ones blue and the exit
 * orange, and each open hole's mouths, blue on the struck side and orange on
 * the far side. A gate whose view drew this frame shows the far side flush
 * inside its oval under a rim of its color; one with no view shows its idle
 * swirl. With Portal in the glove, the portal being chosen from and, while
 * the player sneaks, the one its look faces show their swirl through walls
 * in place of their view, the faced one strobing. Inside a view's own pass
 * only an inert gate draws, its swirl, from that view's camera.
 * portals-link-by-looking-through-portal-vision
 * hole-closes-the-space-to-an-opening-ahead
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PortalFaces {

    /** The blue portal's color: the entry, and any portal not yet the exit. */
    static final int BLUE_RGB = 0x3A7BFF;
    /** The orange portal's color: the exit. */
    static final int ORANGE_RGB = 0xFF8A1E;
    private static final int OPAQUE = 255;
    /** The idle swirl's alpha: nearly solid, a little of the wall showing through its dark heart. */
    private static final int SWIRL_ALPHA = 235;
    /** The faintest a faced portal's strobe dims to. */
    private static final float STROBE_LOW = 60F;
    /** How fast a faced portal strobes, in radians a tick. */
    private static final float STROBE_RATE = 0.6F;
    /** Half a wave's swing, which lifts a sine from minus one to one into nothing to full. */
    private static final float HALF_WAVE = 0.5F;
    /** How far off its blocks a gate's face stands, so the two never fight for depth. */
    private static final double FACE_LIFT = 0.01;

    private PortalFaces() {
    }

    /**
     * The color a portal draws in: orange for the linked exit, blue for every other.
     *
     * @param portal the portal
     * @param link   the player's linked pair, empty for none
     * @return 0xRRGGBB
     */
    static int colorOf(Portal portal, Optional<QuantumPortals.Link> link) {
        return link.isPresent() && link.get().exit().equals(portal) ? ORANGE_RGB : BLUE_RGB;
    }

    /**
     * A gate the frame draws and the color it wears.
     *
     * @param gate a portal's frame or a hole's mouth
     * @param rgb  its color, 0xRRGGBB
     * @param hole whether it is a hole's mouth, drawn see-through with a torn edge and grown open
     */
    record Drawn(Gate gate, int rgb, boolean hole) {

        /**
         * @param camera the camera's position
         * @return the blocks the gate covers, camera-relative, a hole's mouth shrunk as it grows open
         */
        AABB drawnBox(Vec3 camera) {
            AABB box = gate.blocks().move(camera.reverse());
            if (!hole) {
                return box;
            }
            float grown = ClientHoles.grownOpen(gate, Minecraft.getInstance().getDeltaTracker()
                    .getGameTimeDeltaPartialTick(false));
            Direction.Axis normal = gate.face().getAxis();
            return AABB.ofSize(box.getCenter(), box.getXsize() * (normal == Direction.Axis.X ? 1 : grown),
                    box.getYsize() * (normal == Direction.Axis.Y ? 1 : grown),
                    box.getZsize() * (normal == Direction.Axis.Z ? 1 : grown));
        }
    }

    /**
     * Draws the gates once the world has drawn.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        Minecraft mc = Minecraft.getInstance();
        LocalPlayer player = mc.player;
        if (player == null || mc.level == null) {
            return;
        }
        Vec3 camera = PortalView.drawing() ? PortalView.cameraPosition() : mc.gameRenderer.getMainCamera().position();
        PoseStack.Pose pose = event.getPoseStack().last();
        MultiBufferSource.BufferSource buffers = mc.renderBuffers().bufferSource();
        List<Drawn> gates = gatesIn(mc.level.dimension());
        if (PortalView.drawing()) {
            drawIdle(pose, buffers, camera, gates);
            return;
        }
        List<Portal> worked = workedOn(player);
        gates.removeIf(drawn -> worked.stream().anyMatch(portal -> portal.gate().equals(drawn.gate())));
        drawViews(pose, buffers, camera, gates);
        drawIdle(pose, buffers, camera, gates);
        drawWorked(pose, buffers, camera, worked, player);
    }

    /**
     * The portals a right click with Portal in the glove works on, which
     * light up to say so: the one being chosen from, and, while the player
     * sneaks, the one its look faces.
     *
     * @param player the local player
     * @return the portals, the chosen-from first
     */
    private static List<Portal> workedOn(LocalPlayer player) {
        if (!PortalAim.selected(GloveAim.selectedAbilityId(player))) {
            return List.of();
        }
        List<Portal> worked = new ArrayList<>(ClientPortals.choosing().stream().toList());
        if (PortalAim.working(player)) {
            PortalAim.faced(player).filter(faced -> !worked.contains(faced)).ifPresent(worked::add);
        }
        return worked;
    }

    /**
     * Draws the portals a right click works on as their swirl in place of
     * their view, seen through walls: the one being chosen from steady, the
     * one the look faces while sneaking strobing.
     *
     * @param pose    the pose
     * @param buffers the frame's buffers
     * @param camera  the camera's position
     * @param worked  the portals worked on
     * @param player  the local player
     */
    private static void drawWorked(PoseStack.Pose pose, MultiBufferSource.BufferSource buffers, Vec3 camera,
            List<Portal> worked, LocalPlayer player) {
        if (worked.isEmpty()) {
            return;
        }
        Optional<Portal> faced = PortalAim.working(player) ? PortalAim.faced(player) : Optional.empty();
        float time = player.tickCount + Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
        int strobe = (int) Mth.lerp(HALF_WAVE + HALF_WAVE * Mth.sin(time * STROBE_RATE), STROBE_LOW, OPAQUE);
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.GOO_PORTAL_VISION_TYPE);
        for (Portal portal : worked) {
            int alpha = faced.isPresent() && faced.get().equals(portal) ? strobe : SWIRL_ALPHA;
            emitOval(pose, consumer, portal.blocks().move(camera.reverse()), portal.face(),
                    ARGB.color(alpha, colorOf(portal, ClientPortals.link())));
        }
        buffers.endBatch(GooRenderTypes.GOO_PORTAL_VISION_TYPE);
    }

    /**
     * Every gate in a level the frame draws: the local player's portals,
     * every pair another player linked, and each open hole's two mouths.
     *
     * @param dimension the level drawn
     * @return the gates
     */
    private static List<Drawn> gatesIn(ResourceKey<Level> dimension) {
        List<Drawn> gates = new ArrayList<>();
        ClientPortals.portals().forEach(portal -> gates.add(new Drawn(portal.gate(),
                colorOf(portal, ClientPortals.link()), false)));
        // linked-partner-chunks-stream-to-any-viewer
        ClientPortals.links().stream()
                .filter(link -> !ClientPortals.portals().contains(link.entry()))
                .forEach(link -> {
                    gates.add(new Drawn(link.entry().gate(), BLUE_RGB, false));
                    gates.add(new Drawn(link.exit().gate(), ORANGE_RGB, false));
                });
        ClientHoles.holes().forEach(hole -> {
            gates.add(new Drawn(hole.entry(), BLUE_RGB, true));
            gates.add(new Drawn(hole.exit(), ORANGE_RGB, true));
        });
        gates.removeIf(drawn -> !drawn.gate().dimension().equals(dimension));
        return gates;
    }

    /**
     * Draws the view through each gate that drew one this frame, inside its oval.
     *
     * @param pose    the pose
     * @param buffers the frame's buffers
     * @param camera  the camera's position
     * @param gates   the gates the frame draws
     */
    private static void drawViews(PoseStack.Pose pose, MultiBufferSource.BufferSource buffers, Vec3 camera,
            List<Drawn> gates) {
        for (Drawn drawn : gates) {
            PortalView.viewOf(drawn.gate()).ifPresent(view -> {
                // hole-closes-the-space-to-an-opening-ahead
                RenderType type = drawn.hole() ? GooRenderTypes.gooHoleView(view) : GooRenderTypes.gooPortalView(view);
                emitOval(pose, buffers.getBuffer(type), drawn.drawnBox(camera), drawn.gate().face(),
                        ARGB.color(OPAQUE, drawn.rgb()));
                buffers.endBatch(type);
            });
        }
    }

    /**
     * Draws each gate with no view to show as its idle swirl. Inside a view
     * through a pair only an inert gate, one no link holds, draws its swirl:
     * a linked gate seen through the view stands clear of any overlay, and
     * the pair looked through never draws over its own view.
     *
     * @param pose    the pose
     * @param buffers the frame's buffers
     * @param camera  the camera's position
     * @param gates   the gates the frame draws
     */
    private static void drawIdle(PoseStack.Pose pose, MultiBufferSource.BufferSource buffers, Vec3 camera,
            List<Drawn> gates) {
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.GOO_PORTAL_IDLE_TYPE);
        for (Drawn drawn : gates) {
            boolean swirls = PortalView.drawing() ? inert(drawn.gate()) : PortalView.viewOf(drawn.gate()).isEmpty();
            if (swirls) {
                emitOval(pose, consumer, drawn.drawnBox(camera), drawn.gate().face(),
                        ARGB.color(SWIRL_ALPHA, drawn.rgb()));
            }
        }
        buffers.endBatch(GooRenderTypes.GOO_PORTAL_IDLE_TYPE);
    }

    /**
     * @param gate a gate
     * @return whether no link the client knows holds it
     */
    private static boolean inert(Gate gate) {
        return GatePassage.clientPairs().stream().noneMatch(pair -> pair.one().equals(gate)
                || pair.other().equals(gate));
    }

    /**
     * Emits a gate's face as a quad standing a lift off it, its corners
     * mapped zero to one across each way, for the shader to fill as an oval.
     *
     * @param pose     the pose
     * @param consumer the vertex consumer
     * @param box      the gate's blocks, camera-relative
     * @param side     the face
     * @param color    the packed ARGB color
     */
    static void emitOval(PoseStack.Pose pose, VertexConsumer consumer, AABB box, Direction side, int color) {
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
     * @param box    the box
     * @param side   the side
     * @param first  zero for the low end across the side's first axis, one for the high
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
}
