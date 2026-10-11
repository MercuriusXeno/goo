package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.crystal.OreVeins;
import com.mercuriusxeno.goo.ability.program.DetectOreStep;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.item.GooSourceScanner;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * Gametests for Crystal's Glitter: a caster holding no lapis channels
 * Glitter, since it takes no item cost (operator ruling 2026-10-10), and
 * its ping finds the gem ore veins buried around the caster, each at its
 * centroid, centering its shell where the caster stands.
 * decision glitter-sphere-icons-gem-ore-groups
 */
public final class GlitterChannelTests {

    private static final Identifier CRYSTAL_GLITTER = Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_glitter");
    private static final Identifier DIAMOND_ORE = Identifier.withDefaultNamespace("diamond_ore");
    private static final Identifier LAPIS_ORE = Identifier.withDefaultNamespace("lapis_ore");
    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** Two touching diamond ores buried in the floor, one vein. */
    private static final BlockPos DIAMOND_A = new BlockPos(4, 0, 1);
    private static final BlockPos DIAMOND_B = new BlockPos(4, 0, 2);
    /** One lapis ore buried in the floor across the bay. */
    private static final BlockPos LAPIS = new BlockPos(4, 0, 5);
    private static final int HOLD_TICKS = 3;
    private static final double TOLERANCE = 1e-6;
    private static final String ABILITY_REQUIRED = "Ability registry must hold crystal_glitter";
    private static final String SHOULD_SPEND = "A hold should spend crystal goo with no lapis held, %d of %d left";
    private static final String SHELL_AT_CASTER = "The ping's shell should center where the caster stands, at %s";
    private static final String NO_SENSE = "crystal_glitter should name a detect_ore step";
    private static final String SHOULD_FIND = "The ping should find the %s vein of %s at %s, found %s";

    private GlitterChannelTests() {
    }

    /**
     * A mock player holding no lapis holds Glitter for three ticks over a
     * floor with a two block diamond vein and a lapis ore buried in it: the
     * hold spends goo, and its ping names both veins at their centroids.
     *
     * @param helper the gametest helper
     */
    public static void glitterRevealsDiamondAndLapis(GameTestHelper helper) {
        helper.setBlock(DIAMOND_A, Blocks.DIAMOND_ORE);
        helper.setBlock(DIAMOND_B, Blocks.DIAMOND_ORE);
        helper.setBlock(LAPIS, Blocks.LAPIS_ORE);
        AbilityDefinition glitter = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_GLITTER);
        helper.assertTrue(glitter != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.CRYSTAL);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        caster.setPos(stand.x, stand.y, stand.z);
        KnownRecipes.teachRequires(caster, glitter);
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.CRYSTAL), CRYSTAL_GLITTER.toString(),
                caster.getEyePosition(), caster.getEyePosition(), helper.absolutePos(STAND_POS.below()),
                Direction.UP.get3DDataValue());
        int gooBefore = GooSourceScanner.aggregateAvailable(caster).getOrDefault(GooTypes.CRYSTAL, 0);
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(caster, tick));
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            int gooAfter = GooSourceScanner.aggregateAvailable(caster).getOrDefault(GooTypes.CRYSTAL, 0);
            helper.assertTrue(caster.getInventory().countItem(Items.LAPIS_LAZULI) == 0 && gooAfter < gooBefore,
                    String.format(SHOULD_SPEND, gooAfter, gooBefore));
            DetectOreStep sense = glitter.behaviors().stream().filter(DetectOreStep.class::isInstance)
                    .map(DetectOreStep.class::cast).findFirst().orElse(null);
            helper.assertTrue(sense != null, NO_SENSE);
            OreRevealPayload reveal = sense.revealAround(caster);
            helper.assertTrue(reveal.origin().equals(caster.position()), String.format(SHELL_AT_CASTER, reveal.origin()));
            assertFound(helper, reveal, DIAMOND_ORE, 2,
                    Vec3.atCenterOf(helper.absolutePos(DIAMOND_A)).add(0, 0, 0.5));
            assertFound(helper, reveal, LAPIS_ORE, 1, Vec3.atCenterOf(helper.absolutePos(LAPIS)));
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    private static void assertFound(GameTestHelper helper, OreRevealPayload reveal, Identifier ore, int count,
                                    Vec3 centroid) {
        boolean found = reveal.veins().stream().anyMatch(vein -> vein.ore().equals(ore) && vein.count() == count
                && vein.centroid().distanceTo(centroid) < TOLERANCE);
        helper.assertTrue(found, String.format(SHOULD_FIND, count, ore, centroid,
                reveal.veins().stream().map(OreVeins.Vein::toString).toList()));
    }
}
