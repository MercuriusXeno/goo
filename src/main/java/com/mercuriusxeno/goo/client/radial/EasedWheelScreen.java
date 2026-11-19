package com.mercuriusxeno.goo.client.radial;

import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

/**
 * A wheel screen open while the glove menu key is held: the cursor and the scroll steer its wheel, the client tick
 * eases its petals, and the game runs on beneath it. The glove's wheel and the sprayer's share this input.
 * decision petal-moves-animate
 * decision sprayer-wheel-picks-nozzle-flow-and-goo-type
 */
abstract class EasedWheelScreen extends Screen {

    /** Halves the screen's size to its center. */
    static final int HALF = 2;

    EasedWheelScreen() {
        super(Component.empty());
    }

    /**
     * @return the wheel the screen steers
     */
    abstract RadialWheel wheel();

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    /**
     * @return the wheel's outer radius at the screen's size
     */
    final int radius() {
        return (int) RadialWheel.outerRadius(width, height);
    }

    @Override
    public void mouseMoved(double x, double y) {
        wheel().moveCursor(x - width / (double) HALF, y - height / (double) HALF, radius());
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        wheel().scroll(scrollY);
        return true;
    }

    /**
     * Advances the petals' ease on the client tick.
     * decision petal-moves-animate
     */
    @Override
    public void tick() {
        super.tick();
        wheel().tick();
    }
}
