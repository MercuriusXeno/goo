package com.mercuriusxeno.goo.ability.program;

import java.util.Locale;

/**
 * What a step asks of its host. A {@link HostKind} names the capabilities
 * it provides, a {@link Step} names the ones it needs, and
 * {@link ProgramBehavior#forHost} refuses a program at load when a step
 * needs one the host lacks (decision host-agnostic-runtime).
 */
public enum HostCapability {
    /**
     * A stack count that can be read and spent, and the marker variables.
     */
    STACKS,
    /**
     * A placed face on a block.
     */
    PLACED_FACE,
    /**
     * A driver that ticks the program past its first tick.
     */
    TICKING,
    /**
     * An explosion at the host anchor.
     */
    EXPLODE,
    /**
     * A scan for entities around the anchor.
     */
    ENTITY_SCAN,
    /**
     * A struck entity the effect steps act on.
     */
    TARGET,
    /**
     * A block position the host can write a block state into.
     */
    PLACE_BLOCK;

    private static final String ERR_REFUSED = "Capability %s is not provided by the %s host";

    /**
     * Returns the lower-case name a refusal message uses.
     *
     * @return the key
     */
    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }

    /**
     * Builds the exception a host throws from a method of a capability it
     * does not provide; load-time checks keep a program from reaching it.
     *
     * @param kind the host refusing
     * @return the exception to throw
     */
    public UnsupportedOperationException refusedBy(HostKind kind) {
        return new UnsupportedOperationException(String.format(ERR_REFUSED, key(), kind.label()));
    }
}
