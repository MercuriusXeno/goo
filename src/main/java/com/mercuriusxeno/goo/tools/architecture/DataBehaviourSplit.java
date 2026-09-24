package com.mercuriusxeno.goo.tools.architecture;

/**
 * How the graded types split between state and behaviour.
 *
 * @param behaviourOnly types with a drawn method and no instance field
 * @param stateOnly     types with an instance field and no drawn method
 * @param both          types with both
 * @param neither       types with members but neither, such as constants alone
 */
public record DataBehaviourSplit(int behaviourOnly, int stateOnly, int both, int neither) {
}