package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.FieldEffectState;
import com.mercuriusxeno.goo.ability.program.PhasedState;
import com.mercuriusxeno.goo.ability.program.ShellWalk;
import com.mercuriusxeno.goo.entity.CompressedHoard;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;

/**
 * The state a program running on a marker host keeps between ticks, held by
 * every block entity a marker host stands at: the field effect's strikes,
 * the phased step's cursor and the hoard a black hole pulled in.
 */
public final class MarkerProgramState {

    private static final String TAG_HOARD = "Hoard";
    private static final String TAG_CAST_SIZE = "CastSize";
    private static final String TAG_TAKE_RADIUS = "TakeRadius";
    private static final String TAG_TAKE_SHELL = "TakeShell";
    private static final String TAG_TAKE_X = "TakeX";
    private static final String TAG_TAKE_Y = "TakeY";
    private static final String TAG_DROP_WHEN_TAKEN = "DropWhenTaken";
    /** The take radius of a marker taking no sphere. */
    private static final int NO_TAKE = -1;

    private final FieldEffectState fieldEffect = new FieldEffectState();
    private final PhasedState phased = new PhasedState();
    private CompressedHoard hoard = new CompressedHoard();
    private double castSize;
    private int takeRadius = NO_TAKE;
    private ShellWalk.Cursor takeCursor = ShellWalk.START;
    private boolean dropWhenTaken;

    /**
     * Starts taking a sphere of blocks around the marker, core outward, a
     * budget a tick (decision black-hole-leaves-a-compression-sphere).
     *
     * @param radius the sphere radius in whole blocks
     */
    public void beginTaking(int radius) {
        takeRadius = radius;
        takeCursor = ShellWalk.START;
    }

    /**
     * @return true while a sphere is still being taken
     */
    public boolean taking() {
        return takeRadius != NO_TAKE;
    }

    /**
     * @return the sphere radius being taken
     */
    public int takeRadius() {
        return takeRadius;
    }

    /**
     * @return where the take of the sphere stands
     */
    public ShellWalk.Cursor takeCursor() {
        return takeCursor;
    }

    /**
     * Records how far the take has reached, ending it once every cell is taken.
     *
     * @param next where the take stands, or null once every cell is taken
     * @return true when this ends the take and the program asked for the sphere meanwhile, so it is due now
     */
    public boolean tookTo(ShellWalk.@Nullable Cursor next) {
        if (next != null) {
            takeCursor = next;
            return false;
        }
        takeRadius = NO_TAKE;
        takeCursor = ShellWalk.START;
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
        takeCursor = new ShellWalk.Cursor(input.getIntOr(TAG_TAKE_SHELL, ShellWalk.START.shell()),
                input.getIntOr(TAG_TAKE_X, ShellWalk.START.x()), input.getIntOr(TAG_TAKE_Y, ShellWalk.START.y()));
        dropWhenTaken = input.getBooleanOr(TAG_DROP_WHEN_TAKEN, false);
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
            output.putInt(TAG_TAKE_SHELL, takeCursor.shell());
            output.putInt(TAG_TAKE_X, takeCursor.x());
            output.putInt(TAG_TAKE_Y, takeCursor.y());
        }
        if (dropWhenTaken) {
            output.putBoolean(TAG_DROP_WHEN_TAKEN, true);
        }
    }
}
