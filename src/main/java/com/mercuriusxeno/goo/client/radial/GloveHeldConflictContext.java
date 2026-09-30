package com.mercuriusxeno.goo.client.radial;

import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import java.util.function.Predicate;

/**
 * The glove menu key's conflict context: active only while a glove is in
 * either hand, and conflicting with no other context, so the key fires
 * nowhere else and shares its key with another mod's binding unmarked.
 * decision g-opens-radial-while-glove-held
 */
public final class GloveHeldConflictContext implements IKeyConflictContext {

    private final Predicate<InteractionHand> holdsGlove;

    /**
     * Creates the context over a read of the local player's hands.
     *
     * @param holdsGlove whether the local player holds a glove in a hand
     */
    public GloveHeldConflictContext(Predicate<InteractionHand> holdsGlove) {
        this.holdsGlove = holdsGlove;
    }

    @Override
    public boolean isActive() {
        for (InteractionHand hand : InteractionHand.values()) {
            if (holdsGlove.test(hand)) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean conflicts(IKeyConflictContext other) {
        return this == other;
    }
}
