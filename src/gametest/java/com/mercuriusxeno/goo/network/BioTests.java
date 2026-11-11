package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.network.GooEffectScheduler.PendingEffect;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.neoforged.neoforge.registries.RegisterEvent;
import java.util.function.Consumer;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;

/**
 * Gametest for Leaf Bio: a struck zombie loses the toxin's share of its max
 * health each second.
 * bio-toxin-stacks-to-amplitude-two
 */
@RegistersGameTests
public final class BioTests {
    private static final String BIO_DRAINS_ITS_SHARE = "bio_drains_its_share_a_second";

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, BIO_DRAINS_ITS_SHARE, BioTests::bioDrainsItsShareASecond);
    }


    private static final String BIO = "goo:leaf_bio";
    private static final BlockPos ZOMBIE_POS = new BlockPos(2, 1, 2);
    private static final int SECOND = 20;
    /** leaf_bio.json's share of max health a second. */
    private static final double SHARE = 0.06;
    /** The tick the strike lands on; the second watched is the toxin's second. */
    private static final int STRIKE = 1;
    private static final float TOLERANCE = 0.01f;
    private static final String ABILITY_REQUIRED = "Ability registry must hold leaf_bio";
    private static final String ONE_SECOND_LOSS = "A second of the toxin should take %.2f, took %.2f";

    private BioTests() {
    }

    /**
     * Strikes a helmeted zombie with Bio once: one second of the toxin at
     * amplitude 1 takes 6% of its max health.
     *
     * @param helper the gametest helper
     */
    public static void bioDrainsItsShareASecond(GameTestHelper helper) {
        Mob zombie = helper.spawnWithNoFreeWill(EntityType.ZOMBIE, ZOMBIE_POS);
        zombie.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        float perSecond = (float) (SHARE * zombie.getMaxHealth());
        float[] health = new float[1];
        helper.runAfterDelay(STRIKE, () -> strike(helper, zombie));
        helper.runAfterDelay(STRIKE + SECOND, () -> health[0] = zombie.getHealth());
        helper.runAfterDelay(STRIKE + 2 * SECOND, () -> {
            float lost = health[0] - zombie.getHealth();
            helper.assertTrue(Math.abs(lost - perSecond) < TOLERANCE, String.format(ONE_SECOND_LOSS, perSecond, lost));
            helper.succeed();
        });
    }

    private static void strike(GameTestHelper helper, Mob mob) {
        AbilityDefinition bio = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(BIO));
        helper.assertTrue(bio != null, ABILITY_REQUIRED);
        GooEffectScheduler.applyEffect(new PendingEffect(0, helper.getLevel(), null, bio.gooType(),
                mob.getId(), mob.blockPosition(), Direction.UP, BIO));
    }
}
