package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.ARGB;
import org.joml.Matrix4fc;
import org.joml.Vector3f;

/**
 * Writes a mob model's vertices as its slime shell: each vertex stands off
 * the skin along its normal, as a slime's outer jelly stands off its core,
 * in the goo's color, shown below the shell's melt line and gone above it,
 * the line feathered so the shell slides down the body rather than snapping.
 * decision charm-like-abilities-lock-the-highlighted-mob-on-right-click
 */
final class ShellVertices implements VertexConsumer {

    /** Blocks the shell stands off the skin: a texel of a sixteen-texel block. */
    static final float PUFF_BLOCKS = 1f / 16f;
    /** The share of the body the melt line feathers across. */
    private static final float FEATHER = 0.12f;
    /** A color channel's full value. */
    private static final int MAX_CHANNEL = 255;

    private final VertexConsumer delegate;
    private final Matrix4fc cameraToRoot;
    private final float crownY;
    private final float feetY;
    private final float melted;
    private final int rgb;
    private final float opacity;
    private final Vector3f root = new Vector3f();

    /**
     * @param delegate     the shell render type's buffer
     * @param cameraToRoot the inverse of the model root's pose
     * @param crownY       the model's highest point in root space, which is y-down
     * @param feetY        the model's lowest point in root space
     * @param melted       how far down the body the shell has melted, 0 to 1
     * @param rgb          the goo's color, 0xRRGGBB
     * @param opacity      how opaque the shell draws where it stands whole, 0 to 1
     */
    ShellVertices(VertexConsumer delegate, Matrix4fc cameraToRoot, float crownY, float feetY, float melted, int rgb,
                  float opacity) {
        this.delegate = delegate;
        this.cameraToRoot = cameraToRoot;
        this.crownY = crownY;
        this.feetY = feetY;
        this.melted = melted;
        this.rgb = rgb;
        this.opacity = opacity;
    }

    /**
     * How much of the shell shows at a height, by where the melt line stands.
     *
     * @param fromCrown the height as a share of the body from the crown, 0 at the crown to 1 at the feet
     * @param melted    how far down the body the shell has melted, 0 to 1
     * @return 0 above the line, rising to 1 a feather's width below it
     */
    static float shownAt(float fromCrown, float melted) {
        return Math.clamp((fromCrown - melted) / FEATHER, 0f, 1f);
    }

    /**
     * A vertex stood off the skin along its normal.
     *
     * @param x  the vertex's x
     * @param y  the vertex's y
     * @param z  the vertex's z
     * @param nx the normal's x
     * @param ny the normal's y
     * @param nz the normal's z
     * @return the shell's vertex
     */
    static Vector3f puffedOut(float x, float y, float z, float nx, float ny, float nz) {
        Vector3f normal = new Vector3f(nx, ny, nz);
        if (normal.lengthSquared() > 0f) {
            normal.normalize(PUFF_BLOCKS);
        }
        return new Vector3f(x, y, z).add(normal);
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlayCoords,
            int lightCoords, float nx, float ny, float nz) {
        cameraToRoot.transformPosition(x, y, z, root);
        float span = feetY - crownY;
        float fromCrown = span > 0f ? (root.y - crownY) / span : 1f;
        int alpha = Math.round(opacity * shownAt(fromCrown, melted) * MAX_CHANNEL);
        Vector3f puffed = puffedOut(x, y, z, nx, ny, nz);
        delegate.addVertex(puffed.x, puffed.y, puffed.z, ARGB.color(alpha, rgb), u, v, overlayCoords, lightCoords,
                nx, ny, nz);
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        delegate.addVertex(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setColor(int r, int g, int b, int a) {
        delegate.setColor(r, g, b, a);
        return this;
    }

    @Override
    public VertexConsumer setColor(int color) {
        delegate.setColor(color);
        return this;
    }

    @Override
    public VertexConsumer setUv(float u, float v) {
        delegate.setUv(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(u, v);
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        delegate.setUv2(u, v);
        return this;
    }

    @Override
    public VertexConsumer setNormal(float x, float y, float z) {
        delegate.setNormal(x, y, z);
        return this;
    }

    @Override
    public VertexConsumer setLineWidth(float width) {
        delegate.setLineWidth(width);
        return this;
    }
}
