package com.mercuriusxeno.goo.block.alientech;

import net.minecraft.core.Direction;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A click on the metronome lands on the gap switch or the button by where it hits the plate: a click on the switch
 * torch's front face, on the plate's center line, steps the cadence rather than pressing the button
 * (operator ruling 2026-11-28).
 */
class MetronomePartTest {

    private static final double MIDDLE = 0.5;
    /** The switch torch's front face at its first step, on the center line, for a plate whose back faces south. */
    private static final double TORCH_FRONT_FACE = 8.0 / 16;
    /** The middle of the button's top, for the same plate. */
    private static final double BUTTON_TOP = 4.0 / 16;

    @Test
    void aClickOnTheSwitchTorchsFrontFaceStepsTheCadence() {
        assertEquals(MetronomePart.GAP_SWITCH, MetronomePart.at(Direction.SOUTH, MIDDLE, TORCH_FRONT_FACE));
    }

    @Test
    void aClickOnTheButtonPressesIt() {
        assertEquals(MetronomePart.BUTTON, MetronomePart.at(Direction.SOUTH, MIDDLE, BUTTON_TOP));
    }
}
