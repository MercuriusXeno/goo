package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.hex.Charmed;
import com.mercuriusxeno.goo.ability.yarn.Led;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Gametests for the lingering goo effects a player applies: a mock player applies Toxin through Leaf Bio's
 * landing and Mycosis through its spray, and each tick of the effect after the
 * cast hurts the mob with its own damage type naming that player (decisions
 * lingering-goo-effect-credits-whoever-applied-it, goo-hit-keeps-its-kind-and-names-the-player-behind-it). The same
 * effect applied the same way to a mob the player led and a mob the player charmed hurts both with its type
 * and leaves the lead and the charm standing. They sit in the scheduler's package because its landing is
 * package-private.
 */
@RegistersGameTests
public final class LingeringCreditTests {
    private static final String TOXIN_NAMES_THE_APPLIER = "goo_lingering_credit_toxin_names_the_applier";
    private static final String MYCOSIS_NAMES_THE_APPLIER = "goo_lingering_credit_mycosis_names_the_applier";
    private static final String TOXIN_KEEPS_LEAD_AND_CHARM = "goo_lingering_credit_toxin_keeps_lead_and_charm";
    private static final String MYCOSIS_KEEPS_LEAD_AND_CHARM = "goo_lingering_credit_mycosis_keeps_lead_and_charm";

    private static final String LEAF_BIO = "goo:leaf_bio";
    private static final Identifier SCULK_MYCOSIS = Identifier.parse("goo:sculk_mycosis");

    /** Where the first afflicted mob stands. */
    private static final BlockPos MOB_POS = new BlockPos(4, 1, 3);
    /** Beside the first mob, where the second afflicted mob stands, inside the spray's cone too. */
    private static final BlockPos SECOND_MOB_POS = MOB_POS.north();
    /** The sprayer stands three blocks west of the first mob, looking east at it. */
    private static final BlockPos SPRAYER_POS = MOB_POS.west(3);
    private static final float FACING_EAST = -90f;
    /** Pitch down from the eye toward a husk's middle three blocks off. */
    private static final float LOOKING_AT_THE_MOB = 12f;
    private static final int SETTLE_TICKS = 1;
    private static final int SPRAY_TICKS = 3;
    private static final float LEAD_DAMAGE = 6f;
    /** Sculk goo the sprayer holds, thousands: enough to pay for the spray. */
    private static final int HELD_GOO = 25;

    private static final String ABILITY_REQUIRED = "Ability registry must hold %s";
    private static final String SHOULD_NAME_THE_APPLIER = "A tick of %s should hurt the %s as %s naming the player who "
            + "applied it, last such hit named %s";
    private static final String SHOULD_KEEP = "A tick of %s should hurt the player's %s mob as %s and leave the %s "
            + "standing; it is %s and the mob was %s by the tick";
    private static final String LEAD = "lead";
    private static final String CHARM = "charm";
    private static final String STANDING = "standing";
    private static final String ENDED = "ended";

    private LingeringCreditTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, TOXIN_NAMES_THE_APPLIER, LingeringCreditTests::toxinNamesTheApplier);
        GooTestFunctions.registerTest(registrar, MYCOSIS_NAMES_THE_APPLIER,
                LingeringCreditTests::mycosisNamesTheApplier);
        GooTestFunctions.registerTest(registrar, TOXIN_KEEPS_LEAD_AND_CHARM,
                LingeringCreditTests::toxinKeepsLeadAndCharm);
        GooTestFunctions.registerTest(registrar, MYCOSIS_KEEPS_LEAD_AND_CHARM,
                LingeringCreditTests::mycosisKeepsLeadAndCharm);
    }

    /**
     * A Leaf Bio blob a mock player threw lands on a husk, and the toxin's tick hurts it with magic naming the
     * player.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void toxinNamesTheApplier(GameTestHelper helper) {
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, MOB_POS);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        helper.runAfterDelay(SETTLE_TICKS, () -> land(helper, player, LEAF_BIO, husk));
        succeedOnceNamed(helper, player, husk, "Toxin", DamageTypes.MAGIC);
    }

    /**
     * A mock player sprays Mycosis at a husk, and the spore poison's tick hurts it with magic naming the player.
     *
     * @param helper the gametest helper
     */
    public static void mycosisNamesTheApplier(GameTestHelper helper) {
        Mob husk = still(helper.spawnWithNoFreeWill(EntityType.HUSK, MOB_POS));
        ServerPlayer player = sprayer(helper);
        spray(helper, player);
        succeedOnceNamed(helper, player, husk, "Mycosis", DamageTypes.MAGIC);
    }

    /**
     * Leaf Bio blobs a mock player threw land on a husk it led and a husk it charmed, and the toxin's tick
     * hurts both with magic, leaving the lead and the charm standing.
     *
     * @param helper the gametest helper
     */
    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    public static void toxinKeepsLeadAndCharm(GameTestHelper helper) {
        Mob led = still(helper.spawnWithNoFreeWill(EntityType.HUSK, MOB_POS));
        Mob charmed = still(helper.spawnWithNoFreeWill(EntityType.HUSK, SECOND_MOB_POS));
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        makeOwn(player, led, charmed);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            land(helper, player, LEAF_BIO, led);
            land(helper, player, LEAF_BIO, charmed);
        });
        succeedOnceKept(helper, player, "Toxin", DamageTypes.MAGIC, led, charmed);
    }

    /**
     * A mock player sprays Mycosis at a husk it led and a husk it charmed, and the spore poison's tick hurts
     * both with magic, leaving the lead and the charm standing.
     *
     * @param helper the gametest helper
     */
    public static void mycosisKeepsLeadAndCharm(GameTestHelper helper) {
        Mob led = still(helper.spawnWithNoFreeWill(EntityType.HUSK, MOB_POS));
        Mob charmed = still(helper.spawnWithNoFreeWill(EntityType.HUSK, SECOND_MOB_POS));
        ServerPlayer player = sprayer(helper);
        makeOwn(player, led, charmed);
        spray(helper, player);
        succeedOnceKept(helper, player, "Mycosis", DamageTypes.MAGIC, led, charmed);
    }

    /**
     * Lands one blob of the ability the player threw on the mob through the scheduler's landing, the path an
     * arrived throw takes.
     *
     * @param helper    the gametest helper
     * @param player    the player who threw it
     * @param abilityId the ability thrown
     * @param mob       the struck mob
     */
    private static void land(GameTestHelper helper, ServerPlayer player, String abilityId, Mob mob) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(abilityId));
        helper.assertTrue(ability != null, String.format(ABILITY_REQUIRED, abilityId));
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), player, ability.gooType(),
                mob.getId(), mob.blockPosition(), Direction.UP, abilityId));
    }

    private static Mob still(Mob mob) {
        mob.setNoAi(true);
        return mob;
    }

    private static void makeOwn(ServerPlayer player, Mob led, Mob charmed) {
        led.setData(GooAttachments.LED, new Led(player.getUUID(), LEAD_DAMAGE));
        charmed.setData(GooAttachments.CHARMED, new Charmed(player.getUUID()));
    }

    /**
     * Passes the test once the latest hit of the type the mob took names the player, checked each tick.
     *
     * @param helper the gametest helper
     * @param player the player who applied the effect
     * @param mob    the afflicted mob
     * @param effect the effect, for the failure message
     * @param type   the damage type the effect's tick keeps
     */
    private static void succeedOnceNamed(GameTestHelper helper, ServerPlayer player, LivingEntity mob, String effect,
                                         ResourceKey<DamageType> type) {
        DamageSource[] latest = {null};
        helper.succeedWhen(() -> {
            latest[0] = latestOfType(mob, type, latest[0]);
            DamageSource hit = latest[0];
            helper.assertTrue(hit != null && hit.getEntity() == player, String.format(SHOULD_NAME_THE_APPLIER,
                    effect, mob.getName().getString(), type.identifier(),
                    hit == null ? "no hit" : hit.getEntity() == null ? "no one" : hit.getEntity().getName().getString()));
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    /**
     * Passes the test once both mobs took a hit of the type with the lead and the charm standing, checked each
     * tick.
     *
     * @param helper  the gametest helper
     * @param player  the player who applied the effect
     * @param effect  the effect, for the failure message
     * @param type    the damage type the effect's tick keeps
     * @param led the player's led mob
     * @param charmed the player's charmed mob
     */
    private static void succeedOnceKept(GameTestHelper helper, ServerPlayer player, String effect,
                                        ResourceKey<DamageType> type, Mob led, Mob charmed) {
        DamageSource[] ledLatest = {null};
        DamageSource[] charmedLatest = {null};
        helper.succeedWhen(() -> {
            ledLatest[0] = latestOfType(led, type, ledLatest[0]);
            charmedLatest[0] = latestOfType(charmed, type, charmedLatest[0]);
            assertKept(helper, effect, type, LEAD, led.hasData(GooAttachments.LED), ledLatest[0]);
            assertKept(helper, effect, type, CHARM, charmed.hasData(GooAttachments.CHARMED), charmedLatest[0]);
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    /**
     * Asserts the mob took a hit of the type and its hold on the player, the lead or the charm, still stands.
     *
     * @param helper   the gametest helper
     * @param effect   the effect, for the failure message
     * @param type     the damage type the effect's tick keeps
     * @param hold     the hold's name, for the failure message
     * @param standing whether the hold stands
     * @param hit      the latest hit of the type the mob took, or null for none
     */
    private static void assertKept(GameTestHelper helper, String effect, ResourceKey<DamageType> type,
                                   String hold, boolean standing, @Nullable DamageSource hit) {
        helper.assertTrue(hit != null && standing, String.format(SHOULD_KEEP, effect, hold, type.identifier(), hold,
                standing ? STANDING : ENDED, hit == null ? "not hurt" : "hurt"));
    }

    private static @Nullable DamageSource latestOfType(LivingEntity mob, ResourceKey<DamageType> type,
                                                       @Nullable DamageSource held) {
        DamageSource now = mob.getLastDamageSource();
        return now != null && now.is(type) ? now : held;
    }

    private static void spray(GameTestHelper helper, ServerPlayer player) {
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.SCULK), SCULK_MYCOSIS.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 1; held <= SPRAY_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer sprayer(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(SPRAYER_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setYHeadRot(FACING_EAST);
        player.setXRot(LOOKING_AT_THE_MOB);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.SCULK, HELD_GOO * GooStacks.THOUSAND));
        AbilityDefinition mycosis = AbilityRegistry.of(helper.getLevel()).getAbility(SCULK_MYCOSIS);
        helper.assertTrue(mycosis != null, String.format(ABILITY_REQUIRED, SCULK_MYCOSIS));
        KnownRecipes.teachRequires(player, mycosis);
        return player;
    }
}
