package com.mercuriusxeno.goo.client;

import com.mojang.renderpearl.api.pipeline.BlendFunction;
import com.mojang.renderpearl.api.pipeline.ColorTargetState;
import com.mojang.renderpearl.api.pipeline.CompareOp;
import com.mojang.renderpearl.api.pipeline.DepthStencilState;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.client.renderer.oit.OitPipelineSet;
import net.minecraft.client.renderer.oit.OitStage;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.stencil.StencilOperation;
import net.neoforged.neoforge.client.stencil.StencilPerFaceTest;
import net.neoforged.neoforge.client.stencil.StencilTest;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;
import java.util.stream.Collectors;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
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

    private static final String PIPELINE_PREFIX = "pipeline/";
    private static final String OIT_ACCUMULATE_PREFIX = "pipeline/oit_accumulate_";

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

    /**
     * The goo fluid surface draws through 26.3's order-independent transparency under improved transparency: its
     * render type carries a registered OIT set whose three phases run the fluid surface's own shaders with every
     * sampler they read, and its fragment shader takes the OIT stages from minecraft:oit.glsl; with transparency
     * off it keeps drawing on its translucent parity pipeline.
     * decision goo-translucency-draws-through-order-independent-transparency
     */
    @Test
    void theFluidSurfaceDrawsThroughOrderIndependentTransparency() throws IOException {
        RenderType surface = GooRenderTypes.gooFluidSurface(Identifier.withDefaultNamespace("textures/atlas/blocks.png"));
        OitPipelineSet oit = PipelineReads.oitPipelines(surface);
        assertNotNull(oit, "the fluid surface's render type carries no OIT pipeline set");
        RegisterRenderPipelinesEvent event = mock(RegisterRenderPipelinesEvent.class);
        GooRenderTypes.registerPipelines(event);
        Identifier shader = PipelineReads.vertexShader(GooRenderTypes.GOO_FLUID_SURFACE);

        verify(event).registerOitPipelineSet(oit);
        for (OitStage stage : OitStage.values()) {
            RenderPipeline phase = oit.getPipeline(stage);
            assertEquals(List.of(true, shader, shader, true),
                    List.of(phase.getShaderDefines().flags().contains("OIT_" + stage.name()),
                            PipelineReads.vertexShader(phase), PipelineReads.fragmentShader(phase),
                            PipelineReads.samplers(phase.getBindGroupLayouts()).containsAll(List.of("Sampler0", "MingleTable"))),
                    stage + " phase: its define, its vertex and fragment shaders, and its samplers");
        }
        assertEquals(List.of(GooRenderTypes.GOO_FLUID_SURFACE, Optional.of(BlendFunction.TRANSLUCENT)),
                List.of(surface.pipeline(), PipelineReads.colorTarget(surface.pipeline()).blendFunction()));
        try (InputStream in = GooRenderTypesTest.class.getResourceAsStream(
                "/assets/goo/shaders/core/goo_fluid_surface.fsh")) {
            assertNotNull(in, "goo_fluid_surface.fsh is not on the classpath");
            assertTrue(new String(in.readAllBytes(), StandardCharsets.UTF_8).contains("#include <minecraft:oit.glsl>"),
                    "goo_fluid_surface.fsh includes no minecraft:oit.glsl");
        }
    }

    /**
     * Every translucent goo type submitted through the frame's collector draws in 26.3's OIT phase under improved
     * transparency, where a type with no OIT set crashes the client (defect row 9): each carries a registered set
     * whose three phases run its own parity pipeline's shaders, and each of those fragment shaders takes the OIT
     * stages from minecraft:oit.glsl.
     * decision goo-translucency-draws-through-order-independent-transparency
     */
    @Test
    void everySubmittedTranslucentTypeDrawsThroughOrderIndependentTransparency() throws IOException {
        Identifier texture = Identifier.withDefaultNamespace("textures/atlas/blocks.png");
        List<RenderType> submitted = List.of(GooRenderTypes.METEOR_ROCK_TYPE, GooRenderTypes.CRYSTAL_SHARD_TYPE,
                GooRenderTypes.crucibleDissolve(texture), GooRenderTypes.gooMobCoat(texture),
                GooRenderTypes.PETRIFY_STONE_TYPE, GooRenderTypes.FROST_ICE_TYPE, GooRenderTypes.VINE_TANGLE_TYPE,
                GooRenderTypes.WEB_TANGLE_TYPE, GooRenderTypes.gooPhase(texture), GooRenderTypes.GOO_AILMENT_OVERLAY_TYPE);
        RegisterRenderPipelinesEvent event = mock(RegisterRenderPipelinesEvent.class);
        ArgumentCaptor<OitPipelineSet> registered = ArgumentCaptor.forClass(OitPipelineSet.class);
        GooRenderTypes.registerPipelines(event);
        verify(event, atLeastOnce()).registerOitPipelineSet(registered.capture());
        List<String> missing = new java.util.ArrayList<>();

        for (RenderType type : submitted) {
            OitPipelineSet oit = PipelineReads.oitPipelines(type);
            if (oit == null || !registered.getAllValues().contains(oit)) {
                missing.add(type.pipeline().getLocation() + " carries no registered OIT set");
                continue;
            }
            for (OitStage stage : OitStage.values()) {
                RenderPipeline phase = oit.getPipeline(stage);
                if (!PipelineReads.fragmentShader(phase).equals(PipelineReads.fragmentShader(type.pipeline()))
                        || !PipelineReads.vertexShader(phase).equals(PipelineReads.vertexShader(type.pipeline()))) {
                    missing.add(phase.getLocation() + " runs other shaders than its parity pipeline");
                }
            }
            if (!fragmentSource(type.pipeline()).contains("#include <minecraft:oit.glsl>")) {
                missing.add(PipelineReads.fragmentShader(type.pipeline()) + " includes no minecraft:oit.glsl");
            }
        }

        assertEquals(List.of(), missing);
    }

    /**
     * Every goo pipeline that blends in the level either draws through a registered OIT set of its own or stays on
     * its parity path with a reason; an immediately drawn pipeline counts by its stays entry, never by a set it does
     * not use, and the glow shell's set adds its light under OIT_ADDITIVE.
     * decision goo-translucency-draws-through-order-independent-transparency
     */
    @Test
    void everyBlendingPipelineDrawsThroughOrderIndependentTransparencyOrStaysWithAReason() {
        RegisterRenderPipelinesEvent event = mock(RegisterRenderPipelinesEvent.class);
        ArgumentCaptor<RenderPipeline> pipelines = ArgumentCaptor.forClass(RenderPipeline.class);
        ArgumentCaptor<OitPipelineSet> sets = ArgumentCaptor.forClass(OitPipelineSet.class);
        GooRenderTypes.registerPipelines(event);
        verify(event, atLeastOnce()).registerPipeline(pipelines.capture());
        verify(event, atLeastOnce()).registerOitPipelineSet(sets.capture());
        Set<String> withSets = sets.getAllValues().stream()
                .map(set -> set.accumulatePipeline().getLocation().getPath().substring(OIT_ACCUMULATE_PREFIX.length()))
                .collect(Collectors.toSet());

        Set<String> blending = pipelines.getAllValues().stream()
                .filter(pipeline -> PipelineReads.colorTarget(pipeline) != null
                        && PipelineReads.colorTarget(pipeline).blendFunction().isPresent())
                .map(pipeline -> pipeline.getLocation().getPath().substring(PIPELINE_PREFIX.length()))
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> unaccounted = blending.stream()
                .filter(name -> withSets.contains(name) == GooOitStays.REASONS.containsKey(name))
                .collect(Collectors.toCollection(TreeSet::new));
        Set<String> stale = GooOitStays.REASONS.keySet().stream().filter(name -> !blending.contains(name))
                .collect(Collectors.toCollection(TreeSet::new));

        assertEquals(List.of(Set.of(), Set.of()), List.of(unaccounted, stale),
                "blending pipelines with neither a set nor a stays reason, or both; then stays entries naming no blending pipeline");
    }

    /**
     * The beam, bolt and wind visuals' far-to-near pipelines draw in the OIT phase: each carries a registered set whose
     * phases run its own shaders, each fragment shader taking the OIT stages from minecraft:oit.glsl, an additive one
     * built with coverage under no OIT_ADDITIVE define while its parity pipeline keeps adding its light, and the
     * stays-on-parity list names none of them. FIRE_FIELD writes its own depth, so it stays where it draws.
     * decision goo-translucency-draws-through-order-independent-transparency
     * decision additive-glows-draw-as-translucent-layers-in-the-oit-phase
     */
    @Test
    void theBeamBoltAndWindPipelinesDrawInTheOitPhase() throws IOException {
        List<RenderPipeline> moved = List.of(GooRenderTypes.GLOW_BEAM, GooRenderTypes.LINES_ADDITIVE_GLOW,
                GooRenderTypes.GROWTH_BREEZE, GooRenderTypes.CRYSTAL_SHARD, GooRenderTypes.GLOW_SHELL,
                GooRenderTypes.GLOW_COLUMN);
        List<RenderPipeline> additive = List.of(GooRenderTypes.GLOW_BEAM, GooRenderTypes.LINES_ADDITIVE_GLOW,
                GooRenderTypes.GROWTH_BREEZE, GooRenderTypes.GLOW_SHELL);
        RegisterRenderPipelinesEvent event = mock(RegisterRenderPipelinesEvent.class);
        ArgumentCaptor<OitPipelineSet> sets = ArgumentCaptor.forClass(OitPipelineSet.class);
        GooRenderTypes.registerPipelines(event);
        verify(event, atLeastOnce()).registerOitPipelineSet(sets.capture());
        List<String> wrong = new java.util.ArrayList<>();

        for (RenderPipeline pipeline : moved) {
            String name = pipeline.getLocation().getPath().substring(PIPELINE_PREFIX.length());
            OitPipelineSet set = sets.getAllValues().stream().filter(candidate -> candidate.accumulatePipeline()
                    .getLocation().getPath().equals(OIT_ACCUMULATE_PREFIX + name)).findFirst().orElse(null);
            if (set == null || GooOitStays.REASONS.containsKey(name)) {
                wrong.add(name + " carries no registered set, or stays on parity");
                continue;
            }
            for (OitStage stage : OitStage.values()) {
                RenderPipeline phase = set.getPipeline(stage);
                if (!PipelineReads.fragmentShader(phase).equals(PipelineReads.fragmentShader(pipeline))
                        || !PipelineReads.vertexShader(phase).equals(PipelineReads.vertexShader(pipeline))
                        || phase.getShaderDefines().flags().contains("OIT_ADDITIVE")) {
                    wrong.add(phase.getLocation() + " runs other shaders than its parity pipeline, or adds");
                }
            }
            if (!fragmentSource(pipeline).contains("#include <minecraft:oit.glsl>")) {
                wrong.add(name + "'s fragment shader includes no minecraft:oit.glsl");
            }
        }
        additive.stream().filter(pipeline -> !PipelineReads.colorTarget(pipeline).blendFunction()
                        .equals(Optional.of(BlendFunction.LIGHTNING)))
                .forEach(pipeline -> wrong.add(pipeline.getLocation() + " no longer adds its light off the OIT phase"));

        assertEquals(List.of(), wrong);
        assertEquals(GooOitStays.WRITES_DEPTH, GooOitStays.REASONS.get("fire_field"));
    }

    /**
     * Under improved transparency the glow shell draws as a translucent layer with coverage, as vanilla's beacon
     * beam does, through a fragment shader that retunes its color toward its additive look, while with improved
     * transparency off it keeps adding its light.
     * decision additive-glows-draw-as-translucent-layers-in-the-oit-phase
     */
    @Test
    void theGlowShellDrawsAsATranslucentLayerInTheOitPhaseAndAddsItsLightOffIt() {
        Identifier retuned = Identifier.fromNamespaceAndPath("goo", "core/glow_shell");
        assertEquals(List.of(List.of(false, false, false), List.of(retuned, retuned, retuned),
                        Optional.of(BlendFunction.LIGHTNING)),
                List.of(Arrays.stream(OitStage.values()).map(stage -> GooRenderTypes.GLOW_SHELL_OIT.getPipeline(stage)
                                .getShaderDefines().flags().contains("OIT_ADDITIVE")).toList(),
                        Arrays.stream(OitStage.values()).map(stage -> PipelineReads.fragmentShader(
                                GooRenderTypes.GLOW_SHELL_OIT.getPipeline(stage))).toList(),
                        PipelineReads.colorTarget(GooRenderTypes.GLOW_SHELL).blendFunction()));
    }

    private static String fragmentSource(RenderPipeline pipeline) throws IOException {
        Identifier shader = PipelineReads.fragmentShader(pipeline);
        String path = "/assets/" + shader.getNamespace() + "/shaders/" + shader.getPath() + ".fsh";
        try (InputStream in = GooRenderTypesTest.class.getResourceAsStream(path)) {
            assertNotNull(in, path + " is not on the classpath");
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        }
    }

    /** Meteo's rock writes depth, so its near faces hide its far ones rather than drawing back to front. */
    @Test
    void meteorsRockWritesDepth() {
        assertTrue(GooRenderTypes.METEOR_ROCK.getDepthStencilState().writeDepth());
    }
}
