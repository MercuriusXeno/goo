package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import net.minecraft.world.item.ItemStack;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.mockito.Mockito.mock;

/** Covers the crosshair panel's row: the first source, the blobs it holds and the cost at the aimed stack (decision crosshair-panel-shows-source-and-cost). */
class CrosshairFuelPanelTest {

    private static final int CANISTER_VOLUME = 3000;
    private static final int AIMED_STACKS = 2;

    private static ClientAbility proximityMine() {
        AbilityDefinition definition = AbilityJson.decode("unstable_proximity_mine");
        return new ClientAbility(definition.id(), definition.displayName(), definition.icon(),
                definition.order(), definition.tags(), definition.chain().fuseTicks(),
                definition.chain().maxStacks(), definition.behaviors(), definition.cost());
    }

    @Test
    void rowReadsTheCanisterItsVolumeAndTheCostAtTheAimedStack() {
        ItemStack canister = mock(ItemStack.class);
        int cost = proximityMine().throwCost(AIMED_STACKS);

        CrosshairFuelPanel.FuelRow row = CrosshairFuelPanel.fuelRow(canister, GooTypes.UNSTABLE, CANISTER_VOLUME, cost);

        assertSame(canister, row.source());
        assertEquals(GooTypes.UNSTABLE, row.type());
        assertEquals("3", row.heldText());
        assertEquals("- 4.5", row.costText());
    }

    /** A 60 by 22 panel at (100, 50) cuts the 24x24 effect background into nine slices with a 3px border (decision diagnose-then-fix-aiming-panel-stretch). */
    @Test
    void backgroundCutsTheEffectBackgroundIntoNineSlices() {
        float b = 3f / 24f;
        float e = 1f - b;
        List<NineSlice.Slice> expected = List.of(
                new NineSlice.Slice(100, 50, 103, 53, 0f, 0f, b, b),
                new NineSlice.Slice(157, 50, 160, 53, e, 0f, 1f, b),
                new NineSlice.Slice(100, 69, 103, 72, 0f, e, b, 1f),
                new NineSlice.Slice(157, 69, 160, 72, e, e, 1f, 1f),
                new NineSlice.Slice(103, 50, 157, 53, b, 0f, e, b),
                new NineSlice.Slice(103, 69, 157, 72, b, e, e, 1f),
                new NineSlice.Slice(100, 53, 103, 69, 0f, b, b, e),
                new NineSlice.Slice(157, 53, 160, 69, e, b, 1f, e),
                new NineSlice.Slice(103, 53, 157, 69, b, b, e, e));

        assertEquals(expected, CrosshairFuelPanel.backgroundSlices(new PanelRectangle(100, 50, 60, 22)));
    }

    /** The panel's right and bottom edges sit 4px inside the gui's (decision fuel-panel-sits-at-bottom-right). */
    @Nested
    class BottomRightAnchor {

        @Test
        void panelSitsFourPixelsInFromTheBottomRightOfA480By270Gui() {
            PanelRectangle rect = CrosshairFuelPanel.bottomRightAnchor(480, 270, 80, 22);

            assertEquals(396f, rect.x());
            assertEquals(244f, rect.y());
            assertEquals(476f, rect.x() + rect.w());
            assertEquals(266f, rect.y() + rect.h());
        }

        @Test
        void panelKeepsTheFourPixelMarginOnAnotherGuiSize() {
            PanelRectangle rect = CrosshairFuelPanel.bottomRightAnchor(320, 240, 60, 22);

            assertEquals(256f, rect.x());
            assertEquals(214f, rect.y());
            assertEquals(316f, rect.x() + rect.w());
            assertEquals(236f, rect.y() + rect.h());
        }
    }

    @Test
    void aimedStackCostsAboveTheFirstThrowForAPowerLaw() {
        ClientAbility mine = proximityMine();

        assertEquals(2000, mine.throwCost(0));
        assertEquals(4500, mine.throwCost(AIMED_STACKS));
    }
}
