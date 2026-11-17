package com.mercuriusxeno.goo.block.ability;

import com.mercuriusxeno.goo.ability.program.FieldEffectState;
import net.minecraft.core.UUIDUtil;
import net.minecraft.world.level.storage.ValueInput;
import net.minecraft.world.level.storage.ValueOutput;
import org.jspecify.annotations.Nullable;
import java.util.UUID;

/**
 * The state a program running on a marker host keeps between ticks, held by
 * every block entity a marker host stands at: the field effect's strikes,
 * the size the cast was dragged to and the player whose cast stood it.
 */
public final class MarkerProgramState {

    private static final String TAG_CAST_SIZE = "CastSize";
    private static final String TAG_PLACER = "Placer";

    private final FieldEffectState fieldEffect = new FieldEffectState();
    private double castSize;
    private @Nullable UUID placer;

    /**
     * @return the player whose cast stood the marker, or null where no player did
     */
    public @Nullable UUID placer() {
        return placer;
    }

    /**
     * Sets the player whose cast stood the marker, whom each hit its field deals credits.
     * standing-goo-thing-credits-whoever-placed-it
     *
     * @param player the player, or null for none
     */
    public void setPlacer(@Nullable UUID player) {
        placer = player;
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
     * Restores the state from persistent data.
     *
     * @param input the value input to read from
     */
    public void load(ValueInput input) {
        fieldEffect.load(input);
        castSize = input.getDoubleOr(TAG_CAST_SIZE, 0);
        placer = input.read(TAG_PLACER, UUIDUtil.CODEC).orElse(null);
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
        if (placer != null) {
            output.store(TAG_PLACER, UUIDUtil.CODEC, placer);
        }
    }
}
