package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
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

    private HexSpawnTests() {
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
