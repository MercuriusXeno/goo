package com.mercuriusxeno.goo.ability.program;

import java.util.Set;

/**
 * The hosts a program can run on, each with the capabilities it provides
 * and the variables it binds. The label is what a load refusal names.
 */
public enum HostKind {
    /**
     * The chain marker block: stacks, a placed face, a tick driver, the
     * world around the block, the block position itself to write, and
     * the blocks around it to strike layer by layer.
     */
    MARKER("marker block",
            Set.of(HostCapability.STACKS, HostCapability.PLACED_FACE, HostCapability.TICKING,
                    HostCapability.EXPLODE, HostCapability.ENTITY_SCAN, HostCapability.PLACE_BLOCK,
                    HostCapability.LAYER_WALK),
            Set.of(HostVariables.STACKS, HostVariables.MAX_STACKS, HostVariables.FLAT)),
    /**
     * The struck living entity: a target and its thrower, acted on in the
     * tick the blob lands, with no driver for later ticks.
     */
    ENTITY("struck entity",
            Set.of(HostCapability.TARGET, HostCapability.EXPLODE, HostCapability.ENTITY_SCAN),
            Set.of(HostVariables.HEALTH, HostVariables.MAX_HEALTH, HostVariables.DISTANCE,
                    HostVariables.UNDEAD));

    private final String label;
    private final Set<HostCapability> capabilities;
    private final Set<String> variables;

    HostKind(String label, Set<HostCapability> capabilities, Set<String> variables) {
        this.label = label;
        this.capabilities = capabilities;
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
     * Returns the capabilities this host provides.
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
