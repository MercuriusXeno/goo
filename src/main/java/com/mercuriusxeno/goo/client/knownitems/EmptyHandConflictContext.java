package com.mercuriusxeno.goo.client.knownitems;

import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import java.util.function.BooleanSupplier;

/**
 * The known items key's conflict context: active only while the main hand is empty and no glove
 * is held, and conflicting with no other context, so the glove's G keeps its press.
 * decision g-opens-the-known-items-menu-empty-handed
 */
public final class EmptyHandConflictContext implements IKeyConflictContext {

    private final BooleanSupplier handsFree;

    /**
     * Creates the context over a read of the local player's hands.
     *
     * @param handsFree whether the main hand is empty and neither hand holds a glove
     */
    public EmptyHandConflictContext(BooleanSupplier handsFree) {
        this.handsFree = handsFree;
    }

    @Override
    public boolean isActive() {
        return handsFree.getAsBoolean();
    }

    @Override
    public boolean conflicts(IKeyConflictContext other) {
        return this == other;
    }
}
