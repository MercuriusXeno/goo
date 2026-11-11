package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.pipeline.BlendFunction;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
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
     * Sunbeam's scorch draws its char translucent, darkening the face, and its
     * glow in a pass of its own added onto the face, the block showing through.
     * decision sunbeam-leaves-a-glowing-scorch-that-trails-and-fades
     */
    @Test
    void sunbeamsScorchAddsItsGlowOntoTheFace() {
        assertEquals(Optional.of(BlendFunction.TRANSLUCENT),
                GooRenderTypes.SUNBEAM_SCORCH.getColorTargetState().blendFunction());
        assertEquals(Optional.of(BlendFunction.ADDITIVE),
                GooRenderTypes.SUNBEAM_SCORCH_GLOW.getColorTargetState().blendFunction());
    }

    /**
     * A glow prism's column depth tests and writes no depth, so a beam drawn
     * after it, Sunbeam's ray among them, shows in front of it.
     * decision diagnose-and-correct-the-sunbeam-prism-depth
     */
    @Test
    void theGlowColumnTestsDepthAndWritesNone() {
        assertEquals(new DepthStencilState(CompareOp.LESS_THAN_OR_EQUAL, false),
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
