package com.mercuriusxeno.goo.lab;

import com.mercuriusxeno.goo.GooTypeDefinition;
import com.mercuriusxeno.goo.GooTypes;
import com.mercuriusxeno.goo.block.canister.CanisterBlock;
import com.mercuriusxeno.goo.block.canister.CanisterBlockEntity;
import com.mercuriusxeno.goo.block.crucible.CrucibleBlockEntity;
import com.mercuriusxeno.goo.block.hub.HubBlockEntity;
import com.mercuriusxeno.goo.block.plexer.PlexerBlockEntity;
import com.mercuriusxeno.goo.block.reactor.ReactorBlockEntity;
import com.mercuriusxeno.goo.block.tap.TapBlockEntity;
import com.mercuriusxeno.goo.block.vat.VatBlockEntity;
import com.mercuriusxeno.goo.data.GasketRegistry;
import com.mercuriusxeno.goo.item.CanisterItem;
import com.mercuriusxeno.goo.item.gasket.GasketPartner;
import com.mercuriusxeno.goo.item.gasket.GasketRole;
import com.mercuriusxeno.goo.registry.GooItems;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BiConsumer;

/**
 * Fills each machine bay after {@link LabBuilder} sets its blocks, so the
 * machine runs where it stands: canisters slotted and filled, fuel loaded,
 * gaskets paired (decision lab-holds-bays-supply-pens-kit). Every step goes
 * through the block entity's own insertion methods, the ones a player's
 * click reaches, rather than written NBT; the gasket pairing makes the
 * writes a completed choral tuner link makes.
 */
public final class LabRigs {

    /**
     * Goo each filled canister holds: enough to watch a machine run for a while.
     */
    public static final int CANISTER_FILL = 16_000;
    /**
     * Goo the vat starts with.
     */
    public static final int VAT_FILL = 64_000;
    /**
     * The type the single-type bays run on; it carries no destructive tap ability.
     */
    public static final ResourceKey<GooTypeDefinition> BAY_TYPE = GooTypes.ROCK;
    /**
     * The reactor's corner inputs, a pair the bundled crystal_from_unstable_rock reaction takes.
     */
    public static final List<ResourceKey<GooTypeDefinition>> REACTOR_INPUTS = List.of(GooTypes.UNSTABLE, GooTypes.ROCK);
    /**
     * The canister block's corner slots, the ones a reactor reads.
     */
    private static final int[] CORNER_SLOTS = {0, 2, 6, 8};
    /** Blaze goo poured into the lab crucible as fuel: a bucket's worth, 4000 heat ticks at the default grade. */
    private static final int LAB_BLAZE_FUEL = 1_000;
    /**
     * Hub slots the bay fills: every other radial slot, so the empty ones show where more go.
     */
    private static final int[] HUB_SLOTS = {0, 2, 4, 6};
    /**
     * Each single-position machine's rig, given the machine block's world position.
     */
    private static final Map<LabMachine, BiConsumer<ServerLevel, BlockPos>> RIGS = rigTable();

    private LabRigs() {
    }

    /**
     * Fills a plot's bay with its origin at the given world position.
     *
     * @param level  the level the bay stands in
     * @param origin the world position of the plan's zero offset
     * @param plot   the plot whose bay to fill
     */
    public static void rig(ServerLevel level, BlockPos origin, LabPlot plot) {
        if (plot.machine() == LabMachine.CHORAL_GASKET) {
            rigGasketRun(level, LabBuilder.worldPos(origin, LabBays.gasketSource(plot)),
                    LabBuilder.worldPos(origin, LabBays.gasketReceiver(plot)));
            return;
        }
        BiConsumer<ServerLevel, BlockPos> machineRig = RIGS.get(plot.machine());
        if (machineRig != null) {
            machineRig.accept(level, LabBuilder.worldPos(origin, plot.machineOffset()));
        }
    }

    /**
     * Builds the table of each single-position machine's rig.
     *
     * @return the table, keyed by machine
     */
    private static Map<LabMachine, BiConsumer<ServerLevel, BlockPos>> rigTable() {
        Map<LabMachine, BiConsumer<ServerLevel, BlockPos>> table = new EnumMap<>(LabMachine.class);
        table.put(LabMachine.TAP, LabRigs::rigTap);
        table.put(LabMachine.VAT, LabRigs::rigVat);
        table.put(LabMachine.CRUCIBLE, LabRigs::rigCrucible);
        table.put(LabMachine.HUB, LabRigs::rigHub);
        table.put(LabMachine.PLEXER, LabRigs::rigPlexer);
        table.put(LabMachine.REACTOR, LabRigs::rigReactor);
        return table;
    }

    /**
     * Slots a filled canister into the tap; the open valve drips it onto the floor below.
     *
     * @param level the level
     * @param at    the tap's position
     */
    private static void rigTap(ServerLevel level, BlockPos at) {
        if (level.getBlockEntity(at) instanceof TapBlockEntity tap) {
            tap.insertCanister(new ItemStack(GooItems.CANISTER.get()));
            tap.insertGoo(BAY_TYPE, CANISTER_FILL);
        }
    }

    /**
     * Fills the vat.
     *
     * @param level the level
     * @param at    the vat's position
     */
    private static void rigVat(ServerLevel level, BlockPos at) {
        if (level.getBlockEntity(at) instanceof VatBlockEntity vat) {
            vat.insertGoo(BAY_TYPE, VAT_FILL);
        }
    }

    /**
     * Pours blaze goo into the crucible's reservoir as fuel, so an item dropped in melts.
     *
     * @param level the level
     * @param at    the crucible's position
     */
    private static void rigCrucible(ServerLevel level, BlockPos at) {
        if (level.getBlockEntity(at) instanceof CrucibleBlockEntity crucible) {
            crucible.insertGoo(GooTypes.BLAZE, LAB_BLAZE_FUEL);
        }
    }

    /**
     * Slots a filled canister into every other hub slot.
     *
     * @param level the level
     * @param at    the hub's position
     */
    private static void rigHub(ServerLevel level, BlockPos at) {
        if (level.getBlockEntity(at) instanceof HubBlockEntity hub) {
            for (int slot : HUB_SLOTS) {
                hub.insertCanister(slot, filledCanister(BAY_TYPE));
            }
        }
    }

    /**
     * Fills the canister block on the plexer's top and sets its target to cobblestone.
     *
     * @param level the level
     * @param at    the plexer's position
     */
    private static void rigPlexer(ServerLevel level, BlockPos at) {
        if (level.getBlockEntity(at.above()) instanceof CanisterBlockEntity supply) {
            supply.insertCanister(CanisterBlock.CENTER_SLOT, filledCanister(BAY_TYPE), false);
        }
        if (level.getBlockEntity(at) instanceof PlexerBlockEntity plexer) {
            plexer.setTargetItem(new ItemStack(Items.COBBLESTONE));
        }
    }

    /**
     * Fills the reactor's corner inputs with a reaction's pair and slots an empty output canister.
     *
     * @param level the level
     * @param at    the reactor's position
     */
    private static void rigReactor(ServerLevel level, BlockPos at) {
        if (level.getBlockEntity(at.above()) instanceof CanisterBlockEntity inputs) {
            for (int index = 0; index < CORNER_SLOTS.length; index++) {
                ResourceKey<GooTypeDefinition> type = REACTOR_INPUTS.get(index % REACTOR_INPUTS.size());
                inputs.insertCanister(CORNER_SLOTS[index], filledCanister(type), false);
            }
        }
        if (level.getBlockEntity(at) instanceof ReactorBlockEntity reactor) {
            reactor.insertOutputCanister(new ItemStack(GooItems.CANISTER.get()));
        }
    }

    /**
     * Joins two canister blocks with a choral gasket run: the source's centre
     * canister transmits from its bottom gasket to the receiver's centre canister's top gasket.
     *
     * @param level    the level
     * @param source   the transmitting canister block's position
     * @param receiver the receiving canister block's position
     */
    private static void rigGasketRun(ServerLevel level, BlockPos source, BlockPos receiver) {
        if (!(level.getBlockEntity(source) instanceof CanisterBlockEntity from)
                || !(level.getBlockEntity(receiver) instanceof CanisterBlockEntity to)) {
            return;
        }
        UUID transmitter = UUID.randomUUID();
        UUID intake = UUID.randomUUID();
        ItemStack sending = filledCanister(BAY_TYPE);
        CanisterItem.setMetadata(sending, CanisterItem.getMetadata(sending).withBottomGasketId(transmitter));
        from.insertCanister(CanisterBlock.CENTER_SLOT, sending, false);
        ItemStack receiving = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.setMetadata(receiving, CanisterItem.getMetadata(receiving).withTopGasketId(intake));
        to.insertCanister(CanisterBlock.CENTER_SLOT, receiving, false);
        GasketRegistry.get(level).link(transmitter, intake);
        to.setPartner(GasketRole.RECEIVER, CanisterBlock.CENTER_SLOT, new GasketPartner(source, CanisterBlock.CENTER_SLOT));
        from.setPartner(GasketRole.TRANSMITTER, CanisterBlock.CENTER_SLOT,
                new GasketPartner(receiver, CanisterBlock.CENTER_SLOT));
    }

    /**
     * Answers a canister item holding {@link #CANISTER_FILL} of a type.
     *
     * @param type the goo type
     * @return the filled canister
     */
    static ItemStack filledCanister(ResourceKey<GooTypeDefinition> type) {
        ItemStack canister = new ItemStack(GooItems.CANISTER.get());
        CanisterItem.addGoo(canister, type, CANISTER_FILL);
        return canister;
    }
}
