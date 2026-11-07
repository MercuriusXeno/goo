package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunkSection;
import net.minecraft.world.phys.Vec3;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;

/**
 * Draws the streamed far side of a linked pair inside the portal pass: each
 * section the streamed view holds is tesselated once as it arrives, the way
 * the game's own section compiler lays out a section, and drawn in every
 * view through a portal whose partner the ClientLevel does not hold.
 * linked-partner-chunks-stream-to-any-viewer
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PartnerMeshes {

    /** How many streamed chunks tesselate in one frame, so a fresh stream lands without a hitch. */
    private static final int CHUNKS_PER_FRAME = 1;
    private static final int BUFFER_START = 1 << 16;
    private static final String PASS_LABEL = "Goo streamed far side";
    /** The block pipelines' uniform and samplers, as the game's moving blocks bind them. */
    private static final String TRANSFORMS = "DynamicTransforms";
    private static final String BLOCK_ATLAS_SAMPLER = "Sampler0";
    private static final String LIGHTMAP_SAMPLER = "Sampler2";

    /** One section's tesselated layers, each with the memory it lives in. */
    private record Built(SectionPos section, Map<ChunkSectionLayer, MeshData> meshes,
            Map<ChunkSectionLayer, ByteBufferBuilder> memory) {
        void close() {
            meshes.values().forEach(MeshData::close);
            memory.values().forEach(ByteBufferBuilder::close);
        }
    }

    /** Each streamed chunk's sections as last tesselated, keyed by the sections array they were read from. */
    private static final Map<ChunkPos, Map<LevelChunkSection[], java.util.List<Built>>> BUILT = new HashMap<>();

    private PartnerMeshes() {
    }

    /**
     * Draws the streamed far side once the portal pass's own blocks have drawn.
     *
     * @param event the level render stage event
     */
    @SubscribeEvent
    public static void onAfterTranslucentBlocks(RenderLevelStageEvent.AfterTranslucentBlocks event) {
        if (!PortalView.drawing() || !PortalView.drawsStreamed()) {
            return;
        }
        rebuild(PartnerView.CLIENT);
        Matrix4f view = new Matrix4f(RenderSystem.getModelViewMatrix()).mul(event.getPoseStack().last().pose());
        Vec3 camera = PortalView.cameraPosition();
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            for (Map<LevelChunkSection[], java.util.List<Built>> chunk : BUILT.values()) {
                chunk.values().forEach(sections -> sections.forEach(built -> draw(built, layer, view, camera)));
            }
        }
    }

    /**
     * Drops the meshes of chunks the view no longer holds, and tesselates
     * those it holds afresh.
     *
     * @param view the streamed view
     */
    private static void rebuild(PartnerView view) {
        for (Iterator<Map.Entry<ChunkPos, Map<LevelChunkSection[], java.util.List<Built>>>> it =
                BUILT.entrySet().iterator(); it.hasNext();) {
            Map.Entry<ChunkPos, Map<LevelChunkSection[], java.util.List<Built>>> entry = it.next();
            LevelChunkSection[] now = view.sectionsOf(entry.getKey());
            if (!entry.getValue().containsKey(now)) {
                entry.getValue().values().forEach(sections -> sections.forEach(Built::close));
                it.remove();
            }
        }
        int built = 0;
        for (ChunkPos pos : view.held()) {
            if (!BUILT.containsKey(pos) && built++ < CHUNKS_PER_FRAME) {
                LevelChunkSection[] sections = view.sectionsOf(pos);
                java.util.List<Built> meshes = new java.util.ArrayList<>();
                for (int index = 0; index < sections.length; index++) {
                    if (!sections[index].hasOnlyAir()) {
                        meshes.add(tesselate(view, SectionPos.of(pos, view.minSectionY() + index)));
                    }
                }
                Map<LevelChunkSection[], java.util.List<Built>> keyed = new java.util.IdentityHashMap<>();
                keyed.put(sections, meshes);
                BUILT.put(pos, keyed);
            }
        }
    }

    /**
     * Tesselates one streamed section, its blocks and fluids laid out from the
     * section's origin as the game's section compiler lays them.
     *
     * @param view    the streamed view
     * @param section the section
     * @return its meshes
     */
    private static Built tesselate(PartnerView view, SectionPos section) {
        Minecraft mc = Minecraft.getInstance();
        Map<ChunkSectionLayer, BufferBuilder> builders = new EnumMap<>(ChunkSectionLayer.class);
        Map<ChunkSectionLayer, ByteBufferBuilder> memory = new EnumMap<>(ChunkSectionLayer.class);
        ModelBlockRenderer blocks = new ModelBlockRenderer(mc.options.ambientOcclusion().get(), true,
                mc.getBlockColors());
        FluidRenderer fluids = new FluidRenderer(mc.getModelManager().getFluidStateModelSet());
        FluidRenderer.Output fluidOut = layer -> builderFor(builders, memory, layer);
        BlockQuadOutput quadOut = (x, y, z, quad, instance) ->
                builderFor(builders, memory, quad.materialInfo().layer()).putBlockBakedQuad(x, y, z, quad, instance);
        BlockPos origin = section.origin();
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(SectionPos.SECTION_MAX_INDEX,
                SectionPos.SECTION_MAX_INDEX, SectionPos.SECTION_MAX_INDEX))) {
            BlockState state = view.getBlockState(pos);
            if (state.isAir()) {
                continue;
            }
            if (!state.getFluidState().isEmpty()) {
                fluids.tesselate(view, pos, fluidOut, state, state.getFluidState());
            }
            if (state.getRenderShape() == RenderShape.MODEL) {
                blocks.tesselateBlock(quadOut, SectionPos.sectionRelative(pos.getX()),
                        SectionPos.sectionRelative(pos.getY()), SectionPos.sectionRelative(pos.getZ()), view, pos,
                        state, mc.getModelManager().getBlockStateModelSet().get(state), state.getSeed(pos));
            }
        }
        Map<ChunkSectionLayer, MeshData> meshes = new EnumMap<>(ChunkSectionLayer.class);
        builders.forEach((layer, builder) -> {
            MeshData mesh = builder.build();
            if (mesh != null) {
                meshes.put(layer, mesh);
            }
        });
        return new Built(section, meshes, memory);
    }

    private static VertexConsumer builderFor(Map<ChunkSectionLayer, BufferBuilder> builders,
            Map<ChunkSectionLayer, ByteBufferBuilder> memory, ChunkSectionLayer layer) {
        return builders.computeIfAbsent(layer, key -> {
            ByteBufferBuilder bytes = new ByteBufferBuilder(BUFFER_START);
            memory.put(key, bytes);
            return new BufferBuilder(bytes, VertexFormat.Mode.QUADS, pipelineOf(key).getVertexFormat());
        });
    }

    /**
     * The block pipeline a section layer draws with outside the game's own
     * section pass: the one moving blocks draw with.
     *
     * @param layer the layer
     * @return the pipeline
     */
    private static RenderPipeline pipelineOf(ChunkSectionLayer layer) {
        return switch (layer) {
            case SOLID -> RenderPipelines.SOLID_BLOCK;
            case CUTOUT -> RenderPipelines.CUTOUT_BLOCK;
            case TRANSLUCENT -> RenderPipelines.TRANSLUCENT_BLOCK;
        };
    }

    private static void draw(Built built, ChunkSectionLayer layer, Matrix4f view, Vec3 camera) {
        MeshData mesh = built.meshes().get(layer);
        if (mesh == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderPipeline pipeline = pipelineOf(layer);
        BlockPos origin = built.section().origin();
        Matrix4f modelView = new Matrix4f(view).translate((float) (origin.getX() - camera.x()),
                (float) (origin.getY() - camera.y()), (float) (origin.getZ() - camera.z()));
        GpuBufferSlice transforms = RenderSystem.getDynamicUniforms().writeTransform(modelView,
                new Vector4f(1F, 1F, 1F, 1F), new Vector3f(), new Matrix4f());
        GpuBuffer vertices = pipeline.getVertexFormat().uploadImmediateVertexBuffer(mesh.vertexBuffer());
        RenderSystem.AutoStorageIndexBuffer indices = RenderSystem.getSequentialBuffer(mesh.drawState().mode());
        RenderTarget target = mc.mainRenderTarget;
        try (RenderPass pass = RenderSystem.getDevice().createCommandEncoder().createRenderPass(
                () -> PASS_LABEL, target.getColorTextureView(), OptionalInt.empty(),
                target.getDepthTextureView(), OptionalDouble.empty())) {
            pass.setPipeline(pipeline);
            RenderSystem.bindDefaultUniforms(pass);
            pass.setUniform(TRANSFORMS, transforms);
            pass.bindTexture(BLOCK_ATLAS_SAMPLER, mc.getAtlasManager().getAtlasOrThrow(AtlasIds.BLOCKS)
                    .getTextureView(), RenderSystem.getSamplerCache().getSampler(AddressMode.CLAMP_TO_EDGE,
                    AddressMode.CLAMP_TO_EDGE, FilterMode.LINEAR, FilterMode.NEAREST, true));
            pass.bindTexture(LIGHTMAP_SAMPLER, mc.gameRenderer.lightmap(), RenderSystem.getSamplerCache()
                    .getClampToEdge(FilterMode.LINEAR));
            pass.setVertexBuffer(0, vertices);
            pass.setIndexBuffer(indices.getBuffer(mesh.drawState().indexCount()), indices.type());
            pass.drawIndexed(0, 0, mesh.drawState().indexCount(), 1);
        }
    }

    /**
     * Drops every streamed chunk and its meshes as the client leaves the server.
     *
     * @param event the logout event
     */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut event) {
        PartnerView.CLIENT.clear();
        BUILT.values().forEach(chunk -> chunk.values().forEach(sections -> sections.forEach(Built::close)));
        BUILT.clear();
    }
}
