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
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
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
import java.util.function.BiPredicate;
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
 * The edges of the Outsider room the player stands in show the opposite side
 * of the room flush over their whole face, with no rim and no swirl.
 * portals-link-by-looking-through-portal-vision
 * hole-closes-the-space-to-an-opening-ahead
 * outsider-room-wraps-and-repeats-at-the-platform-edge
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
     * How far the swirl band's arch peaks out of the wall along the face's
     * normal, right over its line, in blocks: one sixteenth, as the operator
     * set it.
     */
    static final double BAND_DEPTH = 1.0 / 16;
    /**
     * How far the swirl band's sleeve runs back into the portal behind its
     * face, in blocks: one sixteenth, as the operator set it, so the band
     * straddles the block face.
     */
    static final double BAND_SLEEVE = 1.0 / 16;
    /**
     * How far inside the oval's edge the swirl band's line runs, in blocks:
     * half a texel, through the middle of the rim's pixel ring, so the band's
     * smooth line cuts the pixel staircase.
     */
    static final double BAND_INSET = 0.5 / 16;
    /**
     * How far inside its line the swirl band lands on the face, over the
     * portal's view, in blocks: one texel, so the band runs no wider than the
     * rim's own pixel ring, tuned by eye.
     */
    static final double BAND_INSIDE = 1.0 / 16;
    /** How far outside its line the swirl band lands on the face, on the wall, in blocks: one texel, tuned by eye. */
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
            if (gate.mask() == Gate.Mask.RECTANGLE) {
                return box;
            }
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
    static List<Drawn> gatesIn(ResourceKey<Level> dimension) {
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
        // outsider-room-wraps-and-repeats-at-the-platform-edge: a room's edges wear no color, drawing no rim
        ClientRoomEdges.edges().forEach(pair -> {
            gates.add(new Drawn(pair.one(), 0, false));
            gates.add(new Drawn(pair.other(), 0, false));
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
                Face face = faceOf(drawn.gate(), true, PortalView.drawing(), false);
                if (face == Face.NONE) {
                    return;
                }
                RenderType type = viewTypeOf(face, drawn.hole(), view);
                emitOval(pose, buffers.getBuffer(type), drawn.drawnBox(camera), drawn.gate().face(),
                        ARGB.color(OPAQUE, drawn.rgb()));
                buffers.endBatch(type);
            });
        }
    }

    /**
     * The render type a gate's view draws through: a room edge's over its
     * whole rectangle, a hole's mouth see-through with a torn edge, a
     * portal's inside its oval under its rim.
     *
     * @param face how the gate's face draws
     * @param hole whether the gate is a hole's mouth
     * @param view the view's texture
     * @return the render type
     */
    private static RenderType viewTypeOf(Face face, boolean hole, Identifier view) {
        if (face == Face.RECTANGLE_VIEW) {
            // outsider-room-wraps-and-repeats-at-the-platform-edge
            return GooRenderTypes.gooRoomEdgeView(view);
        }
        // hole-closes-the-space-to-an-opening-ahead
        return hole ? GooRenderTypes.gooHoleView(view) : GooRenderTypes.gooPortalView(view);
    }

    /**
     * How a gate's face draws this frame.
     */
    enum Face {
        /** The view through, inside an oval under a rim of the gate's color. */
        OVAL_VIEW,
        /** The view through, over the face's whole rectangle with no rim. */
        RECTANGLE_VIEW,
        /** The idle swirl. */
        SWIRL,
        /** Nothing. */
        NONE
    }

    /**
     * How a gate's face draws: the view through it where one drew, else the
     * idle swirl, which inside a view's own pass only an inert gate shows.
     *
     * @param gate   the gate
     * @param viewed whether a view through it drew for the pass drawing
     * @param inPass whether a view's own pass is drawing
     * @param inert  whether no link the client knows holds it
     * @return how its face draws
     */
    static Face faceOf(Gate gate, boolean viewed, boolean inPass, boolean inert) {
        if (gate.mask() == Gate.Mask.RECTANGLE) {
            // outsider-room-wraps-and-repeats-at-the-platform-edge: one copy of the room shows past each edge, the
            // void beyond it, so an edge with no view, or one met inside a view, shows nothing
            return viewed && !inPass ? Face.RECTANGLE_VIEW : Face.NONE;
        }
        if (viewed) {
            return Face.OVAL_VIEW;
        }
        return !inPass || inert ? Face.SWIRL : Face.NONE;
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
            Face face = faceOf(drawn.gate(), PortalView.viewOf(drawn.gate()).isPresent(), PortalView.drawing(),
                    inert(drawn.gate()));
            if (face == Face.SWIRL) {
                emitOval(pose, consumer, drawn.drawnBox(camera), drawn.gate().face(),
                        ARGB.color(SWIRL_ALPHA, drawn.rgb()));
            }
        }
        buffers.endBatch(GooRenderTypes.GOO_PORTAL_IDLE_TYPE);
    }

    /**
     * The gates that wear a halo and swirl band in the pass drawing now:
     * every gate, but inside a view through a pair, never the partner the
     * view looks out of, whose own halo and band would ring the near gate's
     * view in the partner's color, blue on the orange portal. A room's edge
     * wears none, so the room runs on past it unringed.
     * outsider-room-wraps-and-repeats-at-the-platform-edge
     *
     * @param gates        the gates the frame draws
     * @param lookingOutOf the partner the view drawing now looks out of, empty outside a view
     * @return the gates that wear theirs
     */
    static List<Drawn> wornIn(List<Drawn> gates, Optional<Gate> lookingOutOf) {
        return gates.stream().filter(drawn -> drawn.gate().mask() != Gate.Mask.RECTANGLE
                && lookingOutOf.filter(drawn.gate()::equals).isEmpty()).toList();
    }

    /**
     * The gate whose swirl band's sleeve the pass drawing now shows: the band
     * straddles its portal's face, and the half behind the face runs back
     * into the portal, where only the view through it sees it. So inside a
     * view through a pair, the partner the view looks out of wears the near
     * gate's sleeve in the near gate's color: looking into the orange
     * portal, its orange swirl recedes into it. Outside a view, or where the
     * near gate is none the frame draws, or a room's edge, no gate wears one.
     * tornadic-halo-spirals-out-from-the-rim
     * outsider-room-wraps-and-repeats-at-the-platform-edge
     *
     * @param gates   the gates the frame draws
     * @param viewing the gate the view drawing now looks into, then its partner, empty outside a view
     * @return the partner in the near gate's color, or none
     */
    static List<Drawn> sleevedIn(List<Drawn> gates, Optional<GatePassage.Pair> viewing) {
        return viewing.flatMap(pair -> gates.stream().filter(drawn -> drawn.gate().equals(pair.one())
                        && drawn.gate().mask() != Gate.Mask.RECTANGLE).findFirst()
                .map(near -> new Drawn(pair.other(), near.rgb(), near.hole()))).stream().toList();
    }

    /**
     * Draws the halo of every placed portal, waiting or linked, blinking or
     * not, and of both mouths of each hole, glowing onto the wall around its
     * edge in its own color; beside it each one's swirl band standing out of
     * the wall; and inside a view through a pair, the near gate's band's
     * sleeve running back into it.
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
        Optional<GatePassage.Pair> viewing = PortalView.viewing();
        List<Drawn> worn = wornIn(gates, viewing.map(GatePassage.Pair::other));
        VertexConsumer halo = buffers.getBuffer(GooRenderTypes.GOO_PORTAL_HALO_TYPE);
        emitHalos(pose, halo, worn, drawn -> drawn.drawnBox(camera));
        Level level = Minecraft.getInstance().level;
        emitCornerGlows(pose, halo, worn, drawn -> drawn.drawnBox(camera),
                (pos, face) -> level != null && level.getBlockState(pos).isFaceSturdy(level, pos, face));
        buffers.endBatch(GooRenderTypes.GOO_PORTAL_HALO_TYPE);
        VertexConsumer bands = buffers.getBuffer(GooRenderTypes.GOO_PORTAL_SWIRL_BAND_TYPE);
        emitSwirlBands(pose, bands, worn, drawn -> drawn.drawnBox(camera));
        emitSleeves(pose, bands, sleevedIn(gates, viewing), drawn -> drawn.drawnBox(camera));
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
            emitSwirlBand(pose, consumer, Arch.of(drawn, boxOf), colorOf(drawn), false);
        }
    }

    /**
     * Emits the sleeve of each gate given: the half of its swirl band behind
     * its portal's face, a ring at the band's line running the named sleeve
     * depth back into the portal, which the view through the pair draws in
     * front of the partner's face.
     * tornadic-halo-spirals-out-from-the-rim
     *
     * @param pose     the pose
     * @param consumer the swirl band pipeline's vertex consumer
     * @param gates    the gates whose sleeves show, each in the color it wears
     * @param boxOf    the box each gate draws over, camera-relative
     */
    static void emitSleeves(PoseStack.Pose pose, VertexConsumer consumer, List<Drawn> gates,
            Function<Drawn, AABB> boxOf) {
        for (Drawn drawn : gates) {
            emitSwirlBand(pose, consumer, Arch.of(drawn, boxOf), colorOf(drawn), true);
        }
    }

    /**
     * @param drawn a gate the frame draws
     * @return its swirl band's packed ARGB color, its alpha the edge mark
     */
    private static int colorOf(Drawn drawn) {
        return ARGB.color(drawn.hole() ? TORN_EDGE : CLEAN_EDGE, drawn.rgb());
    }

    /**
     * Emits a gate's swirl band: before the face, an arch from the named
     * distance inside its line to the named distance outside, rising to the
     * named depth right over the line; or, for the sleeve, a ring at the line
     * running back into the portal. Each quad maps zero to one around the
     * rim, and across the arch zero to one from inside to outside, or along
     * the sleeve a half to one from the face back, the arch's line and the
     * sleeve's mouth meeting at a half; every vertex carries the texels
     * around the rim and across the arch at sixteen a block.
     * tornadic-halo-spirals-out-from-the-rim
     *
     * @param pose     the pose
     * @param consumer the vertex consumer
     * @param arch     the arch over the gate's rim
     * @param color    the packed ARGB color, its alpha the edge mark
     * @param sleeve   whether to emit the sleeve rather than the arch
     */
    private static void emitSwirlBand(PoseStack.Pose pose, VertexConsumer consumer, Arch arch, int color,
            boolean sleeve) {
        int[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        int[] texels = arch.texels();
        for (int level = 0; level < BAND_RINGS; level++) {
            for (int segment = 0; segment < BAND_SEGMENTS; segment++) {
                for (int[] corner : corners) {
                    double share = (double) (level + corner[1]) / BAND_RINGS;
                    Vec3 at = sleeve ? arch.sleevePoint(segment + corner[0], share)
                            : arch.point(segment + corner[0], share);
                    consumer.addVertex(pose, (float) at.x(), (float) at.y(), (float) at.z())
                            .setUv((float) (segment + corner[0]) / BAND_SEGMENTS,
                                    (float) (sleeve ? HALF_WAVE + HALF_WAVE * share : share))
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
         * @param drawn a gate the frame draws
         * @param boxOf the box each gate draws over, camera-relative
         * @return the arch over its rim, over the middle of its tear for a hole's mouth
         */
        static Arch of(Drawn drawn, Function<Drawn, AABB> boxOf) {
            return over(boxOf.apply(drawn), drawn.gate().face(), drawn.hole() ? TORN_MIDDLE : 1);
        }

        /**
         * @param segment how many segments around the rim, of the named count
         * @param share   how far across the arch from inside its line to outside, zero to one
         * @return the arch's point there
         */
        Vec3 point(int segment, double share) {
            return around(segment, Mth.lerp(share, -BAND_INSIDE, BAND_OUTSIDE))
                    .add(side.getUnitVec3().scale(BAND_DEPTH * Math.sin(Math.PI * share)));
        }

        /**
         * @param segment how many segments around the rim, of the named count
         * @param share   how far along the sleeve from the face back into the portal, zero to one
         * @return the sleeve's point there
         */
        Vec3 sleevePoint(int segment, double share) {
            return around(segment, 0).add(side.getUnitVec3().scale(BAND_SLEEVE * share));
        }

        /**
         * @param segment how many segments around the rim, of the named count
         * @param off     how far out past the band's line, in blocks
         * @return the point on the face that far out past the line at that segment
         */
        private Vec3 around(int segment, double off) {
            double angle = Mth.TWO_PI * segment / BAND_SEGMENTS;
            double out = off - BAND_INSET;
            return center.add(Vec3.ZERO.with(firstAxisOf(side), (half[0] * base + out) * Math.cos(angle))
                    .with(secondAxisOf(side), (half[1] * base + out) * Math.sin(angle)));
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
     * Emits the surface glow onto every block face forming a tight corner
     * around a gate's mouth: each full solid face standing in front of the
     * gate's wall, bordering its box on the left, the right, below or above,
     * and facing the mouth, lit in the gate's own color, a mouth's carrying
     * the torn edge's mark. Each quad runs from the wall out the halo margin
     * along the face, and its face coordinate unfolds the face past the wall
     * quad's edge, so the halo shader reads how far past as the height off
     * the wall and fades by true distance from the rim.
     * glow-wraps-onto-corner-faces
     * hole-mouths-wear-the-portal-halo
     *
     * @param pose     the pose
     * @param consumer the halo pipeline's vertex consumer
     * @param gates    the gates the frame draws
     * @param boxOf    the box each gate draws over, camera-relative
     * @param fullFace whether a block shows a full solid face its given way
     */
    static void emitCornerGlows(PoseStack.Pose pose, VertexConsumer consumer, List<Drawn> gates,
            Function<Drawn, AABB> boxOf, BiPredicate<BlockPos, Direction> fullFace) {
        for (Drawn drawn : gates) {
            AABB world = drawn.gate().blocks();
            Vec3 shift = boxOf.apply(drawn).getCenter().subtract(world.getCenter());
            for (Corner corner : cornersOf(world, drawn.gate().face(), fullFace)) {
                emitCornerGlow(pose, consumer, new CornerQuad(world, drawn.gate().face(), corner, shift),
                        ARGB.color(drawn.hole() ? TORN_EDGE : CLEAN_EDGE, drawn.rgb()));
            }
        }
    }

    /**
     * A block forming a corner around a gate's mouth and the way out from the
     * mouth to it.
     *
     * @param block the block, in the layer in front of the gate's wall
     * @param out   the way across the face from the mouth to the block
     */
    record Corner(BlockPos block, Direction out) {
    }

    /**
     * The blocks forming a tight corner around a gate's mouth: in the layer in
     * front of its wall, bordering its box on each way across the face, each
     * showing a full solid face back toward the mouth.
     * glow-wraps-onto-corner-faces
     *
     * @param world    the gate's blocks
     * @param side     the face
     * @param fullFace whether a block shows a full solid face its given way
     * @return the corners
     */
    static List<Corner> cornersOf(AABB world, Direction side, BiPredicate<BlockPos, Direction> fullFace) {
        List<Corner> corners = new ArrayList<>();
        for (BlockPos block : BlockPos.betweenClosed(BlockPos.containing(world.minX, world.minY, world.minZ),
                BlockPos.containing(world.maxX - 1, world.maxY - 1, world.maxZ - 1))) {
            for (Direction out : Direction.values()) {
                BlockPos beside = block.relative(out);
                BlockPos corner = beside.relative(side);
                if (out.getAxis() != side.getAxis() && !world.contains(Vec3.atCenterOf(beside))
                        && fullFace.test(corner, out.getOpposite())) {
                    corners.add(new Corner(corner.immutable(), out));
                }
            }
        }
        return corners;
    }

    /**
     * Emits one corner face's glow as a quad from the wall out the halo
     * margin, lifted off the face toward the mouth.
     *
     * @param pose     the pose
     * @param consumer the vertex consumer
     * @param quad     the corner's quad
     * @param color    the packed ARGB color, its alpha the edge mark
     */
    private static void emitCornerGlow(PoseStack.Pose pose, VertexConsumer consumer, CornerQuad quad, int color) {
        int[][] corners = {{0, 0}, {1, 0}, {1, 1}, {0, 1}};
        int[] texels = texelsAcross(quad.halo(), quad.side());
        for (int[] corner : corners) {
            double height = corner[1] * HALO_MARGIN;
            Vec3 at = quad.point(corner[0], height);
            double[] frame = quad.frame(corner[0], height);
            consumer.addVertex(pose, (float) at.x(), (float) at.y(), (float) at.z())
                    .setUv((float) frame[0], (float) frame[1]).setUv2(texels[0], texels[1]).setColor(color);
        }
    }

    /**
     * A corner face's glow quad: on the face of a block beside a gate's mouth
     * toward the mouth, running along the block and out from the wall.
     *
     * @param world  the gate's blocks
     * @param side   the gate's face
     * @param corner the corner block and the way out to it
     * @param shift  what moves world positions to where the frame draws them
     */
    private record CornerQuad(AABB world, Direction side, Corner corner, Vec3 shift) {

        /** @return the gate's halo quad's box, whose face coordinate the corner unfolds past */
        AABB halo() {
            Direction.Axis normal = side.getAxis();
            return world.inflate(normal == Direction.Axis.X ? 0 : HALO_MARGIN,
                    normal == Direction.Axis.Y ? 0 : HALO_MARGIN, normal == Direction.Axis.Z ? 0 : HALO_MARGIN);
        }

        /**
         * @param end    zero for the block's low end along the box's edge, one for its high
         * @param height how far off the wall, in blocks
         * @return the quad's corner there, lifted off the face toward the mouth
         */
        Vec3 point(int end, double height) {
            Direction out = corner.out();
            Vec3 on = Vec3.ZERO.with(side.getAxis(), wall() + side.getAxisDirection().getStep() * height)
                    .with(out.getAxis(), boundary() - out.getAxisDirection().getStep() * FACE_LIFT)
                    .with(along(), corner.block().get(along()) + end);
            return on.add(shift);
        }

        /**
         * @param end    zero for the block's low end along the box's edge, one for its high
         * @param height how far off the wall, in blocks
         * @return the face coordinate there, the face unfolded past the halo quad's edge by the height
         */
        double[] frame(int end, double height) {
            Direction out = corner.out();
            Vec3 unfolded = Vec3.ZERO.with(out.getAxis(), boundary()
                    + out.getAxisDirection().getStep() * (HALO_MARGIN + height))
                    .with(along(), corner.block().get(along()) + end);
            AABB halo = halo();
            return new double[] {share(halo, unfolded, firstAxisOf(side)), share(halo, unfolded, secondAxisOf(side))};
        }

        private static double share(AABB box, Vec3 at, Direction.Axis axis) {
            return (at.get(axis) - box.min(axis)) / (box.max(axis) - box.min(axis));
        }

        private double wall() {
            return side.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? world.max(side.getAxis()) : world.min(side.getAxis());
        }

        private double boundary() {
            Direction out = corner.out();
            return out.getAxisDirection() == Direction.AxisDirection.POSITIVE
                    ? world.max(out.getAxis()) : world.min(out.getAxis());
        }

        private Direction.Axis along() {
            return firstAxisOf(side) == corner.out().getAxis() ? secondAxisOf(side) : firstAxisOf(side);
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
