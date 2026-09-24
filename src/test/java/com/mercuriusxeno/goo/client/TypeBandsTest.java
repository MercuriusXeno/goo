package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.resources.ResourceKey;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests that the type bands partition [0, 1] over a vat's goo, one band per
 * type, largest first, each as wide as its type's volume ratio.
 */
class TypeBandsTest {

    private static final float RATIO_TOLERANCE = 1e-6f;

    static Stream<Arguments> contentsAndLargestFirstOrder() {
        return Stream.of(
            Arguments.of(Map.of(GooTypes.BLAZE, 700), List.of(GooTypes.BLAZE)),
            Arguments.of(Map.of(GooTypes.BLAZE, 300, GooTypes.FROST, 900),
                List.of(GooTypes.FROST, GooTypes.BLAZE)),
            Arguments.of(Map.of(GooTypes.AEON, 100, GooTypes.BLAZE, 600, GooTypes.FROST, 300),
                List.of(GooTypes.BLAZE, GooTypes.FROST, GooTypes.AEON)),
            Arguments.of(Map.of(GooTypes.GLOW, 500, GooTypes.CRYSTAL, 500),
                List.of(GooTypes.CRYSTAL, GooTypes.GLOW)),
            Arguments.of(Map.of(GooTypes.ENDER, 1, GooTypes.BLAZE, 2_000_000_000),
                List.of(GooTypes.BLAZE, GooTypes.ENDER)));
    }

    @ParameterizedTest
    @MethodSource("contentsAndLargestFirstOrder")
    void bandsPartitionTheRangeByVolumeRatioLargestFirst(
            Map<ResourceKey<GooTypeDefinition>, Integer> volumes,
            List<ResourceKey<GooTypeDefinition>> largestFirst) {
        GooContents contents = new GooContents(volumes);

        List<TypeBand> bands = TypeBands.over(contents);

        assertEquals(largestFirst, bands.stream().map(TypeBand::type).toList());
        assertEquals(0f, bands.getFirst().lo());
        assertEquals(1f, bands.getLast().hi());
        for (int i = 1; i < bands.size(); i++) {
            assertEquals(bands.get(i - 1).hi(), bands.get(i).lo());
        }
        for (TypeBand band : bands) {
            float ratio = (float) contents.getVolume(band.type()) / contents.totalVolume();
            assertEquals(ratio, band.hi() - band.lo(), RATIO_TOLERANCE);
        }
    }

    @Test
    void emptyContentsAnswerNoBand() {
        assertTrue(TypeBands.over(GooContents.EMPTY).isEmpty());
    }
}
