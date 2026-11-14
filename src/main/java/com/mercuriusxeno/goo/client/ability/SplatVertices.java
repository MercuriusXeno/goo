package com.mercuriusxeno.goo.client.ability;

import com.mojang.blaze3d.vertex.VertexConsumer;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;

/**
 * Re-encodes a mob model's vertices for the goo splat shader: each vertex
 * carries the fluid sprite's atlas origin in UV0, and its offset from the
 * pinned hit point, in the model's own root space, in UV1 and the high bytes
 * of UV2, so the splat rides the body as the mob moves and turns.
 * Decision shader-coat-on-every-mob-landing.
 */
final class SplatVertices implements VertexConsumer {

    /** Offset units per block; must match OFFSET_UNITS in goo_mob_coat.vsh. */
    static final float OFFSET_UNITS_PER_BLOCK = 1024f;

    /** The low byte of a light coordinate, the part the lightmap reads. */
    private static final int LIGHT_BYTE = 0xFF;
    /** Bits a byte spans. */
    private static final int BYTE_BITS = 8;
    /** The low sixteen bits of a packed coordinate. */
    private static final int SHORT_MASK = 0xFFFF;
    /** Bits a short spans. */
    private static final int SHORT_BITS = 16;

    private final VertexConsumer delegate;
    private final Matrix4fc rootInverse;
    private final Vector3fc pinnedHit;
    private final float spriteU0;
    private final float spriteV0;
    private final Vector3f offset = new Vector3f();

    /**
     * @param delegate    the splat render type's buffer
     * @param rootInverse the inverse of the model root's pose
     * @param pinnedHit   the hit point in the model's root space
     * @param spriteU0    the fluid sprite's atlas u origin
     * @param spriteV0    the fluid sprite's atlas v origin
     */
    SplatVertices(VertexConsumer delegate, Matrix4fc rootInverse, Vector3fc pinnedHit, float spriteU0,
            float spriteV0) {
        this.delegate = delegate;
        this.rootInverse = rootInverse;
        this.pinnedHit = pinnedHit;
        this.spriteU0 = spriteU0;
        this.spriteV0 = spriteV0;
    }

    /**
     * A posed vertex's offset from the hit point in the model's root space,
     * the same wherever the pose has carried the mob.
     *
     * @param rootInverse the inverse of the model root's pose
     * @param pinnedHit   the hit point in the model's root space
     * @param x           the posed vertex's x
     * @param y           the posed vertex's y
     * @param z           the posed vertex's z
     * @param into        the vector to write
     * @return into, holding the offset in blocks
     */
    static Vector3f splatOffset(Matrix4fc rootInverse, Vector3fc pinnedHit, float x, float y, float z,
            Vector3f into) {
        return rootInverse.transformPosition(x, y, z, into).sub(pinnedHit);
    }

    /**
     * Packs one offset component in offset units into a signed short.
     *
     * @param blocks the component in blocks
     * @return the component in offset units
     */
    static int offsetUnits(float blocks) {
        return Math.clamp(Math.round(blocks * OFFSET_UNITS_PER_BLOCK), Short.MIN_VALUE, Short.MAX_VALUE);
    }

    @Override
    public void addVertex(float x, float y, float z, int color, float u, float v, int overlayCoords,
            int lightCoords, float nx, float ny, float nz) {
        addVertex(x, y, z);
        setColor(color);
        setUv(u, v);
        setOverlay(overlayCoords);
        setLight(lightCoords);
        setNormal(nx, ny, nz);
    }

    @Override
    public VertexConsumer addVertex(float x, float y, float z) {
        splatOffset(rootInverse, pinnedHit, x, y, z, offset);
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
        delegate.setUv(spriteU0, spriteV0);
        return this;
    }

    @Override
    public VertexConsumer setUv1(int u, int v) {
        delegate.setUv1(offsetUnits(offset.x), offsetUnits(offset.y));
        return this;
    }

    @Override
    public VertexConsumer setUv2(int u, int v) {
        int z = offsetUnits(offset.z) & SHORT_MASK;
        delegate.setUv2((u & LIGHT_BYTE) | (z & LIGHT_BYTE) << BYTE_BITS,
                (v & LIGHT_BYTE) | (z >> BYTE_BITS) << BYTE_BITS);
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

    @Override
    public VertexConsumer setLight(int packedLightCoords) {
        return setUv2(packedLightCoords & SHORT_MASK, packedLightCoords >> SHORT_BITS & SHORT_MASK);
    }

    @Override
    public VertexConsumer setOverlay(int packedOverlayCoords) {
        return setUv1(packedOverlayCoords & SHORT_MASK, packedOverlayCoords >> SHORT_BITS & SHORT_MASK);
    }
}
