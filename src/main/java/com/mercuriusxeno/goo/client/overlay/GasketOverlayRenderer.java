package com.mercuriusxeno.goo.client.overlay;

import com.mercuriusxeno.goo.block.gasket.IGasketHolder;
import com.mercuriusxeno.goo.client.CuboidBounds;
import com.mercuriusxeno.goo.client.ImmediateBuffers;
import com.mercuriusxeno.goo.client.LineContext;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.item.gasket.ChoralTunerItem;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.util.ARGB;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import java.util.function.Consumer;

/**
 * Renders translucent overlay quads on gasket face regions when the player
 * holds a choral tuner and looks at a gasket-capable block. Blue = RECEIVER,
 * orange = TRANSMITTER. Only the single region under the crosshair is shown.
 * Connected gaskets display diagonal warning stripes in the fog.
 */
public final class GasketOverlayRenderer {
    /** Translucent blue for receiver (input/cap) regions. */
    private static final int RECEIVER_COLOR = ARGB.color(100, 80, 140, 255);

    /** Translucent orange for transmitter (output/base) regions. */
    private static final int TRANSMITTER_COLOR = ARGB.color(100, 255, 160, 40);

    /** Stripe color for connected gaskets: darker variant with higher alpha. */
    private static final int RECEIVER_STRIPE = ARGB.color(160, 40, 80, 200);
    private static final int TRANSMITTER_STRIPE = ARGB.color(160, 200, 100, 10);

    /**
     * Small expansion applied to overlay AABBs so faces sit above the block
     * geometry instead of coplanar with it, preventing z-fighting flicker.
     */
    private static final double OVERLAY_EPSILON = 0.002;

    /** Wireframe color for receiver outlines (bright blue). */
    private static final int RECEIVER_WIRE = ARGB.color(200, 80, 140, 255);

    /** Wireframe color for transmitter outlines (bright orange). */
    private static final int TRANSMITTER_WIRE = ARGB.color(200, 255, 160, 40);

    private GasketOverlayRenderer() {}

    /**
     * Hands the shared path the gasket overlay when the player holds a
     * choral tuner and looks at a gasket-capable block. Its fill and stripes
     * hug the block's faces and its lines write depth, so it draws with the
     * opaque blocks and sorts by nothing.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     *
     * @param frame the frame being drawn
     * @param draws where the overlay's draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        Minecraft mc = Minecraft.getInstance();
        if (!isGasketOverlayActive(mc)) { return; }

        BlockHitResult hit = (BlockHitResult) mc.hitResult;
        BlockPos pos = hit.getBlockPos();
        if (!(mc.level.getBlockEntity(pos) instanceof IGasketHolder holder)) { return; }

        draws.accept(new SortedDraw(0, () -> renderGasketOverlay(mc, frame.poseStack(), hit, pos, holder)));
    }

    /**
     * Renders fill, wireframe, and connection overlays for a valid gasket target.
     *
     * @param mc the Minecraft instance
     * @param poseStack the level's pose stack
     * @param hit the block hit result
     * @param pos the block position
     * @param holder the gasket holder
     */
    private static void renderGasketOverlay(
            Minecraft mc, PoseStack poseStack,
            BlockHitResult hit, BlockPos pos, IGasketHolder holder) {
        AABB bounds = resolveOverlayBounds(holder, hit);
        if (bounds == null) { return; }

        GasketRole role = holder.resolveRole(hit);
        int slot = holder.resolveSlot(hit);
        boolean connected = holder.getPartner(role, slot) != null;

        renderFillOverlay(mc, poseStack, pos, bounds, role, connected);
        renderWireframeOverlay(mc, poseStack, pos, bounds, role);
        renderConnectionIfLinked(mc, poseStack, pos, bounds, role, slot, holder, connected);
    }

    /**
     * Checks whether the gasket overlay should render: player exists, holds a
     * choral tuner, and is looking at a block.
     *
     * @param mc the Minecraft instance
     * @return true if overlay rendering should proceed
     */
    private static boolean isGasketOverlayActive(Minecraft mc) {
        if (mc.player == null || mc.level == null) { return false; }
        if (!(mc.player.getMainHandItem().getItem() instanceof ChoralTunerItem)) { return false; }
        return mc.hitResult != null && mc.hitResult.getType() == HitResult.Type.BLOCK;
    }

    /**
     * Resolves the inflated overlay bounds for the targeted gasket region,
     * or null if the target is invalid (unsupported role, empty slot, etc.).
     *
     * @param holder the gasket holder interface
     * @param hit the block hit result
     * @return the inflated bounds, or null if invalid
     */
    private static AABB resolveOverlayBounds(IGasketHolder holder,
            BlockHitResult hit) {
        GasketRole role = holder.resolveRole(hit);
        int slot = holder.resolveSlot(hit);
        if (!holder.supportsRole(role)) { return null; }

        AABB bounds = holder.slotBoundsFor(slot, role);
        if (bounds == null) { return null; }
        return bounds.inflate(OVERLAY_EPSILON);
    }

    /**
     * Computes the camera-relative offset for a block position.
     *
     * @param pos the block position
     * @param camera the render camera
     * @return the camera-relative offset as a Vec3
     */
    private static Vec3 cameraOffset(BlockPos pos, Camera camera) {
        return new Vec3(
                pos.getX() - camera.position().x,
                pos.getY() - camera.position().y,
                pos.getZ() - camera.position().z);
    }

    /**
     * Renders the translucent filled quad and optional diagonal stripes.
     *
     * @param mc the Minecraft instance
     * @param poseStack the level's pose stack
     * @param pos the block position
     * @param bounds the inflated gasket bounds
     * @param role the gasket role
     * @param connected whether this gasket has a partner
     */
    private static void renderFillOverlay(
            Minecraft mc, PoseStack poseStack,
            BlockPos pos, AABB bounds, GasketRole role, boolean connected) {
        Vec3 ofs = cameraOffset(pos, mc.gameRenderer.mainCamera());
        ImmediateBuffers buf = ImmediateBuffers.shared();
        VertexConsumer quad = buf.getBuffer(RenderTypes.debugQuads());

        GasketMeshEmitter.renderFilledBox(poseStack, quad, bounds, ofs, roleColor(role));
        renderStripesIfConnected(poseStack, quad, bounds, ofs, role, connected);
        buf.endLastBatch();
    }

    /**
     * Renders diagonal stripes over the filled box if the gasket is connected.
     *
     * @param poseStack the pose stack
     * @param quad the vertex consumer
     * @param bounds the gasket bounds
     * @param ofs the camera offset
     * @param role the gasket role
     * @param connected whether this gasket has a partner
     */
    private static void renderStripesIfConnected(PoseStack poseStack, VertexConsumer quad,
            AABB bounds, Vec3 ofs, GasketRole role, boolean connected) {
        if (!connected) { return; }
        int stripeColor = role == GasketRole.RECEIVER ? RECEIVER_STRIPE : TRANSMITTER_STRIPE;
        GasketMeshEmitter.renderDiagonalStripes(poseStack, quad, bounds, ofs, stripeColor);
    }

    /**
     * Returns the fill color for a gasket role.
     *
     * @param role the gasket role
     * @return the ARGB fill color
     */
    private static int roleColor(GasketRole role) {
        return role == GasketRole.RECEIVER ? RECEIVER_COLOR : TRANSMITTER_COLOR;
    }

    /**
     * Renders the wireframe outline around the gasket region.
     *
     * @param mc the Minecraft instance
     * @param poseStack the level's pose stack
     * @param pos the block position
     * @param bounds the inflated gasket bounds
     * @param role the gasket role
     */
    private static void renderWireframeOverlay(
            Minecraft mc, PoseStack poseStack,
            BlockPos pos, AABB bounds, GasketRole role) {
        Vec3 ofs = cameraOffset(pos, mc.gameRenderer.mainCamera());
        ImmediateBuffers buf = ImmediateBuffers.shared();
        VertexConsumer line = buf.getBuffer(RenderTypes.lines());

        emitWireframeCuboid(poseStack, line, bounds, ofs, role, mc);
        buf.endLastBatch();
    }

    /**
     * Emits the wireframe cuboid edges for a gasket region.
     *
     * @param poseStack the pose stack
     * @param line the line vertex consumer
     * @param bounds the gasket bounds
     * @param ofs the camera offset
     * @param role the gasket role
     * @param mc the Minecraft instance
     */
    private static void emitWireframeCuboid(PoseStack poseStack, VertexConsumer line,
            AABB bounds, Vec3 ofs, GasketRole role, Minecraft mc) {
        int wireColor = role == GasketRole.RECEIVER ? RECEIVER_WIRE : TRANSMITTER_WIRE;
        float lineWidth = mc.getWindow().getAppropriateLineWidth();
        LineContext ctx = new LineContext(poseStack.last(), line);
        ctx.emitWireframe(new CuboidBounds(
            (float) (bounds.minX + ofs.x), (float) (bounds.maxX + ofs.x),
            (float) (bounds.minZ + ofs.z), (float) (bounds.maxZ + ofs.z),
            (float) (bounds.minY + ofs.y), (float) (bounds.maxY + ofs.y)),
            wireColor, lineWidth);
    }

    /**
     * Renders the glowing connection line to the partner block if linked.
     *
     * @param mc the Minecraft instance
     * @param poseStack the level's pose stack
     * @param pos the block position
     * @param bounds the inflated gasket bounds
     * @param role the gasket role
     * @param slot the slot index
     * @param holder the gasket holder
     * @param connected whether this gasket has a partner
     */
    private static void renderConnectionIfLinked(
            Minecraft mc, PoseStack poseStack,
            BlockPos pos, AABB bounds, GasketRole role, int slot,
            IGasketHolder holder, boolean connected) {
        if (!connected) { return; }
        GasketConnectionRenderer.renderConnectionLine(mc, poseStack,
                mc.gameRenderer.mainCamera(), pos, bounds, role, slot, holder);
    }
}
