package com.mercuriusxeno.goo.client.ber.style;

import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.client.FlatQuadContext;
import com.mercuriusxeno.goo.client.GooRenderTypes;
import com.mercuriusxeno.goo.client.ability.BlackHolePhases;
import com.mercuriusxeno.goo.client.ability.NetherDiscMesh;
import com.mercuriusxeno.goo.client.ability.NetherLensEffect;
import com.mercuriusxeno.goo.client.ability.NetherSphereVisual;
import com.mercuriusxeno.goo.client.ber.ChainMarkerRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.util.ARGB;

/**
 * Cube-shaped nether black-hole experiment. Mirrors the three-pass
 * structure of {@link NetherSphereVisual}
 * but with cube geometry for the occluder and a cube-edge-glow shader
 * in place of the fresnel corona:
 * <ol>
 *   <li>Solid cube occluder reusing {@link GooRenderTypes#NETHER_BLACKHOLE_TYPE}
 *       - the sphere occluder shader reads only {@code Position}, so
 *       feeding it cube vertices produces a black cube with correct
 *       depth write.</li>
 *   <li>Cube edge glow via {@link GooRenderTypes#NETHER_CUBE_EDGE_TYPE}
 *       - same cube mesh at {@link #EDGE_SCALE} the occluder size, with
 *       per-vertex intra-face UVs packed into {@code Color.rg} so the
 *       fragment shader can compute distance to the nearest face edge.</li>
 *   <li>Flat accretion disc via {@link GooRenderTypes#NETHER_DISK_TYPE}
 *       - the one {@link NetherDiscMesh} the sphere style also submits.</li>
 * </ol>
 *
 * <p>Cube "radius" is interpreted as a half-extent: an occluder of
 * "radius" R produces a cube spanning {@code [-R, +R]} on every axis,
 * so the cube's corner distance from the center is {@code R * sqrt(3)}
 * ≈ 1.73 * R (longer than the sphere silhouette of the same R). Lens
 * marking and disc placement still key off R as the canonical radius,
 * matching the sphere style so A/B comparisons are apples-to-apples.
 */
public final class CubeHoleStyle implements NetherHoleStyle {

    /** Offset to get block center from integer position. */
    private static final float BLOCK_CENTER = 0.5f;

    /** Scale of the edge-glow cube relative to the occluder. 1.04
     * pushes the glow cube just past the occluder surface so its
     * faces are visible against depth-test even with depth write off. */
    private static final float EDGE_SCALE = 1.04f;

    /** Disc inner edge as a multiple of the current cube half-extent.
     * 1.12 sits just past the corner circumradius (1.0 at a face,
     * 1.414 at an edge, 1.732 at a corner) so the disc clears the
     * cube face silhouette but hugs it at the corners. */
    private static final float DISK_INNER_CUBE_MULT = 1.12f;

    /** 0xFF opaque alpha for vertex color packing. */
    private static final int OPAQUE_ALPHA = 0xFF;
    /** Maximum byte value for a 0..1 to byte mapping. */
    private static final int PROGRESS_BYTE_MAX = 255;

    /** Number of faces on a cube. */
    private static final int CUBE_FACES = 6;
    /** Vertices per face when emitting as {@code VertexFormat.Mode.QUADS}. */
    private static final int CUBE_VERTICES_PER_FACE = 4;
    /** Floats per entry in {@link #CUBE_FACE_POSITIONS} and
     * {@link #CUBE_FACE_NORMALS} - an {@code (x, y, z)} triple. */
    private static final int CUBE_POS_STRIDE = 3;
    /** Floats per entry in {@link #CUBE_FACE_UVS} - a {@code (u, v)}
     * pair. */
    private static final int CUBE_UV_STRIDE = 2;
    /** Offset of the Z component inside a stride-3 position or normal
     * triple. Named so array accesses like {@code data[p + CUBE_Z]}
     * don't trip checkstyle's magic-number rule. */
    private static final int CUBE_Y = 1;
    private static final int CUBE_Z = 2;
    /** Unit magnitude for cube half-extent literals. Used through its
     * negation as {@code -UNIT} in the cube vertex tables so the
     * tables contain no raw {@code -1f} literals (0f and 1f are fine;
     * -1f is flagged). */
    private static final float UNIT = 1f;

    /** Unit cube vertex positions for all six faces in QUADS order,
     * CCW winding when viewed from outside the cube. Each face spans
     * {@code [-1, +1]} on its two in-plane axes. Laid out as stride-3
     * triples so the emit helper can scale and translate without
     * unpacking a JOML object per vertex. 6 faces × 4 vertices × 3 =
     * 72 floats. */
    private static final float[] CUBE_FACE_POSITIONS = buildCubeFacePositions();

    /** Per-vertex intra-face UV coordinates matching
     * {@link #CUBE_FACE_POSITIONS}: each quad walks (0,0), (1,0),
     * (1,1), (0,1) so the edge shader sees a full 0..1 range across
     * every face. 6 faces × 4 vertices × 2 = 48 floats. */
    private static final float[] CUBE_FACE_UVS = buildCubeFaceUvs();

    /** Per-face outward normals, one per vertex (4 duplicates per face)
     * so the vertex consumer's {@code setNormal} has a sensible value
     * even though neither the occluder nor the edge shader consumes
     * them. 6 faces × 4 vertices × 3 = 72 floats. */
    private static final float[] CUBE_FACE_NORMALS = buildCubeFaceNormals();

    /** Total vertex count for one full cube emit. */
    private static final int CUBE_VERTEX_COUNT = CUBE_FACES * CUBE_VERTICES_PER_FACE;

    CubeHoleStyle() {}

    @Override
    public void extract(ChainMarkerBlockEntity be, ChainMarkerRenderState state) {
        if (BlackHolePhases.populateRenderState(be, state)) {
            NetherLensEffect.markHoleActive(BlackHolePhases.holeCenter(be),
                    BlackHolePhases.visibleRadius(state), NetherLensEffect.LensShape.HEX);
        }
    }

    @Override
    public void submit(ChainMarkerRenderState state, PoseStack poseStack,
            SubmitNodeCollector nodeCollector) {
        float occluderHalf = BlackHolePhases.visibleRadius(state);
        float edgeHalf = occluderHalf * EDGE_SCALE;
        float innerR = occluderHalf * DISK_INNER_CUBE_MULT;
        float outerR = NetherDiscMesh.outerRadius(innerR, occluderHalf,
                BlackHolePhases.fullRadius(state), state.diskExpansionScale);
        float animPhase = state.animationTime;

        // Pass 1: cube occluder. Reuses the sphere occluder pipeline -
        // its shader only reads Position so cube vertices produce a
        // black cube with correct depth write and nothing else.
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.NETHER_BLACKHOLE_TYPE,
            (pose, c) -> emitCubeMesh(pose, c, occluderHalf, false));
        // Pass 2: cube edge glow. Slightly enlarged cube, per-vertex
        // intra-face UVs packed into Color.rg; the fragment shader
        // brightens toward each face edge.
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.NETHER_CUBE_EDGE_TYPE,
            (pose, c) -> emitCubeMesh(pose, c, edgeHalf, true));
        nodeCollector.submitCustomGeometry(poseStack, GooRenderTypes.NETHER_DISK_TYPE,
            (pose, c) -> NetherDiscMesh.emitDisc(pose, c, innerR, outerR, animPhase));
    }

    /**
     * Emits the pre-generated unit cube mesh with each vertex scaled to
     * {@code halfExtent} and translated to the block center. When
     * {@code packUv} is true, Color.rg carries the intra-face UV for
     * the edge shader; otherwise Color is opaque white (the occluder
     * shader ignores it).
     *
     * @param pose       the current pose entry
     * @param c          the vertex consumer
     * @param halfExtent world-space half-extent in blocks (±halfExtent on each axis)
     * @param packUv     whether to pack the intra-face UV into Color.rg
     */
    private static void emitCubeMesh(PoseStack.Pose pose, VertexConsumer c,
            float halfExtent, boolean packUv) {
        for (int i = 0; i < CUBE_VERTEX_COUNT; i++) {
            int color = packUv ? vertexUvColor(i) : packCubeOccluderColor();
            emitCubeVertex(pose, c, i, halfExtent, color);
        }
    }

    /** Emits a single cube vertex: reads the position and normal from
     * the static tables at index {@code i}, scales the position to
     * {@code halfExtent}, offsets to the block center, and forwards
     * the pre-packed {@code color} to the vertex consumer.
     *
     * @param pose       the current pose entry
     * @param c          the vertex consumer
     * @param i          cube vertex index (0 .. CUBE_VERTEX_COUNT - 1)
     * @param halfExtent world-space half-extent on each axis in blocks
     * @param color      pre-packed ARGB vertex color
     */
    private static void emitCubeVertex(PoseStack.Pose pose, VertexConsumer c,
            int i, float halfExtent, int color) {
        int p = i * CUBE_POS_STRIDE;
        float px = CUBE_FACE_POSITIONS[p];
        float py = CUBE_FACE_POSITIONS[p + 1];
        float pz = CUBE_FACE_POSITIONS[p + CUBE_Z];
        float nx = CUBE_FACE_NORMALS[p];
        float ny = CUBE_FACE_NORMALS[p + 1];
        float nz = CUBE_FACE_NORMALS[p + CUBE_Z];
        new FlatQuadContext(pose, c).vertex(
                BLOCK_CENTER + px * halfExtent,
                BLOCK_CENTER + py * halfExtent,
                BLOCK_CENTER + pz * halfExtent,
                color, nx, ny, nz);
    }

    /** Packs the edge-glow vertex color for cube vertex {@code i}
     * from the static UV table.
     *
     * @param i cube vertex index
     * @return the packed ARGB color with UV in R/G
     */
    private static int vertexUvColor(int i) {
        int u = i * CUBE_UV_STRIDE;
        int uByte = Math.round(CUBE_FACE_UVS[u] * PROGRESS_BYTE_MAX);
        int vByte = Math.round(CUBE_FACE_UVS[u + 1] * PROGRESS_BYTE_MAX);
        return packCubeUvColor(uByte, vByte);
    }

    /** Packs the edge-glow vertex color: R = intra-face U, G = intra-face V.
     *
     * @param uByte intra-face U already encoded to a byte
     * @param vByte intra-face V already encoded to a byte
     * @return the packed ARGB color
     */
    private static int packCubeUvColor(int uByte, int vByte) {
        return ARGB.color(OPAQUE_ALPHA, uByte, vByte, 0);
    }

    /** Packs the occluder vertex color. The occluder shader ignores
     * the vertex color entirely - any opaque value works - but we use
     * opaque white so render-debug overlays read sensibly.
     *
     * @return the packed ARGB color
     */
    private static int packCubeOccluderColor() {
        return ARGB.color(OPAQUE_ALPHA, PROGRESS_BYTE_MAX, PROGRESS_BYTE_MAX, PROGRESS_BYTE_MAX);
    }

    /** Builds the unit cube vertex position table as stride-3 floats.
     * Six faces, 4 CCW vertices each when viewed from outside, all
     * spanning {@code [-1, +1]} on their in-plane axes.
     *
     * @return stride-3 position table
     */
    private static float[] buildCubeFacePositions() {
        final float n = -UNIT;
        return new float[] {
            // +X face: normal +X, in-plane axes (-Z → +Z, -Y → +Y)
            UNIT,    n,    n,   UNIT,    n, UNIT,   UNIT, UNIT, UNIT,   UNIT, UNIT,    n,
            // -X face: normal -X, flipped winding vs +X
               n,    n, UNIT,      n,    n,    n,      n, UNIT,    n,      n, UNIT, UNIT,
            // +Y face (top)
               n, UNIT,    n,   UNIT, UNIT,    n,   UNIT, UNIT, UNIT,      n, UNIT, UNIT,
            // -Y face (bottom)
               n,    n, UNIT,   UNIT,    n, UNIT,   UNIT,    n,    n,      n,    n,    n,
            // +Z face
               n,    n, UNIT,      n, UNIT, UNIT,   UNIT, UNIT, UNIT,   UNIT,    n, UNIT,
            // -Z face
            UNIT,    n,    n,   UNIT, UNIT,    n,      n, UNIT,    n,      n,    n,    n,
        };
    }

    /** Builds the unit cube intra-face UV table as stride-2 floats.
     * Every face walks (0,0) → (1,0) → (1,1) → (0,1) so the edge
     * shader sees a full 0..1 sweep across each face.
     *
     * @return stride-2 UV table
     */
    private static float[] buildCubeFaceUvs() {
        // Each face has 4 vertices x 2 UV components = 8 floats.
        // Quad winding (u, v): (0,0) -> (1,0) -> (1,1) -> (0,1).
        float[] tile = {0f, 0f, 1f, 0f, 1f, 1f, 0f, 1f};
        int step = CUBE_VERTICES_PER_FACE * CUBE_UV_STRIDE;
        float[] out = new float[CUBE_VERTEX_COUNT * CUBE_UV_STRIDE];
        for (int face = 0; face < CUBE_FACES; face++) {
            System.arraycopy(tile, 0, out, face * step, step);
        }
        return out;
    }

    /** Builds the per-vertex outward normal table. Each face
     * contributes its unit outward normal four times (once per vertex)
     * so the stride-3 layout matches the position table and the emit
     * loop can share an index.
     *
     * @return stride-3 normal table
     */
    private static float[] buildCubeFaceNormals() {
        final float n = -UNIT;
        float[][] normals = {
            {UNIT, 0f, 0f}, {n, 0f, 0f}, {0f, UNIT, 0f},
            {0f, n, 0f}, {0f, 0f, UNIT}, {0f, 0f, n},
        };
        float[] out = new float[CUBE_VERTEX_COUNT * CUBE_POS_STRIDE];
        for (int face = 0; face < CUBE_FACES; face++) {
            fillFaceNormals(out, face, normals[face]);
        }
        return out;
    }

    /**
     * Fills 4 vertices of a face with the same normal vector.
     * @param out the stride-3 normal output array
     * @param face the face index (0-5)
     * @param normal the xyz unit normal for this face
     */
    private static void fillFaceNormals(float[] out, int face, float[] normal) {
        int base = face * CUBE_VERTICES_PER_FACE * CUBE_POS_STRIDE;
        for (int v = 0; v < CUBE_VERTICES_PER_FACE; v++) {
            int off = base + v * CUBE_POS_STRIDE;
            out[off] = normal[0];
            out[off + CUBE_Y] = normal[CUBE_Y];
            out[off + CUBE_Z] = normal[CUBE_Z];
        }
    }
}
