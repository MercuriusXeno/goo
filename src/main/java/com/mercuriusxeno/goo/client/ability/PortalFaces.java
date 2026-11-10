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
    /**
     * How far the swirl band's arch peaks off the face along its normal,
     * right over the rim, in blocks: two sixteenths, the most the operator's
     * reference stands out, tuned by eye.
     */
    static final double BAND_DEPTH = 2.0 / 16;
    /**
     * How far inside the rim the swirl band lands on the face, over the
     * portal's view, in blocks: one texel, so the band runs no wider than the
     * rim's own pixel ring, tuned by eye.
     */
    static final double BAND_INSIDE = 1.0 / 16;
    /** How far outside the rim the swirl band lands on the face, on the wall, in blocks: one texel, tuned by eye. */
    static final double BAND_OUTSIDE = 1.0 / 16;
    /** How many rings the swirl band's arch runs across in, from inside the rim to outside. */
    private static final int BAND_RINGS = 6;
    /** How many segments each of the band's rings runs around the rim in, enough that it draws round. */
    private static final int BAND_SEGMENTS = 64;
    /**
     * Where a hole mouth's band arches over, as a share of the oval's
     * radius: the middle of the mouth's tear, one less half HOLE_TEAR_DEPTH
     * in portal_texels.glsl, so the band reaches the tear wherever the tear
     * stands, and the swirl band shader cuts what lies inside it.
     */
    private static final double TORN_MIDDLE = 0.92;
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
     * edge in its own color, and beside it each portal's swirl band rising off
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
    /**
     * The gates that wear a halo and swirl band in the pass drawing now:
     * every gate, but inside a view through a pair, never the partner the
     * view looks out of, whose own halo and band would ring the near gate's
     * view in the partner's color, blue on the orange portal.
     *
     * @param gates        the gates the frame draws
     * @param lookingOutOf the partner the view drawing now looks out of, empty outside a view
     * @return the gates that wear theirs
     */
    static List<Drawn> wornIn(List<Drawn> gates, Optional<Gate> lookingOutOf) {
        return gates.stream().filter(drawn -> lookingOutOf.filter(drawn.gate()::equals).isEmpty()).toList();
    }

    private static void drawHalos(PoseStack.Pose pose, MultiBufferSource.BufferSource buffers, Vec3 camera,
            List<Drawn> gates) {
        List<Drawn> worn = wornIn(gates, PortalView.lookingOutOf());
        emitHalos(pose, buffers.getBuffer(GooRenderTypes.GOO_PORTAL_HALO_TYPE), worn, drawn -> drawn.drawnBox(camera));
        buffers.endBatch(GooRenderTypes.GOO_PORTAL_HALO_TYPE);
        emitSwirlBands(pose, buffers.getBuffer(GooRenderTypes.GOO_PORTAL_SWIRL_BAND_TYPE), worn,
                drawn -> drawn.drawnBox(camera));
        buffers.endBatch(GooRenderTypes.GOO_PORTAL_SWIRL_BAND_TYPE);
    }

    /**
     * Emits the swirl band of every gate the frame draws, each portal and
     * each of a hole's two mouths, beside its surface glow, in its own color.
     * A mouth's edge is marked torn and its band arches over the middle of
     * its tear, so the shader starts it at the tear.
     * tornadic-halo-spirals-out-from-the-rim
     * hole-mouths-wear-the-tornadic-halo
     *
     * @param pose     the pose
     * @param consumer the swirl band pipeline's vertex consumer
     * @param gates    the gates the frame draws
     * @param boxOf    the box each gate draws over, camera-relative
     */
    static void emitSwirlBands(PoseStack.Pose pose, VertexConsumer consumer, List<Drawn> gates,
            Function<Drawn, AABB> boxOf) {
        for (Drawn drawn : gates) {
            Arch arch = Arch.over(boxOf.apply(drawn), drawn.gate().face(), drawn.hole() ? TORN_MIDDLE : 1);
            emitSwirlBand(pose, consumer, arch, ARGB.color(drawn.hole() ? TORN_EDGE : CLEAN_EDGE, drawn.rgb()));
        }
    }

    /**
     * Emits a gate's swirl band as an arch straddling the rim: rings trace
     * the rim's oval from the named distance inside it to the named distance
     * outside, rising off the face to the named depth right over the rim and
     * landing on the face at both edges. Each quad maps zero to one around
     * the rim and across the arch from inside to outside, and every vertex
     * carries the texels around the rim and across the arch at sixteen a
     * block.
     * tornadic-halo-spirals-out-from-the-rim
     *
     * @param pose     the pose
     * @param consumer the vertex consumer
     * @param arch     the arch over the gate's rim
     * @param color    the packed ARGB color, its alpha the edge mark
     */
    private static void emitSwirlBand(PoseStack.Pose pose, VertexConsumer consumer, Arch arch, int color) {
        int[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        int[] texels = arch.texels();
        for (int level = 0; level < BAND_RINGS; level++) {
            for (int segment = 0; segment < BAND_SEGMENTS; segment++) {
                for (int[] corner : corners) {
                    Vec3 at = arch.point(segment + corner[0], level + corner[1]);
                    consumer.addVertex(pose, (float) at.x(), (float) at.y(), (float) at.z())
                            .setUv((float) (segment + corner[0]) / BAND_SEGMENTS,
                                    (float) (level + corner[1]) / BAND_RINGS)
                            .setUv2(texels[0], texels[1]).setColor(color);
                }
            }
        }
    }

    /**
     * The arch a gate's swirl band draws over: the rim's oval on the face,
     * its half sizes across the face's two ways, the arch straddling it.
     *
     * @param center the oval's middle on the face's plane, lifted off it
     * @param side   the face
     * @param half   the oval's half size across the face's first way, then its second
     * @param base   where the arch peaks, as a share of the oval's radius
     */
    private record Arch(Vec3 center, Direction side, double[] half, double base) {

        /**
         * @param box  the gate's blocks, camera-relative
         * @param side the face
         * @param base where the arch peaks, as a share of the oval's radius
         * @return the arch
         */
        static Arch over(AABB box, Direction side, double base) {
            Vec3 middle = cornerOf(box, side, 0, 0).add(cornerOf(box, side, 1, 1)).scale(HALF_WAVE);
            return new Arch(middle, side, new double[] {(box.max(firstAxisOf(side)) - box.min(firstAxisOf(side)))
                    * HALF_WAVE, (box.max(secondAxisOf(side)) - box.min(secondAxisOf(side))) * HALF_WAVE}, base);
        }

        /**
         * @param segment how many segments around the rim, of the named count
         * @param level   how many rings across the arch from inside the rim, of the named count
         * @return the arch's point there
         */
        Vec3 point(int segment, int level) {
            double share = (double) level / BAND_RINGS;
            double off = Mth.lerp(share, -BAND_INSIDE, BAND_OUTSIDE);
            double angle = Mth.TWO_PI * segment / BAND_SEGMENTS;
            Vec3 across = Vec3.ZERO.with(firstAxisOf(side), (half[0] * base + off) * Math.cos(angle))
                    .with(secondAxisOf(side), (half[1] * base + off) * Math.sin(angle));
            return center.add(across).add(side.getUnitVec3().scale(BAND_DEPTH * Math.sin(Math.PI * share)));
        }

        /**
         * @return the texels around the arch's peak, by Ramanujan's reckoning
         *         of an oval's perimeter, then across the arch from inside the
         *         rim to outside, at sixteen a block
         */
        int[] texels() {
            double first = half[0] * base;
            double second = half[1] * base;
            double around = Math.PI * (RAMANUJAN_THREE * (first + second) - Math.sqrt((RAMANUJAN_THREE * first
                    + second) * (first + RAMANUJAN_THREE * second)));
            return new int[] {(int) Math.round(around * TEXELS_PER_BLOCK),
                    (int) Math.round((BAND_INSIDE + BAND_OUTSIDE) * TEXELS_PER_BLOCK)};
        }
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
