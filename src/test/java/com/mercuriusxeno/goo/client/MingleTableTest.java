package com.mercuriusxeno.goo.client;

import org.junit.jupiter.api.Test;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests that the mingle table lays each layer out as goo_fluid_surface.fsh
 * reads it, and that a surface's lightmap coordinates name its own rows
 * however many types it holds (decision one-pass-blends-every-type).
 */
class MingleTableTest {

    private static final GooRenderUtil.UvRect SPRITE = new GooRenderUtil.UvRect(0.25f, 0.5f, 0.3125f, 0.5625f);

    private static MingleLayer layer(float share, int seed) {
        return new MingleLayer(SPRITE, 0x80112233, share, seed);
    }

    @Test
    void aRowCarriesTheSpriteTintShareAndSeedAsTheShaderReadsThem() {
        MingleTable table = new MingleTable();

        table.append(List.of(layer(0.75f, 9)));

        assertEquals(Float.floatToRawIntBits(0.25f), table.texel(0, MingleTable.SPRITE_U0));
        assertEquals(Float.floatToRawIntBits(0.5f), table.texel(0, MingleTable.SPRITE_V0));
        assertEquals(Float.floatToRawIntBits(0.3125f), table.texel(0, MingleTable.SPRITE_U1));
        assertEquals(Float.floatToRawIntBits(0.5625f), table.texel(0, MingleTable.SPRITE_V1));
        assertEquals(0x80332211, table.texel(0, MingleTable.TINT), "the tint's red byte stands lowest");
        assertEquals(Float.floatToRawIntBits(0.75f), table.texel(0, MingleTable.SHARE));
        assertEquals(9, table.texel(0, MingleTable.SEED));
    }

    @Test
    void eachSurfaceNamesItsFirstRowLowAndItsRowCountHigh() {
        MingleTable table = new MingleTable();

        int first = table.append(List.of(layer(1f, 0), layer(0.5f, 1)));
        int second = table.append(List.of(layer(1f, 2), layer(0.5f, 3), layer(0.25f, 4)));

        assertEquals(2 << 16, first);
        assertEquals(2 | 3 << 16, second);
        assertEquals(5, table.rows());
        assertEquals(4, table.texel(4, MingleTable.SEED));
    }

    @Test
    void aSurfaceOfMoreTypesThanTheTableFirstHeldStillGetsItsRows() {
        MingleTable table = new MingleTable();
        int types = 200;
        List<MingleLayer> layers = java.util.stream.IntStream.range(0, types).mapToObj(k -> layer(1f, k)).toList();

        assertEquals(types << 16, table.append(layers));
        assertEquals(types - 1, table.texel(types - 1, MingleTable.SEED));
    }

    @Test
    void clearingStartsTheNextFrameAtRowZero() {
        MingleTable table = new MingleTable();
        table.append(List.of(layer(1f, 0)));

        table.clear();

        assertEquals(1 << 16, table.append(List.of(layer(1f, 5))));
        assertEquals(5, table.texel(0, MingleTable.SEED));
    }
}
