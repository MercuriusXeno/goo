package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.client.ability.SectionMeshes.Built;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.buffers.GpuBufferSlice;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.systems.RenderPass;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.textures.AddressMode;
import com.mojang.blaze3d.textures.FilterMode;
import com.mojang.blaze3d.vertex.MeshData;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.data.AtlasIds;
import net.minecraft.world.level.ChunkPos;
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
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.Set;

/**
 * Draws the streamed far side of a linked pair inside the portal pass: each
 * section the streamed view holds is tesselated once as it arrives, the way
 * the game's own section compiler lays out a section, its faces' sight of
 * one another read as the game reads it, and drawn in every view through a
 * portal whose partner the ClientLevel does not hold wherever the far side's
 * own section graph, walked from the partner's mouth, reaches it.
 * linked-partner-chunks-stream-to-any-viewer
 */
@EventBusSubscriber(modid = Goo.MODID, value = Dist.CLIENT)
public final class PartnerMeshes {

    /** How many streamed chunks tesselate in one frame, so a fresh stream lands without a hitch. */
    private static final int CHUNKS_PER_FRAME = 1;
    private static final String PASS_LABEL = "Goo streamed far side";
    /** The block pipelines' uniform and samplers, as the game's moving blocks bind them. */
    private static final String TRANSFORMS = "DynamicTransforms";
    private static final String BLOCK_ATLAS_SAMPLER = "Sampler0";
    private static final String LIGHTMAP_SAMPLER = "Sampler2";
    /** An air section, every face seeing through to every other. */
    private static final VisibilitySet OPEN = new VisibilitySet();

    static {
        OPEN.setAll(true);
    }

    /** Each streamed chunk's sections as last tesselated, keyed by the sections array they were read from. */
    private static final Map<ChunkPos, Map<LevelChunkSection[], java.util.List<Built>>> BUILT = new HashMap<>();
    /** Each streamed chunk's sections, air ones too, with which of their faces see through to which. */
    private static final Map<ChunkPos, Map<SectionPos, VisibilitySet>> GRAPH = new HashMap<>();

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
        Map<SectionPos, VisibilitySet> held = new HashMap<>();
        GRAPH.values().forEach(held::putAll);
        Set<SectionPos> seen = PartnerSectionGraph.visible(PortalView.partnerMouth(), held, PortalView::frustumHolds);
        for (ChunkSectionLayer layer : ChunkSectionLayer.values()) {
            for (Map<LevelChunkSection[], java.util.List<Built>> chunk : BUILT.values()) {
                chunk.values().forEach(sections -> sections.stream().filter(built -> seen.contains(built.section()))
                        .forEach(built -> draw(built, layer, view, camera)));
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
                GRAPH.remove(entry.getKey());
                it.remove();
            }
        }
        int built = 0;
        for (ChunkPos pos : view.held()) {
            if (!BUILT.containsKey(pos) && built++ < CHUNKS_PER_FRAME) {
                LevelChunkSection[] sections = view.sectionsOf(pos);
                java.util.List<Built> meshes = new java.util.ArrayList<>();
                Map<SectionPos, VisibilitySet> graph = new HashMap<>();
                for (int index = 0; index < sections.length; index++) {
                    SectionPos section = SectionPos.of(pos, view.minSectionY() + index);
                    if (sections[index].hasOnlyAir()) {
                        graph.put(section, OPEN);
                    } else {
                        Built mesh = SectionMeshes.tesselate(view, section, (at, state) -> true);
                        meshes.add(mesh);
                        graph.put(section, mesh.visibility());
                    }
                }
                Map<LevelChunkSection[], java.util.List<Built>> keyed = new java.util.IdentityHashMap<>();
                keyed.put(sections, meshes);
                BUILT.put(pos, keyed);
                GRAPH.put(pos, graph);
            }
        }
    }

    private static void draw(Built built, ChunkSectionLayer layer, Matrix4f view, Vec3 camera) {
        MeshData mesh = built.meshes().get(layer);
        if (mesh == null) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        RenderPipeline pipeline = SectionMeshes.pipelineOf(layer);
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
        GRAPH.clear();
    }
}
