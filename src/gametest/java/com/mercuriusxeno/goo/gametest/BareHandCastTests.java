package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.gate.GateSight;
import com.mercuriusxeno.goo.item.GooItem;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.lunar.Lunar;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
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
 * through the server's throw entry, paying as a glove cast pays, and is refused a cast of any other type or a cast
 * holding neither glove nor raw goo. Astral's gate is the ability cast, its cost a flat 1000 mB.
 * decision raw-goo-items-work-bare-handed-for-the-held-type
 */
@RegistersGameTests
public final class BareHandCastTests {
    private static final String RAW_GOO_CASTS_ITS_TYPE = "raw_goo_bare_handed_casts_its_type";
    private static final String BARE_HAND_REFUSES_ANOTHER_TYPE = "bare_hand_refuses_another_type_and_an_empty_hand";
    private static final String RAW_ASTRAL_CARRIES_YOU_HOME = "raw_astral_bare_handed_carries_you_home_from_lunar";

    private static final Identifier ASTRAL_GATE = Identifier.parse("goo:astral_gate");
    /** Two gate casts' worth of goo, in mB. */
    private static final int HELD_MB = 2 * GooStacks.THOUSAND;
    /** The gate's cost, astral_gate.json's behavior cost. */
    private static final int GATE_COST_MB = GooStacks.THOUSAND;
    /** Past astral_gate.json's fixation, so the gate has carried the caster by then. */
    private static final int PAST_THE_FIXATION = 80;
    /** Where in lunar the stranded caster stands, high over its ground. */
    private static final Vec3 STRANDED_AT = new Vec3(0.5, 160, 0.5);
    private static final int NO_ENTITY = -1;

    private static final String ABILITY_REQUIRED = "goo:astral_gate must be loaded";
    private static final String LUNAR_REQUIRED = "The server should hold goo:lunar";
    private static final String SHOULD_RUN = "A gloveless cast of the held raw goo's type should run the gate";
    private static final String SHOULD_PAY = "The held raw goo should drop from %d mB by the gate's %d mB, holds %d";
    private static final String OTHER_TYPE_REFUSED =
            "A gloveless cast of astral while holding raw zoo goo should run nothing";
    private static final String EMPTY_HAND_REFUSED = "A cast holding neither glove nor raw goo should run nothing";
    private static final String NOTHING_SPENT = "A refused cast should spend nothing: astral %d of %d, zoo %d of %d";
    private static final String HOME = "The gate should carry the gloveless caster from lunar to %s, stands in %s";

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
    }

    /**
     * A gloveless player holding raw astral goo and looking at the moon casts the gate: its fixation stands and the
     * held goo drops by the gate's cost.
     *
     * @param helper the gametest helper
     */
    public static void rawGooCastsItsType(GameTestHelper helper) {
        ServerPlayer caster = gloveless(helper);
        ItemStack held = GooStacks.createForOutput(GooTypes.ASTRAL, HELD_MB);
        caster.setItemInHand(InteractionHand.MAIN_HAND, held);
        face(caster, moonFrom(caster));
        cast(caster, GooTypes.ASTRAL);
        boolean ran = caster.getData(GooAttachments.GATE_FIXATION).stands();
        int left = GooItem.getVolume(caster.getMainHandItem());
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.assertTrue(ran, SHOULD_RUN);
        helper.assertTrue(left == HELD_MB - GATE_COST_MB, String.format(SHOULD_PAY, HELD_MB, GATE_COST_MB, left));
        helper.succeed();
    }

    /**
     * A gloveless player holding raw zoo goo, with raw astral goo elsewhere in the inventory, is refused an astral
     * gate; so is a player whose hands hold nothing, the same astral goo carried. Neither cast spends any goo.
     *
     * @param helper the gametest helper
     */
    public static void bareHandRefusesAnotherTypeAndAnEmptyHand(GameTestHelper helper) {
        ServerPlayer caster = gloveless(helper);
        ItemStack heldZoo = GooStacks.createForOutput(GooTypes.ZOO, HELD_MB);
        caster.setItemInHand(InteractionHand.MAIN_HAND, heldZoo);
        caster.getInventory().add(GooStacks.createForOutput(GooTypes.ASTRAL, HELD_MB));
        face(caster, moonFrom(caster));
        cast(caster, GooTypes.ASTRAL);
        boolean otherTypeRan = caster.getData(GooAttachments.GATE_FIXATION).stands();
        int zoo = GooItem.getVolume(heldZoo);
        caster.setItemInHand(InteractionHand.MAIN_HAND, ItemStack.EMPTY);
        cast(caster, GooTypes.ASTRAL);
        boolean emptyHandRan = caster.getData(GooAttachments.GATE_FIXATION).stands();
        int astral = carried(caster, GooTypes.ASTRAL);
        helper.getLevel().getServer().getPlayerList().remove(caster);
        helper.assertFalse(otherTypeRan, OTHER_TYPE_REFUSED);
        helper.assertFalse(emptyHandRan, EMPTY_HAND_REFUSED);
        helper.assertTrue(astral == HELD_MB && zoo == HELD_MB,
                String.format(NOTHING_SPENT, astral, HELD_MB, zoo, HELD_MB));
        helper.succeed();
    }

    /**
     * A gloveless player stranded in lunar, holding raw astral goo and looking at the blue star, casts the gate and
     * stands in the overworld once its fixation runs out.
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
        face(caster, Lunar.OVERWORLD_STAR_DIRECTION);
        cast(caster, GooTypes.ASTRAL);
        helper.runAfterDelay(PAST_THE_FIXATION, () -> {
            boolean home = caster.level().dimension() == Level.OVERWORLD;
            helper.assertTrue(home, String.format(HOME, Level.OVERWORLD.identifier(),
                    caster.level().dimension().identifier()));
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer gloveless(GameTestHelper helper) {
        helper.assertTrue(AbilityRegistry.of(helper.getLevel()).getAbility(ASTRAL_GATE) != null, ABILITY_REQUIRED);
        ServerPlayer caster = helper.makeMockServerPlayerInLevel();
        caster.connection.markClientLoaded();
        return caster;
    }

    /**
     * Sends the gate's cast through the server's throw entry. Paying the cost lays the caster's remainders, an
     * attachment NeoForge syncs in full on every dimension change, which the mock connection cannot carry, so the
     * test drops the remainders the gate never reads.
     *
     * @param caster  the caster
     * @param gooType the goo type the cast names
     */
    private static void cast(ServerPlayer caster, ResourceKey<GooTypeDefinition> gooType) {
        GooThrowHandler.execute(caster, new GooThrowPayload(GooTypes.id(gooType), NO_ENTITY,
                caster.blockPosition(), NO_ENTITY, false, ASTRAL_GATE.toString(), caster.getEyePosition()));
        caster.removeData(GooAttachments.REMAINDERS);
    }

    private static int carried(ServerPlayer caster, ResourceKey<GooTypeDefinition> gooType) {
        return GooSourceScanner.aggregateAvailable(caster).getOrDefault(gooType, 0);
    }

    private static Vec3 moonFrom(ServerPlayer caster) {
        return GateSight.moonDirection(caster.level().environmentAttributes()
                .getValue(EnvironmentAttributes.MOON_ANGLE, caster.position()));
    }

    private static void face(ServerPlayer caster, Vec3 direction) {
        Vec2 facing = direction.rotation();
        caster.setXRot(facing.x);
        caster.setYRot(facing.y);
    }
}
