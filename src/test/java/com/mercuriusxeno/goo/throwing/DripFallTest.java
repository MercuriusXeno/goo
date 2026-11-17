package com.mercuriusxeno.goo.throwing;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A fall's ticks for its height are the ticks a drip stepped from rest under
 * DripFall's gravity and drag takes to drop that far, as the client's falling
 * drip steps, so the server's arrival and the drawn drip's landing fall on
 * one tick (decision falling-layer-lands-when-its-drip-arrives).
 */
class DripFallTest {

    @ParameterizedTest
    @ValueSource(doubles = {1.0, 3.0, 10.0})
    void aFallsTicksAreTheStepsADripTakesToDropItsHeight(double height) {
        double velocity = 0;
        double fallen = 0;
        int steps = 0;
        while (fallen < height) {
            velocity -= DripFall.GRAVITY;
            fallen -= velocity;
            velocity *= DripFall.DRAG;
            steps++;
        }

        assertEquals(steps, DripFall.ticksToFall(height), "a fall of " + height + " blocks");
    }
}
