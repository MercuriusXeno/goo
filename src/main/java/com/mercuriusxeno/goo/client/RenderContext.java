package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;

/**
 * Threading context for vertex emission. Created once per render call,
 * carries the pose, consumer, light and color so every downstream method
 * drops those parameters. The uncolored emitters below take the context
 * color, which GooSubmitter sets for a crossfading fluid.
 *
 * @param pose  the current pose matrix entry
 * @param c     the vertex consumer for geometry emission
 * @param light the packed light level for shading
 * @param color the ARGB color the uncolored emitters use
 */
public record RenderContext(PoseStack.Pose pose, VertexConsumer c, int light, int color) {

    /** Full white opaque color. */
    private static final int OPAQUE_WHITE = 0xFFFFFFFF;
    /** Normal sign for negative-facing surfaces. */
    private static final float NORMAL_NEG = -1f;
    /** Gasket cap U/V extent: 4px / 16px. */
    private static final float GC_UV = 0.25f;
    /** Gasket bottom V end: 8px / 16px. */
    private static final float GC_BOTTOM_V = 0.5f;

    /** Cells along each side of a rippling liquid surface grid. */
    public static final int SURFACE_GRID_CELLS = 8;
    /** Must match goo_fluid_surface.vsh: overlay-U units per block of amplitude. */
    public static final int AMPLITUDE_UNITS_PER_BLOCK = 4096;
    /** The faint ripple a still vat or crucible surface shows, in blocks. */
    public static final float RESTING_RIPPLE_AMPLITUDE = 0.012f;

    /**
     * Creates a context whose uncolored emitters draw opaque white.
     *
     * @param pose  the current pose matrix entry
     * @param c     the vertex consumer for geometry emission
     * @param light the packed light level for shading
     */
    public RenderContext(PoseStack.Pose pose, VertexConsumer c, int light) {
        this(pose, c, light, OPAQUE_WHITE);
    }

    /**
     * Creates a context for a mingled fluid surface, whose lightmap
     * coordinates carry the type band on every vertex it emits (decision
     * noise-mingled-type-textures).
     *
     * @param pose  the current pose matrix entry
     * @param c     the vertex consumer for geometry emission
     * @param color the ARGB color the uncolored emitters use
     * @param band  the type band the surface shows
     * @return the band-carrying context
     */
    public static RenderContext banded(PoseStack.Pose pose, VertexConsumer c, int color, TypeBand band) {
        return new RenderContext(pose, c, band.packed(), color);
    }

    /**
     * Emits a single vertex with explicit color.
     *
     * @param color the ARGB color
     * @param x     the X position
     * @param y     the Y position
     * @param z     the Z position
     * @param u     the U texture coordinate
     * @param v     the V texture coordinate
     * @param nx    the X normal
     * @param ny    the Y normal
     * @param nz    the Z normal
     */
    public void vertexColored(int color, float x, float y, float z, float u, float v,
                       float nx, float ny, float nz) {
        c.addVertex(pose, x, y, z)
            .setColor(color)
            .setUv(u, v)
            .setOverlay(OverlayTexture.NO_OVERLAY)
            .setLight(light)
            .setNormal(pose, nx, ny, nz);
    }

    /**
     * Emits a single vertex in the context color.
     *
     * @param x  the X position
     * @param y  the Y position
     * @param z  the Z position
     * @param u  the U texture coordinate
     * @param v  the V texture coordinate
     * @param nx the X normal
     * @param ny the Y normal
     * @param nz the Z normal
     */
    public void vertex(float x, float y, float z, float u, float v,
                float nx, float ny, float nz) {
        vertexColored(color, x, y, z, u, v, nx, ny, nz);
    }


    /**
     * Emits a quad face of a cuboid for the given direction in the context color.
     *
     * @param box the axis-aligned bounds
     * @param uv  the texture coordinate rectangle
     * @param dir the face direction (normal)
     */
    public void emitFace(CuboidBounds box, GooRenderUtil.UvRect uv, Direction dir) {
        emitFace(color, box, uv, dir);
    }

    /** Emits all 4 horizontal side faces of a cuboid in the context color.
     *
     * @param box the axis-aligned bounds
     * @param uv  the texture coordinate rectangle
     */
    public void emitSides(CuboidBounds box, GooRenderUtil.UvRect uv) {
        emitSides(color, box, uv);
    }

    /** Emits all 6 faces of a cuboid in the context color.
     *
     * @param box the axis-aligned bounds
     * @param uv  the texture coordinate rectangle
     */
    public void emitBox(CuboidBounds box, GooRenderUtil.UvRect uv) {
        emitBox(color, box, uv);
    }


    /**
     * Emits a colored quad face of a cuboid for the given direction.
     *
     * @param color the ARGB color
     * @param box   the axis-aligned bounds
     * @param uv    the texture coordinate rectangle
     * @param dir   the face direction (normal)
     */
    public void emitFace(int color, CuboidBounds box, GooRenderUtil.UvRect uv, Direction dir) {
        float sign = isPositiveFace(dir) ? 1f : NORMAL_NEG;
        switch (dir.getAxis()) {
            case X -> emitFaceX(color, box, uv, sign);
            case Y -> emitFaceY(color, box, uv, sign);
            case Z -> emitFaceZ(color, box, uv, sign);
        }
    }

    /** Returns true for directions whose normal points along the positive axis. */
    private static boolean isPositiveFace(Direction dir) {
        return dir == Direction.UP || dir == Direction.SOUTH || dir == Direction.EAST;
    }

    /** Emits all 4 horizontal colored side faces of a cuboid.
     *
     * @param color the ARGB color
     * @param box   the axis-aligned bounds
     * @param uv    the texture coordinate rectangle
     */
    public void emitSides(int color, CuboidBounds box, GooRenderUtil.UvRect uv) {
        for (Direction dir : Direction.Plane.HORIZONTAL) {
            emitFace(color, box, uv, dir);
        }
    }

    /** Emits all 6 colored faces of a cuboid.
     *
     * @param color the ARGB color
     * @param box   the axis-aligned bounds
     * @param uv    the texture coordinate rectangle
     */
    public void emitBox(int color, CuboidBounds box, GooRenderUtil.UvRect uv) {
        for (Direction dir : Direction.values()) {
            emitFace(color, box, uv, dir);
        }
    }


    /**
     * Emits an upward-facing liquid surface quad with explicit color.
     *
     * @param color the ARGB color
     * @param box   the horizontal bounds (uses x0, x1, z0, z1, yTop)
     * @param uv    the texture coordinate rectangle
     */
    public void liquidSurface(int color, CuboidBounds box, GooRenderUtil.UvRect uv) {
        float y = box.yTop();
        vertexColored(color, box.x0(), y, box.z0(), uv.u0(), uv.v0(), 0f, 1f, 0f);
        vertexColored(color, box.x0(), y, box.z1(), uv.u0(), uv.v1(), 0f, 1f, 0f);
        vertexColored(color, box.x1(), y, box.z1(), uv.u1(), uv.v1(), 0f, 1f, 0f);
        vertexColored(color, box.x1(), y, box.z0(), uv.u1(), uv.v0(), 0f, 1f, 0f);
    }

    /**
     * Emits an upward-facing liquid surface as a grid of quads in the
     * context color (decision undulating-fluid-surface): every rim vertex
     * carries zero amplitude so the surface keeps meeting the walls, and
     * every interior vertex carries the given ripple amplitude, capped below
     * the goo depth so the ripple trough stays above the floor.
     *
     * @param box       the bounds, yBot the reservoir floor and yTop the fill height
     * @param uv        the texture coordinate rectangle
     * @param amplitude the interior ripple amplitude in blocks
     */
    public void liquidSurfaceGrid(CuboidBounds box, GooRenderUtil.UvRect uv, float amplitude) {
        liquidSurfaceLayer(box, uv, amplitude, 0f, 0f);
    }

    /**
     * Emits the downward-facing twin of {@link #liquidSurfaceGrid}, rippling
     * with it because the shader's lift depends on position alone.
     *
     * @param box       the bounds, yBot the reservoir floor and yTop the fill height
     * @param uv        the texture coordinate rectangle
     * @param amplitude the interior ripple amplitude in blocks
     */
    public void liquidSurfaceGridDown(CuboidBounds box, GooRenderUtil.UvRect uv, float amplitude) {
        liquidSurfaceLayerDown(box, uv, amplitude, 0f, 0f);
    }

    /**
     * Emits one mingled layer's upward surface grid at the layer's lift above
     * layer 0's fill height, its rim pushed out by rimOutset. Every interior
     * vertex sits where layer 0's does and carries layer 0's amplitude, so
     * the shader lifts every layer alike and each stays above the one below
     * (decision diagnose-then-fix-undulation-blend-exposure).
     *
     * @param box       layer 0's bounds, yBot the reservoir floor and yTop the fill height
     * @param uv        the texture coordinate rectangle
     * @param amplitude the interior ripple amplitude in blocks
     * @param lift      the distance the layer sits above layer 0
     * @param rimOutset the distance the rim sits outward of layer 0's rim
     */
    public void liquidSurfaceLayer(CuboidBounds box, GooRenderUtil.UvRect uv, float amplitude,
                                   float lift, float rimOutset) {
        emitSurfaceGrid(new SurfaceGrid(box, uv, amplitudeUnitsAboveFloor(box, amplitude),
            box.yTop() + lift, rimOutset), 1f);
    }

    /**
     * Emits the downward-facing twin of {@link #liquidSurfaceLayer}, the
     * lift taken below layer 0's fill height.
     *
     * @param box       layer 0's bounds, yBot the reservoir floor and yTop the fill height
     * @param uv        the texture coordinate rectangle
     * @param amplitude the interior ripple amplitude in blocks
     * @param lift      the distance the layer sits below layer 0
     * @param rimOutset the distance the rim sits outward of layer 0's rim
     */
    public void liquidSurfaceLayerDown(CuboidBounds box, GooRenderUtil.UvRect uv, float amplitude,
                                       float lift, float rimOutset) {
        emitSurfaceGrid(new SurfaceGrid(box, uv, amplitudeUnitsAboveFloor(box, amplitude),
            box.yTop() - lift, rimOutset), NORMAL_NEG);
    }

    /**
     * One surface grid to emit.
     *
     * @param box           layer 0's horizontal bounds
     * @param uv            the texture coordinate rectangle
     * @param interiorUnits the encoded amplitude interior vertices carry
     * @param y             the height the grid sits at
     * @param rimOutset     the distance the rim sits outward of the bounds
     */
    private record SurfaceGrid(CuboidBounds box, GooRenderUtil.UvRect uv, int interiorUnits,
                               float y, float rimOutset) {
    }

    /**
     * Encodes the amplitude capped strictly below the goo depth, since the
     * shader's ripple trough sits a full amplitude under the fill height
     * (decision diagnose-then-fix-undulation-floor).
     *
     * @param box       the bounds, yBot the reservoir floor and yTop the fill height
     * @param amplitude the requested ripple amplitude in blocks
     * @return the encoded amplitude whose trough stays above yBot
     */
    private static int amplitudeUnitsAboveFloor(CuboidBounds box, float amplitude) {
        int depthUnits = (int) Math.ceil((box.yTop() - box.yBot()) * AMPLITUDE_UNITS_PER_BLOCK) - 1;
        return Math.min(encodeAmplitude(amplitude), Math.max(depthUnits, 0));
    }

    /**
     * Encodes a ripple amplitude into the overlay-U units the surface shader decodes.
     *
     * @param amplitude the ripple amplitude in blocks
     * @return the amplitude in 1/{@value #AMPLITUDE_UNITS_PER_BLOCK} block, clamped to a short
     */
    public static int encodeAmplitude(float amplitude) {
        int units = Math.round(amplitude * AMPLITUDE_UNITS_PER_BLOCK);
        return Math.clamp(units, 0, Short.MAX_VALUE);
    }

    /**
     * Emits the grid, one quad per cell, winding by the normal sign.
     *
     * @param grid the grid to emit
     * @param ny   the Y normal, positive for the upward face
     */
    private void emitSurfaceGrid(SurfaceGrid grid, float ny) {
        for (int i = 0; i < SURFACE_GRID_CELLS; i++) {
            for (int j = 0; j < SURFACE_GRID_CELLS; j++) {
                if (ny > 0) {
                    gridVertex(grid, i, j, ny);
                    gridVertex(grid, i, j + 1, ny);
                    gridVertex(grid, i + 1, j + 1, ny);
                    gridVertex(grid, i + 1, j, ny);
                } else {
                    gridVertex(grid, i + 1, j, ny);
                    gridVertex(grid, i + 1, j + 1, ny);
                    gridVertex(grid, i, j + 1, ny);
                    gridVertex(grid, i, j, ny);
                }
            }
        }
    }

    /**
     * Emits the grid vertex at column i and row j, zero amplitude on the rim.
     *
     * @param grid the grid the vertex belongs to
     * @param i    the column index along X, 0 to the cell count
     * @param j    the row index along Z, 0 to the cell count
     * @param ny   the Y normal
     */
    private void gridVertex(SurfaceGrid grid, int i, int j, float ny) {
        CuboidBounds box = grid.box();
        GooRenderUtil.UvRect uv = grid.uv();
        float tx = (float) i / SURFACE_GRID_CELLS;
        float tz = (float) j / SURFACE_GRID_CELLS;
        boolean isRim = i == 0 || j == 0 || i == SURFACE_GRID_CELLS || j == SURFACE_GRID_CELLS;
        c.addVertex(pose, gridCoordinate(box.x0(), box.x1(), i, grid.rimOutset()), grid.y(),
                gridCoordinate(box.z0(), box.z1(), j, grid.rimOutset()))
            .setColor(color)
            .setUv(lerp(uv.u0(), uv.u1(), tx), lerp(uv.v0(), uv.v1(), tz))
            .setUv1(isRim ? 0 : grid.interiorUnits(), 0)
            .setLight(light)
            .setNormal(pose, 0f, ny, 0f);
    }

    /**
     * The coordinate of grid line index between from and to, the two rim
     * lines pushed outward by rimOutset and the interior lines left in place.
     */
    private static float gridCoordinate(float from, float to, int index, float rimOutset) {
        if (index == 0) {
            return from - rimOutset;
        }
        if (index == SURFACE_GRID_CELLS) {
            return to + rimOutset;
        }
        return lerp(from, to, (float) index / SURFACE_GRID_CELLS);
    }

    /** Linear interpolation that lands exactly on both ends. */
    private static float lerp(float from, float to, float t) {
        return from * (1f - t) + to * t;
    }


    /**
     * Emits a complete gasket box (top cap, bottom cap, 4 side faces)
     * using the standard gasket UV layout.
     *
     * @param box  the axis-aligned bounds
     * @param gsU0 gasket side U start
     * @param gsU1 gasket side U end
     * @param gsV1 gasket side V end
     */
    public void gasketBox(CuboidBounds box, float gsU0, float gsU1, float gsV1) {
        gasketTopCap(box);
        gasketBottomCap(box);
        GooRenderUtil.UvRect sideUv = new GooRenderUtil.UvRect(gsU0, 0, gsU1, gsV1);
        emitSides(box, sideUv);
    }

    /** Emits the upward-facing top cap quad of a gasket box. */
    private void gasketTopCap(CuboidBounds box) {
        float y1 = box.yTop();
        vertex(box.x0(), y1, box.z0(), 0, 0, 0f, 1f, 0f);
        vertex(box.x0(), y1, box.z1(), 0, GC_UV, 0f, 1f, 0f);
        vertex(box.x1(), y1, box.z1(), GC_UV, GC_UV, 0f, 1f, 0f);
        vertex(box.x1(), y1, box.z0(), GC_UV, 0, 0f, 1f, 0f);
    }

    /** Emits the downward-facing bottom cap quad of a gasket box. */
    private void gasketBottomCap(CuboidBounds box) {
        float y0 = box.yBot();
        vertex(box.x1(), y0, box.z0(), GC_UV, GC_UV, 0f, NORMAL_NEG, 0f);
        vertex(box.x1(), y0, box.z1(), GC_UV, GC_BOTTOM_V, 0f, NORMAL_NEG, 0f);
        vertex(box.x0(), y0, box.z1(), 0, GC_BOTTOM_V, 0f, NORMAL_NEG, 0f);
        vertex(box.x0(), y0, box.z0(), 0, GC_UV, 0f, NORMAL_NEG, 0f);
    }


    /** Y-axis face with winding based on normal sign. */
    private void emitFaceY(int color, CuboidBounds box, GooRenderUtil.UvRect uv, float ny) {
        float y = ny > 0 ? box.yTop() : box.yBot();
        if (ny > 0) {
            vertexColored(color, box.x0(), y, box.z0(), uv.u0(), uv.v0(), 0f, ny, 0f);
            vertexColored(color, box.x0(), y, box.z1(), uv.u0(), uv.v1(), 0f, ny, 0f);
            vertexColored(color, box.x1(), y, box.z1(), uv.u1(), uv.v1(), 0f, ny, 0f);
            vertexColored(color, box.x1(), y, box.z0(), uv.u1(), uv.v0(), 0f, ny, 0f);
        } else {
            vertexColored(color, box.x1(), y, box.z0(), uv.u1(), uv.v0(), 0f, ny, 0f);
            vertexColored(color, box.x1(), y, box.z1(), uv.u1(), uv.v1(), 0f, ny, 0f);
            vertexColored(color, box.x0(), y, box.z1(), uv.u0(), uv.v1(), 0f, ny, 0f);
            vertexColored(color, box.x0(), y, box.z0(), uv.u0(), uv.v0(), 0f, ny, 0f);
        }
    }

    /** X-axis face with winding based on normal sign. */
    private void emitFaceX(int color, CuboidBounds box, GooRenderUtil.UvRect uv, float nx) {
        float x = nx > 0 ? box.x1() : box.x0();
        if (nx > 0) {
            vertexColored(color, x, box.yTop(), box.z1(), uv.u1(), uv.v0(), nx, 0f, 0f);
            vertexColored(color, x, box.yBot(), box.z1(), uv.u1(), uv.v1(), nx, 0f, 0f);
            vertexColored(color, x, box.yBot(), box.z0(), uv.u0(), uv.v1(), nx, 0f, 0f);
            vertexColored(color, x, box.yTop(), box.z0(), uv.u0(), uv.v0(), nx, 0f, 0f);
        } else {
            vertexColored(color, x, box.yTop(), box.z0(), uv.u1(), uv.v0(), nx, 0f, 0f);
            vertexColored(color, x, box.yBot(), box.z0(), uv.u1(), uv.v1(), nx, 0f, 0f);
            vertexColored(color, x, box.yBot(), box.z1(), uv.u0(), uv.v1(), nx, 0f, 0f);
            vertexColored(color, x, box.yTop(), box.z1(), uv.u0(), uv.v0(), nx, 0f, 0f);
        }
    }

    /** Z-axis face with winding based on normal sign. */
    private void emitFaceZ(int color, CuboidBounds box, GooRenderUtil.UvRect uv, float nz) {
        float z = nz > 0 ? box.z1() : box.z0();
        if (nz > 0) {
            vertexColored(color, box.x0(), box.yTop(), z, uv.u1(), uv.v0(), 0f, 0f, nz);
            vertexColored(color, box.x0(), box.yBot(), z, uv.u1(), uv.v1(), 0f, 0f, nz);
            vertexColored(color, box.x1(), box.yBot(), z, uv.u0(), uv.v1(), 0f, 0f, nz);
            vertexColored(color, box.x1(), box.yTop(), z, uv.u0(), uv.v0(), 0f, 0f, nz);
        } else {
            vertexColored(color, box.x1(), box.yTop(), z, uv.u0(), uv.v0(), 0f, 0f, nz);
            vertexColored(color, box.x1(), box.yBot(), z, uv.u0(), uv.v1(), 0f, 0f, nz);
            vertexColored(color, box.x0(), box.yBot(), z, uv.u1(), uv.v1(), 0f, 0f, nz);
            vertexColored(color, box.x0(), box.yTop(), z, uv.u1(), uv.v0(), 0f, 0f, nz);
        }
    }
}
