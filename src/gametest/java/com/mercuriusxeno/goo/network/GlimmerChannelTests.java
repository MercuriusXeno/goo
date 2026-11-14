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
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametests for Crystal's Glimmer: a caster holding no lapis channels
 * Glimmer, since it takes no item cost (operator ruling 2026-10-10), and
 * its pulse, easing out from where the caster stands, finds the gem ore
 * veins buried around the caster as it reaches them, each at its centroid
 * with the blocks it shows through walls.
 * decision held-sweeps-share-one-fast-shell-visual
 * decision glitter-sphere-icons-gem-ore-groups
 */
@RegistersGameTests
public final class GlimmerChannelTests {
    private static final String GLIMMER_REVEALS_DIAMOND_AND_LAPIS = "glimmer_reveals_diamond_and_lapis";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, GLIMMER_REVEALS_DIAMOND_AND_LAPIS, GlimmerChannelTests::glimmerRevealsDiamondAndLapis);
    }


    private static final Identifier CRYSTAL_GLIMMER = Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_glimmer");
    private static final Identifier DIAMOND_ORE = Identifier.withDefaultNamespace("diamond_ore");
    private static final Identifier LAPIS_ORE = Identifier.withDefaultNamespace("lapis_ore");
    private static final BlockPos STAND_POS = new BlockPos(1, 1, 3);
    /** Two touching diamond ores buried in the floor, one vein. */
    private static final BlockPos DIAMOND_A = new BlockPos(4, 0, 1);
    private static final BlockPos DIAMOND_B = new BlockPos(4, 0, 2);
    /** One lapis ore buried in the floor across the bay. */
    private static final BlockPos LAPIS = new BlockPos(4, 0, 5);
    private static final int HOLD_TICKS = 3;
    /** Pulse ticks whose front reaches past every buried ore. */
    private static final int REACHING_HOLD = 5;
    private static final String ABILITY_REQUIRED = "Ability registry must hold crystal_glimmer";
    private static final String SHOULD_SPEND = "A hold should spend crystal goo, %d of %d left";
    private static final String NO_SENSE = "crystal_glimmer should name a detect_ore step";
    private static final String SHOULD_FIND = "The ping should find a vein of %s, found %s";

    private GlimmerChannelTests() {
    }

    /**
     * A mock player holds Glimmer for three ticks over a floor with a two block
     * diamond vein and a lapis ore buried in it: the hold spends goo, and its
     * ping names a diamond vein and a lapis vein.
     *
     * @param helper the gametest helper
     */
    public static void glimmerRevealsDiamondAndLapis(GameTestHelper helper) {
        helper.setBlock(DIAMOND_A, Blocks.DIAMOND_ORE);
        helper.setBlock(DIAMOND_B, Blocks.DIAMOND_ORE);
        helper.setBlock(LAPIS, Blocks.LAPIS_ORE);
        AbilityDefinition glimmer = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_GLIMMER);
        helper.assertTrue(glimmer != null, ABILITY_REQUIRED);
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.CRYSTAL);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(STAND_POS));
        caster.setPos(stand.x, stand.y, stand.z);
        KnownRecipes.teachRequires(caster, glimmer);
        GooStreamPayload tick = new GooStreamPayload(GooTypes.id(GooTypes.CRYSTAL), CRYSTAL_GLIMMER.toString(),
                caster.getEyePosition(), caster.getEyePosition(), helper.absolutePos(STAND_POS.below()),
                Direction.UP.get3DDataValue());
        int gooBefore = GooSourceScanner.aggregateAvailable(caster).getOrDefault(GooTypes.CRYSTAL, 0);
        for (int held = 1; held <= HOLD_TICKS; held++) {
            helper.runAfterDelay(held, () -> GooStreamHandler.streamTick(caster, tick));
        }
        helper.runAfterDelay(HOLD_TICKS + 1, () -> {
            int gooAfter = GooSourceScanner.aggregateAvailable(caster).getOrDefault(GooTypes.CRYSTAL, 0);
            helper.assertTrue(gooAfter < gooBefore,
                    String.format(SHOULD_SPEND, gooAfter, gooBefore));
            DetectOreStep sense = glimmer.behaviors().stream().filter(DetectOreStep.class::isInstance)
                    .map(DetectOreStep.class::cast).findFirst().orElse(null);
            helper.assertTrue(sense != null, NO_SENSE);
            List<OreVeins.Vein> veins = veinsHeld(helper, sense, caster.position(), REACHING_HOLD);
            assertFound(helper, veins, DIAMOND_ORE);
            assertFound(helper, veins, LAPIS_ORE);
            helper.getLevel().getServer().getPlayerList().remove(caster);
            helper.succeed();
        });
    }

    private static List<OreVeins.Vein> veinsHeld(GameTestHelper helper, DetectOreStep sense, Vec3 origin, int ticks) {
        List<OreVeins.Vein> veins = new ArrayList<>();
        for (int held = 1; held <= ticks; held++) {
            veins.addAll(sense.revealAt(helper.getLevel(), origin, held).veins());
        }
        return veins;
    }

    private static void assertFound(GameTestHelper helper, List<OreVeins.Vein> veins, Identifier ore) {
        boolean found = veins.stream().anyMatch(vein -> vein.ore().equals(ore));
        helper.assertTrue(found, String.format(SHOULD_FIND, ore, veins.stream().map(OreVeins.Vein::toString).toList()));
    }
}
