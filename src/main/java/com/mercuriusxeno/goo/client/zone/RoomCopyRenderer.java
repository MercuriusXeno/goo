package com.mercuriusxeno.goo.client.zone;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.zone.RoomExtent;
import com.mercuriusxeno.goo.ability.zone.ZoneRooms;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.StagePass;
import com.mercuriusxeno.goo.client.TranslucentVisual.LevelFrame;
import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.ability.ClientRoomEdges;
import com.mercuriusxeno.goo.client.ability.PortalView;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.renderpearl.api.buffers.GpuBuffer;
import com.mojang.renderpearl.api.buffers.GpuBufferSlice;
import com.mojang.renderpearl.api.commands.RenderPass;
import com.mojang.renderpearl.api.pipeline.PrimitiveTopology;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import com.mojang.renderpearl.api.textures.AddressMode;
import com.mojang.renderpearl.api.textures.FilterMode;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.state.level.LevelRenderState;
import net.minecraft.core.BlockPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.jspecify.annotations.Nullable;
import java.util.Optional;
import java.util.function.Consumer;

/**
 * Draws the copies of the Zone room the player stands in, out to the
 * client's render distance in every direction, corners included: one
 * instance of each of the room's meshes for every copy the camera sees, each
 * moved by its copy's offset, its solid and cutout blocks after the frame's
 * own, its translucent blocks after the frame's translucent blocks, and each block entity it holds submitted once a copy.
 * The pocket's star sky drew before, so the copies stand in front of it. A
 * view through a linked portal draws them around its own camera.
 * decision room-tiles-to-render-distance-held-to-the-picture
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class RoomCopyRenderer {

    private static final String PASS_LABEL = "Goo room copies";
    private static final String TRANSLATIONS_LABEL = "Goo room copy translations";
    private static final String TRANSFORMS = "DynamicTransforms";
    private static final String BLOCK_ATLAS_SAMPLER = "Sampler0";
    private static final String LIGHTMAP_SAMPLER = "Sampler2";
    private static final Vector4f WHITE = new Vector4f(1F, 1F, 1F, 1F);

    private RoomCopyRenderer() {
    }

    /**
     * Notes a section's blocks changed, as the level renderer marks it dirty.
     *
     * @param sectionX the section's x
     * @param sectionY the section's y
     * @param sectionZ the section's z
     */
    public static void markDirty(int sectionX, int sectionY, int sectionZ) {
        RoomCopyMeshes.markDirty(sectionX, sectionY, sectionZ);
    }

    /**
     * Hands the shared path the copies' solid and cutout blocks, laying the
     * room's meshes out afresh first: opaque, writing their depth, drawn
     * with the opaque blocks, so they sort by nothing.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision room-tiles-to-render-distance-held-to-the-picture
     *
     * @param frame the frame being drawn
     * @param draws where the copies' draw goes
     */
    public static void collectSolid(LevelFrame frame, Consumer<SortedDraw> draws) {
        drawsFor(frame.levelRenderState()).ifPresent(seen -> draws.accept(new SortedDraw(0,
                () -> drawSolid(frame, seen))));
    }

    /**
     * Hands the shared path the copies' translucent blocks, one instanced
     * batch drawn before every other far-to-near draw. The copies' floor
     * stars draw with the room's own, through VoidstoneFaces, from the
     * pocket's floor where each copy stands.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     * decision room-tiles-to-render-distance-held-to-the-picture
     *
     * @param frame the frame being drawn
     * @param draws where the copies' draw goes
     */
    public static void collect(LevelFrame frame, Consumer<SortedDraw> draws) {
        // Vanilla draws its translucent terrain before any goo visual, and the copies are that terrain tiled out, so
        // they sort as the farthest draw and run first.
        drawsFor(frame.levelRenderState()).ifPresent(seen -> draws.accept(new SortedDraw(Double.MAX_VALUE,
                () -> drawTranslucent(frame, seen))));
    }

    private static void drawSolid(LevelFrame frame, RoomCopyDraws.Draws seen) {
        try (Copies copies = copiesOf(frame.levelRenderState(), seen)) {
            drawLayer(copies, ChunkSectionLayer.SOLID, GooRenderTypes.ROOM_COPY_SOLID, frame.modelView());
            drawLayer(copies, ChunkSectionLayer.CUTOUT, GooRenderTypes.ROOM_COPY_CUTOUT, frame.modelView());
        }
    }

    private static void drawTranslucent(LevelFrame frame, RoomCopyDraws.Draws seen) {
        try (Copies copies = copiesOf(frame.levelRenderState(), seen)) {
            drawLayer(copies, ChunkSectionLayer.TRANSLUCENT, GooRenderTypes.ROOM_COPY_TRANSLUCENT, frame.modelView());
        }
    }

    /**
     * Submits each block entity the room holds once for every copy the camera sees.
     *
     * @param event the custom geometry submit event
     */
    @SubscribeEvent
    public static void onSubmitCustomGeometry(SubmitCustomGeometryEvent event) {
        LevelRenderState state = event.getLevelRenderState();
        Optional<RoomCopyDraws.Draws> draws = drawsFor(state);
        if (draws.isEmpty() || RoomCopyMeshes.blockEntities().isEmpty()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        BlockEntityRenderDispatcher dispatcher = mc.getBlockEntityRenderDispatcher();
        float partial = mc.getDeltaTracker().getGameTimeDeltaPartialTick(false);
        Vec3 camera = state.cameraRenderState.pos;
        PoseStack poses = event.getPoseStack();
        for (BlockEntity entity : RoomCopyMeshes.blockEntities()) {
            BlockEntityRenderState extracted = extractCopy(dispatcher, entity, partial);
            if (extracted == null) {
                continue;
            }
            BlockPos at = entity.getBlockPos();
            for (RoomCopies.Offset offset : draws.get().drawn()) {
                poses.pushPose();
                poses.translate(at.getX() + offset.east() - camera.x(), at.getY() - camera.y(),
                        at.getZ() + offset.south() - camera.z());
                dispatcher.submit(extracted, poses, event.getSubmitNodeCollector(), state.cameraRenderState);
                poses.popPose();
            }
        }
    }

    /**
     * Drops the room's meshes as the client leaves the server.
     *
     * @param event the logout event
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        RoomCopyMeshes.release();
    }

    /**
     * The copies a pass draws and the translations they read, laid out afresh
     * where the room changed.
     *
     * @param camera the pass's camera
     * @param draws  the copies the camera sees
     * @param translations the translations on the GPU, freed when the pass is done with them
     */
    private record Copies(Vec3 camera, RoomCopyDraws.Draws draws, GpuBuffer translations) implements AutoCloseable {
        @Override
        public void close() {
            translations.close();
        }
    }

    private static Copies copiesOf(LevelRenderState state, RoomCopyDraws.Draws draws) {
        return new Copies(state.cameraRenderState.pos, draws, RenderSystem.getDevice().createBuffer(
                () -> TRANSLATIONS_LABEL, GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER, draws.translations()));
    }

    /**
     * The copies the pass drawing now sees, for the room the player stands
     * in, its meshes laid out afresh where the room changed.
     *
     * @param state the pass's level render state
     * @return the draws, empty outside a room or where the camera sees no copy
     */
    private static Optional<RoomCopyDraws.Draws> drawsFor(LevelRenderState state) {
        Minecraft mc = Minecraft.getInstance();
        Optional<RoomExtent.Bounds> bounds = ClientRoomEdges.bounds();
        if (mc.level == null || mc.level.dimension() != ZoneRooms.POCKET || bounds.isEmpty()) {
            return Optional.empty();
        }
        RoomCopyMeshes.refresh(mc.level, bounds.get());
        Frustum frustum = PortalView.drawing() ? PortalView.camera().getCullFrustum()
                : mc.gameRenderer.mainCamera().getCullFrustum();
        RoomCopyDraws.Draws draws = RoomCopyDraws.of(bounds.get(),
                RoomCopies.offsets(bounds.get(), RoomCopies.reachOf(mc.options.getEffectiveRenderDistance())),
                RoomCopyMeshes.bottom(), RoomCopyMeshes.top(), frustum::isVisible);
        return draws.instances() == 0 ? Optional.empty() : Optional.of(draws);
    }

    private static void drawLayer(Copies copies, ChunkSectionLayer layer, RenderPipeline pipeline,
            Matrix4fc modelView) {
        for (RoomCopyMeshes.Uploaded mesh : RoomCopyMeshes.layer(layer)) {
            draw(copies, mesh, pipeline, modelView);
        }
    }

    /**
     * Draws one of the room's meshes once for every copy seen.
     *
     * @param copies    the copies seen and their translations
     * @param mesh      the mesh
     * @param pipeline  the pipeline it draws through
     * @param modelView the pass's model view
     */
    private static void draw(Copies copies, RoomCopyMeshes.Uploaded mesh, RenderPipeline pipeline,
            Matrix4fc modelView) {
        Minecraft mc = Minecraft.getInstance();
        Vector3f fromCamera = new Vector3f((float) (mesh.origin().getX() - copies.camera().x()),
                (float) (mesh.origin().getY() - copies.camera().y()), (float) (mesh.origin().getZ() - copies.camera().z()));
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView), WHITE,
                fromCamera, new Matrix4f());
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(PrimitiveTopology.QUADS);
        RenderTarget target = mc.gameRenderer.mainRenderTarget();
        try (StagePass stage = StagePass.onMain(() -> PASS_LABEL)) {
            RenderPass pass = stage.pass();
            pass.setPipeline(RenderSystem.getCompiledPipeline(pipeline));
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(TRANSFORMS, transforms);
            pass.setUniform(GooRenderTypes.ROOM_COPY_TRANSLATIONS, copies.translations());
            bindBlockTextures(mc, pass);
            pass.setVertexBuffer(0, mesh.vertices().slice());
            pass.setIndexBuffer(indices.getBuffer(mesh.indexCount()), indices.type());
            pass.drawIndexed(mesh.indexCount(), copies.draws().instances(), 0, 0, 0);
        }
    }

    private static void bindBlockTextures(Minecraft mc, RenderPass pass) {
        pass.setUniform(BLOCK_ATLAS_SAMPLER, mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getTextureView(),
                RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                        FilterMode.LINEAR, FilterMode.NEAREST, true));
        pass.setUniform(LIGHTMAP_SAMPLER, mc.gameRenderer.lightmap(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    }

    // The dispatcher extracts an off-screen renderer's block entity on a pass of its own and asks which pass
    // it is; a copy draws every block entity on one, so it answers as each renderer asks.
    private static @Nullable BlockEntityRenderState extractCopy(BlockEntityRenderDispatcher dispatcher, BlockEntity entity,
                                                                float partial) {
        BlockEntityRenderer<BlockEntity, BlockEntityRenderState> renderer = dispatcher.getRenderer(entity);
        return renderer == null ? null
                : dispatcher.tryExtractRenderState(entity, partial, null, renderer.shouldRenderOffScreen(), null);
    }
}
