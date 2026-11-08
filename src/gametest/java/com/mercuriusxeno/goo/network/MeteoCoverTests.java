package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.MeteoTests;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;

/**
 * Gametests for Meteo landing under cover through
 * {@link GooEffectScheduler#applyEffect}: the meteor strikes the roof over
 * the hit point and breaks it. They sit in the scheduler's package because
 * the scheduler is package-private.
 * decision meteo-crashes-through-whatever-is-above
 */
@RegistersGameTests
public final class MeteoCoverTests {
    private static final String METEOR_BREAKS_THE_ROOF = "meteor_breaks_the_roof";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, METEOR_BREAKS_THE_ROOF, MeteoCoverTests::meteorBreaksTheRoof);
    }

    private static final String ASTRAL_METEO = "goo:astral_meteo";
    /** The floor block the blob lands on, its top face struck. */
    private static final BlockPos FLOOR = new BlockPos(4, 1, 4);
    /** How far over the floor the roof stands: beyond the reach of a blast at the floor, so only a strike on it breaks it. */
    private static final int ROOF_HEIGHT = 10;
    /** Marks a pending effect aimed at a block rather than an entity. */
    private static final int NO_ENTITY = -1;
    private static final String ROOF_SHOULD_BREAK = "The meteor should break the stone roof at %s";

    private MeteoCoverTests() {
    }

    /**
     * Meteo landing on a floor under a stone roof calls a meteor that
     * strikes the roof and breaks it.
     *
     * @param helper the gametest helper
     */
    public static void meteorBreaksTheRoof(GameTestHelper helper) {
        ServerLevel level = helper.getLevel();
        BlockPos floor = helper.absolutePos(FLOOR);
        BlockPos roof = floor.above(ROOF_HEIGHT);
        level.setBlockAndUpdate(floor, Blocks.STONE.defaultBlockState());
        MeteoTests.openCageRoofOver(level, floor);
        level.setBlockAndUpdate(roof, Blocks.STONE.defaultBlockState());

        GooEffectScheduler.applyEffect(new PendingEffect(0, level, null, GooTypes.ASTRAL, NO_ENTITY, floor,
                Direction.UP, ASTRAL_METEO));

        helper.succeedWhen(() -> helper.assertTrue(!level.getBlockState(roof).is(Blocks.STONE),
                String.format(ROOF_SHOULD_BREAK, roof)));
    }
}
