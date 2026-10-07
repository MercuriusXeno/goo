package com.mercuriusxeno.goo.client.network;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.data.KnownItems;
import com.mercuriusxeno.goo.network.AbilitySyncPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.resources.Identifier;
import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The client lists a synced type's abilities in the fan order the server
 * registry does, badge rank then order (decision fan-sorts-badge-then-order),
 * and offers the radial every one, locking those whose required items the player does not all know.
 */
class ClientAbilitiesTest {

    private static final Identifier GLASS = Identifier.withDefaultNamespace("glass");
    private static final Identifier SAND = Identifier.withDefaultNamespace("sand");
    private static final Identifier CLAY = Identifier.withDefaultNamespace("clay");

    private static AbilitySyncPayload.Entry entry(String name, int order, AbilityBadge badge) {
        return gatedEntry(name, order, badge, List.of());
    }

    private static AbilitySyncPayload.Entry gatedEntry(String name, int order, AbilityBadge badge,
                                                       List<Identifier> requires) {
        return new AbilitySyncPayload.Entry("goo:" + name, "goo:rock", name, "", order, List.of(),
                List.of(), 0, Delivery.ARC, badge, requires);
    }

    /** Each offered ability's path and locked flag, as "path:locked" or "path:open", in fan order. */
    private static List<String> offeredPetals(KnownItems known) {
        AbilitySyncPayload payload = new AbilitySyncPayload(List.of(
                entry("free", 0, AbilityBadge.WORLD),
                gatedEntry("gated", 1, AbilityBadge.WORLD, List.of(GLASS, SAND))));
        return ClientAbilities.fromPayload(payload).offeredForType(GooTypes.ROCK, known).stream()
                .map(offered -> offered.ability().id().getPath() + (offered.locked() ? ":locked" : ":open")).toList();
    }

    /** A gated ability stays on the radial, locked until every item it requires is known (decision locked-petal-stays-on-the-wheel). */
    @Test
    void gatedAbilityOfferedLockedToAPlayerWhoKnowsNothing() {
        assertEquals(List.of("free:open", "gated:locked"), offeredPetals(KnownItems.NONE));
    }

    @Test
    void gatedAbilityStaysLockedWhileOneRequiredItemIsUnknown() {
        assertEquals(List.of("free:open", "gated:locked"), offeredPetals(KnownItems.NONE.with(GLASS)));
    }

    @Test
    void gatedAbilityUnlocksOnceEveryRequiredItemIsKnown() {
        assertEquals(List.of("free:open", "gated:open"), offeredPetals(KnownItems.NONE.with(GLASS).with(SAND)));
    }

    private static OfferedAbility offeredGlassSandClay(KnownItems known) {
        AbilitySyncPayload payload = new AbilitySyncPayload(List.of(
                gatedEntry("gated", 0, AbilityBadge.WORLD, List.of(GLASS, SAND, CLAY))));
        return ClientAbilities.fromPayload(payload).offeredForType(GooTypes.ROCK, known).getFirst();
    }

    /** A locked petal lists every required item, each flagged learned or not (decision locked-petal-lists-the-unlearned-items). */
    @Test
    void offeredAbilityListsEveryRequiredItemWithWhetherItIsLearned() {
        OfferedAbility offered = offeredGlassSandClay(KnownItems.NONE.with(SAND));

        assertEquals(List.of(new OfferedAbility.RequiredItem(GLASS, false), new OfferedAbility.RequiredItem(SAND, true),
                new OfferedAbility.RequiredItem(CLAY, false)), offered.required());
        assertTrue(offered.locked());
    }

    /** Once every listed item is melted the petal unlocks (decision locked-petal-lists-the-unlearned-items). */
    @Test
    void meltingEveryRequiredItemMarksEachLearnedAndUnlocks() {
        OfferedAbility offered = offeredGlassSandClay(KnownItems.NONE.with(GLASS).with(SAND).with(CLAY));

        assertTrue(offered.required().stream().allMatch(OfferedAbility.RequiredItem::learned));
        assertFalse(offered.locked());
    }

    @Test
    void mobAtOrderTenLeadsWorldAtOrdersZeroToTwo() {
        AbilitySyncPayload payload = new AbilitySyncPayload(List.of(
                entry("world_two", 2, AbilityBadge.WORLD),
                entry("mob_ten", 10, AbilityBadge.MOB),
                entry("world_zero", 0, AbilityBadge.WORLD),
                entry("world_one", 1, AbilityBadge.WORLD)));

        assertEquals(List.of("mob_ten", "world_zero", "world_one", "world_two"),
                ClientAbilities.fromPayload(payload).forType(GooTypes.ROCK).stream()
                        .map(ability -> ability.id().getPath()).toList());
    }
}
