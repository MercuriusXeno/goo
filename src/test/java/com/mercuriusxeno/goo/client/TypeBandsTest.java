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
 * type, in the fixed type order for a surface and largest first for a
 * dissolve glow, each as wide as its type's volume ratio, and that their
 * layers over-blend to each type's volume ratio.
 */
class TypeBandsTest {

    private static final float RATIO_TOLERANCE = 1e-6f;
    private static final float COVERAGE_TOLERANCE = 1e-5f;

    static Stream<Arguments> contentsAndTypeOrder() {
        return Stream.of(
            Arguments.of(Map.of(GooTypes.BLAZE, 700), List.of(GooTypes.BLAZE)),
            Arguments.of(Map.of(GooTypes.BLAZE, 300, GooTypes.FROST, 900),
                List.of(GooTypes.BLAZE, GooTypes.FROST)),
            Arguments.of(Map.of(GooTypes.AEON, 100, GooTypes.BLAZE, 600, GooTypes.FROST, 300),
                List.of(GooTypes.AEON, GooTypes.BLAZE, GooTypes.FROST)),
            Arguments.of(Map.of(GooTypes.GLOW, 500, GooTypes.CRYSTAL, 500),
                List.of(GooTypes.CRYSTAL, GooTypes.GLOW)),
            Arguments.of(Map.of(GooTypes.ENDER, 2_000_000_000, GooTypes.BLAZE, 1),
                List.of(GooTypes.BLAZE, GooTypes.ENDER)));
    }

    @Test
    void dissolveBandsStandLargestFirst() {
        GooContents contents = new GooContents(Map.of(GooTypes.AEON, 100, GooTypes.BLAZE, 300, GooTypes.FROST, 900));

        assertEquals(List.of(GooTypes.FROST, GooTypes.BLAZE, GooTypes.AEON),
            TypeBands.largestFirst(contents).stream().map(TypeBand::type).toList());
    }

    @ParameterizedTest
    @MethodSource("contentsAndTypeOrder")
    void bandsPartitionTheRangeByVolumeRatioInTypeOrder(
            Map<ResourceKey<GooTypeDefinition>, Integer> volumes,
            List<ResourceKey<GooTypeDefinition>> typeOrder) {
        GooContents contents = new GooContents(volumes);

        List<TypeBand> bands = TypeBands.over(contents);

        assertEquals(typeOrder, bands.stream().map(TypeBand::type).toList());
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

    @ParameterizedTest
    @MethodSource("contentsAndTypeOrder")
    void layersOverBlendToEachTypesVolumeRatio(
            Map<ResourceKey<GooTypeDefinition>, Integer> volumes,
            List<ResourceKey<GooTypeDefinition>> typeOrder) {
        GooContents contents = new GooContents(volumes);

        List<TypeBand> bands = TypeBands.over(contents);

        assertEquals(1f, bands.getFirst().share());
        for (int k = 0; k < bands.size(); k++) {
            TypeBand band = bands.get(k);
            assertEquals(k, band.layer());
            assertEquals(k * TypeBand.LAYER_LIFT, band.lift());
            float coverage = band.share();
            for (TypeBand later : bands.subList(k + 1, bands.size())) {
                coverage *= 1f - later.share();
            }
            float ratio = (float) contents.getVolume(band.type()) / contents.totalVolume();
            assertEquals(ratio, coverage, COVERAGE_TOLERANCE);
        }
    }

    @Test
    void packingCarriesTheShareLowAndTheTypeSeedHigh() {
        TypeBand band = new TypeBand(GooTypes.FROST, 0.75f, 1f, 2);

        assertEquals(TypeBand.SHARE_UNITS / 4 | GooTypes.indexOf(GooTypes.FROST) << 16, band.packed());
    }

    @Test
    void emptyContentsAnswerNoBand() {
        assertTrue(TypeBands.over(GooContents.EMPTY).isEmpty());
    }
}
