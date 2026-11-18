package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.item.SprayerItem.CanisterClick;
import com.mercuriusxeno.goo.item.SprayerItem.ClickedStacks;
import com.mercuriusxeno.goo.type.GooTypes;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the sprayer and the cursor hold after an inventory click, read through the click seam
 * over stand-in stacks carrying a canister's goo and label, since the unit suite registers no item
 * (decisions sprayer-takes-gives-and-swaps-its-canister-by-clicks and
 * sprayer-holds-a-canister-sideways-like-a-caulk-gun).
 */
class SprayerItemTest {

    private static final int VOLUME = 300;

    private StampedGoo goo;

    @BeforeEach
    void stampGooTypes() {
        goo = new StampedGoo();
    }

    @AfterEach
    void releaseGooFluids() {
        goo.close();
    }

    @Test
    void canisterClickedOntoAnEmptySprayerGoesIn() {
        CanisterFluidContent twoTypes = CanisterFluidContent.of(goo.resource(GooTypes.BLAZE), VOLUME)
                .withVolume(goo.resource(GooTypes.ROCK), VOLUME);
        StandInStack canister = StandInStack.canister(twoTypes, CanisterMetadata.EMPTY.withGooLabel(GooTypes.BLAZE));

        CanisterClick<StandInStack> click = SprayerItem.clickCanister(StandInStack.EMPTY, canister, false, KINDS);

        assertTrue(click.handled());
        assertEquals(twoTypes, click.fitted().content());
        assertEquals(canister.metadata(), click.fitted().metadata());
        assertSame(StandInStack.EMPTY, click.cursor());
    }

    @Test
    void rightClickTakesTheCanisterOut() {
        StandInStack fitted = StandInStack.canister(CanisterFluidContent.of(goo.resource(GooTypes.BLAZE), VOLUME),
                CanisterMetadata.EMPTY);

        CanisterClick<StandInStack> click = SprayerItem.clickCanister(fitted, StandInStack.EMPTY, true, KINDS);

        assertTrue(click.handled());
        assertEquals(fitted.content(), click.cursor().content());
        assertSame(StandInStack.EMPTY, click.fitted());
    }

    @Test
    void canisterClickedOntoALoadedSprayerSwaps() {
        StandInStack fitted = StandInStack.canister(CanisterFluidContent.of(goo.resource(GooTypes.BLAZE), VOLUME),
                CanisterMetadata.EMPTY);
        StandInStack clicked = StandInStack.canister(CanisterFluidContent.of(goo.resource(GooTypes.ROCK), VOLUME),
                CanisterMetadata.EMPTY);

        CanisterClick<StandInStack> click = SprayerItem.clickCanister(fitted, clicked, false, KINDS);

        assertTrue(click.handled());
        assertEquals(clicked.content(), click.fitted().content());
        assertEquals(fitted.content(), click.cursor().content());
    }

    @Test
    void vatClickedOntoTheSprayerStaysOnTheCursor() {
        StandInStack fitted = StandInStack.canister(CanisterFluidContent.of(goo.resource(GooTypes.BLAZE), VOLUME),
                CanisterMetadata.EMPTY);
        StandInStack vat = StandInStack.other();

        CanisterClick<StandInStack> click = SprayerItem.clickCanister(fitted, vat, false, KINDS);

        assertFalse(click.handled());
        assertSame(vat, click.cursor());
        assertSame(fitted, click.fitted());
    }

    private static final ClickedStacks<StandInStack> KINDS = new ClickedStacks<>() {
        @Override
        public boolean isEmpty(StandInStack stack) {
            return stack == StandInStack.EMPTY;
        }

        @Override
        public boolean isCanister(StandInStack stack) {
            return stack.isCanister();
        }

        @Override
        public StandInStack empty() {
            return StandInStack.EMPTY;
        }
    };

    /**
     * A stack the click seam reads: a canister with its goo and label, another item, or nothing.
     *
     * @param isCanister whether the stack is a canister
     * @param content    the canister's goo
     * @param metadata   the canister's label and gaskets
     */
    private record StandInStack(boolean isCanister, CanisterFluidContent content, CanisterMetadata metadata) {
        static final StandInStack EMPTY = new StandInStack(false, CanisterFluidContent.EMPTY, CanisterMetadata.EMPTY);

        static StandInStack canister(CanisterFluidContent content, CanisterMetadata metadata) {
            return new StandInStack(true, content, metadata);
        }

        static StandInStack other() {
            return new StandInStack(false, CanisterFluidContent.EMPTY, CanisterMetadata.EMPTY);
        }
    }
}
