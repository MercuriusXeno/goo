package com.mercuriusxeno.goo.client.ability;

import com.google.gson.JsonParser;
import com.mercuriusxeno.goo.ability.program.AilmentKind;
import com.mercuriusxeno.goo.client.RecordingVertexConsumer;
import com.mercuriusxeno.goo.type.GooColors;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.serialization.JsonOps;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.ToIntFunction;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * HeldSweepVisual's shell traces a block's voxel shape: a revealed side is
 * drawn on each of the shape's boxes, at the box's own bounds rather than
 * the cube's; and Scry's sphere, faces and glisten take the casting goo
 * type's color, read from the bundled type JSON.
 * decision scry-sphere-reveals-faces-and-glistens-mobs
 * decision scry-ships-on-astral
 */
class HeldSweepVisualTest {

    private static final AABB BOTTOM_SLAB = new AABB(0, 0, 0, 1, 0.5, 1);
    private static final AABB FENCE_POST = new AABB(0.375, 0, 0.375, 0.625, 1.5, 0.625);
    private static final float TOLERANCE = 0.01f;

    /**
     * The sonar return: every wall a front hits lights at the hit and fades
     * behind the front, fronts overlap, and a let-go front runs on to its
     * range without flicker or snapping back.
     * decision held-sweeps-share-one-fast-shell-visual
     */
    @Nested
    class Echoes {

        /** A world of air over one floor of solid blocks just under the caster's feet. */
        private static final Predicate<BlockPos> OVER_A_FLOOR = pos -> pos.getY() != -1;
        private static final Function<BlockPos, List<AABB>> CUBES = pos -> List.of(new AABB(0, 0, 0, 1, 1, 1));
        private static final Vec3 CENTER = new Vec3(0.5, 0.9, 0.5);
        private static final double SCRY_RANGE = 96;
        private static final int TINT = 0x8866FF;
        private static final int ROOM = 100_000;
        private static final double BLOCKS_A_TICK = 2.4;
        private static final long HEARD_AT = 100;

        @Test
        void aHitWallGlowsWholeOnItsHitTickHalfAtTwentyAndGoneAtForty() {
            assertEquals(1f, HeldSweepVisual.glowAt(0), 1e-6f);
            assertEquals(0.5f, HeldSweepVisual.glowAt(20), 1e-6f);
            assertEquals(0f, HeldSweepVisual.glowAt(40), 0f);
        }

        @Test
        void aWallWhoseGlowReachedNothingLeavesTheRevealSet() {
            HeldSweepPulse pulse = new HeldSweepPulse(new EchoField(CENTER), SCRY_RANGE, TINT);
            pulse.heard(12, HEARD_AT);
            pulse.advance(HEARD_AT + 1, OVER_A_FLOOR, CUBES, ROOM);
            assertTrue(!pulse.lit().isEmpty(), "the floor the front crossed lights");

            pulse.fadeOut(HEARD_AT + 1 + 40);

            assertTrue(pulse.lit().isEmpty(), "every lit wall has faded and left");
        }

        @Test
        void aLetGoFrontRunsOnToItsRangeAndNeverSnapsBack() {
            HeldSweepPulse pulse = new HeldSweepPulse(new EchoField(CENTER), SCRY_RANGE, TINT);
            pulse.heard(24, HEARD_AT);

            assertEquals(24, pulse.drawnRadius(HEARD_AT + 1), 1e-9, "the last radius the server sent");
            assertEquals(24 + BLOCKS_A_TICK, pulse.drawnRadius(HEARD_AT + 2), 1e-9, "a tick on, unheard");
            double drawn = 0;
            for (double now = HEARD_AT; now <= HEARD_AT + 60; now += 0.25) {
                double next = pulse.drawnRadius(now);
                assertTrue(next >= drawn, "never back at " + now);
                drawn = next;
            }
            assertEquals(SCRY_RANGE, drawn, 1e-9, "on to its range");
        }

        @Test
        void twoPulsesWallsDrawInOneFrameEachAtItsOwnFront() {
            EchoField field = new EchoField(CENTER);
            HeldSweepPulse first = new HeldSweepPulse(field, SCRY_RANGE, TINT);
            HeldSweepPulse second = new HeldSweepPulse(field, SCRY_RANGE, TINT);
            first.heard(11 * BLOCKS_A_TICK, HEARD_AT);
            second.heard(BLOCKS_A_TICK, HEARD_AT);
            double frame = HEARD_AT + 1;

            first.advance(frame, OVER_A_FLOOR, CUBES, ROOM);
            second.advance(frame, OVER_A_FLOOR, CUBES, ROOM);

            assertFront(first, frame);
            assertFront(second, frame);
        }

        /**
         * A new pulse lights the walls near the caster even while an older
         * front has lit as many walls as the sweep allows far out (the
         * operator saw Scry stop lighting near walls mid-hold).
         */
        @Test
        void aNewPulseLightsNearWallsWhileAnOlderOneHasLitItsMost() {
            EchoField field = new EchoField(CENTER);
            HeldSweepPulse older = new HeldSweepPulse(field, SCRY_RANGE, TINT);
            HeldSweepPulse newer = new HeldSweepPulse(field, SCRY_RANGE, TINT);
            older.heard(SCRY_RANGE, HEARD_AT);
            newer.heard(BLOCKS_A_TICK, HEARD_AT);
            Map<Integer, HeldSweepPulse> live = new LinkedHashMap<>();
            live.put(1, older);
            live.put(2, newer);

            HeldSweepVisual.advanceAll(live, HEARD_AT + 1, OVER_A_FLOOR, CUBES);

            assertEquals(HeldSweepVisual.MOST_BLOCKS, older.lit().size(), "the older front lit its most, far out");
            assertTrue(!newer.lit().isEmpty(), "the newer front still lights the floor beside the caster");
        }

        /**
         * A wall two fronts lit draws once, at the brighter of its two glows,
         * so a later pulse re-lights it rather than stacking brighter (the
         * operator saw walls brighten with each pulse past the first).
         */
        @Test
        void aWallTwoFrontsLitDrawsOnceAtItsBrighterGlow() {
            EchoField field = new EchoField(CENTER);
            HeldSweepPulse first = new HeldSweepPulse(field, SCRY_RANGE, TINT);
            HeldSweepPulse second = new HeldSweepPulse(field, SCRY_RANGE, TINT);
            first.heard(4 * BLOCKS_A_TICK, HEARD_AT);
            first.advance(HEARD_AT + 1, OVER_A_FLOOR, CUBES, ROOM);
            second.heard(4 * BLOCKS_A_TICK, HEARD_AT + 10);
            second.advance(HEARD_AT + 11, OVER_A_FLOOR, CUBES, ROOM);

            List<HeldSweepVisual.WallGlow> walls = HeldSweepVisual.wallsToDraw(List.of(first, second), HEARD_AT + 11);

            assertEquals(walls.stream().map(wall -> wall.face().pos()).distinct().count(), walls.size(),
                    "each wall draws once");
            assertTrue(walls.stream().allMatch(wall -> wall.glow() == 1f), "at the fresh hit's glow, not dimmer");
        }

        /** The pulse holds lit walls, the farthest within one tick's travel inside its front. */
        private void assertFront(HeldSweepPulse pulse, double frame) {
            double front = pulse.drawnRadius(frame);
            assertTrue(!pulse.lit().isEmpty(), "the pulse lights the floor it crossed");
            double farthest = pulse.lit().stream().mapToDouble(lit -> lit.face().distance()).max().orElse(-1);
            assertTrue(farthest <= front && farthest > front - BLOCKS_A_TICK,
                    "the farthest lit wall " + farthest + " stands at the front " + front);
        }
    }

    @Test
    void aSlabsTopShowsAtItsHalfHeight() {
        List<RecordingVertexConsumer.Vertex> quad = emit(BOTTOM_SLAB, Direction.UP);

        assertEquals(4, quad.size());
        for (RecordingVertexConsumer.Vertex v : quad) {
            assertEquals(0.5f, v.y(), TOLERANCE);
        }
    }

    @Test
    void aFencePostsSideSpansThePostNotTheCube() {
        List<RecordingVertexConsumer.Vertex> quad = emit(FENCE_POST, Direction.EAST);

        for (RecordingVertexConsumer.Vertex v : quad) {
            assertEquals(0.625f, v.x(), TOLERANCE);
            assertEquals(true, v.z() >= 0.375f - TOLERANCE && v.z() <= 0.625f + TOLERANCE, "z within the post");
        }
        assertEquals(1.5f, quad.stream().map(RecordingVertexConsumer.Vertex::y).max(Float::compare).orElseThrow(),
                TOLERANCE);
    }

    private static List<RecordingVertexConsumer.Vertex> emit(AABB box, Direction side) {
        RecordingVertexConsumer consumer = new RecordingVertexConsumer();
        HeldSweepVisual.emitBoxFace(new PoseStack().last(), consumer, box, side, 0xFFFFE628);
        return consumer.vertices();
    }

    /** Scry's look reads in the astral type's color, resolved from the bundled astral and glow JSON. */
    @Nested
    class TintFromTheCastingType {

        private static final String BUNDLED_JSON_FORMAT = "/data/goo/goo/goo_type/%s.json";

        @Test
        void anAstralPingDrawsInTheAstralTypesColorNotGlowYellow() throws IOException {
            ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor = bundledColors();

            int tint = HeldSweepVisual.tint(GooTypes.ASTRAL, typeColor);

            assertEquals(GooColors.get(bundled(GooTypes.ASTRAL)), tint);
            assertNotEquals(GooColors.get(bundled(GooTypes.GLOW)), tint);
        }

        @Test
        void scrysGlistenDrawsInTheAstralTypesColorNotGlowYellow() throws IOException {
            ToIntFunction<ResourceKey<GooTypeDefinition>> typeColor = bundledColors();

            int glisten = AilmentKind.ASTRAL.rgb(typeColor);

            assertEquals(GooColors.get(bundled(GooTypes.ASTRAL)), glisten);
            assertNotEquals(AilmentKind.GLOW.rgb(typeColor), glisten);
        }

        private ToIntFunction<ResourceKey<GooTypeDefinition>> bundledColors() throws IOException {
            Map<ResourceKey<GooTypeDefinition>, GooTypeDefinition> types = Map.of(
                    GooTypes.ASTRAL, bundled(GooTypes.ASTRAL), GooTypes.GLOW, bundled(GooTypes.GLOW));
            return key -> GooColors.get(types.get(key));
        }

        private GooTypeDefinition bundled(ResourceKey<GooTypeDefinition> key) throws IOException {
            String path = BUNDLED_JSON_FORMAT.formatted(key.identifier().getPath());
            try (InputStream in = HeldSweepVisualTest.class.getResourceAsStream(path)) {
                assertNotNull(in, "Bundled JSON missing on classpath: " + path);
                return GooTypeDefinition.CODEC.parse(JsonOps.INSTANCE,
                        JsonParser.parseReader(new InputStreamReader(in, StandardCharsets.UTF_8))).getOrThrow();
            }
        }
    }
}
