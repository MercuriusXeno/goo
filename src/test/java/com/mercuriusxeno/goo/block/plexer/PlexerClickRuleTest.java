package com.mercuriusxeno.goo.block.plexer;

import com.mercuriusxeno.goo.block.plexer.PlexerClickRule.Click;
import com.mercuriusxeno.goo.block.plexer.PlexerClickRule.Held;
import net.minecraft.core.Direction;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A right-click on any face of the plexer opens the known items menu, with an empty hand or any item;
 * a held canister whose own use takes the click passes it on
 * (decision right-click-on-the-plexer-opens-the-known-items-menu). The rule reads no face, so every
 * face answers alike.
 */
class PlexerClickRuleTest {

    @Nested
    class AnyFace {
        @ParameterizedTest
        @EnumSource(Direction.class)
        void emptyHandOpensTheMenu(Direction face) {
            assertEquals(Click.OPEN_MENU, PlexerClickRule.resolve(Held.EMPTY, false), "face " + face);
        }

        @ParameterizedTest
        @EnumSource(Direction.class)
        void nonCanisterItemOpensTheMenu(Direction face) {
            assertEquals(Click.OPEN_MENU, PlexerClickRule.resolve(Held.ITEM, false), "face " + face);
        }
    }

    @Nested
    class HeldCanister {
        @Test
        void canisterWhoseUseTakesTheClickPassesItOn() {
            assertEquals(Click.PASS, PlexerClickRule.resolve(Held.CANISTER, true));
        }

        @Test
        void canisterWhoseUseTakesNothingOpensTheMenu() {
            assertEquals(Click.OPEN_MENU, PlexerClickRule.resolve(Held.CANISTER, false));
        }
    }
}
