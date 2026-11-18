package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.block.ability.VolumeCellBlock;
import com.mercuriusxeno.goo.gametest.GooTestFunctions;
import com.mercuriusxeno.goo.gametest.KnownRecipes;
import com.mercuriusxeno.goo.gametest.RegistersGameTests;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jspecify.annotations.Nullable;
import java.util.function.Consumer;

/**
 * A Molten cell burns a living thing touching it as vanilla lava does, a
 * layer for each hit that lands, spares a thing fire cannot hurt and every
 * item, and names the player who sprayed it on its hit. The cell stands on
 * dirt, which neither melts nor cooks, so no layer goes to the block below.
 * molten-burns-mobs-as-hard-as-lava-and-a-hit-spends-a-layer
 * molten-deals-no-fire-damage-to-items
 * standing-goo-thing-credits-whoever-placed-it
 */
@RegistersGameTests
public final class MoltenBurnTests {

    private static final String BURNS_A_PIG = "molten_burns_a_pig_as_lava_does";
    private static final String SPARES_A_STRIDER = "molten_spares_a_strider";
    private static final String SPARES_AN_ITEM = "molten_spares_an_item";
    private static final String HIT_NAMES_ITS_SPRAYER = "molten_hit_names_its_sprayer";
    private static final String UNPLACED_HIT_NAMES_NO_ONE = "molten_hit_from_an_unplaced_cell_names_no_one";
    private static final Identifier MOLTEN = Identifier.parse("goo:blaze_molten");
    /** The floor block the cell stands on, in the middle of the bay. */
    private static final BlockPos FLOOR = new BlockPos(2, 1, 2);
    /** The cell over the floor block. */
    private static final BlockPos CELL = FLOOR.above();
    private static final int FIVE_LAYERS = 5;
    /** The damage vanilla lava deals a hit, stated here so the test checks the hit rather than echoing it. */
    private static final float LAVA_DAMAGE = 4f;
    /** Ticks the strider stands on the cell. */
    private static final int STRIDER_TICKS = 60;
    /** Ticks the item lies on the cell. */
    private static final int ITEM_TICKS = 100;
    private static final int PLANKS = 16;
    private static final String ABILITY_REQUIRED = "The ability registry must hold blaze_molten";
    private static final String NOT_HIT = "The pig has not been hit yet";
    private static final String HIT_WRONG = "The pig's first hit should take %s health, set it alight and spend one"
            + " layer; it stands at %s health with %s fire ticks over %s";
    private static final String STRIDER_HURT = "A strider should take no Molten hit and cost no layer; it stands at"
            + " %s of %s health over %s";
    private static final String ITEM_HURT = "Oak planks on Molten should lie whole, unlit and cost no layer; alive %s,"
            + " %s planks, %s fire ticks over %s";
    private static final String SHOULD_NAME = "The Molten hit should be fire naming %s, is %s naming %s";

    private MoltenBurnTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, BURNS_A_PIG, MoltenBurnTests::moltenBurnsAPigAsLavaDoes);
        GooTestFunctions.registerTest(registrar, SPARES_A_STRIDER, MoltenBurnTests::moltenSparesAStrider);
        GooTestFunctions.registerTest(registrar, SPARES_AN_ITEM, MoltenBurnTests::moltenSparesAnItem);
        GooTestFunctions.registerTest(registrar, HIT_NAMES_ITS_SPRAYER, MoltenBurnTests::moltenHitNamesItsSprayer);
        GooTestFunctions.registerTest(registrar, UNPLACED_HIT_NAMES_NO_ONE,
                MoltenBurnTests::moltenHitFromAnUnplacedCellNamesNoOne);
    }

    /**
     * An armorless pig standing on five layers of Molten takes lava's 4
     * damage on its first hit, is set alight, and the cell stands a layer
     * lower, at four.
     *
     * @param helper the gametest helper
     */
    public static void moltenBurnsAPigAsLavaDoes(GameTestHelper helper) {
        pit(helper);
        setCell(helper, FIVE_LAYERS);
        Mob pig = helper.spawnWithNoFreeWill(EntityType.PIG, CELL);
        float whole = pig.getMaxHealth();
        helper.succeedWhen(() -> {
            helper.assertTrue(pig.getHealth() < whole, NOT_HIT);
            boolean hitAsLava = pig.getHealth() == whole - LAVA_DAMAGE && pig.getRemainingFireTicks() > 0
                    && layersOf(helper) == FIVE_LAYERS - 1;
            helper.assertTrue(hitAsLava, String.format(HIT_WRONG, LAVA_DAMAGE, pig.getHealth(),
                    pig.getRemainingFireTicks(), helper.getBlockState(CELL)));
        });
    }

    /**
     * A strider, which fire cannot hurt, stands on five layers of Molten for
     * three seconds whole, and the cell keeps all five.
     *
     * @param helper the gametest helper
     */
    public static void moltenSparesAStrider(GameTestHelper helper) {
        pit(helper);
        setCell(helper, FIVE_LAYERS);
        Mob strider = helper.spawnWithNoFreeWill(EntityType.STRIDER, CELL);
        helper.runAfterDelay(STRIDER_TICKS, () -> {
            helper.assertTrue(strider.getHealth() == strider.getMaxHealth() && layersOf(helper) == FIVE_LAYERS,
                    String.format(STRIDER_HURT, strider.getHealth(), strider.getMaxHealth(),
                            helper.getBlockState(CELL)));
            helper.succeed();
        });
    }

    /**
     * A stack of oak planks, which burn, lies on five layers of Molten for
     * five seconds whole and unlit, and the cell keeps all five.
     *
     * @param helper the gametest helper
     */
    public static void moltenSparesAnItem(GameTestHelper helper) {
        pit(helper);
        setCell(helper, FIVE_LAYERS);
        Vec3 drop = Vec3.atCenterOf(helper.absolutePos(CELL));
        ItemEntity planks = new ItemEntity(helper.getLevel(), drop.x, drop.y, drop.z,
                new ItemStack(Items.OAK_PLANKS, PLANKS), 0, 0, 0);
        helper.getLevel().addFreshEntity(planks);
        helper.runAfterDelay(ITEM_TICKS, () -> {
            boolean whole = planks.isAlive() && planks.getItem().getCount() == PLANKS
                    && planks.getRemainingFireTicks() <= 0 && layersOf(helper) == FIVE_LAYERS;
            helper.assertTrue(whole, String.format(ITEM_HURT, planks.isAlive(), planks.getItem().getCount(),
                    planks.getRemainingFireTicks(), helper.getBlockState(CELL)));
            helper.succeed();
        });
    }

    /**
     * A mock player sprays five layers of Molten into the pit, and the hit
     * the cell deals a pig standing on it is fire naming that player.
     *
     * @param helper the gametest helper
     */
    public static void moltenHitNamesItsSprayer(GameTestHelper helper) {
        pit(helper);
        AbilityDefinition molten = AbilityRegistry.of(helper.getLevel()).getAbility(MOLTEN);
        helper.assertTrue(molten != null, ABILITY_REQUIRED);
        ServerPlayer sprayer = SelfDeliveryTests.invoker(helper, GooTypes.BLAZE);
        KnownRecipes.teachRequires(sprayer, molten);
        LandedDrips.landOn(helper, sprayer, molten, FLOOR, FIVE_LAYERS);
        Mob pig = helper.spawnWithNoFreeWill(EntityType.PIG, CELL);
        succeedWhenNamed(helper, pig, sprayer);
    }

    /**
     * A Molten cell the test sets, which no player placed, hits a pig
     * standing on it with fire naming no entity.
     *
     * @param helper the gametest helper
     */
    public static void moltenHitFromAnUnplacedCellNamesNoOne(GameTestHelper helper) {
        pit(helper);
        setCell(helper, FIVE_LAYERS);
        Mob pig = helper.spawnWithNoFreeWill(EntityType.PIG, CELL);
        succeedWhenNamed(helper, pig, null);
    }

    // the pig's last hit may be the burning it was left with, so the test polls until the cell's own hit reads
    private static void succeedWhenNamed(GameTestHelper helper, Mob pig, @Nullable ServerPlayer named) {
        helper.succeedWhen(() -> {
            DamageSource last = pig.getLastDamageSource();
            helper.assertTrue(last != null, NOT_HIT);
            boolean firedNaming = last.is(DamageTypeTags.IS_FIRE) && last.getEntity() == named;
            helper.assertTrue(firedNaming, String.format(SHOULD_NAME, named == null ? "no one" : named.getName(),
                    last.getMsgId(), last.getEntity()));
            if (named != null) {
                helper.getLevel().getServer().getPlayerList().remove(named);
            }
        });
    }

    /**
     * A dirt floor walled in smooth stone with open air over it, so the
     * cell's goo cannot pour off and a mob stood in the cell stays on it.
     * The cell clings to its walls, and smooth stone neither melts nor
     * cooks, so the cell spends no layer heating them.
     * molten-heats-every-block-its-cell-touches
     */
    private static void pit(GameTestHelper helper) {
        helper.setBlock(FLOOR, Blocks.DIRT);
        for (Direction side : Direction.Plane.HORIZONTAL) {
            helper.setBlock(CELL.relative(side), Blocks.SMOOTH_STONE);
        }
        helper.setBlock(CELL, Blocks.AIR);
    }

    private static void setCell(GameTestHelper helper, int layers) {
        helper.setBlock(CELL, GooBlocks.BLAZE_MOLTEN.get().defaultBlockState().setValue(VolumeCellBlock.LAYERS, layers));
    }

    private static int layersOf(GameTestHelper helper) {
        BlockState state = helper.getBlockState(CELL);
        return state.is(GooBlocks.BLAZE_MOLTEN.get()) ? state.getValue(VolumeCellBlock.LAYERS) : 0;
    }
}
