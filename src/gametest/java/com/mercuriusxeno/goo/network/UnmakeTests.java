package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.GooConfig;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.SiphonFace;
import com.mercuriusxeno.goo.ability.program.SiphonRule;
import com.mercuriusxeno.goo.ability.program.Soups;
import com.mercuriusxeno.goo.ability.program.UnmakeDrops;
import com.mercuriusxeno.goo.data.GooValue;
import com.mercuriusxeno.goo.data.GooValues;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooContents;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Gametests for Unmake's soup: a hold at a cobblestone wall drinks the 3x3 on
 * the aimed face, burning twice the unstable crucible's fuel for each block,
 * and once released the soup turns into goo items holding the nine blocks'
 * full goo; a block that started siphoning goes until it is done though the
 * cursor leaves it; and a mob at the cursor is left alone.
 * decision unmake-waves-dissolve-by-crucible-cost
 */
public final class UnmakeTests {

    private static final Identifier UNMAKE = Identifier.parse("goo:unstable_unmake");
    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** Three blocks east at eye height, the middle of the wall. */
    private static final BlockPos AIMED_POS = new BlockPos(4, 2, 3);
    /** The yaw a player faces east, toward +x, at. */
    private static final float FACING_EAST = -90f;
    private static final double FACE_MIDDLE = 0.5;
    /** The blocks a 3x3 face holds. */
    private static final int FACE_BLOCKS = 9;
    /** A hold long enough to drink the whole face: every start, then the last block's siphon, with slack. */
    private static final int DRINK_TICKS = FACE_BLOCKS * SiphonRule.START_INTERVAL_TICKS + SiphonRule.SIPHON_TICKS + 2;
    /** Ticks past a hold's end until the soup's items have dropped. */
    private static final int RELEASE_TICKS = Soups.HOLD_GRACE_TICKS + UnmakeDrops.MORPH_TICKS + 4;
    private static final double DROP_REACH = 4;
    private static final String ABILITY_REQUIRED = "Ability registry must hold unstable_unmake";
    private static final String STANDS = "The face's block at %s should be drunk within %d ticks";
    private static final String UNPAID = "The hold should burn exactly twice the unstable crucible's fuel per block";
    private static final String WRONG_YIELD = "The soup should turn into %s, turned into %s";
    private static final String UNFINISHED = "A block that started siphoning should go though the cursor leaves it";
    private static final String NEIGHBOR_GONE = "A block the cursor left before its turn should stand";
    private static final String MOB_GONE = "A mob at the cursor should be left alone";

    private UnmakeTests() {
    }

    /**
     * A mock player holding exactly the nine blocks' fuel holds Unmake at the
     * middle of a cobblestone wall: the 3x3 is drunk, the fuel is spent, and
     * once released the soup drops goo items holding the nine blocks' full goo.
     *
     * @param helper the gametest helper
     */
    public static void unmakeDrinksTheFace(GameTestHelper helper) {
        AbilityDefinition unmake = AbilityRegistry.of(helper.getLevel()).getAbility(UNMAKE);
        helper.assertTrue(unmake != null, ABILITY_REQUIRED);
        List<BlockPos> face = SiphonFace.square(AIMED_POS, Direction.Axis.X, 1);
        face.forEach(pos -> helper.setBlock(pos, Blocks.COBBLESTONE));
        GooValue cobblestone = GooValues.of(helper.getLevel()).lookup(new ItemStack(Blocks.COBBLESTONE));
        int fuel = SiphonRule.fuelFor(cobblestone.totalGoo(), GooConfig.UNSTABLE_MELT_EXPONENT.get(),
                GooConfig.UNSTABLE_TICKS_PER_MB.get());
        ServerPlayer player = channeler(helper, FACE_BLOCKS * fuel);
        KnownRecipes.teachRequires(player, unmake);
        GooStreamPayload tick = aimedAt(player, westFace(helper, AIMED_POS));
        for (int held = 1; held <= DRINK_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(DRINK_TICKS + 1L, () -> {
            for (BlockPos pos : face) {
                helper.assertTrue(helper.getBlockState(pos).isAir(), String.format(STANDS, pos, DRINK_TICKS));
            }
            helper.assertFalse(GooSourceScanner.hasEnough(player, GooTypes.UNSTABLE, 1), UNPAID);
        });
        helper.runAfterDelay(DRINK_TICKS + RELEASE_TICKS, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            Map<ResourceKey<GooTypeDefinition>, Integer> expected = new HashMap<>();
            cobblestone.getAll().forEach((type, amount) -> expected.put(type, amount * FACE_BLOCKS));
            Map<ResourceKey<GooTypeDefinition>, Integer> dropped = droppedGoo(helper);
            helper.assertTrue(new GooContents(expected).getAll().equals(dropped),
                    String.format(WRONG_YIELD, expected, dropped));
            helper.succeed();
        });
    }

    /**
     * A mock player holds Unmake at a cobblestone with another above it for
     * one tick, then looks away into the air: the one above, on the square's
     * ring, started first and is drunk anyway, and the aimed one, whose turn
     * never came, stands.
     *
     * @param helper the gametest helper
     */
    public static void unmakeFinishesWhatItStarts(GameTestHelper helper) {
        AbilityDefinition unmake = AbilityRegistry.of(helper.getLevel()).getAbility(UNMAKE);
        helper.assertTrue(unmake != null, ABILITY_REQUIRED);
        BlockPos beside = AIMED_POS.above();
        helper.setBlock(AIMED_POS, Blocks.COBBLESTONE);
        helper.setBlock(beside, Blocks.COBBLESTONE);
        ServerPlayer player = channeler(helper, GooStacks.THOUSAND);
        KnownRecipes.teachRequires(player, unmake);
        GooStreamPayload aimed = aimedAt(player, westFace(helper, AIMED_POS));
        GooStreamPayload away = aimedAt(player, player.getEyePosition().add(0, 3, 0));
        helper.runAfterDelay(1, () -> GooStreamHandler.streamTick(player, aimed));
        for (int held = 2; held <= SiphonRule.SIPHON_TICKS + 2; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, away));
        }
        helper.runAfterDelay(SiphonRule.SIPHON_TICKS + 3L, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(helper.getBlockState(beside).isAir(), UNFINISHED);
            helper.assertTrue(helper.getBlockState(AIMED_POS).is(Blocks.COBBLESTONE), NEIGHBOR_GONE);
            helper.succeed();
        });
    }

    /**
     * A mock player holds Unmake at a chicken: the soup drinks blocks only,
     * and the chicken stands.
     *
     * @param helper the gametest helper
     */
    public static void unmakeLeavesMobsAlone(GameTestHelper helper) {
        AbilityDefinition unmake = AbilityRegistry.of(helper.getLevel()).getAbility(UNMAKE);
        helper.assertTrue(unmake != null, ABILITY_REQUIRED);
        Mob chicken = helper.spawnWithNoFreeWill(EntityType.CHICKEN, AIMED_POS);
        chicken.setNoGravity(true);
        ServerPlayer player = channeler(helper, GooStacks.THOUSAND);
        KnownRecipes.teachRequires(player, unmake);
        GooStreamPayload tick = aimedAt(player, chicken.getBoundingBox().getCenter());
        for (int held = 1; held <= DRINK_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(DRINK_TICKS + 1L, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            helper.assertTrue(chicken.isAlive() && !chicken.isRemoved(), MOB_GONE);
            helper.succeed();
        });
    }

    private static GooStreamPayload aimedAt(ServerPlayer player, Vec3 aimPoint) {
        return GooStreamPayload.unplaned(GooTypes.id(GooTypes.UNSTABLE), UNMAKE.toString(), player.getEyePosition(),
                aimPoint);
    }

    private static Vec3 westFace(GameTestHelper helper, BlockPos relative) {
        BlockPos pos = helper.absolutePos(relative);
        return new Vec3(pos.getX(), pos.getY() + FACE_MIDDLE, pos.getZ() + FACE_MIDDLE);
    }

    private static Map<ResourceKey<GooTypeDefinition>, Integer> droppedGoo(GameTestHelper helper) {
        AABB reach = new AABB(helper.absolutePos(AIMED_POS)).inflate(DROP_REACH);
        Map<ResourceKey<GooTypeDefinition>, Integer> dropped = new HashMap<>();
        for (ItemEntity item : helper.getLevel().getEntitiesOfClass(ItemEntity.class, reach)) {
            ResourceKey<GooTypeDefinition> type = GooStacks.keyOf(item.getItem());
            if (type != null) {
                dropped.merge(type, GooStacks.volumeOf(item.getItem()), Integer::sum);
            }
        }
        return dropped;
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer channeler(GameTestHelper helper, int unstable) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.setYRot(FACING_EAST);
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.UNSTABLE, unstable));
        return player;
    }
}
