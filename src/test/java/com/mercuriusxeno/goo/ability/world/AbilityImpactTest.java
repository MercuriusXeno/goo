package com.mercuriusxeno.goo.ability.world;

import com.mercuriusxeno.goo.ability.AbilityBadge;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityJson;
import com.mercuriusxeno.goo.ability.Delivery;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.FxAnchor;
import com.mercuriusxeno.goo.ability.program.SoundKind;
import com.mercuriusxeno.goo.ability.program.SoundStep;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.SharedConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.server.Bootstrap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.fml.loading.FMLLoader;
import net.neoforged.neoforge.attachment.AttachmentHolder;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import java.util.Arrays;
import java.util.List;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.RETURNS_DEEP_STUBS;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockingDetails;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * AbilityImpact.land places no block of its own: a program that ends the
 * tick its blob lands leaves only its effect (decisions
 * splat-runs-the-program-no-fuse, lingering-abilities-place-their-own-thing).
 */
class AbilityImpactTest {

    private static final BlockPos WALL = new BlockPos(3, 64, -2);
    private static final Identifier CHIME = Identifier.parse("minecraft:block.note_block.chime");

    /**
     * Stands NeoForge's AttachmentHolder and the built-in registries before the
     * first mock of a level: both class inits ask FML whether it runs in
     * production, which a stubbed loader answers.
     *
     * @throws ClassNotFoundException never, the class is on the test classpath
     */
    @BeforeAll
    static void standRegistries() throws ClassNotFoundException {
        try (MockedStatic<FMLLoader> loader = mockStatic(FMLLoader.class, RETURNS_DEEP_STUBS)) {
            Class.forName(AttachmentHolder.class.getName(), true, AttachmentHolder.class.getClassLoader());
            SharedConstants.tryDetectVersion();
            Bootstrap.bootStrap();
        }
    }

    private static ServerLevel wallLevel() {
        ServerLevel level = mock(ServerLevel.class);
        when(level.getBlockState(WALL)).thenReturn(Blocks.STONE.defaultBlockState());
        when(level.getBlockState(WALL.south())).thenReturn(Blocks.AIR.defaultBlockState());
        when(level.players()).thenReturn(List.of());
        return level;
    }

    private static AbilityDefinition chime(AbilityBadge badge) {
        return new AbilityDefinition(Identifier.parse("goo:test_chime"), GooTypes.UNSTABLE,
                "chime", "", 0, 0, Delivery.ARC,
                List.of(new SoundStep(CHIME, FxAnchor.HOST, SoundKind.BLOCKS, Expr.literal(1), Expr.literal(1))),
                List.of(), badge, List.of());
    }

    /** The coordinates the landing's chime played at. */
    private static Vec3 chimedAt(ServerLevel level) {
        Object[] args = mockingDetails(level).getInvocations().stream()
                .filter(call -> "playSound".equals(call.getMethod().getName()))
                .findFirst().orElseThrow().getArguments();
        List<Double> coordinates = Arrays.stream(args).filter(Double.class::isInstance)
                .map(Double.class::cast).limit(3).toList();
        return new Vec3(coordinates.get(0), coordinates.get(1), coordinates.get(2));
    }

    /** A free ability's program anchors at the aimed point, not the cell's center (decision aim-point-follows-the-cursor). */
    @Test
    void anAimedPointAnchorsTheProgramThere() {
        ServerLevel level = wallLevel();
        Vec3 point = new Vec3(3.2, 64.9, -0.9);

        AbilityImpact.land(level, WALL, GooTypes.UNSTABLE, Direction.SOUTH, chime(AbilityBadge.FREE), point);

        assertEquals(point, chimedAt(level));
    }

    @Test
    void noAimedPointAnchorsTheProgramAtTheCellsCenter() {
        ServerLevel level = wallLevel();

        AbilityImpact.land(level, WALL, GooTypes.UNSTABLE, Direction.SOUTH, chime(AbilityBadge.WORLD));

        assertEquals(Vec3.atCenterOf(WALL.south()), chimedAt(level));
    }

    /**
     * The proximity mine lingers, so its burnout waits for its standing block to
     * explode; Blast resolves at the splat and plays it there (decision
     * elemental-explosion-per-type).
     */
    @Test
    void theMineLingersAndBlastDoesNot() {
        assertTrue(AbilityImpact.lingers(AbilityJson.decode("unstable_proximity_mine")));
        assertFalse(AbilityImpact.lingers(AbilityJson.decode("unstable_explode")));
    }

    @Test
    void aOneTickLandingSendsItsBurnoutToTheTrackingPlayers() {
        ServerLevel level = wallLevel();

        AbilityImpact.land(level, WALL, GooTypes.UNSTABLE, Direction.SOUTH, chime(AbilityBadge.WORLD));

        verify(level).players();
    }

    @Test
    void aOneTickProgramLandsNoBlock() {
        ServerLevel level = wallLevel();
        AbilityDefinition chime = chime(AbilityBadge.WORLD);

        AbilityImpact.land(level, WALL, GooTypes.UNSTABLE, Direction.SOUTH, chime);

        assertTrue(mockingDetails(level).getInvocations().stream()
                .anyMatch(call -> "playSound".equals(call.getMethod().getName())), "the program did not run");
        verify(level, never()).setBlock(any(), any(), anyInt());
    }
}
