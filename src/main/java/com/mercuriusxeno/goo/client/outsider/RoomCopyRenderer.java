package com.mercuriusxeno.goo.client.outsider;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.outsider.OutsiderRooms;
import com.mercuriusxeno.goo.ability.outsider.RoomExtent;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ability.ClientRoomEdges;
import com.mercuriusxeno.goo.client.ability.PortalView;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
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
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import net.neoforged.neoforge.client.event.SubmitCustomGeometryEvent;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws the copies of the Outsider room the player stands in, out to the
 * client's render distance in every direction, corners included: one
 * instance of each of the room's meshes for every copy the camera sees, each
 * moved by its copy's offset, its solid and cutout blocks after the frame's
 * own, its translucent blocks and its floor's stars after the frame's
 * translucent blocks, and each block entity it holds submitted once a copy.
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
     * @param sectionZ the section's z
     */
    public static void markDirty(int sectionX, int sectionZ) {
        RoomCopyMeshes.markDirty(sectionX, sectionZ);
    }

    /**
     * Draws the copies' solid and cutout blocks once the frame's own are drawn.
     *
     * @param event the level stage event
     */
    @SubscribeEvent
    public static void onAfterOpaqueBlocks(RenderLevelStageEvent.AfterOpaqueBlocks event) {
        seenCopies(event.getLevelRenderState()).ifPresent(copies -> {
            try (copies) {
                drawLayer(copies, ChunkSectionLayer.SOLID, GooRenderTypes.ROOM_COPY_SOLID, event.getModelViewMatrix());
                drawLayer(copies, ChunkSectionLayer.CUTOUT, GooRenderTypes.ROOM_COPY_CUTOUT,
                        event.getModelViewMatrix());
            }
        });
    }

    /**
     * Draws the copies' translucent blocks and floor stars once the frame's translucent blocks are drawn.
     *
     * @param event the level stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        seenCopies(event.getLevelRenderState()).ifPresent(copies -> {
            try (copies) {
                drawLayer(copies, ChunkSectionLayer.TRANSLUCENT, GooRenderTypes.ROOM_COPY_TRANSLUCENT,
                        event.getModelViewMatrix());
                RoomCopyMeshes.Uploaded floor = RoomCopyMeshes.floor();
                if (floor != null) {
                    draw(copies, floor, GooRenderTypes.VOIDSTONE_COPY, event.getModelViewMatrix(), false);
                }
            }
        });
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
            BlockEntityRenderState extracted = dispatcher.tryExtractRenderState(entity, partial, null, null);
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

    private static Optional<Copies> seenCopies(LevelRenderState state) {
        return drawsFor(state).map(draws -> new Copies(state.cameraRenderState.pos, draws,
                RenderSystem.getDevice().createBuffer(() -> TRANSLATIONS_LABEL, GpuBuffer.USAGE_UNIFORM_TEXEL_BUFFER,
                        draws.translations())));
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
        if (mc.level == null || mc.level.dimension() != OutsiderRooms.POCKET || bounds.isEmpty()) {
            return Optional.empty();
        }
        RoomCopyMeshes.refresh(mc.level, bounds.get());
        Frustum frustum = PortalView.drawing() ? PortalView.camera().getCullFrustum()
                : mc.gameRenderer.getMainCamera().getCullFrustum();
        RoomCopyDraws.Draws draws = RoomCopyDraws.of(bounds.get(),
                RoomCopies.offsets(bounds.get(), RoomCopies.reachOf(mc.options.getEffectiveRenderDistance())),
                RoomCopyMeshes.bottom(), RoomCopyMeshes.top(), frustum::isVisible);
        return draws.instances() == 0 ? Optional.empty() : Optional.of(draws);
    }

    private static void drawLayer(Copies copies, ChunkSectionLayer layer, RenderPipeline pipeline,
            Matrix4fc modelView) {
        for (RoomCopyMeshes.Uploaded mesh : RoomCopyMeshes.layer(layer)) {
            draw(copies, mesh, pipeline, modelView, true);
        }
    }

    /**
     * Draws one of the room's meshes once for every copy seen.
     *
     * @param copies    the copies seen and their translations
     * @param mesh      the mesh
     * @param pipeline  the pipeline it draws through
     * @param modelView the pass's model view
     * @param textured  whether the mesh samples the block atlas and the lightmap
     */
    private static void draw(Copies copies, RoomCopyMeshes.Uploaded mesh, RenderPipeline pipeline,
            Matrix4fc modelView, boolean textured) {
        Minecraft mc = Minecraft.getInstance();
        Vector3f fromCamera = new Vector3f((float) (mesh.origin().getX() - copies.camera().x()),
                (float) (mesh.origin().getY() - copies.camera().y()), (float) (mesh.origin().getZ() - copies.camera().z()));
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(new Matrix4f(modelView), WHITE,
                fromCamera, new Matrix4f());
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(VertexFormat.Mode.QUADS);
        RenderTarget target = mc.mainRenderTarget;
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(() -> PASS_LABEL,
                target.getColorTextureView(), OptionalInt.empty(), target.getDepthTextureView(),
                OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(TRANSFORMS, transforms);
            pass.setUniform(GooRenderTypes.ROOM_COPY_TRANSLATIONS, copies.translations());
            if (textured) {
                bindBlockTextures(mc, pass);
            }
            pass.setVertexBuffer(0, mesh.vertices());
            pass.setIndexBuffer(indices.getBuffer(mesh.indexCount()), indices.type());
            pass.drawIndexed(0, 0, mesh.indexCount(), copies.draws().instances());
        }
    }

    private static void bindBlockTextures(Minecraft mc, RenderPass pass) {
        pass.bindTexture(BLOCK_ATLAS_SAMPLER, mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS).getTextureView(),
                RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE, AddressMode.CLAMP_TO_EDGE,
                        FilterMode.LINEAR, FilterMode.NEAREST, true));
        pass.bindTexture(LIGHTMAP_SAMPLER, mc.gameRenderer.lightmap(),
                RenderSystem.getSamplerCache().getClampToEdge(FilterMode.LINEAR));
    }
}
