package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.quantum.Gate;
import com.mercuriusxeno.goo.ability.quantum.PortalTransform;
import com.mojang.blaze3d.ProjectionType;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.systems.RenderSystem;
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
    private static final float CLIP_OFFSET = 0.02F;
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

    /** The most views one frame draws: a portal pair's two and a hole's two. */
    private static final int MOST_VIEWS = 4;
    private static final String VIEW_PATH = "portal_view/";

    private static final List<RenderTarget> TARGETS = new ArrayList<>();
    private static final List<PortalViewTexture> TEXTURES = new ArrayList<>();
    private static final Map<Gate, Identifier> FRESH = new HashMap<>();
    private static final PortalCamera CAMERA = new PortalCamera();
    private static ProjectionMatrixBuffer projection;
    private static boolean drawing;

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
        return Optional.ofNullable(FRESH.get(gate));
    }

    /**
     * Draws the view through each gate the main camera faces, a linked
     * portal's or an open hole's mouth, before the frame's own extraction.
     *
     * @param delta   the frame's delta tracker
     * @param advance whether the frame draws the level
     */
    public static void drawBeforeExtract(DeltaTracker delta, boolean advance) {
        FRESH.clear();
        Minecraft mc = Minecraft.getInstance();
        if (!advance || !inWorld(mc) || !mc.gameRenderer.getMainCamera().isInitialized()) {
            return;
        }
        Camera main = mc.gameRenderer.getMainCamera();
        for (Gate[] pair : pairs()) {
            drawPair(mc, main, delta, pair);
        }
    }

    private static void drawPair(Minecraft mc, Camera main, DeltaTracker delta, Gate[] pair) {
        for (int end = 0; end < pair.length && FRESH.size() < MOST_VIEWS; end++) {
            drawEnd(mc, main, delta, pair[end], pair[pair.length - 1 - end]);
        }
    }

    /**
     * Every pair of gates a view can be drawn through: the local player's
     * linked portals and each open hole's two mouths.
     *
     * @return the pairs, each its two gates
     */
    private static List<Gate[]> pairs() {
        List<Gate[]> pairs = new ArrayList<>();
        ClientPortals.link().ifPresent(link -> pairs.add(new Gate[] {link.entry().gate(), link.exit().gate()}));
        ClientHoles.holes().forEach(hole -> pairs.add(new Gate[] {hole.entry(), hole.exit()}));
        return pairs;
    }

    private static void drawEnd(Minecraft mc, Camera main, DeltaTracker delta, Gate near, Gate far) {
        int slot = FRESH.size();
        if (shows(mc, main, near) && holds(mc, main, far) && drawThrough(mc, main, delta, slot, near, far)) {
            FRESH.put(near, Identifier.fromNamespaceAndPath(Goo.MODID, VIEW_PATH + slot));
        }
    }

    private static boolean inWorld(Minecraft mc) {
        return mc.level != null && mc.player != null && mc.isGameLoadFinished();
    }

    /**
     * Whether the main camera faces a portal, near enough and in its frustum, in its own level.
     *
     * @param mc   the client
     * @param main the main camera
     * @param near the portal looked into
     * @return true where the portal's view is worth drawing
     */
    private static boolean shows(Minecraft mc, Camera main, Gate near) {
        Vec3 toEye = main.position().subtract(near.center());
        return near.dimension().equals(mc.level.dimension())
                && toEye.dot(near.face().getUnitVec3()) > 0
                && toEye.length() < mc.options.getEffectiveRenderDistance() * BLOCKS_PER_CHUNK
                && main.getCullFrustum().isVisible(near.blocks().inflate(TOUCH));
    }

    /**
     * Whether the client holds the partner's surroundings to draw: its
     * chunk, within the view area around the main camera.
     *
     * @param mc   the client
     * @param main the main camera
     * @param far  the partner portal
     * @return true where the partner's side can be drawn
     */
    private static boolean holds(Minecraft mc, Camera main, Gate far) {
        ChunkPos chunk = ChunkPos.containing(BlockPos.containing(far.center()));
        return far.dimension().equals(mc.level.dimension())
                && mc.level.hasChunk(chunk.x(), chunk.z())
                && far.center().distanceTo(main.position())
                < (mc.options.getEffectiveRenderDistance() - 1) * BLOCKS_PER_CHUNK;
    }

    /**
     * Draws the partner's side into a portal's target from the camera
     * mirrored through the pair.
     *
     * @param mc    the client
     * @param main  the main camera
     * @param delta the frame's delta tracker
     * @param slot  the portal's view slot
     * @param near  the portal looked into
     * @param far   its partner
     * @return true when the view drew
     */
    private static boolean drawThrough(Minecraft mc, Camera main, DeltaTracker delta, int slot, Gate near,
            Gate far) {
        PortalTransform through = PortalTransform.between(near, far);
        Vec3 eye = through.point(main.position());
        if (eye.subtract(far.center()).dot(far.face().getUnitVec3()) > -NEAREST_PLANE) {
            return false;
        }
        CAMERA.place(mc.level, mc.player, delta, main.getFov(), eye,
                turnOf(through.direction(vec(main.forwardVector())), through.direction(vec(main.upVector()))));
        CameraRenderState camera = extractThrough(mc, delta);
        clipAt(camera.projectionMatrix, camera.viewRotationMatrix, eye, far);
        renderInto(mc, delta, targetFor(mc, slot), camera);
        return true;
    }

    /**
     * Extracts the level for the portal camera over the sections it sees,
     * putting the frame's own visible sections back after.
     *
     * @param mc    the client
     * @param delta the frame's delta tracker
     * @return the camera state the view renders with
     */
    private static CameraRenderState extractThrough(Minecraft mc, DeltaTracker delta) {
        LevelRenderer levels = mc.levelRenderer;
        CameraRenderState camera = mc.gameRenderer.gameRenderState.levelRenderState.cameraRenderState;
        List<SectionRenderDispatcher.RenderSection> mainSections = new ArrayList<>(levels.visibleSections);
        try {
            seenSections(levels.visibleSections, levels, CAMERA);
            levels.extractLevel(delta, CAMERA, delta.getGameTimeDeltaPartialTick(false));
            CAMERA.extractRenderState(camera, CAMERA.getCameraEntityPartialTicks(delta));
            camera.fogType = CAMERA.getFluidInCamera();
            camera.fogData = mc.gameRenderer.fogRenderer.setupFog(CAMERA, mc.options.getEffectiveRenderDistance(),
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
