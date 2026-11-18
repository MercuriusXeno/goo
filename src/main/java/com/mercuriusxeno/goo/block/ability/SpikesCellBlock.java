package com.mercuriusxeno.goo.block.ability;

/**
 * Metal's Spikes cell: the volume cell bound to Metal's goo, which impales
 * every living thing standing on its top, still or moving, by the strike,
 * cooldown and spend chance Spikes' step names; SpikesStanding finds the
 * cell under each living thing's feet on that thing's tick. The strike
 * names the player who placed the cell.
 * spikes-is-a-coat-of-metal-goo-that-impales-what-walks-on-it
 * urchin-rests-bristled-and-spears
 * standing-goo-thing-credits-whoever-placed-it
 */
public class SpikesCellBlock extends VolumeCellBlock {

    /** How far over the goo's surface a thing's feet may stand and still count as standing on it, in blocks. */
    static final double STANDING_REACH = 0.25;

    /**
     * Creates a Spikes cell.
     *
     * @param properties the block properties
     * @param binding    the Spikes binding
     */
    public SpikesCellBlock(Properties properties, CellBinding binding) {
        super(properties, binding);
    }

    /**
     * A Spikes cell's strike hurts, so the cell remembers who placed it.
     * standing-goo-thing-credits-whoever-placed-it
     */
    @Override
    protected boolean creditsItsPlacer() {
        return true;
    }
}
