package com.mercuriusxeno.goo.ability.program;

/**
 * Thrown by {@link ProgramBehavior#forHost} when a program names a step or
 * a variable its host cannot serve. The message names the step or variable
 * and the host, and it fires before the first tick.
 */
public final class ProgramLoadException extends IllegalArgumentException {

    /**
     * Creates the refusal.
     *
     * @param message the reason, naming the step or variable and the host
     */
    public ProgramLoadException(String message) {
        super(message);
    }
}
