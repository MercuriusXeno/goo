package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.QuadInstance;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.resources.model.geometry.BakedQuad;

/**
 * A VertexConsumer that keeps nothing and counts what it is handed: the
 * vertices emitted one by one and the baked quads put whole, and the
 * batches a draw submitted, so a test counts one frame's submission at the
 * cost of a counter.
 */
public final class CountingVertexConsumer implements VertexConsumer {

    private static final int VERTICES_A_QUAD = 4;

    private long vertices;
    private long bakedQuads;
    private int batches;

    /** @return a batch filling this consumer, counting each time it is submitted */
    public BufferedPass pass() {
        return new BufferedPass(this, () -> batches++);
    }

    /** @return every quad handed so far: the emitted vertices four to a quad, beside the baked quads */
    public long quads() {
        return vertices / VERTICES_A_QUAD + bakedQuads;
    }

    /** @return the batches submitted so far */
    public int batches() {
        return batches;
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        vertices++;
        return this;
    }

    @Override
    public void putBakedQuad(PoseStack.Pose pose, BakedQuad quad, QuadInstance instance) {
        bakedQuads++;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        return this;
    }

    @Override
    public VertexConsumer setColor(int argb) {
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setUv3(float u, float v) {
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        return this;
    }
}
