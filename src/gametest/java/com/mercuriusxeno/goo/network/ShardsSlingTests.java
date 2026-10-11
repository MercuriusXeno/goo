package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * Gametests for Crystal's Shards: a full charge let go rains glass knives
 * over an area ahead, striking mobs spread across it, and no knife strikes
 * before it has left the hand and flown.
 * decision shards-sling-then-morph-to-flechettes
 */
public final class ShardsSlingTests {

    private static final Identifier CRYSTAL_SHARDS = Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_shards");
    /** The light bay is sixteen blocks across and holds no floor of its own. */
    private static final int BAY_SIDE = 16;
    private static final BlockPos CASTER_POS = new BlockPos(8, 1, 1);
    /** Husks across the cone seven blocks ahead, where the lobbed knives come down through them. */
    private static final List<BlockPos> SPREAD = List.of(new BlockPos(6, 1, 8), new BlockPos(8, 1, 8),
            new BlockPos(10, 1, 8));
    /** Facing south, down the bay toward the husks. */
    private static final float FACING_SOUTH = 0f;
    private static final int FULL_CHARGE_TICKS = 30;
    /** A fixed scatter, so the rain falls the same each run. */
    private static final long SCATTER_SEED = 11L;
    private static final String ABILITY_REQUIRED = "Ability registry must hold crystal_shards";
    private static final String STRUCK_AT_ONCE = "No husk should take a knife the tick the charge is let go";
    private static final String SHOULD_RAIN = "The rain should strike every husk across the area, struck %s of %s";

    private ShardsSlingTests() {
    }

    /**
     * A mock player holds crystal_shards to a full charge and lets go facing
     * three husks spread across the area ahead: none is struck the tick it
     * lets go, and the rain strikes all three.
     *
     * @param helper the gametest helper
     */
    public static void shardsRainLaysIntoAnArea(GameTestHelper helper) {
        for (int x = 0; x < BAY_SIDE; x++) {
            for (int z = 0; z < BAY_SIDE; z++) {
                helper.setBlock(new BlockPos(x, 0, z), Blocks.STONE);
            }
        }
        List<Mob> husks = SPREAD.stream().<Mob>map(pos -> helper.spawnWithNoFreeWill(EntityType.HUSK, pos)).toList();
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.CRYSTAL);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(CASTER_POS));
        caster.snapTo(stand.x, stand.y, stand.z, FACING_SOUTH, 0f);
        AbilityDefinition shards = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_SHARDS);
        helper.assertTrue(shards != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(caster, shards);

        KnifeRainSling.sling(caster, GooTypes.CRYSTAL, shards, FULL_CHARGE_TICKS, RandomSource.create(SCATTER_SEED));

        helper.assertTrue(husks.stream().allMatch(husk -> husk.getHealth() == husk.getMaxHealth()), STRUCK_AT_ONCE);
        helper.succeedWhen(() -> {
            long struck = husks.stream().filter(husk -> husk.getHealth() < husk.getMaxHealth()).count();
            helper.assertTrue(struck == husks.size(), String.format(SHOULD_RAIN, struck, husks.size()));
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
    }
}
