package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.FieldEffectState;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The state a program running on a marker host keeps between ticks, held by
 * every block entity a marker host stands at: the field effect's strikes and
 * the size the cast was dragged to.
 */
public final class MarkerProgramState {

    private static final String TAG_CAST_SIZE = "CastSize";

    private final FieldEffectState fieldEffect = new FieldEffectState();
    private double castSize;

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
     * Restores the state from persistent data.
     *
     * @param input the value input to read from
     */
    public void load(ValueInput input) {
        fieldEffect.load(input);
        castSize = input.getDoubleOr(TAG_CAST_SIZE, 0);
    }

    /**
     * Writes the state to persistent data.
     *
     * @param output the value output to write to
     */
    public void save(ValueOutput output) {
        fieldEffect.save(output);
        if (castSize > 0) {
            output.putDouble(TAG_CAST_SIZE, castSize);
        }
    }
}
