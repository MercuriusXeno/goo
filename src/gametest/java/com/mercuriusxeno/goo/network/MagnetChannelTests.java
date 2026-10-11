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
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.ArrayList;
import java.util.List;

/**
 * Gametests for Metal's Magnet: Glitter's growing ore sense over the metal
 * ore tag, so a held Magnet spends metal goo and its front finds the iron
 * vein and the ancient debris buried around the caster, each at its
 * centroid, the same reveal Glitter sends for gems.
 * decision magnet-sphere-icons-metal-ores
 */
public final class MagnetChannelTests {

    private static final Identifier METAL_MAGNET = Identifier.fromNamespaceAndPath(Goo.MODID, "metal_magnet");
    private static final Identifier IRON_ORE = Identifier.withDefaultNamespace("iron_ore");
    private static final Identifier ANCIENT_DEBRIS = Identifier.withDefaultNamespace("ancient_debris");
    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** Two touching iron ores buried in the floor, one vein. */
    private static final BlockPos IRON_A = new BlockPos(4, 0, 1);
    private static final BlockPos IRON_B = new BlockPos(4, 0, 2);
    /** One ancient debris buried in the floor across the bay. */
    private static final BlockPos DEBRIS = new BlockPos(4, 0, 5);
    private static final int HOLD_TICKS = 3;
    /** Held ticks whose front, a block a tick, reaches past every buried ore. */
    private static final int REACHING_HOLD = 5;
    private static final double TOLERANCE = 1e-6;
    private static final String ABILITY_REQUIRED = "Ability registry must hold metal_magnet";
    private static final String SHOULD_SPEND = "A hold should spend metal goo, %d of %d left";
    private static final String NO_SENSE = "metal_magnet should name a detect_ore step";
    private static final String SHOULD_FIND = "The reveal should find the %s vein of %s at %s, found %s";

    private MagnetChannelTests() {
    }

    /**
     * A mock player holds Magnet over a floor with a two block iron vein
     * and an ancient debris buried in it: the hold spends metal goo, and the
     * reveals its front sends name both veins at their centroids.
     *
     * @param helper the gametest helper
     */
    public static void magnetRevealsIronAndDebris(GameTestHelper helper) {
        helper.setBlock(IRON_A, Blocks.IRON_ORE);
        helper.setBlock(IRON_B, Blocks.IRON_ORE);
        helper.setBlock(DEBRIS, Blocks.ANCIENT_DEBRIS);
        AbilityDefinition magnet = AbilityRegistry.of(helper.getLevel()).getAbility(METAL_MAGNET);
        helper.assertTrue(magnet != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.METAL);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        caster.setPos(stand.x, stand.y, stand.z);
        KnownRecipes.teachRequires(caster, magnet);
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.METAL), METAL_MAGNET.toString(),
                caster.getEyePosition(), caster.getEyePosition(), helper.absolutePos(STAND_POS.below()),
                Direction.UP.get3DDataValue());
        int gooBefore = GooSourceScanner.aggregateAvailable(caster).getOrDefault(GooTypes.METAL, 0);
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(caster, tick));
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            int gooAfter = GooSourceScanner.aggregateAvailable(caster).getOrDefault(GooTypes.METAL, 0);
            helper.assertTrue(gooAfter < gooBefore, String.format(SHOULD_SPEND, gooAfter, gooBefore));
            DetectOreStep sense = magnet.behaviors().stream().filter(DetectOreStep.class::isInstance)
                    .map(DetectOreStep.class::cast).findFirst().orElse(null);
            helper.assertTrue(sense != null, NO_SENSE);
            List<OreVeins.Vein> veins = new ArrayList<>();
            for (int held = 1; held <= REACHING_HOLD; held++) {
                veins.addAll(sense.revealAt(helper.getLevel(), caster.position(), held).veins());
            }
            assertFound(helper, veins, IRON_ORE, 2, Vec3.atCenterOf(helper.absolutePos(IRON_A)).add(0, 0, 0.5));
            assertFound(helper, veins, ANCIENT_DEBRIS, 1, Vec3.atCenterOf(helper.absolutePos(DEBRIS)));
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    private static void assertFound(GameTestHelper helper, List<OreVeins.Vein> veins, Identifier ore, int count,
                                    Vec3 centroid) {
        boolean found = veins.stream().anyMatch(vein -> vein.ore().equals(ore) && vein.count() == count
                && vein.centroid().distanceTo(centroid) < TOLERANCE);
        helper.assertTrue(found, String.format(SHOULD_FIND, count, ore, centroid,
                veins.stream().map(OreVeins.Vein::toString).toList()));
    }
}
