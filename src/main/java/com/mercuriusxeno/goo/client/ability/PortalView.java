package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.PortalTransform;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Camera;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.TextureFilteringMethod;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.ProjectionMatrixBuffer;
import net.minecraft.client.renderer.chunk.CompiledSectionMesh;
import net.minecraft.client.renderer.chunk.RenderRegionCache;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.client.renderer.fog.FogRenderer;
import net.minecraft.client.renderer.state.GameRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.Identifier;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.joml.Vector4f;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Draws the view through each visible linked portal the way the portal gun
 * does: before the frame's own extraction, once per linked portal the camera
 * faces, the level is drawn into that portal's own target from a camera
 * mirrored through the pair, clipped at the partner's face so nothing behind
 * it shows, over the sections that camera sees. The portal's face then
 * samples that target at its own screen point, so looking into one portal
 * shows the other side as though it were right there. A partner beyond what
 * the client holds draws no view, and the portal keeps its idle face.
 * portals-link-by-looking-through-portal-vision
 */
public final class PortalView {

    /** How far in front of the partner's face the clip plane stands, so the partner's own block never shows. */
    private static final float CLIP_OFFSET = 0.001F;
    /** How near the partner's plane, in blocks, the mirrored eye may stand before the view is skipped. */
    private static final double NEAREST_PLANE = 0.01;
    private static final int BLOCKS_PER_CHUNK = 16;
    private static final float DEGREES = (float) (180.0 / Math.PI);
    private static final float RADIANS = (float) (Math.PI / 180.0);
    private static final float HALF_TURN = (float) Math.PI;
    private static final double TOUCH = 0.01;
    private static final double UNIT = 1.0;
    /** The projection's row writing clip depth, which the oblique plane replaces. */
    private static final int CLIP_DEPTH_ROW = 2;
    /** The projection's row writing clip w. */
    private static final int CLIP_W_ROW = 3;
    private static final String VIEW_LABEL = "goo_portal_view";
    /** The depth span of a minus-one-to-one clip space, which the oblique plane's scale doubles for. */
    private static final float MINUS_ONE_TO_ONE_DEPTH = 2F;
    private static final Vector3fc UP = new Vector3f(0, 1, 0);

    /** The most views one frame draws, the views within views among them. */
    private static final int MOST_VIEWS = 12;
    /** How many views deep a view within a view draws: the tunnel a facing pair makes shows this many turns. */
    private static final int DEEPEST_VIEW = 4;
    /** The main frame's pass. */
    private static final int MAIN_PASS = 0;
    private static final String VIEW_PATH = "portal_view/";

    private static final List<RenderTarget> TARGETS = new ArrayList<>();
    private static final List<PortalViewTexture> TEXTURES = new ArrayList<>();
    private static final Map<ViewKey, Identifier> VIEWS = new HashMap<>();
    /** A camera for each depth of view, so a view's camera stands while the views within it draw. */
    private static final List<PortalCamera> CAMERAS = new ArrayList<>();
    private static ProjectionMatrixBuffer projection;
    private static boolean drawing;
    /** The views drawn so far this frame. */
    private static int passes;
    /** The pass drawing now, zero for the main frame. */
    private static int currentPass = MAIN_PASS;
    /** The camera of the pass drawing now. */
    private static Camera currentCamera;
    /** Where the view being drawn draws its partner's side from. */
    private static PartnerView.Source source = PartnerView.Source.NONE;

    private PortalView() {
    }

    /**
     * Whether a portal view is being drawn now, so nothing drawn into it
     * samples a view again.
     *
     * @return true while the pass runs
     */
    public static boolean drawing() {
        return drawing;
    }

    /**
     * The view through a gate drawn this frame.
     *
     * @param gate a gate: a portal's frame or a hole's mouth
     * @return the view texture's id, empty where the gate drew no view
     */
    public static Optional<Identifier> viewOf(Gate gate) {
        return Optional.ofNullable(VIEWS.get(new ViewKey(currentPass, gate)));
    }

    /**
     * A view drawn for a gate seen from a pass: the main frame's, or a view's
     * own, so a gate seen through a view draws the view from that view's camera.
     *
     * @param pass the pass the gate is seen from, zero for the main frame
     * @param gate the gate
     */
    private record ViewKey(int pass, Gate gate) {
    }

    /**
     * Where a pass looks from.
     *
     * @param position the eye
     * @param forward  its unit forward
     * @param up       its unit up
     */
    private record Eye(Vec3 position, Vec3 forward, Vec3 up) {
    }

    /**
     * Draws the view through each gate the main camera faces, a linked
     * portal's or an open hole's mouth, before the frame's own extraction,
     * and within each view the views through the gates that view sees, so a
     * pair facing each other draws the tunnel between them.
     *
     * @param delta   the frame's delta tracker
     * @param advance whether the frame draws the level
     */
    public static void drawBeforeExtract(DeltaTracker delta, boolean advance) {
        VIEWS.clear();
        passes = 0;
        Minecraft mc = Minecraft.getInstance();
        if (!advance || !inWorld(mc) || !mc.gameRenderer.getMainCamera().isInitialized()) {
            return;
        }
        Camera main = mc.gameRenderer.getMainCamera();
        Eye eye = new Eye(main.position(), vec(main.forwardVector()), vec(main.upVector()));
        for (Gate[] way : ways()) {
            drawThrough(mc, delta, new Seen(MAIN_PASS, eye, main), way[0], way[1], 1);
        }
    }

    /**
     * A pass a gate is seen from: its id, its eye and its camera.
     *
     * @param pass   the pass, zero for the main frame
     * @param eye    where it looks from
     * @param camera its camera, whose frustum says what it sees
     */
    private record Seen(int pass, Eye eye, Camera camera) {
    }

    /**
     * Every way through a pair a view can be drawn, each gate looked into
     * then its partner: the linked portals' and each open hole's two mouths,
     * each pair both ways.
     *
     * @return the ways
     */
    private static List<Gate[]> ways() {
        List<Gate[]> ways = new ArrayList<>();
        ClientPortals.links().forEach(link -> {
            ways.add(new Gate[] {link.entry().gate(), link.exit().gate()});
            ways.add(new Gate[] {link.exit().gate(), link.entry().gate()});
        });
        ClientHoles.holes().forEach(hole -> {
            ways.add(new Gate[] {hole.entry(), hole.exit()});
            ways.add(new Gate[] {hole.exit(), hole.entry()});
        });
        return ways;
    }

    private static boolean inWorld(Minecraft mc) {
        return mc.level != null && mc.player != null && mc.isGameLoadFinished();
    }

    /**
     * Whether a pass faces a portal, near enough and in its frustum, in its own level.
     *
     * @param mc   the client
     * @param seen the pass looking
     * @param near the portal looked into
     * @return true where the portal's view is worth drawing
     */
    private static boolean shows(Minecraft mc, Seen seen, Gate near) {
        Vec3 toEye = seen.eye().position().subtract(near.center());
        return near.dimension().equals(mc.level.dimension())
                && toEye.dot(near.face().getUnitVec3()) > 0
                && toEye.length() < mc.options.getEffectiveRenderDistance() * BLOCKS_PER_CHUNK
                && seen.camera().getCullFrustum().isVisible(near.blocks().inflate(TOUCH));
    }

    /**
     * Where the client draws a partner's surroundings from: the ClientLevel
     * where its chunk stands within the view area around the main camera,
     * else the streamed view where the server streamed it, else nowhere.
     * linked-partner-chunks-stream-to-any-viewer
     *
     * @param mc  the client
     * @param far the partner portal
     * @return the source, NONE where the partner's side cannot be drawn
     */
    private static PartnerView.Source sourceOf(Minecraft mc, Gate far) {
        ChunkPos chunk = ChunkPos.containing(BlockPos.containing(far.center()));
        boolean clientLevelHolds = mc.level.hasChunk(chunk.x(), chunk.z())
                && far.center().distanceTo(mc.gameRenderer.getMainCamera().position())
                < (mc.options.getEffectiveRenderDistance() - 1) * BLOCKS_PER_CHUNK;
        return far.dimension().equals(mc.level.dimension())
                ? PartnerView.sourceFor(clientLevelHolds, PartnerView.CLIENT.holds(chunk)) : PartnerView.Source.NONE;
    }

    /**
     * @return whether the view being drawn draws its partner's side from the streamed view
     */
    public static boolean drawsStreamed() {
        return source == PartnerView.Source.STREAMED;
    }

    /**
     * Draws the partner's side into a gate's target from the seeing pass's
     * camera mirrored through the pair, having first drawn, from that mirrored
     * camera, the views through every gate it sees but the one it looks out of,
     * so the view shows them as the main frame shows it.
     *
     * @param mc    the client
     * @param delta the frame's delta tracker
     * @param seen  the pass the gate is seen from
     * @param near  the gate looked into
     * @param far   its partner
     * @param depth how many views deep this one stands
     */
    private static void drawThrough(Minecraft mc, DeltaTracker delta, Seen seen, Gate near, Gate far, int depth) {
        PartnerView.Source from = sourceOf(mc, far);
        if (passes >= MOST_VIEWS || from == PartnerView.Source.NONE || !shows(mc, seen, near)) {
            return;
        }
        PortalTransform through = PortalTransform.between(near, far);
        Eye eye = new Eye(through.point(seen.eye().position()), through.direction(seen.eye().forward()),
                through.direction(seen.eye().up()));
        if (eye.position().subtract(far.center()).dot(far.face().getUnitVec3()) > -NEAREST_PLANE) {
            return;
        }
        int pass = ++passes;
        PortalCamera camera = cameraAt(depth);
        camera.place(mc.level, mc.player, delta, mc.gameRenderer.getMainCamera().getFov(), eye.position(),
                turnOf(eye.forward(), eye.up()));
        if (depth < DEEPEST_VIEW) {
            for (Gate[] way : ways()) {
                if (!way[0].equals(far)) {
                    drawThrough(mc, delta, new Seen(pass, eye, camera), way[0], way[1], depth + 1);
                }
            }
        }
        currentPass = pass;
        currentCamera = camera;
        source = from;
        try {
            CameraRenderState state = extractThrough(mc, delta, camera);
            bobAsTheFrameDoes(mc, state);
            clipAt(state.projectionMatrix, state.viewRotationMatrix, eye.position(), far);
            renderInto(mc, delta, targetFor(mc, pass - 1), state);
        } finally {
            currentPass = MAIN_PASS;
        }
        VIEWS.put(new ViewKey(seen.pass(), near), Identifier.fromNamespaceAndPath(Goo.MODID, VIEW_PATH + (pass - 1)));
    }

    private static PortalCamera cameraAt(int depth) {
        while (CAMERAS.size() < depth) {
            CAMERAS.add(new PortalCamera());
        }
        return CAMERAS.get(depth - 1);
    }

    /**
     * Bobs the view's projection as the frame bobs its own, hurt and walk
     * both, so the view sampled on the gate's face stays flush with the frame
     * while the camera moves.
     *
     * @param mc     the client
     * @param camera the portal camera's state, its projection bobbed in place
     */
    private static void bobAsTheFrameDoes(Minecraft mc, CameraRenderState camera) {
        PoseStack bob = new PoseStack();
        mc.gameRenderer.bobHurt(camera, bob);
        if (mc.gameRenderer.gameRenderState.optionsRenderState.bobView) {
            mc.gameRenderer.bobView(camera, bob);
        }
        camera.projectionMatrix.mul(bob.last().pose());
    }

    /**
     * The position of the camera a view is being drawn from, for what draws
     * inside the view's own pass.
     *
     * @return the portal camera's position
     */
    public static Vec3 cameraPosition() {
        return currentCamera.position();
    }

    /**
     * Extracts the level for the portal camera over the sections it sees,
     * putting the frame's own visible sections back after.
     *
     * @param mc     the client
     * @param delta  the frame's delta tracker
     * @param portal the view's camera
     * @return the camera state the view renders with
     */
    private static CameraRenderState extractThrough(Minecraft mc, DeltaTracker delta, PortalCamera portal) {
        LevelRenderer levels = mc.levelRenderer;
        CameraRenderState camera = mc.gameRenderer.gameRenderState.levelRenderState.cameraRenderState;
        List<SectionRenderDispatcher.RenderSection> mainSections = new ArrayList<>(levels.visibleSections);
        try {
            seenSections(levels.visibleSections, levels, portal);
            levels.extractLevel(delta, portal, delta.getGameTimeDeltaPartialTick(false));
            portal.extractRenderState(camera, portal.getCameraEntityPartialTicks(delta));
            camera.fogType = portal.getFluidInCamera();
            camera.fogData = mc.gameRenderer.fogRenderer.setupFog(portal, mc.options.getEffectiveRenderDistance(),
                    delta, 0F, mc.level);
        } finally {
            levels.visibleSections.clear();
            levels.visibleSections.addAll(mainSections);
        }
        return camera;
    }

    /**
     * Renders the extracted level into a portal's target, swapped in for the
     * frame's own while it draws.
     *
     * @param mc     the client
     * @param delta  the frame's delta tracker
     * @param target the portal's target
     * @param camera the portal camera's state
     */
    private static void renderInto(Minecraft mc, DeltaTracker delta, RenderTarget target, CameraRenderState camera) {
        GameRenderer game = mc.gameRenderer;
        LevelRenderState state = game.gameRenderState.levelRenderState;
        RenderTarget frame = mc.mainRenderTarget;
        drawing = true;
        try {
            mc.mainRenderTarget = target;
            placeGlobalCamera(mc, delta, camera.pos);
            RenderSystem.setProjectionMatrix(projectionBuffer().getBuffer(camera.projectionMatrix),
                    ProjectionType.PERSPECTIVE);
            game.fogRenderer.updateBuffer(camera.fogData);
            mc.levelRenderer.renderLevel(game.resourcePool, delta, false, camera, camera.viewRotationMatrix,
                    game.fogRenderer.getBuffer(FogRenderer.FogMode.WORLD), camera.fogData.color, true,
                    state.chunkSectionsToRender);
        } finally {
            mc.mainRenderTarget = frame;
            drawing = false;
        }
    }

    /**
     * Sets the frame's global camera, which the terrain and entity shaders
     * lay geometry out from, to the portal camera's position. The frame's own
     * render sets it back to the main camera's before it draws.
     *
     * @param mc     the client
     * @param delta  the frame's delta tracker
     * @param eye    the portal camera's position
     */
    private static void placeGlobalCamera(Minecraft mc, DeltaTracker delta, Vec3 eye) {
        GameRenderState frame = mc.gameRenderer.gameRenderState;
        mc.gameRenderer.globalSettingsUniform.update(frame.windowRenderState.width, frame.windowRenderState.height,
                frame.optionsRenderState.glintStrength, mc.level.getGameTime(), delta,
                frame.optionsRenderState.menuBackgroundBlurriness, eye,
                frame.optionsRenderState.textureFiltering == TextureFilteringMethod.RGSS);
    }

    /**
     * Fills the visible sections with every section in the view area the
     * portal camera's frustum holds, and queues a build for each one that
     * waits for one: the frame builds only the sections the main camera sees,
     * so a section seen through the portal alone would stand unbuilt.
     *
     * @param visible the level renderer's visible sections, refilled
     * @param levels  the level renderer
     * @param camera  the portal camera
     */
    private static void seenSections(List<SectionRenderDispatcher.RenderSection> visible,
            LevelRenderer levels, Camera camera) {
        visible.clear();
        if (levels.viewArea == null) {
            return;
        }
        RenderRegionCache cache = new RenderRegionCache();
        for (SectionRenderDispatcher.RenderSection section : levels.viewArea.sections) {
            if (camera.getCullFrustum().isVisible(section.getBoundingBox())) {
                visible.add(section);
                buildWhenWaiting(section, cache);
            }
        }
    }

    /**
     * Queues a section's build where it waits for one, as the frame's own
     * compile does for the sections the main camera sees.
     *
     * @param section the section
     * @param cache   the frame's region cache
     */
    private static void buildWhenWaiting(SectionRenderDispatcher.RenderSection section, RenderRegionCache cache) {
        if (section.isDirty()
                && (section.getSectionMesh() != CompiledSectionMesh.UNCOMPILED || section.hasAllNeighbors())) {
            section.rebuildSectionAsync(cache);
            section.setNotDirty();
        }
    }

    /**
     * The yaw, pitch and roll, in degrees, a camera takes to look along a
     * forward with an up, in the camera's own convention.
     *
     * @param forward the unit forward
     * @param up      the unit up
     * @return the turn
     */
    static PortalCamera.Turn turnOf(Vec3 forward, Vec3 up) {
        float yaw = (float) Math.atan2(-forward.x(), forward.z()) * DEGREES;
        float pitch = (float) Math.asin(Math.clamp(-forward.y(), -UNIT, UNIT)) * DEGREES;
        Vector3f wanted = new Vector3f((float) up.x(), (float) up.y(), (float) up.z());
        float apart = (float) Math.acos(Math.clamp(upOf(yaw, pitch, 0F).dot(wanted), -UNIT, UNIT)) * DEGREES;
        float roll = upOf(yaw, pitch, apart).dot(wanted) >= upOf(yaw, pitch, -apart).dot(wanted) ? apart : -apart;
        return new PortalCamera.Turn(yaw, pitch, roll);
    }

    private static Vector3f upOf(float yaw, float pitch, float roll) {
        return new Quaternionf().rotationYXZ(HALF_TURN - yaw * RADIANS, -pitch * RADIANS, -roll * RADIANS)
                .transform(new Vector3f(UP));
    }

    /**
     * Bends a projection's near plane onto the partner's face (the oblique
     * near plane), so nothing between the mirrored eye and the partner draws.
     *
     * @param projection the projection, changed in place
     * @param viewRotation the camera's view rotation
     * @param eye        the mirrored eye
     * @param far        the partner portal
     */
    static void clipAt(Matrix4f projection, Matrix4f viewRotation, Vec3 eye, Gate far) {
        Vec3 normal = far.face().getUnitVec3();
        Vector3f inView = viewRotation.transformDirection(new Vector3f((float) normal.x(), (float) normal.y(),
                (float) normal.z()));
        float distance = (float) normal.dot(eye.subtract(far.center())) - CLIP_OFFSET;
        Vector4f plane = new Vector4f(inView.x(), inView.y(), inView.z(), distance);
        Vector4f corner = projection.invert(new Matrix4f())
                .transform(new Vector4f(Math.signum(plane.x()), Math.signum(plane.y()), 1F, 1F));
        boolean zeroToOne = RenderSystem.getDevice().isZZeroToOne();
        Vector4f scaled = new Vector4f(plane).mul((zeroToOne ? (float) UNIT : MINUS_ONE_TO_ONE_DEPTH)
                / plane.dot(corner));
        if (!zeroToOne) {
            scaled.sub(projection.getRow(CLIP_W_ROW, new Vector4f()));
        }
        projection.setRow(CLIP_DEPTH_ROW, scaled);
    }

    private static RenderTarget targetFor(Minecraft mc, int slot) {
        int width = mc.getMainRenderTarget().width;
        int height = mc.getMainRenderTarget().height;
        while (TARGETS.size() <= slot) {
            RenderTarget target = new TextureTarget(VIEW_LABEL, width, height, true);
            PortalViewTexture texture = new PortalViewTexture();
            TARGETS.add(target);
            TEXTURES.add(texture);
            mc.getTextureManager().register(Identifier.fromNamespaceAndPath(Goo.MODID, VIEW_PATH + (TARGETS.size() - 1)),
                    texture);
        }
        RenderTarget target = TARGETS.get(slot);
        if (target.width != width || target.height != height) {
            target.resize(width, height);
        }
        TEXTURES.get(slot).show(target);
        return target;
    }

    private static ProjectionMatrixBuffer projectionBuffer() {
        if (projection == null) {
            projection = new ProjectionMatrixBuffer(VIEW_LABEL);
        }
        return projection;
    }

    private static Vec3 vec(Vector3fc vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }
}
