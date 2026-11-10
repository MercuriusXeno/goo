package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.zone.StandingColumn;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.monster.zombie.Zombie;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for ender's Elude held on the glove by a survival mock player:
 * a zombie's hit lands no damage and moves the player to a safe spot, and a
 * fall's hit lands and moves them nowhere.
 * elude-moves-you-to-a-random-safe-spot-on-mob-or-projectile-damage
 */
@RegistersGameTests
public final class ShifterTests {
    private static final String ELUDE_ESCAPES_A_ZOMBIE = "shifter_blinks_instead_of_damage";
    private static final String ELUDE_LETS_A_FALL_LAND = "shifter_lets_a_fall_land";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, ELUDE_ESCAPES_A_ZOMBIE, ShifterTests::eludeEscapesAZombiesHit);
        GooTestFunctions.registerTest(registrar, ELUDE_LETS_A_FALL_LAND, ShifterTests::eludeLetsAFallLand);
    }

    private static final Identifier ENDER_SHIFTER = Identifier.parse("goo:ender_shifter");
    /** A hit of two hearts. */
    private static final float HIT = 4f;
    /** Where the zombie stands, beside the player. */
    private static final BlockPos ZOMBIE_POS = new BlockPos(2, 1, 1);
    private static final String SHOULD_TAKE_NO_DAMAGE = "A player under Elude should keep %.1f health, has %.1f";
    private static final String SHOULD_MOVE = "A zombie's hit should move the player from %s, stands at %s";
    private static final String SHOULD_LAND_SAFE = "The player should land on a safe column, stands at %s";
    private static final String SHOULD_TAKE_THE_FALL = "A fall should land on a player under Elude: %.1f health, has %.1f";
    private static final String SHOULD_STAY = "A fall should move the player nowhere from %s, stands at %s";

    private ShifterTests() {
    }

    /**
     * A player holding Elude takes a zombie's hit: the hit lands no damage and
     * the player stands somewhere else, on a safe column.
     *
     * @param helper the gametest helper
     */
    public static void eludeEscapesAZombiesHit(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.ENDER, ENDER_SHIFTER);
        Zombie zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        Vec3 stood = player.position();
        float health = player.getHealth();

        HeartOverlayTests.hurt(helper, player, player.damageSources().mobAttack(zombie), HIT);

        Vec3 after = player.position();
        float healthAfter = player.getHealth();
        boolean safe = StandingColumn.at(helper.getLevel(), player.blockPosition()).safe();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(healthAfter == health, String.format(SHOULD_TAKE_NO_DAMAGE, health, healthAfter));
        helper.assertTrue(after.distanceTo(stood) > 0, String.format(SHOULD_MOVE, stood, after));
        helper.assertTrue(safe, String.format(SHOULD_LAND_SAFE, after));
        helper.succeed();
    }

    /**
     * A player holding Elude takes a fall's hit: the hit lands and the player
     * stands where they stood.
     *
     * @param helper the gametest helper
     */
    public static void eludeLetsAFallLand(GameTestHelper helper) {
        ServerPlayer player = HeartOverlayTests.selfInvoked(helper, GooTypes.ENDER, ENDER_SHIFTER);
        Vec3 stood = player.position();
        float health = player.getHealth();

        HeartOverlayTests.hurt(helper, player, player.damageSources().fall(), HIT);

        Vec3 after = player.position();
        float healthAfter = player.getHealth();
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(healthAfter < health, String.format(SHOULD_TAKE_THE_FALL, health, healthAfter));
        helper.assertTrue(after.equals(stood), String.format(SHOULD_STAY, stood, after));
        helper.succeed();
    }
}
