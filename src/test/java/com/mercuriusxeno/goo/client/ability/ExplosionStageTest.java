package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.client.overlay.GooTargetHighlighter;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.junit.jupiter.api.Test;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The explosions, the burnouts a landing plays and the held ghost a charge
 * previews, draw once the translucent features have, so a prism's crystal,
 * a translucent feature writing depth, draws under the explosion standing in
 * front of it rather than over it.
 */
class ExplosionStageTest {

    private static List<Class<?>> stagesOf(Class<?> renderer) {
        return Arrays.stream(renderer.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(SubscribeEvent.class))
                .map(Method::getParameterTypes).filter(types -> types.length == 1)
                .<Class<?>>map(types -> types[0]).filter(RenderLevelStageEvent.class::isAssignableFrom).toList();
    }

    @Test
    void theBurnoutsDrawAfterTheTranslucentFeatures() {
        assertEquals(List.of(RenderLevelStageEvent.AfterTranslucentFeatures.class),
                stagesOf(ChainBurnoutRenderer.class), "the burnouts draw at " + stagesOf(ChainBurnoutRenderer.class));
    }

    @Test
    void theHeldGhostDrawsAfterTheTranslucentFeatures() {
        List<Class<?>> stages = stagesOf(GooTargetHighlighter.class);
        assertEquals(1, stages.stream().filter(RenderLevelStageEvent.AfterTranslucentFeatures.class::equals).count(),
                "the highlighter draws at " + stages + ", none after the translucent features for the held ghost");
    }
}
