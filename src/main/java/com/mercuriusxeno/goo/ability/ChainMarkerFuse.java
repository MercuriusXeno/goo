package com.mercuriusxeno.goo.ability;

import com.mercuriusxeno.goo.ability.AbilityDefinition.ChainConfig;

/**
 * The stack count and fuse of a chain marker. Each change reads the
 * marker's own ability chain block, at placement, on a declared throw and
 * on a stack, so a datapack's fuse and stack ceiling hold for every goo
 * type (decision diagnose-then-fix-fuse-and-cost).
 */
public final class ChainMarkerFuse {

    private int stackCount = 1;
    private int maxStacks = 1;
    private int fuseRemaining;

    /**
     * Sets one stack and the chain block's full fuse, as a marker is placed.
     *
     * @param chain the ability's chain block
     */
    public void arm(ChainConfig chain) {
        stackCount = 1;
        maxStacks = chain.maxStacks();
        fuseRemaining = chain.fuseTicks();
    }

    /**
     * Resets the fuse to the chain block's full fuse, as when a throw is
     * declared toward the marker or a blob lands on it before it fires.
     *
     * @param chain the ability's chain block
     */
    public void resetFuse(ChainConfig chain) {
        fuseRemaining = chain.fuseTicks();
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
     * Counts a live fuse down one tick. A negative fuse waits on a trigger
     * and never counts.
     *
     * @return true when this tick burned the fuse out
     */
    public boolean countDown() {
        if (fuseRemaining < 0) {
            return false;
        }
        fuseRemaining--;
        return !AbilityMath.isFuseLive(fuseRemaining);
    }

    /**
     * Burns the fuse out, so the next tick fires the marker.
     */
    public void burnOut() {
        fuseRemaining = 0;
    }

    /**
     * Restores the state saved to disk or carried through a fall.
     *
     * @param stacks    the stack count
     * @param ceiling   the stack ceiling
     * @param remaining the fuse remaining
     */
    public void restore(int stacks, int ceiling, int remaining) {
        stackCount = stacks;
        maxStacks = ceiling;
        fuseRemaining = remaining;
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

    /**
     * Returns the fuse ticks remaining; negative while the fuse waits on a trigger.
     *
     * @return the fuse remaining
     */
    public int fuseRemaining() {
        return fuseRemaining;
    }
}
