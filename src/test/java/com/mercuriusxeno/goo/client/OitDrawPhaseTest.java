package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.oit.OitStage;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Where the sorting path's far-to-near draws go while improved transparency is on: a draw whose render type carries
 * an OIT set draws in each OIT stage through that set's pipeline for the stage and is left out of AfterLevel, a draw
 * whose type carries none draws at AfterLevel on its parity pipeline, and with improved transparency off every draw
 * draws on its parity pipeline as before.
 * decision goo-translucency-draws-through-order-independent-transparency
 */
class OitDrawPhaseTest {

    private static final RenderPipeline PARITY = GooRenderTypes.GLOW_SHELL;
    private static final OitPipelineSet SET = GooRenderTypes.GLOW_SHELL_OIT;

    @Nested
    class UnderImprovedTransparency {

        @Test
        void aSetCarryingDrawDrawsInEachOitStageThroughThatStagesPipeline() {
            assertEquals(Arrays.stream(OitStage.values()).map(SET::getPipeline).toList(),
                    Arrays.stream(OitStage.values())
                            .map(stage -> OitDrawPhase.pipelineFor(OitDrawPhase.inTheOitStage(stage), PARITY, SET))
                            .toList());
        }

        @Test
        void aSetCarryingDrawIsLeftOutOfAfterLevel() {
            assertNull(OitDrawPhase.pipelineFor(OitDrawPhase.AFTER_THE_OIT_PHASE, PARITY, SET));
        }

        @Test
        void aDrawCarryingNoSetDrawsAtAfterLevelOnItsParityPipelineAlone() {
            List<RenderPipeline> inTheStages = new ArrayList<>();
            for (OitStage stage : OitStage.values()) {
                inTheStages.add(OitDrawPhase.pipelineFor(OitDrawPhase.inTheOitStage(stage), PARITY, null));
            }
            assertEquals(List.of(Arrays.asList(null, null, null), PARITY),
                    List.of(inTheStages, OitDrawPhase.pipelineFor(OitDrawPhase.AFTER_THE_OIT_PHASE, PARITY, null)));
        }

        /** The path collects its draws once a level pass, at the first stage, and draws them in every stage. */
        @Test
        void thePathCollectsOnceAndDrawsInEveryStage() {
            OitDrawReplay replay = new OitDrawReplay();
            List<SortedDraw> collected = List.of(new SortedDraw(1, () -> { }));
            int[] collects = {0};
            List<List<SortedDraw>> drawn = new ArrayList<>();

            for (OitStage stage : OitStage.values()) {
                drawn.add(replay.atOitStage(stage, () -> {
                    collects[0]++;
                    return collected;
                }));
            }

            assertEquals(List.of(1, List.of(collected, collected, collected), collected),
                    List.of(collects[0], drawn, replay.takeHeld()));
            assertNull(replay.takeHeld(), "AfterLevel takes the held draws once");
        }
    }

    @Test
    void withImprovedTransparencyOffEveryDrawDrawsOnItsParityPipeline() {
        assertEquals(List.of(PARITY, PARITY), List.of(
                OitDrawPhase.pipelineFor(OitDrawPhase.LEVEL, PARITY, SET),
                OitDrawPhase.pipelineFor(OitDrawPhase.LEVEL, PARITY, null)));
    }
}
