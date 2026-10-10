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
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.phys.Vec3;
import java.util.List;

/**
 * Gametests for Crystal's Shards: a held charge released slings a sweep of
 * flecks across a cone, striking several mobs at once.
 * decision shards-sling-then-morph-to-flechettes
 */
public final class ShardsSlingTests {

    private static final Identifier CRYSTAL_SHARDS = Identifier.fromNamespaceAndPath(Goo.MODID, "crystal_shards");
    private static final BlockPos CASTER_POS = new BlockPos(2, 1, 0);
    /** A row of husks across the cone four blocks ahead; husks, being zombies that never burn in daylight. */
    private static final List<BlockPos> ROW = List.of(new BlockPos(1, 1, 4), new BlockPos(2, 1, 4),
            new BlockPos(3, 1, 4));
    /** Facing south, down the bay toward the row. */
    private static final float FACING_SOUTH = 0f;
    private static final int FULL_CHARGE_TICKS = 30;
    private static final int SEVERAL = 2;
    private static final String ABILITY_REQUIRED = "Ability registry must hold crystal_shards";
    private static final String SHOULD_HIT_SEVERAL = "One release should damage at least %s husks in the row, damaged %s";

    private ShardsSlingTests() {
    }

    /**
     * A mock player holds crystal_shards to a full charge and lets go facing
     * a row of three husks; several of them take damage from the one release.
     *
     * @param helper the gametest helper
     */
    public static void releaseHitsARow(GameTestHelper helper) {
        List<Mob> row = ROW.stream().<Mob>map(pos -> helper.spawnWithNoFreeWill(EntityType.HUSK, pos)).toList();
        ServerPlayer caster = SelfDeliveryTests.invoker(helper, GooTypes.CRYSTAL);
        Vec3 stand = Vec3.atBottomCenterOf(helper.absolutePos(CASTER_POS));
        caster.snapTo(stand.x, stand.y, stand.z, FACING_SOUTH, 0f);
        AbilityDefinition shards = AbilityRegistry.of(helper.getLevel()).getAbility(CRYSTAL_SHARDS);
        helper.assertTrue(shards != null, ABILITY_REQUIRED);
        KnownRecipes.teachRequires(caster, shards);

        GooThrowHandler.releaseCharge(caster,
                new GooChargePayload(GooTypes.id(GooTypes.CRYSTAL), CRYSTAL_SHARDS.toString(), FULL_CHARGE_TICKS));

        helper.succeedWhen(() -> {
            long damaged = row.stream().filter(husk -> husk.getHealth() < husk.getMaxHealth()).count();
            helper.assertTrue(damaged >= SEVERAL, String.format(SHOULD_HIT_SEVERAL, SEVERAL, damaged));
            helper.getLevel().getServer().getPlayerList().remove(caster);
        });
    }
}
