package com.mercuriusxeno.goo.gametest;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.PrismCombos;
import com.mercuriusxeno.goo.ability.hex.Charmed;
import com.mercuriusxeno.goo.ability.yarn.Led;
import com.mercuriusxeno.goo.block.ability.CellPlacers;
import com.mercuriusxeno.goo.block.ability.PrismBlockEntity;
import com.mercuriusxeno.goo.block.ability.RailLine;
import com.mercuriusxeno.goo.block.ability.WispsSmite;
import com.mercuriusxeno.goo.registry.GooAttachments;
import com.mercuriusxeno.goo.registry.GooBlocks;
import com.mercuriusxeno.goo.type.GooTypes;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;
import net.neoforged.neoforge.registries.RegisterEvent;
import org.jspecify.annotations.Nullable;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Gametests for the hits a standing goo block deals: a light rail a player's Laser reflectors laid burns an undead
 * naming that player as magic, a rail no player laid names no one, the rail's credited hit leaves that player's
 * lead and charm standing, and a Wisps or Rime cell a player placed names that player on its holy or freeze hit.
 * standing-goo-thing-credits-whoever-placed-it
 * goo-hit-keeps-its-kind-and-names-the-player-behind-it
 * crediting-a-goo-hit-changes-nothing-else
 */
@RegistersGameTests
public final class StandingBlockCreditTests {
    private static final String RAIL_NAMES_ITS_LAYER = "standing_credit_rail_names_the_player_who_laid_it";
    private static final String UNLAID_RAIL_NAMES_NONE = "standing_credit_rail_no_player_laid_names_no_entity";
    private static final String RAIL_KEEPS_LEAD_AND_CHARM = "standing_credit_rail_keeps_lead_and_charm";
    private static final String WISPS_NAMES_ITS_PLACER = "standing_credit_wisps_cell_names_its_placer";
    private static final String RIME_NAMES_ITS_PLACER = "standing_credit_rime_cell_names_its_placer";

    /** Laser, whose on_prism reaction is the reflector. */
    private static final String REFLECTOR = "goo:glow_laser";
    private static final String NOT_A_REFLECTOR = "The prism should take the reflector combo";
    private static final BlockPos NEAR_PRISM = new BlockPos(0, 1, 0);
    private static final BlockPos FAR_PRISM = new BlockPos(5, 1, 0);
    private static final BlockPos CELL = new BlockPos(3, 1, 3);
    /** Blocks above the bay the player hangs, clear of the rail and the cells. */
    private static final int PLAYER_HEIGHT = 12;
    private static final float HARDY_HEALTH = 1024f;
    private static final float LEAD_DAMAGE = 6f;

    private static final String NOT_STRUCK = "The %s has not been hit yet";
    private static final String SHOULD_NAME_THE_PLAYER = "The %s's hit should name the player who placed it as %s, "
            + "last hit by %s from %s";
    private static final String SHOULD_NAME_NONE = "A rail no player laid should hit as magic naming no entity, "
            + "last hit by %s from %s";
    private static final String SHOULD_KEEP = "The layer's own %s should keep its %s after the rail hit it, holds it %s";

    private StandingBlockCreditTests() {
    }

    /**
     * Registers this class's gametest functions, found and called by GooTestFunctions.
     *
     * @param registrar the test function registrar
     */
    public static void register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>> registrar) {
        GooTestFunctions.registerTest(registrar, RAIL_NAMES_ITS_LAYER, StandingBlockCreditTests::railNamesThePlayerWhoLaidIt);
        GooTestFunctions.registerTest(registrar, UNLAID_RAIL_NAMES_NONE,
                StandingBlockCreditTests::railNoPlayerLaidNamesNoEntity);
        GooTestFunctions.registerTest(registrar, RAIL_KEEPS_LEAD_AND_CHARM,
                StandingBlockCreditTests::railKeepsTheLayersLeadAndCharm);
        GooTestFunctions.registerTest(registrar, WISPS_NAMES_ITS_PLACER, StandingBlockCreditTests::wispsCellNamesItsPlacer);
        GooTestFunctions.registerTest(registrar, RIME_NAMES_ITS_PLACER, StandingBlockCreditTests::rimeCellNamesItsPlacer);
    }

    /**
     * Two prisms a mock player's goo converted into reflectors lay a rail that burns a zombie in it as magic
     * naming that player.
     *
     * @param helper the gametest helper
     */
    public static void railNamesThePlayerWhoLaidIt(GameTestHelper helper) {
        ServerPlayer player = playerOverhead(helper);
        List<BlockPos> floorCells = railFloorCells();
        Mob zombie = undeadIn(helper, EntityType.ZOMBIE, floorCells.getFirst());
        layRail(helper, player.getUUID());
        succeedWhenNamed(helper, zombie, "rail", player, DamageTypes.MAGIC);
    }

    /**
     * A rail set with no reflector or player behind it burns a zombie in it as magic naming no entity.
     *
     * @param helper the gametest helper
     */
    public static void railNoPlayerLaidNamesNoEntity(GameTestHelper helper) {
        BlockPos cell = railFloorCells().getFirst();
        helper.setBlock(cell.below(), Blocks.STONE);
        helper.setBlock(cell, GooBlocks.LIGHT_RAIL.get());
        Mob zombie = undeadIn(helper, EntityType.ZOMBIE, cell);
        helper.succeedWhen(() -> {
            DamageSource last = struck(helper, zombie, "zombie");
            helper.assertTrue(last.getEntity() == null && last.is(DamageTypes.MAGIC),
                    String.format(SHOULD_NAME_NONE, causeOf(last), last));
        });
    }

    /**
     * A rail a mock player's reflectors laid burns a zombie that player led and a husk that player charmed,
     * and the lead and the charm still stand once each has been burned.
     *
     * @param helper the gametest helper
     */
    public static void railKeepsTheLayersLeadAndCharm(GameTestHelper helper) {
        ServerPlayer player = playerOverhead(helper);
        List<BlockPos> floorCells = railFloorCells();
        Mob zombie = undeadIn(helper, EntityType.ZOMBIE, floorCells.getFirst());
        zombie.setData(GooAttachments.LED, new Led(player.getUUID(), LEAD_DAMAGE));
        Mob husk = undeadIn(helper, EntityType.HUSK, floorCells.getLast());
        husk.setData(GooAttachments.CHARMED, new Charmed(player.getUUID()));
        layRail(helper, player.getUUID());
        helper.succeedWhen(() -> {
            struck(helper, zombie, "led zombie");
            struck(helper, husk, "charmed husk");
            helper.assertTrue(zombie.hasData(GooAttachments.LED),
                    String.format(SHOULD_KEEP, "zombie", "lead", zombie.hasData(GooAttachments.LED)));
            helper.assertTrue(husk.hasData(GooAttachments.CHARMED),
                    String.format(SHOULD_KEEP, "husk", "charm", husk.hasData(GooAttachments.CHARMED)));
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    /**
     * A Wisps cell a mock player placed smites a wither skeleton in it with holy damage naming that player; the
     * skeleton shrugs off the fire the smite lights, so the holy hit is the one it reads.
     *
     * @param helper the gametest helper
     */
    public static void wispsCellNamesItsPlacer(GameTestHelper helper) {
        ServerPlayer player = playerOverhead(helper);
        placeCell(helper, GooBlocks.GLOW_WISPS.get(), player.getUUID());
        Mob skeleton = undeadIn(helper, EntityType.WITHER_SKELETON, CELL);
        succeedWhenNamed(helper, skeleton, "Wisps cell", player, WispsSmite.HOLY);
    }

    /**
     * A Rime cell a mock player placed freezes a barefoot pig standing on it with freeze damage naming that player.
     *
     * @param helper the gametest helper
     */
    public static void rimeCellNamesItsPlacer(GameTestHelper helper) {
        ServerPlayer player = playerOverhead(helper);
        placeCell(helper, GooBlocks.FROST_RIME.get(), player.getUUID());
        Mob pig = hardy(helper.spawnWithNoFreeWill(EntityType.PIG, CELL));
        succeedWhenNamed(helper, pig, "Rime cell", player, DamageTypes.FREEZE);
    }

    // a mob's last hit may be the vanilla fire or freeze it was left with, so the test polls until the goo's own lands
    private static void succeedWhenNamed(GameTestHelper helper, Mob mob, String hitBy, ServerPlayer player,
                                         ResourceKey<DamageType> type) {
        helper.succeedWhen(() -> {
            DamageSource last = struck(helper, mob, mob.getType().getDescription().getString());
            helper.assertTrue(last.getEntity() == player && last.is(type),
                    String.format(SHOULD_NAME_THE_PLAYER, hitBy, type.identifier(), causeOf(last), last));
            helper.getLevel().getServer().getPlayerList().remove(player);
        });
    }

    private static List<BlockPos> railFloorCells() {
        return RailLine.between(NEAR_PRISM, FAR_PRISM).stream().filter(cell -> cell.getY() == NEAR_PRISM.getY())
                .toList();
    }

    private static void layRail(GameTestHelper helper, @Nullable UUID player) {
        for (BlockPos prism : List.of(NEAR_PRISM, FAR_PRISM)) {
            helper.setBlock(prism.below(), Blocks.STONE);
            helper.setBlock(prism, GooBlocks.PRISM.get());
        }
        AbilityDefinition laser = AbilityRegistry.of(helper.getLevel()).getAbility(Identifier.parse(REFLECTOR));
        helper.assertTrue(laser != null, NOT_A_REFLECTOR);
        for (BlockPos prism : List.of(NEAR_PRISM, FAR_PRISM)) {
            helper.assertTrue(helper.getBlockEntity(prism, PrismBlockEntity.class)
                    .runCombo(GooTypes.GLOW, REFLECTOR, PrismCombos.comboSteps(laser), player), NOT_A_REFLECTOR);
        }
    }

    private static void placeCell(GameTestHelper helper, Block cell, UUID player) {
        helper.setBlock(CELL.below(), Blocks.STONE);
        helper.setBlock(CELL, cell);
        CellPlacers.of(helper.getLevel()).place(helper.absolutePos(CELL), player);
    }

    // a helmet keeps a zombie out of the sun's fire, so the rail's hit is the one it reads
    private static Mob undeadIn(GameTestHelper helper, EntityType<? extends Mob> type, BlockPos cell) {
        helper.setBlock(cell.below(), Blocks.STONE);
        Mob undead = hardy(helper.spawnWithNoFreeWill(type, cell));
        undead.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.LEATHER_HELMET));
        return undead;
    }

    private static Mob hardy(Mob mob) {
        mob.getAttribute(Attributes.MAX_HEALTH).setBaseValue(HARDY_HEALTH);
        mob.setHealth(HARDY_HEALTH);
        return mob;
    }

    private static DamageSource struck(GameTestHelper helper, Mob mob, String name) {
        DamageSource last = mob.getLastDamageSource();
        helper.assertTrue(last != null, String.format(NOT_STRUCK, name));
        return last;
    }

    private static String causeOf(DamageSource source) {
        return source.getEntity() == null ? "no one" : source.getEntity().getName().getString();
    }

    @SuppressWarnings("removal") // vanilla marks the mock server player helper for removal and names no replacement
    private static ServerPlayer playerOverhead(GameTestHelper helper) {
        ServerPlayer player = helper.makeMockServerPlayerInLevel();
        Vec3 hang = Vec3.atBottomCenterOf(helper.absolutePos(CELL.above(PLAYER_HEIGHT)));
        player.setNoGravity(true);
        player.setPos(hang.x, hang.y, hang.z);
        return player;
    }
}
