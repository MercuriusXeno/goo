package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import java.util.ArrayList;
import java.util.List;

/**
 * A VertexConsumer that records every vertex it is handed, so a test can
 * read back the geometry an emitter wrote.
 */
public final class RecordingVertexConsumer implements VertexConsumer {

    /**
     * One recorded vertex.
     *
     * @param x     the X position
     * @param y     the Y position
     * @param z     the Z position
     * @param color the ARGB color
     * @param u     the texture U
     * @param v     the texture V
     * @param uv1U  the overlay U, which the fluid surface reads as ripple amplitude
     * @param uv2U  the lightmap U, which the fluid surface reads as the band's lower edge
     * @param uv2V  the lightmap V, which the fluid surface reads as the band's upper edge
     * @param ny    the Y normal
     * @param uv1V  the overlay V, which the dissolve shader reads as the glow color
     */
    public record Vertex(float x, float y, float z, int color, float u, float v, int uv1U,
                         int uv2U, int uv2V, float ny, int uv1V) {
    }

    private final List<Vertex> vertices = new ArrayList<>();
    private float x;
    private float y;
    private float z;
    private int color;
    private float u;
    private float v;
    private int uv1U;
    private int uv1V;
    private int uv2U;
    private int uv2V;
    private float ny;
    private boolean hasPending;
    private int lightWrites;

    /**
     * @return how many times an emitter wrote the lightmap element
     */
    public int lightWrites() {
        return lightWrites;
    }

    /**
     * @return every vertex recorded so far, in emission order
     */
    public List<Vertex> vertices() {
        flush();
        return List.copyOf(vertices);
    }

    private void flush() {
        if (hasPending) {
            vertices.add(new Vertex(x, y, z, color, u, v, uv1U, uv2U, uv2V, ny, uv1V));
            hasPending = false;
        }
    }

    @Override
    public VertexConsumer addVertex(float vx, float vy, float vz) {
        flush();
        x = vx;
        y = vy;
        z = vz;
        color = 0;
        u = 0f;
        v = 0f;
        uv1U = 0;
        uv1V = 0;
        uv2U = 0;
        uv2V = 0;
        ny = 0f;
        hasPending = true;
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        return setColor((a << 24) | (r << 16) | (g << 8) | b);
    }

    @Override
    public VertexConsumer setColor(int argb) {
        color = argb;
        return this;
    }

    @Override
    public VertexConsumer setUv(float tu, float tv) {
        u = tu;
        v = tv;
        return this;
    }

    @Override
    public VertexConsumer setUv1(int overlayU, int overlayV) {
        uv1U = overlayU;
        uv1V = overlayV;
        return this;
    }

    @Override
    public VertexConsumer setUv2(int lightU, int lightV) {
        lightWrites++;
        uv2U = lightU;
        uv2V = lightV;
        return this;
    }

    @Override
    public VertexConsumer setNormal(float nx, float normalY, float nz) {
        ny = normalY;
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        return this;
    }
}
