package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.FieldEffectState;
import com.mercuriusxeno.goo.ability.program.PhasedState;
import com.mercuriusxeno.goo.item.GooContents;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;

/**
 * The state a program running on a marker host keeps between ticks, held by
 * every block entity a marker host stands at: the field effect's strikes,
 * the phased step's cursor and the goo a black hole consumed.
 */
public final class MarkerProgramState {

    private static final String TAG_CONSUMED_GOO = "ConsumedGoo";

    private final FieldEffectState fieldEffect = new FieldEffectState();
    private final PhasedState phased = new PhasedState();
    private GooContents consumedGoo = GooContents.EMPTY;

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
     * @return the goo consumed so far and not yet dropped
     */
    public GooContents consumedGoo() {
        return consumedGoo;
    }

    /**
     * Adds goo consumed from the blocks around the host to its total.
     *
     * @param consumed the goo just consumed
     */
    public void addConsumedGoo(GooContents consumed) {
        consumedGoo = consumedGoo.mergeWith(consumed);
    }

    /**
     * Empties the consumed goo total, handing back what it held.
     *
     * @return the goo consumed so far
     */
    public GooContents takeConsumedGoo() {
        GooContents taken = consumedGoo;
        consumedGoo = GooContents.EMPTY;
        return taken;
    }

    /**
     * Restores the state from persistent data.
     *
     * @param input the value input to read from
     */
    public void load(ValueInput input) {
        fieldEffect.load(input);
        phased.load(input);
        consumedGoo = input.read(TAG_CONSUMED_GOO, GooContents.CODEC).orElse(GooContents.EMPTY);
    }

    /**
     * Writes the state to persistent data.
     *
     * @param output the value output to write to
     */
    public void save(ValueOutput output) {
        fieldEffect.save(output);
        phased.save(output);
        if (!consumedGoo.isEmpty()) {
            output.store(TAG_CONSUMED_GOO, GooContents.CODEC, consumedGoo);
        }
    }
}
