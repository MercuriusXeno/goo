package com.mercuriusxeno.goo.block.alientech;

import net.minecraft.core.Direction;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * The metronome's shape as its model draws it: the plate, the switch torch at its cadence's step and the button, up
 * or pressed, so a look aimed at the torch or the button lands on it rather than on the plate behind it. Its boxes
 * are the models' own, laid out with the back facing south, as the blockstate draws them unturned, and turned to the
 * back the metronome holds.
 * operator ruling 2026-11-28: the model is what a click hits
 */
final class MetronomeShape {

    private static final float CELL = 16f;
    private static final float PLATE_TOP = 2f;
    private static final float TORCH_LEFT = 7f;
    private static final float TORCH_RIGHT = 9f;
    private static final float TORCH_TOP = 7f;
    private static final float TORCH_DEPTH = 2f;
    /** Where the switch torch's front face stands at each cadence step, in pixels along the model's z. */
    private static final float[] TORCH_FRONT = {6f, 5f, 4f, 3f, 2f};
    private static final float BUTTON_LEFT = 6f;
    private static final float BUTTON_RIGHT = 10f;
    private static final float BUTTON_FRONT = 10f;
    private static final float BUTTON_BACK = 14f;
    private static final float BUTTON_TOP = 4f;
    private static final float BUTTON_PRESSED_TOP = 3f;
    private static final Map<Key, VoxelShape> SHAPES = new ConcurrentHashMap<>();
    private static final int WEST_TURNS = 1;
    private static final int NORTH_TURNS = 2;
    private static final int EAST_TURNS = 3;

    private MetronomeShape() {
    }

    /**
     * The metronome's shape for a back, a cadence step and the button's toggle.
     *
     * @param back     the side its input reads, its FACING
     * @param cadence  its cadence step, 1 to 4
     * @param inverted whether its button is pressed
     * @return the shape
     */
    static VoxelShape of(Direction back, int cadence, boolean inverted) {
        return SHAPES.computeIfAbsent(new Key(back, cadence, inverted), key -> {
            float torchFront = TORCH_FRONT[cadence - 1];
            return Shapes.or(Block.box(0, 0, 0, CELL, PLATE_TOP, CELL),
                    turned(back, new Footprint(TORCH_LEFT, torchFront, TORCH_RIGHT, torchFront + TORCH_DEPTH),
                            TORCH_TOP),
                    turned(back, new Footprint(BUTTON_LEFT, BUTTON_FRONT, BUTTON_RIGHT, BUTTON_BACK),
                            inverted ? BUTTON_PRESSED_TOP : BUTTON_TOP));
        });
    }

    /** What a cached shape is kept by. */
    private record Key(Direction back, int cadence, boolean inverted) {
    }

    /**
     * A box's corners seen from above, in pixels: its x and z low and high.
     *
     * @param x1 its low x
     * @param z1 its low z
     * @param x2 its high x
     * @param z2 its high z
     */
    private record Footprint(float x1, float z1, float x2, float z2) {

        /**
         * The footprint turned a quarter clockwise seen from above, as a blockstate's y of 90 turns a model.
         *
         * @return the turned footprint
         */
        Footprint quarterTurned() {
            return new Footprint(Math.min(CELL - z1, CELL - z2), Math.min(x1, x2), Math.max(CELL - z1, CELL - z2),
                    Math.max(x1, x2));
        }
    }

    /**
     * A box standing on the plate, laid out with the back facing south and turned by the quarter turns that bring
     * south to the back, as the blockstate turns the models.
     *
     * @param back      the metronome's back
     * @param footprint the box's corners from above, laid out with the back facing south
     * @param top       the box's top, in pixels
     * @return the turned box
     */
    private static VoxelShape turned(Direction back, Footprint footprint, float top) {
        Footprint turned = footprint;
        for (int turn = 0; turn < quarterTurns(back); turn++) {
            turned = turned.quarterTurned();
        }
        return Block.box(turned.x1(), PLATE_TOP, turned.z1(), turned.x2(), top, turned.z2());
    }

    /**
     * The blockstate's turns for a back: south none, west one, north two, east three.
     *
     * @param back the metronome's back
     * @return the quarter turns
     */
    private static int quarterTurns(Direction back) {
        return switch (back) {
            case WEST -> WEST_TURNS;
            case NORTH -> NORTH_TURNS;
            case EAST -> EAST_TURNS;
            default -> 0;
        };
    }
}
