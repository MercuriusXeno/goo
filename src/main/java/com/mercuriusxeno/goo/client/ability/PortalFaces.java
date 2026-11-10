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
import java.util.function.Function;

/**
 * Draws every gate as an oval on the face it covers, blue or orange: the
 * local player's portals, the blue entry and unlinked ones blue and the exit
 * orange, and each open hole's mouths, blue on the struck side and orange on
 * the far side. A gate whose view drew this frame shows the far side flush
 * inside its oval under a rim of its color; one with no view shows its idle
 * swirl. With Portal in the glove, the portal being paired blinks its swirl
 * in place of its view the whole time its owner chooses, and the one aimed
 * at, while choosing or sneaking, blinks brighter; both stand behind what
 * stands before them. While choosing, every portal of the owner's shows a
 * thin outline through the blocks, to find and pick one from afar. Inside a
 * view's own pass the views within it draw, and only an inert gate swirls.
 * portals-link-by-looking-through-portal-vision
 * hole-closes-the-space-to-an-opening-ahead
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PortalFaces {

    /** The blue portal's color: the entry, and any portal not yet the exit. */
    static final int BLUE_RGB = 0x3A7BFF;
    /** The orange portal's color: the exit. */
    static final int ORANGE_RGB = 0xFF8A1E;
    /** A block texture's texels a side, the grid a gate's face draws on. */
    private static final int TEXELS_PER_BLOCK = 16;
    private static final int OPAQUE = 255;
    /** The idle swirl's alpha: nearly solid, a little of the wall showing through its dark heart. */
    private static final int SWIRL_ALPHA = 235;
    /** A portal's outline through blocks while its owner chooses. */
    private static final int OUTLINE_ALPHA = 200;
    /** The faintest the portal aimed at blinks down to, brighter than the one being paired. */
    private static final float AIMED_BLINK_LOW = 140F;
    /** How fast the portal aimed at blinks, in radians a tick, quicker than the one being paired. */
    private static final float AIMED_BLINK_RATE = 1.1F;
    /** The faintest a faced portal's strobe dims to. */
    private static final float STROBE_LOW = 60F;
    /** How fast a faced portal strobes, in radians a tick. */
    private static final float STROBE_RATE = 0.6F;
    /** Half a wave's swing, which lifts a sine from minus one to one into nothing to full. */
    private static final float HALF_WAVE = 0.5F;
    /** How far off its blocks a gate's face stands, so the two never fight for depth. */
    private static final double FACE_LIFT = 0.01;
    /**
     * How far past a gate's blocks its halo reaches on each way across the
     * face, in blocks: half a block, eight texels, tuned by eye. The halo
     * shader's HALO_MARGIN_TEXELS counts the same reach in texels.
     */
    static final double HALO_MARGIN = 0.5;
    /**
     * A halo vertex's alpha marks the edge its oval draws, which the halo
     * shader starts from: none for a portal's clean oval, whole for a hole
     * mouth's torn edge. The halo's strength is the shader's own.
     */
    /** How far the tornadic halo stands off the face along its normal, in blocks, tuned by eye. */
    static final double TORNADO_HEIGHT = 1.0;
    /** How much wider than the rim the tornado flares at its far end, as a share of the rim's size, tuned by eye. */
    private static final double TORNADO_FLARE = 0.6;
    /** How many rings the tornado's funnel rises in. */
    private static final int TORNADO_RINGS = 6;
    /** How many segments each of the funnel's rings runs around the rim in. */
    private static final int TORNADO_SEGMENTS = 32;
    /** The three in Ramanujan's reckoning of an oval's perimeter. */
    private static final double RAMANUJAN_THREE = 3;
    static final int CLEAN_EDGE = 0;
    static final int TORN_EDGE = 255;

    private PortalFaces() {
    }

    /**
     * The color a portal draws in: orange for the exit of any linked pair,
     * blue for every other, an entry or an inert portal.
     *
     * @param portal the portal
     * @param links  every linked pair
     * @return 0xRRGGBB
     */
    static int colorOf(Portal portal, List<QuantumPortals.Link> links) {
        return links.stream().anyMatch(link -> link.exit().equals(portal)) ? ORANGE_RGB : BLUE_RGB;
    }

    /**
     * A gate the frame draws and the color it wears.
     *
     * @param gate a portal's frame or a hole's mouth
     * @param rgb  its color, 0xRRGGBB
     * @param hole whether it is a hole's mouth, drawn see-through with a torn edge
     */
    record Drawn(Gate gate, int rgb, boolean hole) {

        /**
         * @param camera the camera's position
         * @return the blocks the gate covers, camera-relative, shrunk as it grows open
         */
        AABB drawnBox(Vec3 camera) {
            AABB box = gate.blocks().move(camera.reverse());
            float partialTick = Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(false);
            // a portal, as a hole's mouth, grows in from nothing to its size as it is placed
            float grown = hole ? ClientHoles.grownOpen(gate, partialTick) : ClientPortals.grownOpen(gate, partialTick);
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
        drawHalos(pose, buffers, camera, gates);
        if (PortalView.drawing()) {
            drawViews(pose, buffers, camera, gates);
            drawIdle(pose, buffers, camera, gates);
            return;
        }
        drawWorldPass(pose, buffers, camera, player, gates);
    }

    /**
     * Draws the gates in the world's own pass: the views and idle swirls,
     * then the portals a right click with Portal in the glove works on
     * blinking, and while choosing, every portal's outline through blocks.
     *
     * @param pose    the pose
     * @param buffers the frame's buffers
     * @param camera  the camera's position
     * @param player  the local player
     * @param gates   the gates the frame draws
     */
    private static void drawWorldPass(PoseStack.Pose pose, MultiBufferSource.BufferSource buffers, Vec3 camera,
            LocalPlayer player, List<Drawn> gates) {
        Minecraft mc = Minecraft.getInstance();
        boolean holding = PortalAim.selected(GloveAim.selectedAbilityId(player));
        Optional<Portal> chosen = holding ? ClientPortals.choosing() : Optional.empty();
        Optional<Portal> aimed = holding && (chosen.isPresent() || PortalAim.working(player))
                ? PortalAim.faced(player) : Optional.empty();
        List<Portal> blinking = new ArrayList<>(chosen.stream().toList());
        aimed.filter(portal -> !blinking.contains(portal)).ifPresent(blinking::add);
        gates.removeIf(drawn -> blinking.stream().anyMatch(portal -> portal.gate().equals(drawn.gate())));
        drawViews(pose, buffers, camera, gates);
        drawIdle(pose, buffers, camera, gates);
        float time = player.tickCount + mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        drawBlinking(pose, buffers, camera, blinking, aimed, time);
        if (chosen.isPresent()) {
            drawOutlines(pose, buffers, camera, player, blinking, aimed, time);
        }
    }

    /**
     * How strongly a blinking portal shows this frame: the portal being
     * paired blinks the whole time its owner chooses, and the one aimed at
     * blinks brighter and quicker.
     *
     * @param aimed whether the portal is the one aimed at
     * @param time  the ticks, with the frame's share of one
     * @return the alpha
     */
    private static int blinkAlpha(boolean aimed, float time) {
        float wave = HALF_WAVE + HALF_WAVE * Mth.sin(time * (aimed ? AIMED_BLINK_RATE : STROBE_RATE));
        return (int) Mth.lerp(wave, aimed ? AIMED_BLINK_LOW : STROBE_LOW, OPAQUE);
    }

    /**
     * Draws the portals a right click with Portal in the glove works on, the
     * one being paired and the one aimed at, as their swirl blinking in place
     * of their view, behind whatever stands before them as any face does.
     *
     * @param pose     the pose
     * @param buffers  the frame's buffers
     * @param camera   the camera's position
     * @param blinking the portals blinking
     * @param aimed    the portal aimed at, empty for none
     * @param time     the ticks, with the frame's share of one
     */
    private static void drawBlinking(PoseStack.Pose pose, MultiBufferSource.BufferSource buffers, Vec3 camera,
            List<Portal> blinking, Optional<Portal> aimed, float time) {
        if (blinking.isEmpty()) {
            return;
        }
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.GOO_PORTAL_IDLE_TYPE);
        for (Portal portal : blinking) {
            int alpha = blinkAlpha(aimed.isPresent() && aimed.get().equals(portal), time);
            emitOval(pose, consumer, portal.blocks().move(camera.reverse()), portal.face(),
                    ARGB.color(alpha, colorOf(portal, ClientPortals.links())));
        }
        buffers.endBatch(GooRenderTypes.GOO_PORTAL_IDLE_TYPE);
    }

    /**
     * While its owner chooses a partner, draws a thin outline of every one of
     * its portals in the level through the blocks, so one hidden or far away
     * can be found and picked without covering what stands before it; the
     * one being paired and the one aimed at blink with their swirl.
     *
     * @param pose     the pose
     * @param buffers  the frame's buffers
     * @param camera   the camera's position
     * @param player   the local player
     * @param blinking the portals blinking
     * @param aimed    the portal aimed at, empty for none
     * @param time     the ticks, with the frame's share of one
     */
    private static void drawOutlines(PoseStack.Pose pose, MultiBufferSource.BufferSource buffers, Vec3 camera,
            LocalPlayer player, List<Portal> blinking, Optional<Portal> aimed, float time) {
        VertexConsumer consumer = buffers.getBuffer(GooRenderTypes.GOO_PORTAL_OUTLINE_TYPE);
        for (Portal portal : ClientPortals.portals()) {
            if (portal.block().dimension().equals(player.level().dimension())) {
                int alpha = blinking.contains(portal)
                        ? blinkAlpha(aimed.isPresent() && aimed.get().equals(portal), time) : OUTLINE_ALPHA;
                emitOval(pose, consumer, portal.blocks().move(camera.reverse()), portal.face(),
                        ARGB.color(alpha, colorOf(portal, ClientPortals.links())));
            }
        }
        buffers.endBatch(GooRenderTypes.GOO_PORTAL_OUTLINE_TYPE);
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
                colorOf(portal, ClientPortals.links()), false)));
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
     * Draws the halo of every placed portal, waiting or linked, blinking or
     * not, and of both mouths of each hole, glowing onto the wall around its
     * edge in its own color, and beside it each portal's tornado rising off
     * the wall.
     * portal-halo-glows-additively-onto-the-wall
     * hole-mouths-wear-the-portal-halo
     * tornadic-halo-spirals-out-from-the-rim
     *
     * @param pose    the pose
     * @param buffers the frame's buffers
     * @param camera  the camera's position
     * @param gates   the gates the frame draws
     */
    private static void drawHalos(PoseStack.Pose pose, MultiBufferSource.BufferSource buffers, Vec3 camera,
            List<Drawn> gates) {
        emitHalos(pose, buffers.getBuffer(GooRenderTypes.GOO_PORTAL_HALO_TYPE), gates, drawn -> drawn.drawnBox(camera));
        buffers.endBatch(GooRenderTypes.GOO_PORTAL_HALO_TYPE);
        emitTornadoes(pose, buffers.getBuffer(GooRenderTypes.GOO_PORTAL_TORNADO_TYPE), gates,
                drawn -> drawn.drawnBox(camera));
        buffers.endBatch(GooRenderTypes.GOO_PORTAL_TORNADO_TYPE);
    }

    /**
     * Emits the halo of every gate the frame draws, each portal and each of
     * a hole's two mouths, in its own color, a mouth's edge marked torn.
     * portal-halo-glows-additively-onto-the-wall
     * hole-mouths-wear-the-portal-halo
     *
     * @param pose     the pose
     * @param consumer the halo pipeline's vertex consumer
     * @param gates    the gates the frame draws
     * @param boxOf    the box each gate draws over, camera-relative
     */
    /**
     * Emits the tornadic halo of every portal the frame draws, beside its
     * surface glow, in its own color.
     * tornadic-halo-spirals-out-from-the-rim
     *
     * @param pose     the pose
     * @param consumer the tornado pipeline's vertex consumer
     * @param gates    the gates the frame draws
     * @param boxOf    the box each gate draws over, camera-relative
     */
    static void emitTornadoes(PoseStack.Pose pose, VertexConsumer consumer, List<Drawn> gates,
            Function<Drawn, AABB> boxOf) {
        for (Drawn drawn : gates) {
            if (!drawn.hole()) {
                emitTornado(pose, consumer, boxOf.apply(drawn), drawn.gate().face(),
                        ARGB.color(CLEAN_EDGE, drawn.rgb()));
            }
        }
    }

    /**
     * Emits a gate's tornado as a funnel of rings: the first traces the
     * rim's oval on the face, each next one stands farther off it along the
     * face's normal and flares wider, up to the named height. Each quad maps
     * zero to one around the rim and up the funnel, and every vertex carries
     * the texels around the rim and up its height at sixteen a block, for the
     * shader's grid.
     * tornadic-halo-spirals-out-from-the-rim
     *
     * @param pose     the pose
     * @param consumer the vertex consumer
     * @param box      the gate's blocks, camera-relative
     * @param side     the face
     * @param color    the packed ARGB color, its alpha the edge mark
     */
    private static void emitTornado(PoseStack.Pose pose, VertexConsumer consumer, AABB box, Direction side,
            int color) {
        Funnel funnel = Funnel.over(box, side);
        int[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        for (int ring = 0; ring < TORNADO_RINGS; ring++) {
            for (int segment = 0; segment < TORNADO_SEGMENTS; segment++) {
                for (int[] corner : corners) {
                    Vec3 at = funnel.point(segment + corner[0], ring + corner[1]);
                    consumer.addVertex(pose, (float) at.x(), (float) at.y(), (float) at.z())
                            .setUv((float) (segment + corner[0]) / TORNADO_SEGMENTS,
                                    (float) (ring + corner[1]) / TORNADO_RINGS)
                            .setUv2(funnel.texels()[0], funnel.texels()[1]).setColor(color);
                }
            }
        }
    }

    /**
     * The funnel a gate's tornado draws over: the rim's oval on the face,
     * its half sizes across the face's two ways, rising off the face's plane.
     *
     * @param center the oval's middle on the face's plane, lifted off it
     * @param side   the face
     * @param half   the oval's half size across the face's first way, then its second
     */
    private record Funnel(Vec3 center, Direction side, double[] half) {

        static Funnel over(AABB box, Direction side) {
            Vec3 middle = cornerOf(box, side, 0, 0).add(cornerOf(box, side, 1, 1)).scale(HALF_WAVE);
            return new Funnel(middle, side, new double[] {(box.max(firstAxisOf(side)) - box.min(firstAxisOf(side)))
                    * HALF_WAVE, (box.max(secondAxisOf(side)) - box.min(secondAxisOf(side))) * HALF_WAVE});
        }

        /**
         * @param segment how many segments around the rim, of the named count
         * @param ring    how many rings up the funnel, of the named count
         * @return the funnel's point there
         */
        Vec3 point(int segment, int ring) {
            double up = (double) ring / TORNADO_RINGS;
            double spread = 1 + TORNADO_FLARE * up * up;
            double angle = Mth.TWO_PI * segment / TORNADO_SEGMENTS;
            Vec3 across = Vec3.ZERO.with(firstAxisOf(side), half[0] * spread * Math.cos(angle))
                    .with(secondAxisOf(side), half[1] * spread * Math.sin(angle));
            return center.add(across).add(side.getUnitVec3().scale(TORNADO_HEIGHT * up));
        }

        /**
         * @return the texels around the rim, by Ramanujan's reckoning of an
         *         oval's perimeter, then up the funnel, at sixteen a block
         */
        int[] texels() {
            double sum = half[0] + half[1];
            double around = Math.PI * (RAMANUJAN_THREE * sum - Math.sqrt((RAMANUJAN_THREE * half[0] + half[1])
                    * (half[0] + RAMANUJAN_THREE * half[1])));
            return new int[] {(int) Math.round(around * TEXELS_PER_BLOCK),
                    (int) Math.round(TORNADO_HEIGHT * TEXELS_PER_BLOCK)};
        }
    }

    static void emitHalos(PoseStack.Pose pose, VertexConsumer consumer, List<Drawn> gates,
            Function<Drawn, AABB> boxOf) {
        for (Drawn drawn : gates) {
            emitHalo(pose, consumer, boxOf.apply(drawn), drawn.gate().face(), drawn.rgb(), drawn.hole());
        }
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
     * mapped zero to one across each way, for the shader to fill as an oval,
     * each corner carrying the face's texel count, so the shader draws the
     * oval on a grid of sixteen texels a block.
     * portals-and-wormhole-draw-sixteen-texels-a-block
     *
     * @param pose     the pose
     * @param consumer the vertex consumer
     * @param box      the gate's blocks, camera-relative
     * @param side     the face
     * @param color    the packed ARGB color
     */
    static void emitOval(PoseStack.Pose pose, VertexConsumer consumer, AABB box, Direction side, int color) {
        int[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        int[] texels = texelsAcross(box, side);
        for (int[] corner : corners) {
            Vec3 at = cornerOf(box, side, corner[0], corner[1]);
            consumer.addVertex(pose, (float) at.x(), (float) at.y(), (float) at.z()).setUv(corner[0], corner[1])
                    .setUv2(texels[0], texels[1]).setColor(color);
        }
    }

    /**
     * Emits a gate's halo as a quad over its face reaching the halo margin
     * past its blocks on each way across the face, its corners mapped zero to
     * one across that wider quad and carrying its texel count, so the halo
     * shader draws on the same sixteen-texel grid as the rim and starts where
     * the gate's edge ends.
     * portal-halo-glows-additively-onto-the-wall
     *
     * @param pose     the pose
     * @param consumer the vertex consumer
     * @param box      the gate's blocks, camera-relative
     * @param side     the face
     * @param rgb      the gate's color, 0xRRGGBB
     * @param torn     whether the gate is a hole's mouth, its edge torn
     */
    private static void emitHalo(PoseStack.Pose pose, VertexConsumer consumer, AABB box, Direction side, int rgb,
            boolean torn) {
        Direction.Axis normal = side.getAxis();
        AABB halo = box.inflate(normal == Direction.Axis.X ? 0 : HALO_MARGIN, normal == Direction.Axis.Y ? 0 : HALO_MARGIN,
                normal == Direction.Axis.Z ? 0 : HALO_MARGIN);
        emitOval(pose, consumer, halo, side, ARGB.color(torn ? TORN_EDGE : CLEAN_EDGE, rgb));
    }

    /**
     * How many texels a gate's face runs across each of its two ways, at
     * sixteen a block as a block's texture does, the ways in the order its
     * corners map zero to one.
     * portals-and-wormhole-draw-sixteen-texels-a-block
     *
     * @param box  the gate's blocks
     * @param side the face
     * @return the texels across its first way, then its second
     */
    static int[] texelsAcross(AABB box, Direction side) {
        return new int[] {texelsAlong(box, firstAxisOf(side)), texelsAlong(box, secondAxisOf(side))};
    }

    private static int texelsAlong(AABB box, Direction.Axis axis) {
        return (int) Math.round((box.max(axis) - box.min(axis)) * TEXELS_PER_BLOCK);
    }

    private static Direction.Axis firstAxisOf(Direction side) {
        return side.getAxis() == Direction.Axis.X ? Direction.Axis.Y : Direction.Axis.X;
    }

    private static Direction.Axis secondAxisOf(Direction side) {
        return side.getAxis() == Direction.Axis.Z ? Direction.Axis.Y : Direction.Axis.Z;
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
        Direction.Axis firstAxis = firstAxisOf(side);
        Direction.Axis secondAxis = secondAxisOf(side);
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
