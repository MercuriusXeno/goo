package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.ability.astral.LunarSight;
import com.mercuriusxeno.goo.item.GooCasters;
import com.mercuriusxeno.goo.item.GooItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.network.GloveSelectHandler;
import com.mercuriusxeno.goo.network.GloveSelectPayload;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.network.GooStreamPayload;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.attribute.EnvironmentAttributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.portal.TeleportTransition;
import net.minecraft.world.phys.Vec2;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for casting bare handed: a player with no glove who holds raw goo casts that goo type's abilities
 * through the server's entry, paying as a glove cast pays, and is refused a cast of any other type or a cast holding
 * neither glove nor raw goo. Astral's Lunar is the ability cast, a hold driven one held tick a tick through the
 * stream entry as a client holding right click sends them, its cost a flat 1000 mB paid at the carry. The selection
 * the server takes for raw goo is the ability the next bare-handed cast casts.
 * decision raw-goo-items-work-bare-handed-for-the-held-type
 */
@RegistersGameTests
public final class BareHandCastTests {
    private static final String RAW_GOO_CASTS_ITS_TYPE = "raw_goo_bare_handed_casts_its_type";
    private static final String BARE_HAND_REFUSES_ANOTHER_TYPE = "bare_hand_refuses_another_type_and_an_empty_hand";
    private static final String RAW_ASTRAL_CARRIES_YOU_HOME = "raw_astral_bare_handed_carries_you_home_from_lunar";
    private static final String BARE_HAND_SELECTION_CASTS = "bare_hand_selection_is_what_the_next_cast_casts";

    private static final Identifier ASTRAL_LUNAR = Identifier.parse("goo:astral_lunar");
    /** Astral's first ability on the radial, ahead of Lunar. */
    private static final Identifier ASTRAL_SCRY = Identifier.parse("goo:astral_scry");
    /** Two Lunar casts' worth of goo, in mB. */
    private static final int HELD_MB = 2 * GooStacks.THOUSAND;
    /** Lunar's cost, astral_lunar.json's behavior cost, paid at the carry. */
    private static final int LUNAR_COST_MB = GooStacks.THOUSAND;
    /** astral_lunar.json's fixation, the held ticks a full pull takes. */
    private static final int FIXATION = 100;
    /** Held ticks past the fixation, so the hold still stands on the tick the fixation runs out. */
    private static final int HOLD_PAST = 3;
    /** Past a full hold, so Lunar has carried the caster by then. */
    private static final int PAST_THE_FIXATION = FIXATION + 20;
    /** Inside the fixation, when a hold Lunar took stands fixed. */
    private static final int INSIDE_THE_FIXATION = 20;
    /** Where in lunar the stranded caster stands, high over its ground. */
    private static final Vec3 STRANDED_AT = new Vec3(0.5, 160, 0.5);

    private static final String ABILITY_REQUIRED = "goo:astral_lunar must be loaded";
    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String SHOULD_RUN = "A gloveless hold of the held raw goo's type should run Lunar, stands in %s";
    private static final String SHOULD_PAY = "The held raw goo should drop from %d mB by Lunar's %d mB, holds %d";
    private static final String OTHER_TYPE_REFUSED =
            "A gloveless hold of astral while holding raw zoo goo should run nothing";
    private static final String EMPTY_HAND_REFUSED = "A hold holding neither glove nor raw goo should run nothing";
    private static final String NOTHING_SPENT = "A refused hold should spend nothing: astral %d of %d, zoo %d of %d";
    private static final String SELECTED = "The bare hand's selection should name %s once selected, names %s";
    private static final String SELECTION_RAN = "The hold the selection names, Lunar, should run";
    private static final String HOME = "Lunar should carry the gloveless caster from lunar to %s, stands in %s";

    private BareHandCastTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, RAW_GOO_CASTS_ITS_TYPE, BareHandCastTests::rawGooCastsItsType);
        GooTestFunctions.registerTest(registrar, BARE_HAND_REFUSES_ANOTHER_TYPE,
                BareHandCastTests::bareHandRefusesAnotherTypeAndAnEmptyHand);
        GooTestFunctions.registerTest(registrar, RAW_ASTRAL_CARRIES_YOU_HOME,
                BareHandCastTests::rawAstralCarriesYouHomeFromLunar);
        GooTestFunctions.registerTest(registrar, BARE_HAND_SELECTION_CASTS,
                BareHandCastTests::bareHandSelectionIsWhatTheNextCastCasts);
    }

    /**
     * A gloveless player holding raw astral goo holds Lunar on the moon: once the fixation runs out they stand in
     * lunar, and the held goo has dropped by Lunar's cost.
     *
     * @param helper the gametest helper
     */
    public static void rawGooCastsItsType(GameTestHelper helper) {
        ServerPlayer caster = gloveless(helper);
        caster.setItemInHand(InteractionHand.MAIN_HAND, GooStacks.createForOutput(GooTypes.ASTRAL, HELD_MB));
        face(caster, moonFrom(caster));
        holdFor(helper, caster, GooTypes.ASTRAL, 1, FIXATION + HOLD_PAST);
        helper.runAfterDelay(PAST_THE_FIXATION, () -> {
            boolean ran = caster.level().dimension() == Lunar.LEVEL;
            int left = GooItem.getVolume(caster.getMainHandItem());
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(ran, String.format(SHOULD_RUN, caster.level().dimension().identifier()));
            helper.assertTrue(left == HELD_MB - LUNAR_COST_MB,
                    String.format(SHOULD_PAY, HELD_MB, LUNAR_COST_MB, left));
            helper.succeed();
        });
    }

    /**
     * A gloveless player holding raw zoo goo, with raw astral goo elsewhere in the inventory, is refused a hold of
     * Lunar; so is a player whose hands hold nothing, the same astral goo carried. Neither hold stands a fixation or
     * spends any goo.
     *
     * @param helper the gametest helper
     */
    public static void bareHandRefusesAnotherTypeAndAnEmptyHand(GameTestHelper helper) {
        ServerPlayer caster = gloveless(helper);
        ItemStack heldZoo = GooStacks.createForOutput(GooTypes.ZOO, HELD_MB);
        caster.setItemInHand(InteractionHand.MAIN_HAND, heldZoo);
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.ASTRAL, HELD_MB));
        face(caster, moonFrom(caster));
        boolean[] ran = new boolean[2];
        holdFor(helper, caster, GooTypes.ASTRAL, 1, INSIDE_THE_FIXATION);
        helper.runAfterDelay(INSIDE_THE_FIXATION, () -> {
            ran[0] = caster.getData(GooAttachments.LUNAR_FIXATION).stands();
            caster.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        });
        holdFor(helper, caster, GooTypes.ASTRAL, INSIDE_THE_FIXATION + 1, INSIDE_THE_FIXATION);
        helper.runAfterDelay(2 * INSIDE_THE_FIXATION + 1, () -> {
            ran[1] = caster.getData(GooAttachments.LUNAR_FIXATION).stands();
            int astral = carried(caster, GooTypes.ASTRAL);
            int zoo = GooItem.getVolume(heldZoo);
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertFalse(ran[0], OTHER_TYPE_REFUSED);
            helper.assertFalse(ran[1], EMPTY_HAND_REFUSED);
            helper.assertTrue(astral == HELD_MB && zoo == HELD_MB,
                    String.format(NOTHING_SPENT, astral, HELD_MB, zoo, HELD_MB));
            helper.succeed();
        });
    }

    /**
     * A gloveless player stranded in lunar, holding raw astral goo and holding Lunar on the blue star, stands in the
     * overworld once the fixation runs out.
     *
     * @param helper the gametest helper
     */
    public static void rawAstralCarriesYouHomeFromLunar(GameTestHelper helper) {
        ServerPlayer caster = gloveless(helper);
        ServerLevel lunar = helper.getLevel().getServer().getLevel(Lunar.LEVEL);
        helper.assertTrue(lunar != null, LUNAR_REQUIRED);
        caster.teleport(new TeleportTransition(lunar, STRANDED_AT, Vec3.ZERO, 0F, 0F,
                TeleportTransition.DO_NOTHING));
        caster.setItemInHand(InteractionHand.MAIN_HAND, GooStacks.createForOutput(GooTypes.ASTRAL, HELD_MB));
        face(caster, moonFrom(caster));
        holdFor(helper, caster, GooTypes.ASTRAL, 1, FIXATION + HOLD_PAST);
        helper.runAfterDelay(PAST_THE_FIXATION, () -> {
            boolean home = caster.level().dimension() == Level.OVERWORLD;
            helper.assertTrue(home, String.format(HOME, Level.OVERWORLD.identifier(),
                    caster.level().dimension().identifier()));
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    /**
     * A gloveless player holding raw astral goo selects Scry, then Lunar, through the selection the server takes;
     * the hold the held selection names is Lunar, and it runs.
     *
     * @param helper the gametest helper
     */
    public static void bareHandSelectionIsWhatTheNextCastCasts(GameTestHelper helper) {
        ServerPlayer caster = gloveless(helper);
        caster.setItemInHand(InteractionHand.MAIN_HAND, GooStacks.createForOutput(GooTypes.ASTRAL, HELD_MB));
        AbilityDefinition scry = AbilityRegistry.of(helper.getLevel()).getAbility(ASTRAL_SCRY);
        if (scry != null) {
            KnownRecipes.teachRequires(caster, scry);
        }
        GloveSelectHandler.select(caster, new GloveSelectPayload(GooTypes.id(GooTypes.ASTRAL), ASTRAL_SCRY.toString()));
        GloveSelectHandler.select(caster, new GloveSelectPayload(GooTypes.id(GooTypes.ASTRAL),
                ASTRAL_LUNAR.toString()));
        GloveSelection selected = GooCasters.heldSelection(caster);
        String named = selected == null ? "nothing" : selected.abilityId();
        helper.assertTrue(ASTRAL_LUNAR.toString().equals(named), String.format(SELECTED, ASTRAL_LUNAR, named));
        face(caster, moonFrom(caster));
        for (int tick = 1; tick <= INSIDE_THE_FIXATION; tick++) {
            helper.runAfterDelay(tick, () -> GooStreamHandler.streamTick(caster, new GooStreamPayload(
                    selected.gooTypeId(), selected.abilityId(), caster.getEyePosition(),
                    caster.getEyePosition().add(caster.getLookAngle()), caster.blockPosition().below(),
                    GooStreamPayload.NO_FACE)));
        }
        helper.runAfterDelay(INSIDE_THE_FIXATION, () -> {
            boolean ran = caster.getData(GooAttachments.LUNAR_FIXATION).stands();
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.assertTrue(ran, SELECTION_RAN);
            helper.succeed();
        });
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer gloveless(GameTestHelper helper) {
        helper.assertTrue(AbilityRegistry.of(helper.getLevel()).getAbility(ASTRAL_LUNAR) != null, ABILITY_REQUIRED);
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        caster.connection.markClientLoaded();
        return caster;
    }

    /**
     * Holds Lunar for a run of ticks, one held tick a tick, as a client holding right click sends them, naming the
     * goo type given.
     *
     * @param helper  the gametest helper
     * @param caster  the caster
     * @param gooType the goo type the hold names
     * @param from    the delay of the first held tick
     * @param ticks   how many ticks the hold lasts
     */
    private static void holdFor(GameTestHelper helper, ServerPlayer caster, ResourceKey<GooTypeDefinition> gooType,
            int from, int ticks) {
        for (int tick = from; tick < from + ticks; tick++) {
            helper.runAfterDelay(tick, () -> GooStreamHandler.streamTick(caster, new GooStreamPayload(
                    GooTypes.id(gooType), ASTRAL_LUNAR.toString(), caster.getEyePosition(),
                    caster.getEyePosition().add(caster.getLookAngle()), caster.blockPosition().below(),
                    GooStreamPayload.NO_FACE)));
        }
    }

    private static int carried(ServerPlayer caster, ResourceKey<GooTypeDefinition> gooType) {
        return GooSourceScanner.aggregateAvailable(caster).getOrDefault(gooType, 0);
    }

    private static Vec3 moonFrom(ServerPlayer caster) {
        return LunarSight.moonDirection(caster.level().environmentAttributes()
                .getValue(EnvironmentAttributes.MOON_ANGLE, caster.position()));
    }

    private static void face(ServerPlayer caster, Vec3 direction) {
        Vec2 facing = direction.rotation();
        caster.setXRot(facing.x);
        caster.setYRot(facing.y);
    }
}
