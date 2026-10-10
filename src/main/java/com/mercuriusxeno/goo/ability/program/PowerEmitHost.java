package com.mercuriusxeno.goo.ability.program;

/**
 * A host whose own block can give redstone power to the blocks beside it
 * (capability {@link HostCapability#EMIT_POWER}).
 * thumper-blob-pulses-periodically-then-fades
 */
public interface PowerEmitHost extends StepHost {

    /**
     * Turns the host block's redstone power on or off.
     *
     * @param on true to give full power, false to give none
     */
    void emitPower(boolean on);
}
