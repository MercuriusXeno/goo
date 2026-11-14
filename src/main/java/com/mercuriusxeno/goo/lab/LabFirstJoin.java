package com.mercuriusxeno.goo.lab;

import com.mercuriusxeno.goo.Goo;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.core.BlockPos;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.SignBlock;

/**
 * The Goo Lab save's first join: builds the lab at the origin, hands the
 * joining player the kit and teaches them every item recipe, once per save,
 * so a new save opens fitted out with nothing typed; {@code /goo lab build},
 * rebuild, kit, learn and forget stay for later runs.
 * decision goo-lab-save-builds-and-kits-on-first-join
 * decision goo-lab-save-teaches-every-recipe-on-first-join
 */
public final class LabFirstJoin {

    /**
     * The level name of the Goo Lab save, as tools/LabTemplateWriter writes it into the template.
     */
    public static final String LAB_LEVEL_NAME = "Goo Lab";
    /**
     * The log line for a first join whose build failed.
     */
    private static final String MSG_BUILD_FAILED = "Goo Lab first join failed to build the lab";

    private LabFirstJoin() {
    }

    /**
     * Answers whether a join fits out its save: a dev launch on the Goo Lab save alone.
     *
     * @param production whether the launch is a production one
     * @param levelName  the server's level name
     * @return true for a dev launch on the save named {@link #LAB_LEVEL_NAME}
     */
    public static boolean fitsOut(boolean production, String levelName) {
        return !production && LAB_LEVEL_NAME.equals(levelName);
    }

    /**
     * Fits out the joining player's save at the lab origin in the overworld,
     * reading the marker the save keeps; the login handler runs this.
     *
     * @param player the joining player
     */
    public static void fitOutOnLogin(ServerPlayer player) {
        MinecraftServer server = player.level().getServer();
        ServerLevel overworld = server.overworld();
        try {
            fitOut(overworld, LabBuilder.worldOrigin(), player, LabFirstJoinMarker.of(overworld));
        } catch (CommandSyntaxException e) {
            Goo.LOGGER.error(MSG_BUILD_FAILED, e);
        }
    }

    /**
     * Fits out the save where its marker reads unbuilt: builds the lab, hands
     * the kit and teaches every item recipe, then writes the marker built. A
     * lab already standing at the origin, built by {@code /goo lab build}
     * before the marker existed, reads as built, so its pen mobs are not
     * spawned twice and the inventory stays; that join still teaches. A join
     * finding the marker built teaches nothing, so a forget holds across a rejoin.
     *
     * @param level  the level to build in
     * @param origin the world position of the plan's zero offset
     * @param player the joining player
     * @param marker the save's marker
     * @throws CommandSyntaxException when a planned block state does not parse
     */
    public static void fitOut(ServerLevel level, BlockPos origin, ServerPlayer player, LabFirstJoinMarker marker)
            throws CommandSyntaxException {
        if (marker.isBuilt()) {
            return;
        }
        LabPlan plan = LabBuilder.planFor(level);
        if (!labStands(level, origin, plan)) {
            LabBuilder.build(level, origin, plan);
            LabKit.give(player, level);
        }
        LabKit.learn(player, level);
        marker.markBuilt();
    }

    /**
     * Answers whether a lab stands at the origin: a planned sign holds a sign
     * block, which a fresh superflat save never does at those positions.
     *
     * @param level  the level to read
     * @param origin the world position of the plan's zero offset
     * @param plan   the plan
     * @return true when any planned sign stands
     */
    private static boolean labStands(ServerLevel level, BlockPos origin, LabPlan plan) {
        return plan.placements().stream().filter(LabPlacement::isSign)
                .anyMatch(sign -> level.getBlockState(LabBuilder.worldPos(origin, sign.offset())).getBlock()
                        instanceof SignBlock);
    }
}
