package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.FieldEffectState;
import com.mercuriusxeno.goo.ability.program.PhasedState;
import com.mercuriusxeno.goo.entity.CompressedHoard;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The state a program running on a marker host keeps between ticks, held by
 * every block entity a marker host stands at: the field effect's strikes,
 * the phased step's cursor and the hoard a black hole pulled in.
 */
public final class MarkerProgramState {

    private static final String TAG_HOARD = "Hoard";

    private final FieldEffectState fieldEffect = new FieldEffectState();
    private final PhasedState phased = new PhasedState();
    private CompressedHoard hoard = new CompressedHoard();

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
    }
}
