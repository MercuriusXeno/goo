package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.FieldEffectState;
import com.mercuriusxeno.goo.ability.program.PhasedState;
import com.mercuriusxeno.goo.entity.CompressedHoard;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.function.BiFunction;

/**
 * The state a program running on a marker host keeps between ticks, held by
 * every block entity a marker host stands at: the field effect's strikes,
 * the phased step's cursor and the hoard a black hole pulled in.
 */
public final class MarkerProgramState {

    private static final String TAG_HOARD = "Hoard";
    private static final String TAG_CAST_SIZE = "CastSize";
    private static final String TAG_TAKE_RADIUS = "TakeRadius";
    private static final String TAG_TAKEN = "Taken";
    private static final String TAG_DROP_WHEN_TAKEN = "DropWhenTaken";
    /** The take radius of a marker taking no sphere. */
    private static final int NO_TAKE = -1;

    private final FieldEffectState fieldEffect = new FieldEffectState();
    private final PhasedState phased = new PhasedState();
    private CompressedHoard hoard = new CompressedHoard();
    private double castSize;
    private int takeRadius = NO_TAKE;
    private int taken;
    private boolean dropWhenTaken;
    /** The cells of the sphere being taken, core outward; rebuilt from the radius when absent. */
    private @Nullable List<BlockPos> takeCells;

    /**
     * Starts taking a sphere of blocks around the marker, core outward, a
     * budget a tick (decision black-hole-leaves-a-compression-sphere).
     *
     * @param radius the sphere radius in whole blocks
     */
    public void beginTaking(int radius) {
        takeRadius = radius;
        taken = 0;
        takeCells = null;
    }

    /**
     * @return true while a sphere is still being taken
     */
    public boolean taking() {
        return takeRadius != NO_TAKE;
    }

    /**
     * The sphere's cells, core outward, built once from the radius.
     *
     * @param center the marker's position
     * @param order  how the cells of a sphere are ordered
     * @return the cells
     */
    public List<BlockPos> takeCells(BlockPos center, BiFunction<BlockPos, Integer, List<BlockPos>> order) {
        if (takeCells == null) {
            takeCells = order.apply(center, takeRadius);
        }
        return takeCells;
    }

    /**
     * @return the index of the first cell of the sphere not yet taken
     */
    public int taken() {
        return taken;
    }

    /**
     * Records how far the take has reached, ending it once every cell is taken.
     *
     * @param next  the index of the first cell still to take
     * @param total the sphere's cell count
     * @return true when this ends the take and the program asked for the sphere meanwhile, so it is due now
     */
    public boolean tookTo(int next, int total) {
        taken = next;
        if (next < total) {
            return false;
        }
        takeRadius = NO_TAKE;
        taken = 0;
        takeCells = null;
        boolean due = dropWhenTaken;
        dropWhenTaken = false;
        return due;
    }

    /**
     * Asks for the sphere: while a take runs it is put off until the take
     * ends, so the sphere holds every block the hole takes.
     *
     * @return true when the sphere is put off, false when it is due now
     */
    public boolean putOffDrop() {
        dropWhenTaken = taking();
        return dropWhenTaken;
    }

    /**
     * @return the size the cast that stood the marker was dragged to, zero for one that named none
     */
    public double castSize() {
        return castSize;
    }

    /**
     * Sets the size the cast that stood the marker was dragged to
     * (decision black-hole-leaves-a-compression-sphere).
     *
     * @param size the cast's size in blocks
     */
    public void setCastSize(double size) {
        castSize = size;
    }

    /**
     * @return the live field-effect state a field-effect step mutates
     */
    public FieldEffectState fieldEffect() {
        return fieldEffect;
    }

    /**
     * @return the live phase cursor a phased step mutates
     */
    public PhasedState phased() {
        return phased;
    }

    /**
     * @return the live hoard of stacks pulled in and not yet left as a sphere
     */
    public CompressedHoard hoard() {
        return hoard;
    }

    /**
     * Restores the state from persistent data.
     *
     * @param input the value input to read from
     */
    public void load(ValueInput input) {
        fieldEffect.load(input);
        phased.load(input);
        hoard = input.read(TAG_HOARD, CompressedHoard.CODEC).orElseGet(CompressedHoard::new);
        castSize = input.getDoubleOr(TAG_CAST_SIZE, 0);
        takeRadius = input.getIntOr(TAG_TAKE_RADIUS, NO_TAKE);
        taken = input.getIntOr(TAG_TAKEN, 0);
        dropWhenTaken = input.getBooleanOr(TAG_DROP_WHEN_TAKEN, false);
        takeCells = null;
    }

    /**
     * Writes the state to persistent data.
     *
     * @param output the value output to write to
     */
    public void save(ValueOutput output) {
        fieldEffect.save(output);
        phased.save(output);
        if (!hoard.isEmpty()) {
            output.store(TAG_HOARD, CompressedHoard.CODEC, hoard);
        }
        if (castSize > 0) {
            output.putDouble(TAG_CAST_SIZE, castSize);
        }
        if (taking()) {
            output.putInt(TAG_TAKE_RADIUS, takeRadius);
            output.putInt(TAG_TAKEN, taken);
        }
        if (dropWhenTaken) {
            output.putBoolean(TAG_DROP_WHEN_TAKEN, true);
        }
    }
}
