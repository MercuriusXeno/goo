package com.mercuriusxeno.goo.client;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.HashSet;
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

    /** Meteo's rock writes depth, so its near faces hide its far ones rather than drawing back to front. */
    @Test
    void meteorsRockWritesDepth() {
        assertTrue(GooRenderTypes.METEOR_ROCK.getDepthStencilState().writeDepth());
    }
}
