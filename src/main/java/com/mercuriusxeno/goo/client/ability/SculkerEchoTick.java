package com.mercuriusxeno.goo.client.ability;

/**
 * The echo state a client tick advances while Sculker stands: the noises
 * waiting for their other half, the rings and the faces they lit, each
 * crossed cell's answer and each lit block's kept quads. SculkerSenses ticks
 * the client's own, and a test ticks one over a world of its own.
 * decision diagnose-and-correct-the-lag-on-four-sense-abilities
 *
 * @param noises what joins each sound to its vibration and rings it
 * @param echoes the rings and the faces they lit
 * @param cells  whether an echo lights each cell a ring crosses, kept across ticks
 * @param quads  each lit block's quads, kept across frames
 */
record SculkerEchoTick(SculkerNoises noises, SculkerEchoes echoes, EchoCells cells, EchoQuads quads) {

    /**
     * Rings the sounds that waited out their vibration, grows every ring,
     * and lets go of the kept quads no echo remembers.
     *
     * @param now the game time
     */
    void advance(long now) {
        SculkerEchoSections.profiled(SculkerEchoSections.NOISES, () -> noises.advance(now));
        echoes.advanceEchoing(now, cells::echoesOff);
        SculkerEchoSections.count(SculkerEchoSections.CELL_READS_COUNTER, cells.takeReads());
        SculkerEchoSections.profiled(SculkerEchoSections.QUADS_TRIM,
                () -> quads.keepWithin(echoes.remembered().keySet()));
    }

    /** Forgets every noise, face, answer and quad, as the held effect's end does. */
    void clear() {
        echoes.clear();
        quads.clear();
        cells.clear();
        noises.clear();
    }
}
