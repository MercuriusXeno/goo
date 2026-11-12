package com.mercuriusxeno.goo.ability.program;

import org.junit.jupiter.api.Test;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The ailment table carries every ailment the type lists name, each with its
 * own color and either a pattern or an outline (decisions ailment-overlay-shader-per-ailment,
 * ailment-glisten-becomes-a-pulsing-outline).
 */
class AilmentKindTest {

    private static final int RGB_MASK = 0xFFFFFF;
    private static final Set<AilmentKind> OUTLINED =
            EnumSet.of(AilmentKind.STASIS, AilmentKind.HEX, AilmentKind.GLOW, AilmentKind.ASTRAL);

    @Test
    void eachAilmentTheListsNameCarriesItsColor() {
        Map<AilmentKind, Integer> named = Stream.of(AilmentKind.STASIS, AilmentKind.SCALES, AilmentKind.ZONE,
                AilmentKind.HEX, AilmentKind.PETRIFY, AilmentKind.FROZEN)
                .collect(Collectors.toMap(Function.identity(), AilmentKind::rgb));

        assertEquals(Map.of(AilmentKind.STASIS, 0xFFD447, AilmentKind.SCALES, 0x4AEDD9, AilmentKind.ZONE, 0xB05CFF,
                AilmentKind.HEX, 0x6A1FB0, AilmentKind.PETRIFY, 0x8C8A86, AilmentKind.FROZEN, 0xC4ECFF), named);
    }

    @Test
    void stasisIsAGoldenOutline() {
        assertTrue(AilmentKind.STASIS.drawsOutline());
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
        assertTrue(AilmentKind.HEX.drawsOutline());
        assertNotEquals(AilmentKind.STASIS.rgb(), AilmentKind.HEX.rgb());
        assertEquals(AilmentPattern.STONE, AilmentKind.PETRIFY.pattern());
        assertEquals(AilmentPattern.FROST, AilmentKind.FROZEN.pattern());
    }

    /**
     * Every ailment drawing an overlay field names one of the patterns left
     * once the glisten went, and the four marking kinds draw the outline
     * alone (decision ailment-glisten-becomes-a-pulsing-outline).
     */
    @Test
    void everyKindDrawingAFieldNamesARemainingPattern() {
        Set<AilmentPattern> remaining = EnumSet.of(AilmentPattern.FACETS, AilmentPattern.SHIMMER,
                AilmentPattern.STONE, AilmentPattern.FROST, AilmentPattern.WOBBLE);

        assertEquals(remaining, EnumSet.allOf(AilmentPattern.class));
        for (AilmentKind kind : AilmentKind.values()) {
            assertEquals(OUTLINED.contains(kind), kind.drawsOutline(), kind.name());
            if (kind.drawsOverlay()) {
                assertTrue(remaining.contains(kind.pattern()), kind.name());
            }
        }
    }

    /** Grab's ailment drives the held mob's flail and draws neither a field nor an outline (decision kinetic-is-electric-blue-and-white). */
    @Test
    void grabDrawsNothingAndEveryOtherAilmentDrawsAFieldOrAnOutline() {
        for (AilmentKind kind : AilmentKind.values()) {
            assertEquals(kind != AilmentKind.GRABBED && !OUTLINED.contains(kind), kind.drawsOverlay(), kind.name());
        }
        assertFalse(AilmentKind.GRABBED.drawsOutline());
    }

    private static boolean isGolden(int rgb) {
        int red = rgb >> 16 & 0xFF;
        int green = rgb >> 8 & 0xFF;
        int blue = rgb & 0xFF;
        return red > green && green > blue;
    }
}
