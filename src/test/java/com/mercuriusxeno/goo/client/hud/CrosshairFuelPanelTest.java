package com.mercuriusxeno.goo.client.hud;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityArea;
import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.DeliveryKind;
import com.mercuriusxeno.goo.ability.IndicatorShowing;
import com.mercuriusxeno.goo.client.hud.CrosshairFuelPanel.AbilityIcon;
import com.mercuriusxeno.goo.client.hud.CrosshairFuelPanel.AbilityName;
import com.mercuriusxeno.goo.client.hud.CrosshairFuelPanel.Count;
import com.mercuriusxeno.goo.client.hud.CrosshairFuelPanel.FuelCell;
import com.mercuriusxeno.goo.client.hud.CrosshairFuelPanel.ItemIcon;
import com.mercuriusxeno.goo.client.hud.CrosshairFuelPanel.TypeIcon;
import com.mercuriusxeno.goo.client.network.AbilitySyncHandler.ClientAbility;
import com.mercuriusxeno.goo.client.network.AffordedCount;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Covers the crosshair panel: the row it reads for the selected ability, its background and its anchor. */
class CrosshairFuelPanelTest {

    /**
     * A fixed-cost ability's row reads the type icon, the name, the ability icon and the count, with each
     * consumed item's icon after the count, and no container or goo amount.
     * panel-and-petals-count-uses-or-seconds
     * reagent-count-is-the-smaller-bound
     */
    @Nested
    class CountRow {

        private static final int THOUSAND = 1000;
        private static final int PLENTY = Integer.MAX_VALUE;
        private static final String NOT_READ = "unread";
        private static final Identifier BOOK = Identifier.withDefaultNamespace("book");

        private static ClientAbility ability(int cost, AbilityBadge badge, List<Identifier> consumes, int upkeep) {
            return new ClientAbility(Identifier.fromNamespaceAndPath(Goo.MODID, "counted"), "ability.goo.counted", "",
                    0, List.of(), List.of(), cost, badge == AbilityBadge.BREW ? Delivery.of(DeliveryKind.SELF) : Delivery.ARC,
                    badge, List.of(), AbilityArea.NONE, IndicatorShowing.HELD, consumes, upkeep);
        }

        private static List<FuelCell> head(ClientAbility ability) {
            return List.of(new TypeIcon(GooTypes.UNSTABLE), new AbilityName("ability.goo.counted"),
                    new AbilityIcon(ability.iconTexture()));
        }

        private static List<FuelCell> concat(List<FuelCell> head, FuelCell... tail) {
            List<FuelCell> row = new ArrayList<>(head);
            row.addAll(List.of(tail));
            return row;
        }

        @Test
        void oneShotReadsItsUses() {
            ClientAbility oneShot = ability(THOUSAND, AbilityBadge.WORLD, List.of(), 0);
            assertEquals(concat(head(oneShot), new Count("x3")),
                    CrosshairFuelPanel.rowCells(GooTypes.UNSTABLE, oneShot, 3500, PLENTY, NOT_READ));
        }

        @Test
        void enchantReadsItsUsesThenTheBook() {
            ClientAbility enchant = ability(THOUSAND, AbilityBadge.WORLD, List.of(BOOK), 0);
            assertEquals(concat(head(enchant), new Count("x3"), new ItemIcon(BOOK)),
                    CrosshairFuelPanel.rowCells(GooTypes.UNSTABLE, enchant, 9000, 3, NOT_READ));
        }

        @Test
        void brewReadsItsSeconds() {
            ClientAbility brew = ability(0, AbilityBadge.BREW, List.of(), 1);
            assertEquals(concat(head(brew), new Count("12s")),
                    CrosshairFuelPanel.rowCells(GooTypes.UNSTABLE, brew, 250, PLENTY, NOT_READ));
        }

        /** The panel's count and the petal's count for one ability and holdings are one text. */
        @Test
        void panelCountMatchesThePetalCount() {
            ClientAbility oneShot = ability(THOUSAND, AbilityBadge.WORLD, List.of(), 0);
            String petalCount = AffordedCount.reckon(oneShot, 3500, PLENTY).orElseThrow().label();
            List<FuelCell> row = CrosshairFuelPanel.rowCells(GooTypes.UNSTABLE, oneShot, 3500, PLENTY, NOT_READ);

            assertEquals(new Count(petalCount), row.get(row.size() - 1));
            assertTrue(oneShot.petalLabel(3500, PLENTY).endsWith(" | " + petalCount));
        }
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
}
