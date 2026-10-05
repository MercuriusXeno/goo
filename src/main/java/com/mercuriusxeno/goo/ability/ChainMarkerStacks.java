package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition.ChainConfig;

/**
 * The stack count of a chain marker under its ability's stack ceiling. A
 * stack reads the marker's own ability chain block, so a datapack's ceiling
 * holds for every goo type.
 */
public final class ChainMarkerStacks {

    private int stackCount = 1;
    private int maxStacks = 1;

    /**
     * Sets one stack and the chain block's ceiling, as a marker is placed.
     *
     * @param chain the ability's chain block
     */
    public void arm(ChainConfig chain) {
        stackCount = 1;
        maxStacks = chain.maxStacks();
    }

    /**
     * Adds one stack when the chain block's ceiling allows it.
     *
     * @param chain the ability's chain block
     * @return true when the stack was added
     */
    public boolean addStack(ChainConfig chain) {
        maxStacks = chain.maxStacks();
        if (!AbilityMath.canStack(stackCount, maxStacks)) {
            return false;
        }
        stackCount++;
        return true;
    }

    /**
     * Spends one stack, as a behavior using stacks for charges does.
     */
    public void spendStack() {
        if (stackCount > 0) {
            stackCount--;
        }
    }

    /**
     * Restores the state saved to disk.
     *
     * @param stacks  the stack count
     * @param ceiling the stack ceiling
     */
    public void restore(int stacks, int ceiling) {
        stackCount = stacks;
        maxStacks = ceiling;
    }

    /**
     * Returns the stack count.
     *
     * @return the stack count
     */
    public int stackCount() {
        return stackCount;
    }

    /**
     * Returns the stack ceiling the chain block last set.
     *
     * @return the stack ceiling
     */
    public int maxStacks() {
        return maxStacks;
    }
}
