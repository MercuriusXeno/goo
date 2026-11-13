package com.mercuriusxeno.goo.client.throwing;

import net.neoforged.neoforge.client.event.InputEvent;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A held ability's visual runs only while right click holds it and goo of the
 * selected type is left, so a channel out of goo stops drawing its fog, breeze,
 * vortex or cursor as its stream stops; while a glove press is live the use
 * key uses no block and swings no hand, and with none live it passes untouched.
 * decision diagnose-and-correct-block-use-during-a-held-channel
 */
class GloveUseTrackerTest {

    @Test
    void aHeldAbilityWithGooLeftRunsItsVisual() {
        assertTrue(GloveUseTracker.heldVisualRuns(true, true));
    }

    @Test
    void aHeldAbilityOutOfGooDrawsNothing() {
        assertFalse(GloveUseTracker.heldVisualRuns(true, false));
    }

    @Test
    void aLivePressCancelsTheUseKey() {
        assertTrue(GloveUseTracker.cancelsUseKey(true, true));
    }

    @Test
    void noLivePressLeavesTheUseKeyToVanilla() {
        assertFalse(GloveUseTracker.cancelsUseKey(true, false));
    }

    /** Decisions use-animation-only-when-goo-throws and use-key-throw-swings-no-more: the cancel swings nothing. */
    @Test
    void theCancelSwingsNoHand() {
        InputEvent.InteractionKeyMappingTriggered event = useKey();
        GloveUseTracker.cancelUseKey(event, true);
        verify(event).setSwingHand(false);
        verify(event).setCanceled(true);
    }

    private static InputEvent.InteractionKeyMappingTriggered useKey() {
        InputEvent.InteractionKeyMappingTriggered event = mock(InputEvent.InteractionKeyMappingTriggered.class);
        when(event.isUseItem()).thenReturn(true);
        return event;
    }

    @Test
    void aReleasedAbilityDrawsNothing() {
        assertFalse(GloveUseTracker.heldVisualRuns(false, true));
    }
}
