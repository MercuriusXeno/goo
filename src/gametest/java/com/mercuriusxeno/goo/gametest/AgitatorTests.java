package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.program.AgitateStep;
import com.mercuriusxeno.goo.ability.program.AgitationState;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import java.util.List;

/**
 * Gametests for hex's agitator prism, its program run on a real prism's
 * block entity the way a combo landing starts it.
 * agitator-prism-quickens-until-a-spawn
 */
public final class AgitatorTests {

    /** The bay's last cell on each axis; the shell is built on its walls. */
    private static final int BAY_EDGE = 5;
    private static final BlockPos PRISM_POS = new BlockPos(2, 1, 2);
    private static final String AGITATOR = "goo:hex_agitator";
    /** A test curve: an attempt every ten ticks, halving on each failure down to two. */
    private static final int START_INTERVAL = 10;
    private static final double SHRINK = 0.5;
    private static final int MIN_INTERVAL = 2;
    private static final int RADIUS = 1;

    private static final String SHOULD_HOLD_COMBO = "The prism should take the agitator combo";
    private static final String SHOULD_SPAWN_AND_RESET =
            "A monster should spawn in the dark room with the interval reset to %d, interval %d, monsters %d";

    private AgitatorTests() {
    }

    /**
     * An agitator prism in a dark stone room spawns a monster within the
     * test's ticks, and the spawn returns its interval to the start
     * (decision agitator-prism-quickens-until-a-spawn).
     *
     * @param helper the gametest helper
     */
    public static void agitatorSpawnsInTheDark(GameTestHelper helper) {
        buildDarkRoom(helper);
        helper.setBlock(PRISM_POS, GooBlocks.PRISM.get());
        PrismBlockEntity prism = (PrismBlockEntity) helper.getLevel().getBlockEntity(helper.absolutePos(PRISM_POS));
        helper.assertTrue(prism != null && prism.runCombo(GooTypes.HEX, AGITATOR,
                List.of(new AgitateStep(RADIUS, START_INTERVAL, SHRINK, MIN_INTERVAL))), SHOULD_HOLD_COMBO);
        AABB room = new AABB(helper.absolutePos(BlockPos.ZERO)).expandTowards(BAY_EDGE, BAY_EDGE, BAY_EDGE);
        helper.succeedWhen(() -> {
            AgitationState agitation = prism.programState().agitation();
            int monsters = helper.getLevel().getEntitiesOfClass(Mob.class, room, mob -> mob instanceof Enemy).size();
            helper.assertTrue(monsters > 0 && agitation.interval() == START_INTERVAL,
                    String.format(SHOULD_SPAWN_AND_RESET, START_INTERVAL, agitation.interval(), monsters));
        });
    }

    /**
     * Walls, floor and roof of stone on the bay's edges, so no light reaches inside.
     *
     * @param helper the gametest helper
     */
    private static void buildDarkRoom(GameTestHelper helper) {
        for (int x = 0; x <= BAY_EDGE; x++) {
            for (int y = 0; y <= BAY_EDGE; y++) {
                for (int z = 0; z <= BAY_EDGE; z++) {
                    boolean onEdge = x == 0 || x == BAY_EDGE || y == 0 || y == BAY_EDGE || z == 0 || z == BAY_EDGE;
                    helper.setBlock(new BlockPos(x, y, z), onEdge ? Blocks.STONE : Blocks.AIR);
                }
            }
        }
    }
}
