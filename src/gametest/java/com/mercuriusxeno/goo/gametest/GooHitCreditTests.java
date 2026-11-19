package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.hex.Charmed;
import com.mercuriusxeno.goo.ability.program.DamageKind;
import com.mercuriusxeno.goo.ability.program.DamageStep;
import com.mercuriusxeno.goo.ability.program.EntityHost;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.StepContext;
import com.mercuriusxeno.goo.ability.yarn.Led;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/**
 * Gametests for the source a goo damage step hurts with: a player thrower is named as the hit's cause and the
 * kind keeps its damage type, no thrower or a thrower striking itself names none, the named cause pushes the
 * struck mob no farther, and a hit on the caster's own led or charmed mob names no one, so the lead and the
 * charm stand as they did before goo hits named their caster (decisions direct-goo-hit-credits-its-caster,
 * goo-hit-keeps-its-kind-and-names-the-player-behind-it).
 */
@RegistersGameTests
public final class GooHitCreditTests {
    private static final String EVERY_KIND_NAMES_THE_PLAYER = "goo_hit_credit_every_kind_names_the_player_thrower";
    private static final String NO_THROWER_NAMES_NONE = "goo_hit_credit_no_or_self_thrower_names_no_entity";
    private static final String PUSHES_NO_FARTHER = "goo_hit_credit_naming_the_thrower_pushes_no_farther";
    private static final String OWN_LED_KEEPS_ITS_LEAD = "goo_hit_credit_own_led_mob_keeps_its_lead";
    private static final String OWN_CHARMED_KEEPS_ITS_CHARM = "goo_hit_credit_own_charmed_mob_keeps_its_charm";
    private static final String PVP_GOVERNS_A_PLAYERS_HIT = "goo_hit_credit_pvp_setting_governs_a_hit_on_a_player";
    private static final String TEAM_SPARES_A_TEAMMATE = "goo_hit_credit_team_without_friendly_fire_spares_a_teammate";
    private static final String NO_FRIENDLY_FIRE_TEAM = "goo_hit_credit_no_friendly_fire";
    private static final String SHOULD_BE_REFUSED = "A goo hit on a player %s should land no damage, health %.1f of %.1f";
    private static final String SHOULD_LAND_NAMING = "With PvP on a goo hit on a player should land naming the thrower, "
            + "health %.1f of %.1f, last hit by %s";

    private static final BlockPos MOB_POS = new BlockPos(3, 1, 3);
    private static final BlockPos BESIDE_THE_MOB = MOB_POS.west();
    private static final double ONE_HEART = 1;
    private static final float LEAD_DAMAGE = 6f;
    private static final String SHOULD_NAME_THE_PLAYER = "A %s hit should name the player as %s, last hit by %s from %s";
    private static final String SHOULD_NAME_NONE = "A magic hit with %s should name no entity as magic, last hit by %s from %s";
    private static final String SHOULD_PUSH_NO_FARTHER =
            "A %s hit naming its thrower moved the mob %s horizontally, the same hit naming none %s";
    private static final long KNOCKBACK_SEED = 4_242L;
    private static final String SHOULD_KEEP = "The caster's own %s should keep its %s and take the hit, holds it %s, "
            + "health %.1f of %.1f, last hit by %s";

    private GooHitCreditTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, EVERY_KIND_NAMES_THE_PLAYER,
                GooHitCreditTests::everyKindNamesThePlayerThrower);
        GooTestFunctions.registerTest(registrar, NO_THROWER_NAMES_NONE, GooHitCreditTests::noOrSelfThrowerNamesNone);
        GooTestFunctions.registerTest(registrar, PUSHES_NO_FARTHER, GooHitCreditTests::namingTheThrowerPushesNoFarther);
        GooTestFunctions.registerTest(registrar, OWN_LED_KEEPS_ITS_LEAD, GooHitCreditTests::ownLedKeepsItsLead);
        GooTestFunctions.registerTest(registrar, OWN_CHARMED_KEEPS_ITS_CHARM, GooHitCreditTests::ownCharmedKeepsItsCharm);
        GooTestFunctions.registerTest(registrar, PVP_GOVERNS_A_PLAYERS_HIT, GooHitCreditTests::pvpGovernsAPlayersHit);
        GooTestFunctions.registerTest(registrar, TEAM_SPARES_A_TEAMMATE, GooHitCreditTests::teamSparesATeammate);
    }

    /**
     * A damage step of every kind, run on a cow with a mock player as thrower, hurts it with a source naming that
     * player and holding the kind's damage type.
     *
     * @param helper the gametest helper
     */
    public static void everyKindNamesThePlayerThrower(GameTestHelper helper) {
        ServerPlayer player = playerBeside(helper);
        for (DamageKind kind : DamageKind.values()) {
            Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, MOB_POS);
            strike(helper, cow, player, kind);
            DamageSource last = cow.getLastDamageSource();
            helper.assertTrue(last != null && last.getEntity() == player && last.is(typeOf(kind)),
                    String.format(SHOULD_NAME_THE_PLAYER, kind, typeOf(kind).identifier(), causeOf(last), last));
        }
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A magic damage step with no thrower, and one whose thrower is its own target, each hurt with magic naming
     * no entity.
     *
     * @param helper the gametest helper
     */
    public static void noOrSelfThrowerNamesNone(GameTestHelper helper) {
        Mob unthrown = helper.spawnWithNoFreeWill(EntityType.COW, MOB_POS);
        strike(helper, unthrown, null, DamageKind.MAGIC);
        assertNamesNone(helper, unthrown, "no thrower");
        Mob selfThrown = helper.spawnWithNoFreeWill(EntityType.COW, MOB_POS);
        strike(helper, selfThrown, selfThrown, DamageKind.MAGIC);
        assertNamesNone(helper, selfThrown, "the target as its thrower");
        helper.succeed();
    }

    /**
     * Each non-attack kind, struck at a still cow with its knockback allowed, leaves the cow's horizontal motion
     * the same whether its host names a mock player beside the cow as thrower or names none.
     *
     * @param helper the gametest helper
     */
    public static void namingTheThrowerPushesNoFarther(GameTestHelper helper) {
        ServerPlayer player = playerBeside(helper);
        for (DamageKind kind : DamageKind.values()) {
            if (kind == DamageKind.ATTACK) {
                continue;
            }
            Vec3 named = horizontalMotionAfter(helper, player, kind);
            Vec3 unnamed = horizontalMotionAfter(helper, null, kind);
            helper.assertTrue(named.equals(unnamed), String.format(SHOULD_PUSH_NO_FARTHER, kind, named, unnamed));
        }
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A magic damage step its caster runs on a cow the caster led hurts the cow and leaves the lead standing,
     * naming no entity, as before goo hits named their caster.
     *
     * @param helper the gametest helper
     */
    public static void ownLedKeepsItsLead(GameTestHelper helper) {
        ServerPlayer player = playerBeside(helper);
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, MOB_POS);
        cow.setData(GooAttachments.LED, new Led(player.getUUID(), LEAD_DAMAGE));
        strike(helper, cow, player, DamageKind.MAGIC);
        assertKept(helper, cow, "led cow", "lead", cow.hasData(GooAttachments.LED));
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A magic damage step its caster runs on a husk the caster charmed hurts the husk and leaves the charm
     * standing, naming no entity, as before goo hits named their caster.
     *
     * @param helper the gametest helper
     */
    public static void ownCharmedKeepsItsCharm(GameTestHelper helper) {
        ServerPlayer player = playerBeside(helper);
        Mob husk = helper.spawnWithNoFreeWill(EntityType.HUSK, MOB_POS);
        husk.setData(GooAttachments.CHARMED, new Charmed(player.getUUID()));
        strike(helper, husk, player, DamageKind.MAGIC);
        assertKept(helper, husk, "charmed husk", "charm", husk.hasData(GooAttachments.CHARMED));
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.succeed();
    }

    /**
     * A magic damage step one survival player throws at another lands no damage with the server's PvP setting off,
     * and with it on lands naming the thrower; vanilla's ServerPlayer.hurtServer refuses a hit whose cause is a
     * player it cannot be harmed by. The test restores the PvP setting it found.
     * decision goo-hit-on-a-player-obeys-pvp-and-team-rules
     *
     * @param helper the gametest helper
     */
    public static void pvpGovernsAPlayersHit(GameTestHelper helper) {
        ServerPlayer thrower = SurvivalPlayers.placeIn(helper);
        ServerPlayer struck = SurvivalPlayers.placeIn(helper);
        boolean pvpFound = helper.getLevel().getGameRules().get(GameRules.PVP);
        try {
            allowPvp(helper, false);
            strike(helper, struck, thrower, DamageKind.MAGIC);
            helper.assertTrue(struck.getHealth() == struck.getMaxHealth(),
                    String.format(SHOULD_BE_REFUSED, "with PvP off", struck.getHealth(), struck.getMaxHealth()));
            allowPvp(helper, true);
            strike(helper, struck, thrower, DamageKind.MAGIC);
            DamageSource last = struck.getLastDamageSource();
            helper.assertTrue(struck.getHealth() < struck.getMaxHealth() && last != null && last.getEntity() == thrower,
                    String.format(SHOULD_LAND_NAMING, struck.getHealth(), struck.getMaxHealth(), causeOf(last)));
        } finally {
            allowPvp(helper, pvpFound);
            removePlayers(helper, thrower, struck);
        }
        helper.succeed();
    }

    /**
     * A magic damage step one survival player throws at a teammate, on a team with friendly fire off and PvP on,
     * lands no damage. The test removes its team and restores the PvP setting it found.
     * decision goo-hit-on-a-player-obeys-pvp-and-team-rules
     *
     * @param helper the gametest helper
     */
    public static void teamSparesATeammate(GameTestHelper helper) {
        ServerPlayer thrower = SurvivalPlayers.placeIn(helper);
        ServerPlayer teammate = SurvivalPlayers.placeIn(helper);
        boolean pvpFound = helper.getLevel().getGameRules().get(GameRules.PVP);
        Scoreboard scoreboard = helper.getLevel().getScoreboard();
        PlayerTeam team = scoreboard.addPlayerTeam(NO_FRIENDLY_FIRE_TEAM);
        try {
            team.setAllowFriendlyFire(false);
            scoreboard.addPlayerToTeam(thrower.getScoreboardName(), team);
            scoreboard.addPlayerToTeam(teammate.getScoreboardName(), team);
            allowPvp(helper, true);
            strike(helper, teammate, thrower, DamageKind.MAGIC);
            helper.assertTrue(teammate.getHealth() == teammate.getMaxHealth(), String.format(SHOULD_BE_REFUSED,
                    "teammate with friendly fire off", teammate.getHealth(), teammate.getMaxHealth()));
        } finally {
            scoreboard.removePlayerTeam(team);
            allowPvp(helper, pvpFound);
            removePlayers(helper, thrower, teammate);
        }
        helper.succeed();
    }

    private static void allowPvp(GameTestHelper helper, boolean allowed) {
        helper.getLevel().getGameRules().set(GameRules.PVP, allowed, helper.getLevel().getServer());
    }

    private static void removePlayers(GameTestHelper helper, ServerPlayer... players) {
        for (ServerPlayer player : players) {
            helper.getLevel().getServer().getPlayerList().remove(player);
        }
    }

    // Vanilla knocks a hit naming no position the way the struck mob's random rolls, so each cow rolls from one seed.
    private static Vec3 horizontalMotionAfter(GameTestHelper helper, @Nullable Entity thrower, DamageKind kind) {
        Mob cow = helper.spawnWithNoFreeWill(EntityType.COW, MOB_POS);
        cow.setDeltaMovement(Vec3.ZERO);
        cow.getRandom().setSeed(KNOCKBACK_SEED);
        strike(helper, cow, thrower, kind);
        Vec3 motion = cow.getDeltaMovement();
        return new Vec3(motion.x, 0, motion.z);
    }

    private static void strike(GameTestHelper helper, LivingEntity target, @Nullable Entity thrower, DamageKind kind) {
        new DamageStep(Expr.literal(ONE_HEART), kind)
                .tick(new StepContext(new EntityHost(helper.getLevel(), target, thrower), 0, 0));
    }

    private static void assertNamesNone(GameTestHelper helper, Mob cow, String thrower) {
        DamageSource last = cow.getLastDamageSource();
        helper.assertTrue(last != null && last.getEntity() == null && last.is(DamageTypes.MAGIC),
                String.format(SHOULD_NAME_NONE, thrower, causeOf(last), last));
    }

    private static void assertKept(GameTestHelper helper, Mob mob, String minion, String bond, boolean holdsIt) {
        DamageSource last = mob.getLastDamageSource();
        helper.assertTrue(holdsIt && mob.getHealth() < mob.getMaxHealth() && last != null && last.getEntity() == null,
                String.format(SHOULD_KEEP, minion, bond, holdsIt, mob.getHealth(), mob.getMaxHealth(), causeOf(last)));
    }

    private static String causeOf(@Nullable DamageSource source) {
        return source == null || source.getEntity() == null ? "no one" : source.getEntity().getName().getString();
    }

    private static ResourceKey<DamageType> typeOf(DamageKind kind) {
        return switch (kind) {
            case MAGIC -> DamageTypes.MAGIC;
            case FREEZE -> DamageTypes.FREEZE;
            case CACTUS -> DamageTypes.CACTUS;
            case FORCE -> DamageTypes.GENERIC;
            case ATTACK -> DamageTypes.PLAYER_ATTACK;
            case STALAGMITE -> DamageStep.STALAGMITE;
            case EXPIRE -> DamageStep.EXPIRE;
        };
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer playerBeside(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(BESIDE_THE_MOB));
        player.setPos(stand.x, stand.y, stand.z);
        return player;
    }
}
