package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.ability.program.DetectOreStep;
import com.mercuriusxeno.goo.ability.program.HeldSweepPulses;
import com.mercuriusxeno.goo.ability.program.HeldSweepSections;
import com.mercuriusxeno.goo.ability.program.HeldSweepWorld;
import com.mercuriusxeno.goo.ability.program.ScryStep;
import com.mercuriusxeno.goo.ability.program.SectionRecorder;
import com.mercuriusxeno.goo.client.CountingVertexConsumer;
import com.mercuriusxeno.goo.client.overlay.OreXray;
import com.mercuriusxeno.goo.type.GooTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.minecraft.tags.TagKey;
import net.minecraft.util.profiling.Profiler;
import net.minecraft.world.phys.Vec3;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Each part of a held sweep's work, run once through its own seam under a
 * recording profiler, reports under a profiler section of its own and
 * closes it, so a profile of Scry, Glimmer or Magnet names the part a
 * frame or a tick went to.
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 * decision diagnose-and-correct-scry-lag-building-over-a-long-cast
 */
class HeldSweepSectionsTest {

    private static final double ONE_TICK_OUT = 2.4;

    private final SectionRecorder recorder = SectionRecorder.untimed();
    private final CountingVertexConsumer consumer = new CountingVertexConsumer();
    private final PoseStack poseStack = new PoseStack();

    private List<String> sectionsOf(Runnable part) {
        try (Profiler.Scope scope = Profiler.use(recorder)) {
            part.run();
        }
        assertTrue(recorder.balanced(), "every section pushed is popped");
        return recorder.pushed();
    }

    /** The caster's client, each frame. */
    @Nested
    class OnTheClient {

        @Test
        void frontUpkeepReportsUnderItsOwnSection() {
            HeldSweepVisual.Upkeep upkeep = new HeldSweepVisual.Upkeep(new LiveSweeps(),
                    CubeWalls.of(HeldSweepWorld::isAir));

            assertEquals(List.of(HeldSweepSections.FRONT_UPKEEP),
                    sectionsOf(() -> HeldSweepVisual.upkeepFronts(upkeep, 0, List.of(), null)));
        }

        @Test
        void wallListReportsUnderItsOwnSection() {
            assertEquals(List.of(HeldSweepSections.WALL_LIST),
                    sectionsOf(() -> HeldSweepVisual.wallsToDraw(new LitWalls(), 0)));
        }

        @Test
        void sortKeyReportsUnderItsOwnSection() {
            assertEquals(List.of(HeldSweepSections.SORT_KEY),
                    sectionsOf(() -> HeldSweepVisual.sortedByNearest(Stream.of(Vec3.ZERO), Vec3.ZERO, () -> { })));
        }

        @Test
        void wallDrawReportsUnderItsOwnSection() {
            assertEquals(List.of(HeldSweepSections.WALL_DRAW), sectionsOf(() ->
                    HeldSweepVisual.drawWalls(poseStack, consumer.pass(), Vec3.ZERO, List.of(), pos -> List.of())));
        }

        @Test
        void frontDrawReportsUnderItsOwnSection() {
            assertEquals(List.of(HeldSweepSections.FRONT_DRAW), sectionsOf(() ->
                    HeldSweepVisual.drawFronts(poseStack.last(), consumer.pass(), Vec3.ZERO, List.of(), 0)));
        }

        @Test
        void oreListReportsUnderItsOwnSection() {
            assertEquals(List.of(HeldSweepSections.ORE_LIST), sectionsOf(() -> OreXray.showingAt(0)));
        }

        @Test
        void oreSortKeyReportsUnderTheSortSection() {
            assertEquals(List.of(HeldSweepSections.SORT_KEY),
                    sectionsOf(() -> OreXray.sortedByNearest(List.of(), Vec3.ZERO, () -> { })));
        }

        @Test
        void oreDrawReportsUnderItsOwnSection() {
            assertEquals(List.of(HeldSweepSections.ORE_DRAW), sectionsOf(() ->
                    OreXray.drawOre(pos -> List.of(), poseStack, Vec3.ZERO, List.of(), gooType -> 0,
                            pass -> consumer.pass())));
        }
    }

    /** The server, each held tick. */
    @Nested
    class OnTheServer {

        private final HeldSweepWorld world = new HeldSweepWorld();

        @Test
        void scryStrikeReportsUnderItsOwnSection() {
            ScryStep.Strike strike = new ScryStep.Strike(world.scanAround(HeldSweepWorld.CASTER), Set.of(),
                    HeldSweepWorld.CASTER, HeldSweepWorld.CASTER);

            assertEquals(List.of(HeldSweepSections.SCRY_STRIKE),
                    sectionsOf(() -> ScryStep.strikeCrossed(strike, 0, ONE_TICK_OUT, struck -> { })));
        }

        @Test
        void oreWalkReportsUnderItsOwnSection() {
            DetectOreStep ore = oreSense();

            assertEquals(List.of(HeldSweepSections.ORE_WALK),
                    sectionsOf(() -> ore.revealAt(HeldSweepWorld::oreAt, HeldSweepWorld.CASTER, 1)));
        }

        @Test
        void payloadSendsReportUnderTheirOwnSection() {
            List<HeldSweepPulses.Pulse> live = List.of(new HeldSweepPulses.Pulse(1, HeldSweepWorld.CASTER, 0));

            assertEquals(List.of(HeldSweepSections.PAYLOAD_SENDS),
                    sectionsOf(() -> oreSense().sendSweeps(live, 1, payload -> { })));
        }

        private DetectOreStep oreSense() {
            return new DetectOreStep(TagKey.create(Registries.BLOCK, Identifier.fromNamespaceAndPath("goo", "gem_ores")),
                    GooTypes.CRYSTAL, HeldSweepHold.ORE_RADIUS, 1);
        }
    }
}
