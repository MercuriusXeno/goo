package com.mercuriusxeno.goo.client.throwing;

import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.Delivery;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The metal flight's morph into the dart follows the share of the flight
 * the ability's delivery names, so the dart's timing is ability data and a
 * different share moves it (decisions traveling-form-transforms-in-flight,
 * dart-transform-point-is-ability-data).
 */
class GooFlightRendererTest {

    private static final float TOLERANCE = 1e-5f;
    private static final float LONG_THROW = 12f;
    private static final float SHORT_THROW = 1f;

    @Test
    void theJavelinsDeliveryNamesItsTransformPointEarlierThanTheDefault() {
        Delivery javelin = AbilityJson.decode("metal_javelin").delivery();

        assertTrue(javelin.transformAt() < Delivery.DEFAULT_TRANSFORM_AT,
                "the javelin takes its dart form no earlier than the default " + javelin.transformAt());
    }

    @Test
    void theMorphIsWholeAtTheTransformPointTheDeliveryNames() {
        float transformAt = (float) AbilityJson.decode("metal_javelin").delivery().transformAt();

        assertEquals(0f, GooFlightRenderer.morphFraction(0f, LONG_THROW, transformAt), 0f);
        assertEquals(0.5f, GooFlightRenderer.morphFraction(transformAt / 2, LONG_THROW, transformAt), TOLERANCE);
        assertEquals(1f, GooFlightRenderer.morphFraction(transformAt, LONG_THROW, transformAt), TOLERANCE);
        assertEquals(1f, GooFlightRenderer.morphFraction(1f, LONG_THROW, transformAt), 0f);
    }

    @Test
    void aLaterTransformPointMovesTheMorphLater() {
        float progress = 0.1f;

        assertEquals(1f, GooFlightRenderer.morphFraction(progress, LONG_THROW, 0.1f), TOLERANCE);
        assertEquals(0.25f, GooFlightRenderer.morphFraction(progress, LONG_THROW, 0.4f), TOLERANCE);
    }

    @Test
    void aShortThrowFliesAsTheDartFromTheStart() {
        assertEquals(1f, GooFlightRenderer.morphFraction(0f, SHORT_THROW, (float) Delivery.DEFAULT_TRANSFORM_AT), 0f);
    }
}
