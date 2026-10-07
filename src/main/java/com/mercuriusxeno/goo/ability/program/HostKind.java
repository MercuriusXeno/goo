package com.mercuriusxeno.goo.ability.program;

import java.util.Set;

/**
 * The hosts a program can run on, each with the host type whose
 * capability interfaces name what it provides, and the variables it
 * binds. The label is what a load refusal names.
 */
public enum HostKind {
    /**
     * The ability block block: a placed face, a tick driver, the
     * world around the block, the block position itself to write, the
     * field-effect state a trap keeps while its budget lasts, the phase
     * cursor of a phased step, and the goo a black hole consumes until it
     * pops.
     */
    MARKER("marker block", MarkerHost.class, Set.of()),
    /**
     * The struck living entity: a target and its thrower, acted on in the
     * tick the goo lands, with no driver for later ticks.
     */
    ENTITY("struck entity", EntityHost.class,
            Set.of(HostVariables.HEALTH, HostVariables.MAX_HEALTH, HostVariables.DISTANCE,
                    HostVariables.UNDEAD, HostVariables.SPRINTING)),
    /**
     * The block a tap's drip lands on: the world around its top face and the
     * block above it to write, acted on in the tick the drip lands, with no
     * target and no driver for later ticks
     * (decision tap-ability-tagged-program).
     */
    TAP("tap landing", TapHost.class, Set.of()),
    /**
     * The cell a thrown world ability's blob lands in: the world around it,
     * the cell itself to write, and the ability's own block to stand there,
     * acted on in the landing tick with no driver for later ticks (decisions
     * splat-runs-the-program-no-fuse, lingering-abilities-place-their-own-thing).
     */
    LANDING("blob landing", LandingHost.class, Set.of()),
    /**
     * A floor a stream's cone or a spore burst reaches: the world around its
     * top face and the cell above it to write, acted on in the tick the spray
     * reaches it, with no target and no driver for later ticks
     * (decision mycosis-spore-stream-buds-and-poisons).
     */
    SURFACE("sprayed floor", SurfaceHost.class, Set.of()),
    /**
     * The player invoking a self ability: target, thrower and anchor at
     * once, acted on in the tick the glove is used, with no driver for
     * later ticks (decision self-delivery-runs-on-player).
     */
    PLAYER("player host", PlayerHost.class,
            Set.of(HostVariables.HEALTH, HostVariables.MAX_HEALTH, HostVariables.DISTANCE,
                    HostVariables.UNDEAD, HostVariables.SPRINTING));

    private final String label;
    private final Set<HostCapability> capabilities;
    private final Set<String> variables;

    HostKind(String label, Class<? extends StepHost> hostType, Set<String> variables) {
        this.label = label;
        this.capabilities = HostCapability.providedBy(hostType);
        this.variables = variables;
    }

    /**
     * Returns the name a refusal message calls this host.
     *
     * @return the label
     */
    public String label() {
        return label;
    }

    /**
     * Returns the capabilities this host provides, read off the capability
     * interfaces its host type implements (decision
     * capability-interfaces-derive-host-kind).
     *
     * @return the capability set
     */
    public Set<HostCapability> capabilities() {
        return capabilities;
    }

    /**
     * Returns the variable names this host binds; the runtime adds
     * {@link StepContext#VAR_TICK} for every host.
     *
     * @return the variable names
     */
    public Set<String> variables() {
        return variables;
    }
}
