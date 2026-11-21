package com.mercuriusxeno.goo.client;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.stencil.StencilOperation;
import net.neoforged.neoforge.client.stencil.StencilPerFaceTest;
import net.neoforged.neoforge.client.stencil.StencilTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Every pipeline GooRenderTypes declares is registered with NeoForge, since
 * a render type over an unregistered pipeline draws nothing in game: Meteo's
 * scorch shipped unregistered and never showed.
 * decision meteo-winds-up-then-drops-in-a-second-trailing-fire
 */
class GooRenderTypesTest {

    @Test
    void everyDeclaredPipelineIsRegistered() throws IllegalAccessException {
        RegisterRenderPipelinesEvent event = mock(RegisterRenderPipelinesEvent.class);
        ArgumentCaptor<RenderPipeline> registered = ArgumentCaptor.forClass(RenderPipeline.class);

        GooRenderTypes.registerPipelines(event);

        verify(event, atLeastOnce()).registerPipeline(registered.capture());
        Set<String> declared = new HashSet<>();
        for (Field field : GooRenderTypes.class.getDeclaredFields()) {
            if (Modifier.isStatic(field.getModifiers()) && RenderPipeline.class.isAssignableFrom(field.getType())) {
                field.setAccessible(true);
                declared.add(((RenderPipeline) field.get(null)).getLocation().toString());
            }
        }
        Set<String> unregistered = declared.stream()
                .filter(location -> registered.getAllValues().stream().map(p -> p.getLocation().toString())
                        .noneMatch(location::equals))
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(Set.of(), unregistered, "declared but never registered");
    }

    /**
     * Dowsing's window: the mask pass marks the Dowsing stencil bit only
     * where the goo is the nearest surface, writing no color, and the ore and
     * glow passes draw only on that bit, while Glimmer's x-ray and Sculker's
     * shroom vision keep drawing through everything.
     * dowsing-goo-is-a-window-onto-the-ore-behind-it
     */
    /**
     * A hole's mouth opens inside the opaque stage's open pass, where Minecraft 26.3 takes no depth copy: the mark
     * sets the hole's stencil bit wherever the oval is the nearest thing seen, and the carve opens the depth on that
     * bit alone and clears it, reading no copy of the world's depth.
     * decision hole-items-are-a-three-dimensional-soup-sunk-in-the-hole
     * decision minecraft-26-3-update-is-a-port-to-parity
     */
    @Test
    void aHolesMarkSetsItsBitWhereTheOvalIsNearestAndTheCarveOpensThatBitAlone() {
        RenderPipeline mark = GooRenderTypes.PORTABLE_HOLE_MARK;
        RenderPipeline carve = GooRenderTypes.PORTABLE_HOLE_CARVE;
        int bit = GooRenderTypes.PORTABLE_HOLE_STENCIL_BIT;
        StencilTest marks = new StencilTest(new StencilPerFaceTest(StencilOperation.KEEP, StencilOperation.KEEP,
                StencilOperation.REPLACE, CompareOp.ALWAYS_PASS), bit, bit, bit);
        StencilTest opensTheMark = new StencilTest(new StencilPerFaceTest(StencilOperation.KEEP,
                StencilOperation.KEEP, StencilOperation.ZERO, CompareOp.EQUAL), bit, bit, bit);

        assertEquals(List.of(CompareOp.GREATER_THAN_OR_EQUAL, false, ColorTargetState.WRITE_NONE, Optional.of(marks)),
                List.of(mark.getDepthStencilState().depthTest(), mark.getDepthStencilState().writeDepth(),
                        PipelineReads.colorTarget(mark).writeMask(), PipelineReads.stencilTest(mark)));
        assertEquals(List.of(CompareOp.ALWAYS_PASS, true, Optional.of(opensTheMark), List.of()),
                List.of(carve.getDepthStencilState().depthTest(), carve.getDepthStencilState().writeDepth(),
                        PipelineReads.stencilTest(carve), PipelineReads.samplers(carve.getBindGroupLayouts())));
        assertEquals(PipelineReads.vertexShader(carve), PipelineReads.vertexShader(mark),
                "the mark rasterizes the very pixels the carve does");
    }

    @Test
    void dowsingsMaskMarksTheNearestGooAndItsOreDrawsOnlyThere() {
        RenderPipeline mask = GooRenderTypes.DOWSING_MASK;
        StencilTest marks = PipelineReads.stencilTest(mask).orElseThrow();
        StencilTest onTheMark = new StencilTest(
                new StencilPerFaceTest(StencilOperation.KEEP, StencilOperation.KEEP, StencilOperation.KEEP,
                        CompareOp.EQUAL), marks.writeMask(), 0, marks.referenceValue());

        assertEquals(List.of(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false).depthTest(), false,
                        ColorTargetState.WRITE_NONE, CompareOp.ALWAYS_PASS, StencilOperation.REPLACE),
                List.of(mask.getDepthStencilState().depthTest(), mask.getDepthStencilState().writeDepth(),
                        PipelineReads.colorTarget(mask).writeMask(), marks.front().compare(), marks.front().pass()));
        assertEquals(List.of(Optional.of(onTheMark), Optional.of(onTheMark)),
                List.of(PipelineReads.stencilTest(GooRenderTypes.DOWSING_ORE), PipelineReads.stencilTest(GooRenderTypes.DOWSING_GLOW)));
    }

    /**
     * Glimmer's x-ray and Sculker's shroom vision draw through every block, untouched by
     * Dowsing's window.
     * dowsing-goo-is-a-window-onto-the-ore-behind-it
     */
    @Test
    void theFungusXrayAndGlowStillDrawThroughEverything() {
        DepthStencilState throughEverything = new DepthStencilState(CompareOp.ALWAYS_PASS, false);

        assertEquals(List.of(throughEverything, Optional.empty(), throughEverything, Optional.empty()),
                List.of(GooRenderTypes.FUNGUS_XRAY.getDepthStencilState(), PipelineReads.stencilTest(GooRenderTypes.FUNGUS_XRAY),
                        GooRenderTypes.FUNGUS_GLOW.getDepthStencilState(), PipelineReads.stencilTest(GooRenderTypes.FUNGUS_GLOW)));
    }

    /**
     * Every translucent pipeline GooRenderTypes registers records its sort
     * route beside it, so a new one states how it sorts against the clouds,
     * the weather, water, glass and other goo visuals as it is written.
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     */
    @Test
    void everyTranslucentPipelineRecordsItsSortRoute() {
        RegisterRenderPipelinesEvent event = mock(RegisterRenderPipelinesEvent.class);
        ArgumentCaptor<RenderPipeline> registered = ArgumentCaptor.forClass(RenderPipeline.class);

        GooRenderTypes.registerPipelines(event);

        verify(event, atLeastOnce()).registerPipeline(registered.capture());
        Set<String> unrecorded = registered.getAllValues().stream()
                .filter(pipeline -> PipelineReads.colorTarget(pipeline).blendFunction().isPresent())
                .filter(pipeline -> GooRenderTypes.sortOf(pipeline).isEmpty())
                .map(pipeline -> pipeline.getLocation().toString())
                .collect(Collectors.toCollection(TreeSet::new));
        assertEquals(Set.of(), unrecorded, "translucent pipelines recording no sort route");
    }

    /**
     * Laser's scorch draws its char translucent, darkening the face, and its
     * glow in a pass of its own added onto the face, the block showing through.
     * decision sunbeam-leaves-a-glowing-scorch-that-trails-and-fades
     */
    @Test
    void lasersScorchAddsItsGlowOntoTheFace() {
        assertEquals(Optional.of(BlendFunction.TRANSLUCENT),
                PipelineReads.colorTarget(GooRenderTypes.LASER_SCORCH).blendFunction());
        assertEquals(Optional.of(BlendFunction.ADDITIVE),
                PipelineReads.colorTarget(GooRenderTypes.LASER_SCORCH_GLOW).blendFunction());
    }

    /**
     * A glow prism's column depth tests and writes no depth, so a beam drawn
     * after it, Laser's ray among them, shows in front of it.
     * decision diagnose-and-correct-the-sunbeam-prism-depth
     */
    @Test
    void theGlowColumnTestsDepthAndWritesNone() {
        assertEquals(new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false),
                GooRenderTypes.GLOW_COLUMN.getDepthStencilState());
    }

    @Test
    void theGlowColumnTypeDrawsOnTheGlowColumnPipeline() {
        assertEquals(GooRenderTypes.GLOW_COLUMN, GooRenderTypes.GLOW_COLUMN_TYPE.pipeline());
    }

    /** Meteo's rock writes depth, so its near faces hide its far ones rather than drawing back to front. */
    @Test
    void meteorsRockWritesDepth() {
        assertTrue(GooRenderTypes.METEOR_ROCK.getDepthStencilState().writeDepth());
    }
}
