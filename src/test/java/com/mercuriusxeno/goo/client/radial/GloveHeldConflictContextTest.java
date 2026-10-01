package com.mercuriusxeno.goo.client.radial;

import net.minecraft.world.InteractionHand;
import net.neoforged.neoforge.client.settings.IKeyConflictContext;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the glove menu key's context: active only with a glove in a hand, conflicting only with itself (decision g-opens-radial-while-glove-held). */
class GloveHeldConflictContextTest {

    @Test
    void gloveInMainHandIsActive() {
        assertTrue(new GloveHeldConflictContext(hand -> hand == InteractionHand.MAIN_HAND).isActive());
    }

    @Test
    void gloveInOffhandIsActive() {
        assertTrue(new GloveHeldConflictContext(hand -> hand == InteractionHand.OFF_HAND).isActive());
    }

    @Test
    void gloveInNeitherHandIsInactive() {
        assertFalse(new GloveHeldConflictContext(hand -> false).isActive());
    }

    @Test
    void conflictsOnlyWithItself() {
        GloveHeldConflictContext context = new GloveHeldConflictContext(hand -> true);

        assertTrue(context.conflicts(context));
        assertFalse(context.conflicts(Mockito.mock(IKeyConflictContext.class)));
    }
}
