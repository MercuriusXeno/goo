package com.mercuriusxeno.goo.client.ability;

import com.mercuriusxeno.goo.GooTypeDefinition;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceKey;

/**
 * The explosion one goo type draws as its chain marker burns out (decision
 * elemental-explosion-per-type).
 */
public interface BurnoutVisual {

    /**
     * @return the goo type whose burnout this visual draws
     */
    ResourceKey<GooTypeDefinition> gooType();

    /**
     * @return how many ticks the explosion plays before its entry is dropped
     */
    int durationTicks();

    /**
     * How many ticks one burnout's explosion plays, for a visual whose
     * length follows the burnout, such as a tunnel's wave.
     *
     * @param burnout the burnout
     * @return the explosion's duration in ticks
     */
    default int durationTicks(ChainBurnouts.Burnout burnout) {
        return durationTicks();
    }

    /**
     * Starts one burnout's explosion, once, as the client adds it: the place
     * for effects the level owns, such as particles.
     *
     * @param burnout the burnout
     * @param level   the client level
     */
    default void begin(ChainBurnouts.Burnout burnout, ClientLevel level) {
        // An explosion drawn wholly by its shader starts nothing.
    }

    /**
     * Draws one burnout at its point in the explosion.
     *
     * @param burnout the burnout
     * @param frame   the frame being drawn
     */
    void render(ChainBurnouts.Burnout burnout, BurnoutFrame frame);

    /**
     * A goo type whose explosion is still to be designed: it holds no
     * entry past the tick it arrives and draws nothing.
     *
     * @param gooType the goo type
     * @return the visual
     */
    static BurnoutVisual undesigned(ResourceKey<GooTypeDefinition> gooType) {
        return new UndesignedBurnout(gooType);
    }

    /**
     * The visual a goo type holds until its explosion is designed.
     *
     * @param gooType the goo type
     */
    record UndesignedBurnout(ResourceKey<GooTypeDefinition> gooType) implements BurnoutVisual {

        @Override
        public int durationTicks() {
            return 0;
        }

        @Override
        public void render(ChainBurnouts.Burnout burnout, BurnoutFrame frame) {
            // An undesigned explosion draws nothing.
        }
    }
}
