package com.mercuriusxeno.goo.ability.program;

/**
 * A child step paired with the kind of host it runs on, so the load check
 * holds a container's children to the host they will receive.
 *
 * @param step the child step
 * @param host the kind of host the child runs on
 */
public record HostedStep(Step step, HostKind host) {
}
