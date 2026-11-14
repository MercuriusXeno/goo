package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.client.renderer.block.BlockQuadOutput;
import net.minecraft.client.renderer.block.FluidRenderer;
import net.minecraft.client.renderer.block.ModelBlockRenderer;
import net.minecraft.client.renderer.chunk.ChunkSectionLayer;
import net.minecraft.client.renderer.chunk.VisGraph;
import net.minecraft.client.renderer.chunk.VisibilitySet;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import net.minecraft.world.level.block.RenderShape;
import net.minecraft.world.level.block.state.BlockState;
import java.util.EnumMap;
import java.util.Map;
import java.util.function.BiPredicate;

/**
 * Tesselates one chunk section's blocks and fluids outside the game's own
 * section compiler, laid out from the section's origin as that compiler lays
 * them, each layer into its own mesh, for whatever draws a section the level
 * renderer does not: the streamed far side of a linked pair, and the copies
 * of a Zone room.
 * linked-partner-chunks-stream-to-any-viewer
 * decision room-tiles-to-render-distance-held-to-the-picture
 */
public final class SectionMeshes {

    private static final int BUFFER_START = 1 << 16;

    private SectionMeshes() {
    }

    /**
     * One section's tesselated layers, each with the memory it lives in, and
     * which of its faces see through to which.
     *
     * @param section    the section
     * @param meshes     each layer's mesh, a layer with no quads left out
     * @param memory     the memory each layer's mesh lives in
     * @param visibility which of the section's faces see through to which
     */
    public record Built(SectionPos section, Map<ChunkSectionLayer, MeshData> meshes,
            Map<ChunkSectionLayer, ByteBufferBuilder> memory, VisibilitySet visibility) {

        /** Frees every layer's mesh and memory. */
        public void close() {
            meshes.values().forEach(MeshData::close);
            memory.values().forEach(ByteBufferBuilder::close);
        }
    }

    /**
     * Tesselates the blocks of a section a filter keeps.
     *
     * @param view    the blocks to read
     * @param section the section
     * @param keeps   whether a block, by its place and state, is drawn
     * @return its meshes
     */
    public static Built tesselate(BlockAndTintGetter view, SectionPos section, BiPredicate<BlockPos, BlockState> keeps) {
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
        VisGraph sight = new VisGraph();
        for (BlockPos pos : BlockPos.betweenClosed(origin, origin.offset(SectionPos.SECTION_MAX_INDEX,
                SectionPos.SECTION_MAX_INDEX, SectionPos.SECTION_MAX_INDEX))) {
            BlockState state = view.getBlockState(pos);
            if (state.isAir() || !keeps.test(pos, state)) {
                continue;
            }
            if (state.isSolidRender()) {
                sight.setOpaque(pos);
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
        return new Built(section, meshes, memory, sight.resolve());
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
    public static RenderPipeline pipelineOf(ChunkSectionLayer layer) {
        return switch (layer) {
            case SOLID -> RenderPipelines.SOLID_BLOCK;
            case CUTOUT -> RenderPipelines.CUTOUT_BLOCK;
            case TRANSLUCENT -> RenderPipelines.TRANSLUCENT_BLOCK;
        };
    }
}
