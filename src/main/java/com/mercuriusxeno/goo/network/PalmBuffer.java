package com.mercuriusxeno.goo.network;

import com.mercuriusxeno.goo.ability.AbilityDefinition;
import com.mercuriusxeno.goo.ability.AbilityRegistry;
import com.mercuriusxeno.goo.ability.Charge;
import com.mercuriusxeno.goo.ability.GloveSelection;
import com.mercuriusxeno.goo.data.PalmBuffers;
import com.mercuriusxeno.goo.item.GooCasters;
import com.mercuriusxeno.goo.item.GooGloveItem;
import com.mercuriusxeno.goo.registry.GooAttachments;
import net.minecraft.resources.Identifier;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The palm blob as a buffer, on the server: each ability that stacks what it
 * throws keeps its own fill per player, from the 10% floor to 100%, born full.
 * Each weapon takes a share as it forms, 90% over the stack, 9% a knife and
 * 30% a javelin, and forms only where the fill less its share stays at or
 * above the floor. The weapons held reserve their shares: the fill refills,
 * at the speed of what casts, only while its ability is selected in hand,
 * and only to 100% less the shares held. A throw frees its share and returns nothing; a
 * release returns every held share.
 * decision palm-blob-is-a-buffer-that-right-click-turns-into-weapons
 * decision held-weapons-reserve-their-share-of-the-buffer
 * decision each-ability-has-its-own-buffer-that-fills-only-while-held
 * decision weapons-form-at-their-standing-pace-when-the-buffer-affords-a-share
 * decision buffer-refills-at-a-constant-speed-set-by-the-glove
 */
public final class PalmBuffer {

    /** Half percents a percent. */
    private static final double HALF_PERCENTS = 2.0;
    /** The fill a tick the Goo Glove refills, 1%, in half percents. */
    static final int REFILL_A_TICK = 2;
    /** The fill a tick raw goo held bare handed refills, 0.5%, in half percents. */
    private static final int BARE_HANDED_REFILL = 1;

    private PalmBuffer() {
    }

    /**
     * An ability's fill for a player, in percent.
     *
     * @param player  the player
     * @param ability the ability
     * @return the fill, 10 to 100
     */
    public static double percentOf(ServerPlayer player, Identifier ability) {
        return fillOf(player, ability.toString()) / HALF_PERCENTS;
    }

    /**
     * The share one weapon of a stacking charge takes, in half percents: 90%
     * over the stack, so the stack held whole leaves the floor.
     *
     * @param charge the charge
     * @return the share
     */
    static int shareOf(Charge charge) {
        return charge.stack() <= 0 ? 0 : (PalmBuffers.FULL - PalmBuffers.FLOOR) / charge.stack();
    }

    /**
     * Whether a fill affords one more share above the floor.
     *
     * @param fill  the fill, in half percents
     * @param share the share, in half percents
     * @return true where the fill less the share stays at or above the floor
     */
    static boolean affords(int fill, int share) {
        return fill - share >= PalmBuffers.FLOOR;
    }

    /**
     * A fill a tick's refill on: up a tick's speed, never past 100% less the
     * shares held, and never down.
     *
     * @param fill       the fill, in half percents
     * @param heldShares the shares of the weapons held, in half percents
     * @param speed      the refill a tick, in half percents
     * @return the fill after the tick
     */
    static int refilled(int fill, int heldShares, int speed) {
        int cap = PalmBuffers.FULL - heldShares;
        return fill >= cap ? fill : Math.min(fill + speed, cap);
    }

    /**
     * Takes one weapon's share from the player's fill for an ability, where it affords one.
     *
     * @param player    the player
     * @param abilityId the ability's id
     * @param share     the share, in half percents
     * @return true where the share was taken
     */
    static boolean takeShare(ServerPlayer player, String abilityId, int share) {
        int fill = fillOf(player, abilityId);
        if (!affords(fill, share)) {
            return false;
        }
        setFill(player, abilityId, fill - share);
        return true;
    }

    /**
     * Returns the shares of weapons released to the player's fill for an ability.
     *
     * @param player    the player
     * @param abilityId the ability's id
     * @param shares    the shares returned, in half percents
     */
    static void giveBack(ServerPlayer player, String abilityId, int shares) {
        if (shares > 0) {
            setFill(player, abilityId, fillOf(player, abilityId) + shares);
        }
    }

    /**
     * Refills, one tick, the buffer of the ability each player has selected
     * in hand, right click up or down; every other buffer stands still.
     *
     * @param server the server
     * @param holds  the standing holds, whose weapons held reserve their shares
     */
    public static void refillEvery(MinecraftServer server, KnifeHolds holds) {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            AbilityDefinition selected = selectedStackingAbility(player);
            if (selected != null) {
                String abilityId = selected.id().toString();
                int heldShares = holds.heldIn(player, abilityId) * shareOf(selected.delivery().charge());
                // buffer-refills-at-a-constant-speed-set-by-the-glove: read each tick, so a swap changes the speed at once
                setFill(player, abilityId, refilled(fillOf(player, abilityId), heldShares, refillSpeedOf(player)));
            }
        }
    }

    /**
     * The refill a tick of what casts, in half percents.
     *
     * @param tier the glove's tier, or null for raw goo held bare handed
     * @return the refill a tick
     */
    static int refillSpeedOf(GooGloveItem.@Nullable GloveTier tier) {
        return tier == null ? BARE_HANDED_REFILL : tier.bufferRefill();
    }

    // The refill a tick of the caster the player holds: its glove's tier, or the bare hand's.
    private static int refillSpeedOf(ServerPlayer player) {
        ItemStack caster = GooCasters.casterOf(player);
        return refillSpeedOf(caster != null && caster.getItem() instanceof GooGloveItem glove ? glove.tier() : null);
    }

    private static @Nullable AbilityDefinition selectedStackingAbility(ServerPlayer player) {
        GloveSelection selection = GooCasters.heldSelection(player);
        Identifier id = selection == null ? null : Identifier.tryParse(selection.abilityId());
        AbilityDefinition ability = id == null ? null : AbilityRegistry.of(player.level()).getAbility(id);
        return ability != null && ability.delivery().charge().stacks() ? ability : null;
    }

    private static int fillOf(ServerPlayer player, String abilityId) {
        return player.getData(GooAttachments.PALM_BUFFERS).fillOf(abilityId);
    }

    private static void setFill(ServerPlayer player, String abilityId, int fill) {
        player.setData(GooAttachments.PALM_BUFFERS, player.getData(GooAttachments.PALM_BUFFERS).with(abilityId, fill));
    }
}
