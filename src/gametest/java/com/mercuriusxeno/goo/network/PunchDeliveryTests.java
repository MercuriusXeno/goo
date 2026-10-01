package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.block.ability.ChainMarkerBlockEntity;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.registry.GooServerState;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for the punch delivery: a glove use with a punch ability
 * strikes what stands within reach through the real throw path and the
 * ability runs on it the same tick, with no flight (decision
 * punch-strikes-at-reach). They sit in the scheduler's package to read its
 * pending effects.
 */
public final class PunchDeliveryTests {

    private static final BlockPos STRUCK_POS = new BlockPos(3, 1, 3);
    /** Two blocks west of the struck position, inside a player's reach of three. */
    private static final BlockPos PUNCHER_POS = STRUCK_POS.west(2);
    private static final int SETTLE_TICKS = 1;
    private static final int NO_ENTITY = -1;
    private static final int HELD_GOO = 4;
    private static final String METAL_FIST = "goo:metal_fist";
    private static final String PUNCH_MARKER = "gootest:punch_marker";
    /** The damage metal_fist.json's damage step names. */
    private static final float FIST_DAMAGE = 8.0f;
    private static final float DAMAGE_TOLERANCE = 0.01f;
    private static final String SHOULD_TAKE_FIST_DAMAGE = "Zombie should lose %.1f health the tick it is punched, lost %.1f";
    private static final String SHOULD_SCHEDULE_NOTHING = "A punch should leave no pending effect behind";
    private static final String SHOULD_STAND_MARKER = "A punch_marker chain marker should stand on the punched face";

    private PunchDeliveryTests() {
    }

    /**
     * A mock player punches a zombie two blocks ahead with metal fist, and
     * the zombie loses the fist's eight health in that tick.
     *
     * @param helper the gametest helper
     */
    public static void metalFist(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, STRUCK_POS);
        ServerPlayer player = puncher(helper, GooTypes.METAL);
        helper.runAfterDelay(SETTLE_TICKS, () -> {
            float before = zombie.getHealth();
            GooEffectScheduler effects = GooServerState.of(helper.getLevel().getServer()).gooEffects();
            int pendingBefore = effects.pendingCount();
            GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.METAL), zombie.getId(),
                    BlockPos.ZERO, NO_ENTITY, false, METAL_FIST, player.getEyePosition()));
            float lost = before - zombie.getHealth();
            // Gametests share the server's scheduler, so the punch is read by what it added.
            boolean pending = effects.pendingCount() != pendingBefore;
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(Math.abs(lost - FIST_DAMAGE) < DAMAGE_TOLERANCE,
                    String.format(SHOULD_TAKE_FIST_DAMAGE, FIST_DAMAGE, lost));
            helper.assertFalse(pending, SHOULD_SCHEDULE_NOTHING);
            helper.succeed();
        });
    }

    /**
     * A mock player punches the top face of a stone block with the test
     * datapack's punch_marker ability, and its chain marker stands on that
     * face the same tick.
     *
     * @param helper the gametest helper
     */
    public static void punchBlock(GameTestHelper helper) {
        helper.setBlock(STRUCK_POS, Blocks.STONE);
        ServerPlayer player = puncher(helper, GooTypes.ROCK);
        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.ROCK), NO_ENTITY,
                helper.absolutePos(STRUCK_POS), Direction.UP.ordinal(), false, PUNCH_MARKER,
                player.getEyePosition()));
        helper.getLevel().getServer().getPlayerList().remove(player);
        ChainMarkerBlockEntity marker = helper.getBlockEntity(STRUCK_POS.above(), ChainMarkerBlockEntity.class);
        helper.assertTrue(PUNCH_MARKER.equals(marker.getAbilityId()), SHOULD_STAND_MARKER);
        helper.succeed();
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer puncher(GameTestHelper helper, ResourceKey<GooTypeDefinition> gooType) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(PUNCHER_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(gooType, HELD_GOO * GooStacks.THOUSAND));
        return player;
    }
}
