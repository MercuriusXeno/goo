package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import com.mercuriusxeno.goo.client.TranslucentVisualPath.Registration;
import com.mercuriusxeno.goo.client.ability.MiasmaVortex;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.RenderLevelStageEvent;
import org.junit.jupiter.api.Test;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The shared path translucent goo visuals draw through: the stage it draws
 * at, the order it draws in, and the visuals registered with it.
 */
class TranslucentVisualPathTest {

    private static final Runnable NO_DRAW = () -> { };

    /**
     * LevelRenderer.renderLevel adds the clouds pass, the weather pass and
     * the Fabulous blend after the main pass, and GameRenderer posts
     * AfterLevel once renderLevel returns, so a visual drawn there has the
     * clouds and weather already behind it.
     * decision diagnose-and-correct-translucent-goo-visuals-that-depth-sort-wrong
     */
    @Test
    void drawsAfterTheCloudsAndTheWeather() {
        List<Class<?>> stages = Arrays.stream(TranslucentVisualPath.class.getDeclaredMethods())
                .filter(method -> method.isAnnotationPresent(SubscribeEvent.class))
                .map(method -> method.getParameterTypes()[0]).toList();
        assertEquals(List.of(RenderLevelStageEvent.AfterLevel.class), stages);
    }

    /**
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     */
    @Test
    void answersDrawsFarthestFromTheCameraFirst() {
        SortedDraw near = new SortedDraw(4, NO_DRAW);
        SortedDraw far = new SortedDraw(900, NO_DRAW);
        SortedDraw middle = new SortedDraw(100, NO_DRAW);
        assertEquals(List.of(far, middle, near), TranslucentVisualPath.farToNear(List.of(near, far, middle)));
    }

    /**
     * decision translucent-goo-visuals-draw-through-one-checked-sorting-path
     */
    @Test
    void drainsVortexDrawsFarToNear() {
        assertTrue(TranslucentVisualPath.REGISTRATIONS.stream()
                        .map(Registration::visual).anyMatch(MiasmaVortex.class::equals),
                "MiasmaVortex registers with the path");
        assertEquals(List.of(SortRoute.FAR_TO_NEAR), TranslucentVisualPath.REGISTRATIONS.stream()
                .filter(registration -> registration.visual() == MiasmaVortex.class)
                .map(Registration::route).toList());
    }
}
