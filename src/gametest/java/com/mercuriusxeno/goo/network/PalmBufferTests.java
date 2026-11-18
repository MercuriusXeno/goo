package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * The palm blob as a buffer, on the server: each ability's own fill from a
 * 10% floor to 100%, born full, a knife taking 9% and a javelin 30% as each
 * forms; the weapons held reserve their shares, so the fill refills only to
 * 100% less them; a throw frees its share and returns nothing, a release
 * returns every held share; the fill refills only while its ability is
 * selected in hand; and a weapon waiting on the refill is paid for only as it forms.
 * decision palm-blob-is-a-buffer-that-right-click-turns-into-weapons
 * decision held-weapons-reserve-their-share-of-the-buffer
 * decision each-ability-has-its-own-buffer-that-fills-only-while-held
 * decision weapons-form-at-their-standing-pace-when-the-buffer-affords-a-share
 */
@RegistersGameTests
public final class PalmBufferTests {

    private static final Identifier METAL_DART = Identifier.fromNamespaceAndPath(Goo.MODID, "metal_dart");
    private static final Identifier CRYSTAL_KNIVES = Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_knives");
    private static final BlockPos CASTER_POS = new BlockPos(8, 1, 1);
    private static final int PRICES = 40;
    private static final double WITHIN = 1e-6;
    private static final int JAVELIN_TICKS = 10;
    private static final int KNIFE_TICKS = 6;
    private static final int THREE_JAVELINS_TICKS = 30;
    private static final int TEN_KNIVES_TICKS = 60;
    private static final int A_HUNDRED_TICKS = 100;
    private static final int WAIT_FOR_A_SHARE = 30;
    private static final int TWENTY_TICKS = 20;
    private static final double FLOOR = 10;
    private static final double FULL = 100;
    private static final double JAVELIN_SHARE = 30;
    private static final double KNIFE_SHARE = 9;
    private static final double A_TICKS_REFILL = 1;

    private static final String DART_BUFFER_DROPS_A_SHARE_PER_JAVELIN = "palm_buffer_drops_a_share_per_javelin";
    private static final String KNIVES_BUFFER_DROPS_A_SHARE_PER_KNIFE = "palm_buffer_drops_a_share_per_knife";
    private static final String HELD_WEAPONS_HOLD_THE_BUFFER_AT_THE_FLOOR =
            "palm_buffer_held_weapons_hold_it_at_the_floor";
    private static final String A_WEAPON_SHORT_OF_A_SHARE_WAITS_FOR_THE_REFILL =
            "palm_buffer_a_weapon_short_of_a_share_waits_for_the_refill";
    private static final String A_THROW_RETURNS_NOTHING_AND_A_RELEASE_EVERY_SHARE =
            "palm_buffer_a_throw_returns_nothing_and_a_release_every_share";
    private static final String EACH_ABILITY_HOLDS_ITS_OWN_BUFFER = "palm_buffer_each_ability_holds_its_own";
    private static final String A_WAITING_KNIFE_IS_PAID_FOR_AS_IT_FORMS =
            "palm_buffer_a_waiting_knife_is_paid_for_as_it_forms";

    private PalmBufferTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, DART_BUFFER_DROPS_A_SHARE_PER_JAVELIN,
                PalmBufferTests::dartBufferDropsASharePerJavelin);
        GooTestFunctions.registerTest(registrar, KNIVES_BUFFER_DROPS_A_SHARE_PER_KNIFE,
                PalmBufferTests::knivesBufferDropsASharePerKnife);
        GooTestFunctions.registerTest(registrar, HELD_WEAPONS_HOLD_THE_BUFFER_AT_THE_FLOOR,
                PalmBufferTests::heldWeaponsHoldTheBufferAtTheFloor);
        GooTestFunctions.registerTest(registrar, A_WEAPON_SHORT_OF_A_SHARE_WAITS_FOR_THE_REFILL,
                PalmBufferTests::aWeaponShortOfAShareWaitsForTheRefill);
        GooTestFunctions.registerTest(registrar, A_THROW_RETURNS_NOTHING_AND_A_RELEASE_EVERY_SHARE,
                PalmBufferTests::aThrowReturnsNothingAndAReleaseEveryShare);
        GooTestFunctions.registerTest(registrar, EACH_ABILITY_HOLDS_ITS_OWN_BUFFER,
                PalmBufferTests::eachAbilityHoldsItsOwnBuffer);
        GooTestFunctions.registerTest(registrar, A_WAITING_KNIFE_IS_PAID_FOR_AS_IT_FORMS,
                PalmBufferTests::aWaitingKnifeIsPaidForAsItForms);
    }

    /**
     * From a full buffer, Dart forms a javelin at ticks 10, 20 and 30 and its
     * buffer reads 70%, 40% and 10% as each forms.
     *
     * @param helper the gametest helper
     */
    public static void dartBufferDropsASharePerJavelin(GameTestHelper helper) {
        ServerPlayer caster = casterSelecting(helper, GooTypes.METAL, METAL_DART);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        List<String> read = new ArrayList<>();
        hold(caster, GooTypes.METAL, METAL_DART, true);
        for (int javelin = 1; javelin <= 3; javelin++) {
            helper.runAfterDelay((long) javelin * JAVELIN_TICKS, () -> read.add(state.knifeHolds().formed(caster)
                    + "@" + PalmBuffer.percentOf(caster, METAL_DART)));
        }
        helper.runAfterDelay(THREE_JAVELINS_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(read.equals(List.of("1@70.0", "2@40.0", "3@10.0")),
                    "Dart should form 1, 2 and 3 javelins at 70%, 40% and 10%, read " + read);
            helper.succeed();
        });
    }

    /**
     * From a full buffer, Knives forms a knife every 6 ticks and its buffer
     * drops 9% per knife, reading 10% as the tenth forms.
     *
     * @param helper the gametest helper
     */
    public static void knivesBufferDropsASharePerKnife(GameTestHelper helper) {
        ServerPlayer caster = casterSelecting(helper, GooTypes.CRYSTAL, CRYSTAL_KNIVES);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        List<String> read = new ArrayList<>();
        List<String> expected = new ArrayList<>();
        hold(caster, GooTypes.CRYSTAL, CRYSTAL_KNIVES, true);
        for (int knife = 1; knife <= StackKnives.KNIVES; knife++) {
            expected.add(knife + "@" + (FULL - knife * KNIFE_SHARE));
            helper.runAfterDelay((long) knife * KNIFE_TICKS, () -> read.add(state.knifeHolds().formed(caster)
                    + "@" + PalmBuffer.percentOf(caster, CRYSTAL_KNIVES)));
        }
        helper.runAfterDelay(TEN_KNIVES_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(read.equals(expected), "Knives should form a knife each 6 ticks, 9% each, reading "
                    + expected + ", read " + read);
            helper.succeed();
        });
    }

    /**
     * With three javelins held, Dart's buffer still reads 10% a hundred ticks
     * on, and Knives' with ten knives held: the weapons held reserve the rest.
     *
     * @param helper the gametest helper
     */
    public static void heldWeaponsHoldTheBufferAtTheFloor(GameTestHelper helper) {
        ServerPlayer dart = casterSelecting(helper, GooTypes.METAL, METAL_DART);
        ServerPlayer knives = casterSelecting(helper, GooTypes.CRYSTAL, CRYSTAL_KNIVES);
        hold(dart, GooTypes.METAL, METAL_DART, true);
        hold(knives, GooTypes.CRYSTAL, CRYSTAL_KNIVES, true);
        helper.runAfterDelay(TEN_KNIVES_TICKS + A_HUNDRED_TICKS, () -> {
            double dartFill = PalmBuffer.percentOf(dart, METAL_DART);
            double knivesFill = PalmBuffer.percentOf(knives, CRYSTAL_KNIVES);
            helper.getLevel().getServer().getPlayerList().remove(dart);
            helper.getLevel().getServer().getPlayerList().remove(knives);
            helper.assertTrue(Math.abs(dartFill - FLOOR) < WITHIN && Math.abs(knivesFill - FLOOR) < WITHIN,
                    "Three javelins or ten knives held should hold their buffer at 10% a hundred ticks on, read "
                            + dartFill + " and " + knivesFill);
            helper.succeed();
        });
    }

    /**
     * After one javelin is thrown from three held, right click still down,
     * the buffer climbs 1% a tick from 10% and the next javelin forms 30 ticks
     * after the throw, as the buffer reaches 40%, leaving it at 10% again.
     *
     * @param helper the gametest helper
     */
    public static void aWeaponShortOfAShareWaitsForTheRefill(GameTestHelper helper) {
        ServerPlayer caster = casterSelecting(helper, GooTypes.METAL, METAL_DART);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        List<String> read = new ArrayList<>();
        hold(caster, GooTypes.METAL, METAL_DART, true);
        helper.runAfterDelay(THREE_JAVELINS_TICKS, () -> {
            KnivesThrowHandler.throwKnife(caster);
            helper.runAfterDelay(WAIT_FOR_A_SHARE - 1, () -> read.add(state.knifeHolds().formed(caster) + "@"
                    + PalmBuffer.percentOf(caster, METAL_DART)));
            helper.runAfterDelay(WAIT_FOR_A_SHARE, () -> {
                read.add(state.knifeHolds().formed(caster) + "@" + PalmBuffer.percentOf(caster, METAL_DART));
                helper.getLevel().getServer().getPlayerList().remove(caster);
                helper.assertTrue(read.equals(List.of("2@39.0", "3@10.0")), "The next javelin should wait for the"
                        + " refill, 2 held at 39% 29 ticks after the throw and 3 at 10% at 30, read " + read);
                helper.succeed();
            });
        });
    }

    /**
     * A javelin thrown from three held adds nothing to the fill but the tick's
     * refill, and releasing right click with the two left held raises the buffer by 60%.
     *
     * @param helper the gametest helper
     */
    public static void aThrowReturnsNothingAndAReleaseEveryShare(GameTestHelper helper) {
        ServerPlayer caster = casterSelecting(helper, GooTypes.METAL, METAL_DART);
        hold(caster, GooTypes.METAL, METAL_DART, true);
        helper.runAfterDelay(THREE_JAVELINS_TICKS, () -> {
            double beforeTheThrow = PalmBuffer.percentOf(caster, METAL_DART);
            KnivesThrowHandler.throwKnife(caster);
            helper.runAfterDelay(1, () -> {
                double afterTheThrow = PalmBuffer.percentOf(caster, METAL_DART);
                hold(caster, GooTypes.METAL, METAL_DART, false);
                double afterTheRelease = PalmBuffer.percentOf(caster, METAL_DART);
                helper.getLevel().getServer().getPlayerList().remove(caster);
                helper.assertTrue(afterTheThrow - beforeTheThrow <= A_TICKS_REFILL + WITHIN,
                        "A throw should add nothing but a tick's refill, " + beforeTheThrow + " to " + afterTheThrow);
                helper.assertTrue(Math.abs(afterTheRelease - afterTheThrow - 2 * JAVELIN_SHARE) < WITHIN,
                        "A release with two held should return 60%, " + afterTheThrow + " to " + afterTheRelease);
                helper.succeed();
            });
        });
    }

    /**
     * Each ability has its own buffer, which moves only while its ability is
     * selected in hand: with Dart's drained to 10% and no javelin held,
     * selecting Knives finds Knives' at 100%; a hundred ticks later Dart's
     * still reads 10%; back on Dart, right click up, Dart's climbs 1% a tick.
     *
     * @param helper the gametest helper
     */
    public static void eachAbilityHoldsItsOwnBuffer(GameTestHelper helper) {
        ServerPlayer caster = casterSelecting(helper, GooTypes.METAL, METAL_DART);
        List<String> read = new ArrayList<>();
        hold(caster, GooTypes.METAL, METAL_DART, true);
        helper.runAfterDelay(THREE_JAVELINS_TICKS, () -> {
            for (int javelin = 0; javelin < 3; javelin++) {
                KnivesThrowHandler.throwKnife(caster);
            }
            hold(caster, GooTypes.METAL, METAL_DART, false);
            select(caster, GooTypes.CRYSTAL, CRYSTAL_KNIVES);
            read.add("knives " + PalmBuffer.percentOf(caster, CRYSTAL_KNIVES));
        });
        helper.runAfterDelay(THREE_JAVELINS_TICKS + A_HUNDRED_TICKS, () -> {
            read.add("dart " + PalmBuffer.percentOf(caster, METAL_DART));
            select(caster, GooTypes.METAL, METAL_DART);
        });
        helper.runAfterDelay(THREE_JAVELINS_TICKS + A_HUNDRED_TICKS + TWENTY_TICKS, () -> {
            read.add("dart " + PalmBuffer.percentOf(caster, METAL_DART));
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(read.equals(List.of("knives 100.0", "dart 10.0", "dart 30.0")),
                    "Knives' buffer should stand full, Dart's frozen at 10% while unselected, then climb to 30%"
                            + " in 20 ticks back on Dart; read " + read);
            helper.succeed();
        });
    }

    /**
     * A knife waiting on the refill after a throw from a full pyramid is paid
     * for only as it forms: the player's crystal goo reads unchanged over the
     * wait and one knife's price less on the tick the knife forms.
     *
     * @param helper the gametest helper
     */
    public static void aWaitingKnifeIsPaidForAsItForms(GameTestHelper helper) {
        ServerPlayer caster = casterSelecting(helper, GooTypes.CRYSTAL, CRYSTAL_KNIVES);
        GooServerState state = GooServerState.of(helper.getLevel().getServer());
        hold(caster, GooTypes.CRYSTAL, CRYSTAL_KNIVES, true);
        List<Integer> heldOverTheWait = new ArrayList<>();
        List<Long> goo = new ArrayList<>();
        helper.runAfterDelay(TEN_KNIVES_TICKS, () -> {
            KnivesThrowHandler.throwKnife(caster);
            int afterTheThrow = state.knifeHolds().formed(caster);
            for (int tick = 1; tick <= KNIFE_SHARE; tick++) {
                helper.runAfterDelay(tick, () -> {
                    heldOverTheWait.add(state.knifeHolds().formed(caster));
                    goo.add(gooHeld(caster));
                });
            }
            helper.runAfterDelay((long) KNIFE_SHARE + 1, () -> {
                helper.getLevel().getServer().getPlayerList().remove(caster);
                int formedAt = heldOverTheWait.indexOf(afterTheThrow + 1);
                helper.assertTrue(formedAt > 0, "a knife should form over the refill, held " + heldOverTheWait);
                helper.assertTrue(goo.subList(0, formedAt).stream().distinct().count() == 1
                        && goo.get(formedAt) < goo.getFirst(),
                        "the goo should stand unchanged over the wait and drop as the knife forms; goo " + goo
                                + ", held " + heldOverTheWait);
                helper.succeed();
            });
        });
    }

    private static long gooHeld(ServerPlayer caster) {
        return GooSourceScanner.aggregateAvailable(caster).getOrDefault(GooTypes.CRYSTAL, 0);
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer casterSelecting(GameTestHelper helper, ResourceKey<GooTypeDefinition> type,
                                                Identifier ability) {
        AbilityDefinition definition = AbilityRegistry.of(helper.getLevel()).getAbility(ability);
        helper.assertTrue(definition != null, "Ability registry must hold " + ability);
        int price = definition.cost().exactIn(GooValues.of(helper.getLevel())).ceiling();
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(CASTER_POS));
        caster.snapTo(stand.x, stand.y, stand.z, 0f, 0f);
        caster.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        select(caster, type, ability);
        caster.getInventory().add(GooStacks.createForOutput(type, PRICES * Math.max(1, price)));
        KnownRecipes.teachRequires(caster, definition);
        return caster;
    }

    private static void select(ServerPlayer caster, ResourceKey<GooTypeDefinition> type, Identifier ability) {
        GooGloveItem.setSelection(caster.getMainHandItem(), GloveSelection.ofAbility(type, ability));
    }

    private static void hold(ServerPlayer caster, ResourceKey<GooTypeDefinition> type, Identifier ability,
                             boolean holding) {
        KnivesHoldHandler.hold(caster, new KnivesHoldPayload(holding, GooTypes.id(type), ability.toString()));
    }

    /** The knives a full pyramid holds. */
    private static final class StackKnives {
        static final int KNIVES = 10;

        private StackKnives() {
        }
    }
}
