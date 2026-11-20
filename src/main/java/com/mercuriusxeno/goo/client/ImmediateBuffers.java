package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.IndexType;
import com.mojang.blaze3d.buffers.GpuBuffer;
import com.mojang.blaze3d.systems.GpuDevice;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.ByteBufferBuilder;
import com.mojang.blaze3d.vertex.MeshData;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.Sheets;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.resources.model.ModelBakery;
import org.jspecify.annotations.Nullable;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.SequencedMap;

/**
 * Render type buffers drawn the moment a batch ends, as 26.1's
 * MultiBufferSource.BufferSource drew them: a render type held in a fixed
 * buffer gathers until the whole source ends, and any other shares one
 * buffer, so asking for a second ends the first. Minecraft 26.2 removed
 * that source and draws its own geometry through feature renderers; goo's
 * visuals draw on a level render stage in the order the sorting path
 * gives them, so each batch here draws at once through the render type's
 * prepared draw.
 * decision minecraft-26-3-update-is-a-port-to-parity
 * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
 */
public final class ImmediateBuffers implements RenderTypeBuffers {

    private static final int SHARED_BUFFER_BYTES = RenderType.SMALL_BUFFER_SIZE;
    private static final int FIXED_BUFFER_BYTES = RenderType.TRANSIENT_BUFFER_SIZE;
    private static final String VERTEX_LABEL = "Goo immediate vertices";
    private static final String INDEX_LABEL = "Goo immediate indices";

    private static @Nullable ImmediateBuffers shared;
    private static @Nullable ImmediateBuffers crumbling;

    private final ByteBufferBuilder sharedBuffer;
    private final SequencedMap<RenderType, ByteBufferBuilder> fixedBuffers;
    private final Map<RenderType, BufferBuilder> startedBuilders = new HashMap<>();
    private @Nullable RenderType lastSharedType;

    private ImmediateBuffers(int sharedBytes, List<RenderType> fixedTypes) {
        this.sharedBuffer = new ByteBufferBuilder(sharedBytes);
        this.fixedBuffers = new LinkedHashMap<>();
        for (RenderType type : fixedTypes) {
            fixedBuffers.put(type, new ByteBufferBuilder(FIXED_BUFFER_BYTES));
        }
    }

    /**
     * The buffers every goo visual on the level stages draws through, as
     * 26.1's game render buffers handed out: the item sheets and the glints
     * held to the source's end, every other type shared.
     *
     * @return the shared source
     */
    public static ImmediateBuffers shared() {
        if (shared == null) {
            shared = new ImmediateBuffers(SHARED_BUFFER_BYTES, List.of(
                    Sheets.cutoutBlockItemSheet(), Sheets.translucentBlockItemSheet(),
                    Sheets.cutoutItemSheet(), Sheets.translucentItemSheet(),
                    RenderTypes.armorEntityGlint(), RenderTypes.glint(), RenderTypes.glintTranslucent(),
                    RenderTypes.entityGlint(), RenderTypes.waterMask()));
        }
        return shared;
    }

    /**
     * The buffers block-breaking cracks draw through, each crack stage held
     * to the source's end, as 26.1's crumbling source held them.
     *
     * @return the crumbling source
     */
    public static ImmediateBuffers crumbling() {
        if (crumbling == null) {
            crumbling = new ImmediateBuffers(0, ModelBakery.DESTROY_TYPES);
        }
        return crumbling;
    }

    @Override
    public VertexConsumer getBuffer(RenderType renderType) {
        BufferBuilder builder = startedBuilders.get(renderType);
        if (builder != null && !renderType.canConsolidateConsecutiveGeometry()) {
            endBatch(renderType, builder);
            builder = null;
        }
        if (builder != null) {
            return builder;
        }
        ByteBufferBuilder fixed = fixedBuffers.get(renderType);
        if (fixed != null) {
            builder = new BufferBuilder(fixed, renderType.primitiveTopology(), renderType.format());
        } else {
            if (lastSharedType != null) {
                endBatch(lastSharedType);
            }
            builder = new BufferBuilder(sharedBuffer, renderType.primitiveTopology(), renderType.format());
            lastSharedType = renderType;
        }
        startedBuilders.put(renderType, builder);
        return builder;
    }

    /** Draws the batch of the shared buffer's render type, if one is open. */
    public void endLastBatch() {
        if (lastSharedType != null) {
            endBatch(lastSharedType);
            lastSharedType = null;
        }
    }

    /** Draws every open batch: the shared buffer's, then each fixed type's in the order they were fixed. */
    public void endBatch() {
        endLastBatch();
        for (RenderType type : fixedBuffers.keySet()) {
            endBatch(type);
        }
    }

    /**
     * Draws one render type's open batch.
     *
     * @param type the render type
     */
    public void endBatch(RenderType type) {
        BufferBuilder builder = startedBuilders.remove(type);
        if (builder != null) {
            endBatch(type, builder);
        }
    }

    private void endBatch(RenderType type, BufferBuilder builder) {
        startedBuilders.remove(type, builder);
        MeshData mesh = builder.build();
        if (mesh != null) {
            if (type.sortOnUpload()) {
                mesh.sortQuads(fixedBuffers.getOrDefault(type, sharedBuffer),
                        RenderSystem.getProjectionType().vertexSorting());
            }
            draw(type, mesh);
        }
        if (type.equals(lastSharedType)) {
            lastSharedType = null;
        }
    }

    // The buffers close straight after the draw; the device destroys each once the frame holding it is done.
    private static void draw(RenderType type, MeshData mesh) {
        GpuDevice device = RenderSystem.getDevice();
        MeshData.DrawState state = mesh.drawState();
        try (mesh; GpuBuffer vertices = device.createBuffer(() -> VERTEX_LABEL, GpuBuffer.USAGE_VERTEX,
                mesh.vertexBuffer())) {
            if (mesh.indexBuffer() == null) {
                RenderSystem.AutoStorageIndexBuffer sequential = RenderSystem.getSequentialBuffer(
                        state.primitiveTopology());
                GpuBuffer indices = sequential.getBuffer(state.indexCount());
                type.prepare().drawFromBuffer(vertices, indices, sequential.type(), 0, 0, state.indexCount());
            } else {
                drawIndexed(device, type, vertices, mesh, state.indexType(), state.indexCount());
            }
        }
    }

    private static void drawIndexed(GpuDevice device, RenderType type, GpuBuffer vertices, MeshData mesh,
                                    IndexType indexType, int indexCount) {
        try (GpuBuffer indices = device.createBuffer(() -> INDEX_LABEL, GpuBuffer.USAGE_INDEX,
                mesh.indexBuffer())) {
            type.prepare().drawFromBuffer(vertices, indices, indexType, 0, 0, indexCount);
        }
    }
}
