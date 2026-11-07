package com.mercuriusxeno.goo.ability.program;

/**
 * A step that, run on its caster, acts on the entity the glove aims at
 * rather than on the caster: Grab lifts the outlined entity the tick sends,
 * and Sunbeam's ray turns to the outlined mob it locks onto. Of a self
 * ability's steps, only these let the glove outline anything.
 * decision steps-declare-the-entities-they-take
 * decision outlined-target-is-the-one-pick
 */
public interface ReachesTheAim extends Step {
}
