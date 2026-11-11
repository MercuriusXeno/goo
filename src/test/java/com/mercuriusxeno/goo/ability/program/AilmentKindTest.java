package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ailment table carries every ailment the type lists name, each with its
 * own color and pattern (decision ailment-overlay-shader-per-ailment).
 */
class AilmentKindTest {

    @Test
    void eachAilmentTheListsNameCarriesItsColor() {
        Map<AilmentKind, Integer> named = Stream.of(AilmentKind.STASIS, AilmentKind.SCALES, AilmentKind.ZONE,
                AilmentKind.HEX, AilmentKind.PETRIFY, AilmentKind.FROZEN)
                .collect(Collectors.toMap(Function.identity(), AilmentKind::rgb));

        assertEquals(Map.of(AilmentKind.STASIS, 0xFFD447, AilmentKind.SCALES, 0x4AEDD9, AilmentKind.ZONE, 0xB05CFF,
                AilmentKind.HEX, 0x6A1FB0, AilmentKind.PETRIFY, 0x8C8A86, AilmentKind.FROZEN, 0xC4ECFF), named);
    }

    @Test
    void stasisIsAGoldenGlint() {
        assertEquals(AilmentPattern.GLINT, AilmentKind.STASIS.pattern());
        assertTrue(isGolden(AilmentKind.STASIS.rgb()));
    }

    @Test
    void scalesIsDiamondBlueWithAPatternOfItsOwn() {
        assertEquals(AilmentPattern.FACETS, AilmentKind.SCALES.pattern());
        int rgb = AilmentKind.SCALES.rgb();
        assertTrue((rgb & 0xFF) > (rgb >> 16), "scales is not blue over red");
    }

    @Test
    void eachRemainingAilmentWearsItsOwnLook() {
        assertEquals(AilmentPattern.SHIMMER, AilmentKind.ZONE.pattern());
        assertEquals(AilmentKind.ZONE.rgb(), AilmentKind.SHIFTER.rgb());
        assertEquals(AilmentPattern.SHIMMER, AilmentKind.SHIFTER.pattern());
        assertEquals(AilmentPattern.GLINT, AilmentKind.HEX.pattern());
        assertNotEquals(AilmentKind.STASIS.rgb(), AilmentKind.HEX.rgb());
        assertEquals(AilmentPattern.STONE, AilmentKind.PETRIFY.pattern());
        assertEquals(AilmentPattern.FROST, AilmentKind.FROZEN.pattern());
    }

    /** Grab's ailment drives the held mob's flail and draws no overlay (decision kinetic-is-electric-blue-and-white). */
    @Test
    void grabDrawsNoOverlayAndEveryOtherAilmentDoes() {
        for (AilmentKind kind : AilmentKind.values()) {
            assertEquals(kind != AilmentKind.GRABBED, kind.drawsOverlay(), kind.name());
        }
    }

    private static boolean isGolden(int rgb) {
        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        return red > green && green > blue;
    }
}
