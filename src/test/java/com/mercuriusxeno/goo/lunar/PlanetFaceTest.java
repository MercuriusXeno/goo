package com.mercuriusxeno.goo.lunar;

import net.minecraft.tags.BiomeTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.level.biome.Biome;
import net.neoforged.neoforge.common.Tags;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.Set;
import java.util.function.Predicate;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The blue planet's face, sampled from the overworld's biomes: each biome gives one color by its tags, and the face is
 * a grid of those colors around world spawn, west to the left and north to the top.
 * decision planet-face-is-procgen-from-the-overworlds-biomes
 */
class PlanetFaceTest {

    private static Predicate<TagKey<Biome>> taggedWith(Set<TagKey<Biome>> tags) {
        return tags::contains;
    }

    @Nested
    class TheColorABiomeGives {

        @Test
        void anOceanReadsSea() {
            assertEquals(PlanetFace.SEA, PlanetFace.colorOf(taggedWith(Set.of(BiomeTags.IS_OCEAN))));
        }

        @Test
        void aRiverReadsSea() {
            assertEquals(PlanetFace.SEA, PlanetFace.colorOf(taggedWith(Set.of(BiomeTags.IS_RIVER))));
        }

        @Test
        void aDesertReadsTan() {
            assertEquals(PlanetFace.TAN, PlanetFace.colorOf(taggedWith(Set.of(Tags.Biomes.IS_DESERT))));
        }

        @Test
        void badlandsReadTan() {
            assertEquals(PlanetFace.TAN, PlanetFace.colorOf(taggedWith(Set.of(BiomeTags.IS_BADLANDS))));
        }

        @Test
        void aBeachReadsTan() {
            assertEquals(PlanetFace.TAN, PlanetFace.colorOf(taggedWith(Set.of(BiomeTags.IS_BEACH))));
        }

        @Test
        void aSnowyBiomeReadsWhite() {
            assertEquals(PlanetFace.SNOW, PlanetFace.colorOf(taggedWith(Set.of(Tags.Biomes.IS_SNOWY))));
        }

        @Test
        void aFrozenOceanReadsWhite() {
            assertEquals(PlanetFace.SNOW,
                    PlanetFace.colorOf(taggedWith(Set.of(BiomeTags.IS_OCEAN, Tags.Biomes.IS_AQUATIC_ICY))));
        }

        @Test
        void otherLandReadsGreen() {
            assertEquals(PlanetFace.LAND, PlanetFace.colorOf(taggedWith(Set.of(BiomeTags.IS_FOREST))));
        }
    }

    @Nested
    class TheGridAroundSpawn {

        private static final int SPAWN_X = 120;
        private static final int SPAWN_Z = -40;
        private static final int LAST = PlanetFace.SIZE - 1;

        /** An ocean west of spawn and a desert from spawn east. */
        private final PlanetFace.BiomeTagsAt oceanWestDesertEast = (x, y, z) -> taggedWith(
                x < SPAWN_X ? Set.of(BiomeTags.IS_OCEAN) : Set.of(Tags.Biomes.IS_DESERT));

        @Test
        void theFaceIsASquareOfTheFacesSize() {
            assertEquals(PlanetFace.SIZE * PlanetFace.SIZE,
                    PlanetFace.sample(oceanWestDesertEast, SPAWN_X, SPAWN_Z).length);
        }

        @Test
        void theWestColumnsReadTheOceanAndTheEastColumnsTheDesert() {
            int[] face = PlanetFace.sample(oceanWestDesertEast, SPAWN_X, SPAWN_Z);
            for (int row = 0; row < PlanetFace.SIZE; row++) {
                assertEquals(PlanetFace.SEA, face[row * PlanetFace.SIZE], "row " + row + " west");
                assertEquals(PlanetFace.SEA, face[row * PlanetFace.SIZE + PlanetFace.SIZE / 2 - 1], "row " + row);
                assertEquals(PlanetFace.TAN, face[row * PlanetFace.SIZE + PlanetFace.SIZE / 2], "row " + row);
                assertEquals(PlanetFace.TAN, face[row * PlanetFace.SIZE + LAST], "row " + row + " east");
            }
        }

        @Test
        void theNorthRowsReadTheTopOfTheFace() {
            PlanetFace.BiomeTagsAt snowNorth = (x, y, z) -> taggedWith(
                    z < SPAWN_Z ? Set.of(Tags.Biomes.IS_SNOWY) : Set.of());
            int[] face = PlanetFace.sample(snowNorth, SPAWN_X, SPAWN_Z);
            assertEquals(PlanetFace.SNOW, face[0]);
            assertEquals(PlanetFace.LAND, face[LAST * PlanetFace.SIZE]);
        }
    }
}
