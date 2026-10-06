package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for a free ability thrown through the real throw path at a point
 * in space: Blast resolves at the aimed point in open air, and a point aimed
 * beyond the range throws to the range's end rather than being refused
 * (decision aim-point-follows-the-cursor).
 */
public final class FreeAimTests {

    private static final Identifier BLAST = Identifier.fromNamespaceAndPath(Goo.MODID, "unstable_explode");
    private static final int NO_TARGET_ENTITY = -1;
    /** The open-air point Blast is aimed at, a cell's center in the bay. */
    private static final Vec3 AIR_POINT = new Vec3(1.5, 3.5, 1.5);
    /** Stone one block beside the point, inside power 3's reach of it. */
    private static final BlockPos NEAR_STONE = new BlockPos(2, 3, 1);
    /** Stone across the bay, beyond power 3's reach of the point. */
    private static final BlockPos FAR_STONE = new BlockPos(5, 3, 5);
    /** How far past the range the sky throw aims. */
    private static final double SKY_REACH = 200;
    /** Ticks past the arc from the player to the point. */
    private static final int ARRIVAL_TICKS = 10;

    private static final String ABILITY_REQUIRED = "Ability registry must hold unstable_explode";
    private static final String NOT_AT_POINT = "Blast left the stone beside the aimed point standing";
    private static final String NOT_CENTERED = "Blast broke stone beyond its reach of the aimed point";
    private static final String SKY_REFUSED = "A throw aimed past the range was refused, spending %d of %d mB";

    private FreeAimTests() {
    }

    private static AbilityDefinition blast(GameTestHelper helper) {
        AbilityDefinition ability = AbilityRegistry.of(helper.getLevel()).getAbility(BLAST);
        helper.assertTrue(ability != null, ABILITY_REQUIRED);
        return ability;
    }

    private static ServerPlayer thrower(GameTestHelper helper, AbilityDefinition ability) {
        ServerPlayer player = StackKeyTests.makeThrower(helper, GooTypes.UNSTABLE);
        KnownRecipes.teachRequires(player, ability);
        return player;
    }

    private static GooThrowPayload throwAt(ServerPlayer player, Vec3 point) {
        return new GooThrowPayload(GooTypes.id(GooTypes.UNSTABLE), NO_TARGET_ENTITY, BlockPos.containing(point),
                Direction.UP.ordinal(), false, BLAST.toString(), player.getEyePosition(), point);
    }

    /**
     * Blast thrown at a point in open air explodes centered there: the stone
     * beside the point breaks and the stone across the bay stands.
     *
     * @param helper the gametest helper
     */
    public static void blastExplodesAtThePointInOpenAir(GameTestHelper helper) {
        AbilityDefinition ability = blast(helper);
        helper.setBlock(NEAR_STONE, Blocks.STONE);
        helper.setBlock(FAR_STONE, Blocks.STONE);
        ServerPlayer player = thrower(helper, ability);

        GooThrowHandler.execute(player, throwAt(player, helper.absoluteVec(AIR_POINT)));
        helper.runAfterDelay(ARRIVAL_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(helper.getBlockState(NEAR_STONE).isAir(), NOT_AT_POINT);
            helper.assertTrue(helper.getBlockState(FAR_STONE).is(Blocks.STONE), NOT_CENTERED);
            helper.succeed();
        });
    }

    /**
     * Blast aimed at the sky, past the range, throws to the range's end: the
     * throw spends its cost rather than being refused.
     *
     * @param helper the gametest helper
     */
    public static void blastAimedAtTheSkyThrowsToTheRangesEnd(GameTestHelper helper) {
        AbilityDefinition ability = blast(helper);
        ServerPlayer player = thrower(helper, ability);
        int before = GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.UNSTABLE, 0);

        GooThrowHandler.execute(player, throwAt(player, player.getEyePosition().add(0, SKY_REACH, 0)));
        int spent = before - GooSourceScanner.aggregateAvailable(player).getOrDefault(GooTypes.UNSTABLE, 0);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(spent == ability.cost(), String.format(SKY_REFUSED, spent, ability.cost()));
        helper.succeed();
    }
}
