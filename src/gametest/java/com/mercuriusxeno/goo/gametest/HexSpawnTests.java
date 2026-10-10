package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.program.Expr;
import com.mercuriusxeno.goo.ability.program.HostKind;
import com.mercuriusxeno.goo.ability.program.ProgramBehavior;
import com.mercuriusxeno.goo.ability.program.SpawnRandomStep;
import com.mercuriusxeno.goo.ability.program.TapHost;
import com.mercuriusxeno.goo.network.GooThrowHandler;
import com.mercuriusxeno.goo.network.GooThrowPayload;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.biome.MobSpawnSettings;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Gametests for hex's Spawn, thrown from the glove the way a real throw
 * reaches the server.
 * spawn-goo-morphs-into-the-mob-it-births
 */
public final class HexSpawnTests {

    private static final BlockPos FLOOR_POS = new BlockPos(2, 1, 2);
    private static final Identifier HEX_SPAWN = Identifier.fromNamespaceAndPath(Goo.MODID, "hex_spawn");
    private static final int NO_TARGET_ENTITY = -1;
    /** How far from the landing cell a conjured mob is looked for. */
    private static final double SEARCH_REACH = 2.0;

    private static final String ABILITY_REQUIRED = "Ability registry must hold hex_spawn";
    private static final String EGG_TAKEN = "The throw should take the one egg, %d remain";
    private static final String NO_NATURAL_MOB = "A mob the landing biome spawns naturally should stand at the landing";

    private static final int MORPH_TICKS = 20;
    private static final float ALWAYS = 100f;
    private static final float NEVER = 0f;
    private static final String NO_TAP_MOB = "A drip at chance 100 should conjure a mob below the tap";
    private static final String TAP_MOB_AT_ZERO = "A drip at chance 0 should conjure nothing, found %s";

    private HexSpawnTests() {
    }

    /**
     * A hex drip landing on a stone floor at chance 0 conjures nothing, and
     * one at chance 100 then conjures a mob into the cell below the tap; each
     * runs Spawn's step on the tap host the drip scheduler builds
     * (decision spawn-drip-rolls-a-fresh-spawn).
     *
     * @param helper the gametest helper
     */
    public static void spawnTapAtFullChance(GameTestHelper helper) {
        helper.setBlock(FLOOR_POS, Blocks.STONE);
        BlockPos landing = helper.absolutePos(FLOOR_POS);
        drip(helper, landing, NEVER);
        List<LivingEntity> atZero = bornAbove(helper, landing);
        helper.assertTrue(atZero.isEmpty(), String.format(TAP_MOB_AT_ZERO, atZero));
        drip(helper, landing, ALWAYS);
        helper.assertFalse(bornAbove(helper, landing).isEmpty(), NO_TAP_MOB);
        helper.succeed();
    }

    private static void drip(GameTestHelper helper, BlockPos landing, float chancePercent) {
        SpawnRandomStep spawn = new SpawnRandomStep(GooTypes.HEX, MORPH_TICKS, List.of(),
                Expr.literal(chancePercent));
        ProgramBehavior.forHost(List.of(spawn), HostKind.TAP)
                .tick(new TapHost(helper.getLevel(), landing, Direction.UP));
    }

    /**
     * The mobs standing in the cell above the landing that were born this
     * tick. Read on the drip's own tick, before a mob's first tick, so a bat
     * cannot fly off nor a walker step off the floor before it is counted,
     * and a mob wandering in from a neighbouring test is never counted.
     *
     * @param helper  the gametest helper
     * @param landing the cell the drip landed on
     * @return the mobs born above the landing this tick
     */
    private static List<LivingEntity> bornAbove(GameTestHelper helper, BlockPos landing) {
        return helper.getLevel().getEntitiesOfClass(LivingEntity.class, new AABB(landing.above()),
                living -> !(living instanceof Player) && living.tickCount == 0);
    }

    /**
     * A hex_spawn throw at a stone floor takes the thrower's egg and births a
     * living mob of a type the landing biome spawns naturally, standing at
     * the landing.
     *
     * @param helper the gametest helper
     */
    public static void spawnBirthsANaturalMob(GameTestHelper helper) {
        AbilityDefinition spawn = AbilityRegistry.of(helper.getLevel()).getAbility(HEX_SPAWN);
        helper.assertTrue(spawn != null, ABILITY_REQUIRED);
        helper.setBlock(FLOOR_POS, Blocks.STONE);
        ServerPlayer player = StackKeyTests.makeThrower(helper, GooTypes.HEX);
        KnownRecipes.teachRequires(player, spawn);
        player.getInventory().add(new ItemStack(Items.EGG));
        BlockPos landing = helper.absolutePos(FLOOR_POS.above());
        Set<EntityType<?>> natural = naturalSpawnsAt(helper, landing);

        GooThrowHandler.execute(player, new GooThrowPayload(GooTypes.id(GooTypes.HEX), NO_TARGET_ENTITY,
                helper.absolutePos(FLOOR_POS), Direction.UP.ordinal(), false, HEX_SPAWN.toString(),
                player.getEyePosition()));

        int eggs = player.getInventory().countItem(Items.EGG);
        helper.assertTrue(eggs == 0, String.format(EGG_TAKEN, eggs));
        helper.succeedWhen(() -> {
            List<LivingEntity> born = helper.getLevel().getEntitiesOfClass(LivingEntity.class,
                    new AABB(landing).inflate(SEARCH_REACH), living -> !(living instanceof Player));
            helper.assertTrue(born.stream().anyMatch(living -> natural.contains(living.getType())), NO_NATURAL_MOB);
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    private static Set<EntityType<?>> naturalSpawnsAt(GameTestHelper helper, BlockPos cell) {
        MobSpawnSettings settings = helper.getLevel().getBiome(cell).value().getMobSettings();
        return Arrays.stream(MobCategory.values())
                .flatMap(category -> settings.getMobs(category).unwrap().stream())
                .map(weighted -> weighted.value().type())
                .collect(Collectors.toSet());
    }
}
