package com.mercuriusxeno.goo.client;

import net.minecraft.util.ARGB;
import java.util.Arrays;
import java.util.List;

/**
 * The frame's table of mingle layers (decision one-pass-blends-every-type):
 * one row per layer, a surface's layers in consecutive rows, so one draw
 * carries every type it holds however many there are. A row is
 * {@value #COLUMNS} texels wide, each texel's int written as the bytes red,
 * green, blue, alpha in memory, which goo_fluid_surface.fsh reassembles.
 */
public final class MingleTable {

    /** Must match goo_fluid_surface.fsh: the texels one row spans. */
    public static final int COLUMNS = 8;
    /** Must match goo_fluid_surface.fsh: the column of each field. */
    static final int SPRITE_U0 = 0;
    static final int SPRITE_V0 = 1;
    static final int SPRITE_U1 = 2;
    static final int SPRITE_V1 = 3;
    static final int TINT = 4;
    static final int SHARE = 5;
    static final int SEED = 6;

    /** Bit offset of the row count in the packed lightmap coordinates. */
    private static final int COUNT_SHIFT = 16;
    /** The largest row index or count a lightmap coordinate carries, the shader reading it as a signed short. */
    private static final int SHORT_LIMIT = 0x7FFF;
    private static final int INITIAL_ROWS = 64;
    /** How many times over the table grows when a frame outruns it. */
    static final int GROWTH_FACTOR = 2;
    private static final String FULL_TABLE = "The mingle table holds no more than " + SHORT_LIMIT + " rows a frame";

    private int[] texels = new int[INITIAL_ROWS * COLUMNS];
    private int rows;

    /**
     * Appends a surface's layers in their order.
     *
     * @param layers the layers, the first drawn first
     * @return the first row and the row count packed as the lightmap coordinates goo_fluid_surface.vsh reads
     */
    public int append(List<MingleLayer> layers) {
        int first = rows;
        if (first > SHORT_LIMIT || layers.size() > SHORT_LIMIT) {
            throw new IllegalStateException(FULL_TABLE);
        }
        ensureRows(first + layers.size());
        for (MingleLayer layer : layers) {
            writeRow(rows, layer);
            rows++;
        }
        return first | layers.size() << COUNT_SHIFT;
    }

    /** Empties the table for a new frame. */
    public void clear() {
        rows = 0;
    }

    /**
     * @return the rows written since the last clear
     */
    public int rows() {
        return rows;
    }

    /**
     * Answers one texel's int, its red byte lowest.
     *
     * @param row    the row
     * @param column the column
     * @return the texel
     */
    public int texel(int row, int column) {
        return texels[row * COLUMNS + column];
    }

    private void writeRow(int row, MingleLayer layer) {
        int at = row * COLUMNS;
        texels[at + SPRITE_U0] = Float.floatToRawIntBits(layer.sprite().u0());
        texels[at + SPRITE_V0] = Float.floatToRawIntBits(layer.sprite().v0());
        texels[at + SPRITE_U1] = Float.floatToRawIntBits(layer.sprite().u1());
        texels[at + SPRITE_V1] = Float.floatToRawIntBits(layer.sprite().v1());
        texels[at + TINT] = ARGB.toABGR(layer.tint());
        texels[at + SHARE] = Float.floatToRawIntBits(layer.share());
        texels[at + SEED] = layer.seed();
    }

    private void ensureRows(int needed) {
        if (needed * COLUMNS > texels.length) {
            texels = Arrays.copyOf(texels, Math.max(needed, rows * GROWTH_FACTOR) * COLUMNS);
        }
    }
}
