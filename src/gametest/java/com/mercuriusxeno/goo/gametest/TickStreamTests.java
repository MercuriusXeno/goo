package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.crucible.CrucibleBasin;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.item.GooStacks;
import com.mercuriusxeno.goo.network.GooStreamHandler;
import com.mercuriusxeno.goo.network.GooStreamPayload;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.registry.GooItems;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.commands.arguments.EntityAnchorArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.Optional;

/**
 * Gametests for aeon Tick: a mock player streams it on a lit crucible
 * melting a stack of cobblestone, which melts faster than its unticked twin
 * melting the same stack; the server names to the client a furnace Tick
 * hastens and nothing for a chest it cannot.
 * tick-channel-marches-squares-on-the-face
 */
public final class TickStreamTests {

    private static final BlockPos TICKED_POS = CrucibleSpawns.CRUCIBLE_POS;
    /** The twin, two blocks south, off the stream's line. */
    private static final BlockPos TWIN_POS = TICKED_POS.south(2);
    /** Three blocks east of the ticked crucible, within aeon_tick.json's reach of five. */
    private static final BlockPos STAND_POS = TICKED_POS.east(3);
    /** Heat for an hour of melting, so neither crucible runs cold. */
    private static final int HEAT_TICKS = 72_000;
    /** Ticks a still item dropped at the basin center takes to land, rest and be consumed. */
    private static final int ABSORB_TICKS = 10;
    private static final int HOLD_TICKS = 20;
    private static final int HELD_GOO = 6;
    private static final int STACK = 64;
    /** A hair above the basin floor, where a dropped item rests in an empty crucible. */
    private static final double JUST_ABOVE_FLOOR = 0.05;
    private static final double BASIN_CENTER = 0.5;
    /**
     * aeon_tick.json ticks the crucible four extra times a tick, five in all;
     * twice the twin's melt leaves room for the melt clock's curve.
     */
    private static final long FASTER_FACTOR = 2;
    private static final Identifier AEON_TICK = Identifier.parse("goo:aeon_tick");
    private static final String ABILITY_REQUIRED = "Ability registry must hold aeon_tick";
    private static final String BOTH_MELTING = "Both crucibles should hold the stack melting, pools %d and %d";
    private static final String SHOULD_MELT_FASTER = "The ticked crucible should melt over %d times the twin's %d mB, melted %d";
    private static final String SHOULD_NAME_FURNACE = "The tick aim should name the furnace at %s, named %s";
    private static final String SHOULD_NAME_NOTHING = "The tick aim should name nothing for a chest, named %s";

    private TickStreamTests() {
    }

    /**
     * A ticked crucible melts more of its stack over a held stream than its
     * twin melts of the same stack in the same ticks.
     *
     * @param helper the gametest helper
     */
    public static void tickHastensTheCrucible(GameTestHelper helper) {
        CrucibleBlockEntity ticked = litCrucibleMelting(helper, TICKED_POS);
        CrucibleBlockEntity twin = litCrucibleMelting(helper, TWIN_POS);
        ServerPlayer player = ticker(helper);
        long[] atStart = new long[2];
        helper.runAfterDelay(ABSORB_TICKS, () -> {
            atStart[0] = ticked.getPoolVolume();
            atStart[1] = twin.getPoolVolume();
            helper.assertTrue(atStart[0] > 0 && atStart[1] > 0, String.format(BOTH_MELTING, atStart[0], atStart[1]));
        });
        GooStreamPayload tick = GooStreamPayload.unplaned(GooTypes.id(GooTypes.AEON), AEON_TICK.toString(),
                player.getEyePosition(), player.getEyePosition());
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(ABSORB_TICKS + held, () -> GooStreamHandler.streamTick(player, tick));
        }
        helper.runAfterDelay(ABSORB_TICKS + HOLD_TICKS + 1, () -> {
            helper.getLevel().getServer().getPlayerList().remove(player);
            long tickedMelted = atStart[0] - ticked.getPoolVolume();
            long twinMelted = atStart[1] - twin.getPoolVolume();
            helper.assertTrue(tickedMelted > FASTER_FACTOR * twinMelted,
                    String.format(SHOULD_MELT_FASTER, FASTER_FACTOR, twinMelted, tickedMelted));
            helper.succeed();
        });
    }

    /**
     * The server's tick aim names a furnace, whose server ticker Tick runs,
     * and names nothing for a chest, whose only ticker animates its lid on
     * the client.
     *
     * @param helper the gametest helper
     */
    public static void tickNamesOnlyABlockItHastens(GameTestHelper helper) {
        helper.setBlock(TICKED_POS, Blocks.FURNACE);
        helper.setBlock(TWIN_POS, Blocks.CHEST);
        ServerPlayer player = ticker(helper);
        AbilityDefinition tick = AbilityRegistry.of(helper.getLevel()).getAbility(AEON_TICK);
        BlockPos furnace = helper.absolutePos(TICKED_POS);
        Optional<BlockPos> onFurnace = GooStreamHandler.tickAim(player, tick);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(TWIN_POS)));
        Optional<BlockPos> onChest = GooStreamHandler.tickAim(player, tick);
        helper.getLevel().getServer().getPlayerList().remove(player);
        helper.assertTrue(onFurnace.equals(Optional.of(furnace)), String.format(SHOULD_NAME_FURNACE, furnace, onFurnace));
        helper.assertTrue(onChest.isEmpty(), String.format(SHOULD_NAME_NOTHING, onChest));
        helper.succeed();
    }

    private static CrucibleBlockEntity litCrucibleMelting(GameTestHelper helper, BlockPos pos) {
        helper.setBlock(pos, GooBlocks.CRUCIBLE.get());
        CrucibleBlockEntity crucible = helper.getBlockEntity(pos, CrucibleBlockEntity.class);
        crucible.addHeat(HEAT_TICKS);
        CrucibleSpawns.spawnAt(helper, new ItemStack(Items.COBBLESTONE, STACK), new Vec3(pos.getX() + BASIN_CENTER,
                pos.getY() + CrucibleBasin.FLOOR_Y + JUST_ABOVE_FLOOR, pos.getZ() + BASIN_CENTER));
        return crucible;
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer ticker(GameTestHelper helper) {
        AbilityDefinition tick = AbilityRegistry.of(helper.getLevel()).getAbility(AEON_TICK);
        helper.assertTrue(tick != null, ABILITY_REQUIRED);
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        player.setPos(stand.x, stand.y, stand.z);
        player.lookAt(EntityAnchorArgument.Anchor.EYES, Vec3.atCenterOf(helper.absolutePos(TICKED_POS)));
        player.setItemInHand(InteractionHand.MAIN_HAND, new ItemStack(GooItems.GOO_GLOVE.get()));
        player.getInventory().add(GooStacks.createForOutput(GooTypes.AEON, HELD_GOO * GooStacks.THOUSAND));
        KnownRecipes.teachRequires(player, tick);
        return player;
    }
}
