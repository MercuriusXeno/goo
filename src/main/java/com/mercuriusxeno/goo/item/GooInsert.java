package com.mercuriusxeno.goo.item;

import com.mercuriusxeno.goo.data.GooFraction;
import com.mercuriusxeno.goo.data.IRemainderLedger;
import com.mercuriusxeno.goo.type.GooTypeDefinition;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jspecify.annotations.Nullable;

/**
 * The in-world goo insert the vat, hub and canister blocks share (decision block-insert-shared),
 * and the one pour every goo item entering a store runs: the store takes whole mB, and a
 * store taking the stack's whole mB entire is credited the stack's part under 1 mB.
 * decision goo-item-carries-its-exact-value-as-a-mixed-remainder
 * decision every-producer-consumer-and-container-holds-a-remainder-buffer
 */
public final class GooInsert {

    private GooInsert() { }

    /**
     * What a pour leaves: the value still on the stack and the part credited to the store.
     *
     * @param left     the value the stack keeps
     * @param credited the part under 1 mB the store's ledger takes
     */
    public record PourSettlement(MixedVolume left, GooFraction credited) {
    }

    /**
     * Settles a pour: a sink that took the stack's whole mB entire takes its part too,
     * and one that took less leaves the part on the stack beside the whole mB it left.
     *
     * @param stack    the stack's exact value
     * @param accepted the whole mB the sink accepted, at most the stack's
     * @return what the stack keeps and what the store's ledger is credited
     */
    static PourSettlement settle(MixedVolume stack, int accepted) {
        if (accepted >= stack.whole()) {
            return new PourSettlement(MixedVolume.ZERO, stack.part());
        }
        return new PourSettlement(new MixedVolume(stack.whole() - accepted, stack.part()), GooFraction.ZERO);
    }

    /**
     * Pours a held goo into a sink and depletes the stack by what the sink took.
     * Any other stack, or a sink accepting nothing, leaves the stack whole.
     *
     * @param stack  the held stack
     * @param player the player holding it
     * @param sink   where the goo goes
     * @return the volume accepted, 0 when nothing moved
     */
    public static int pour(ItemStack stack, Player player, GooSink sink) {
        return pourInto(stack, sink, () -> stack.consume(1, player));
    }

    /**
     * Pours a goo stack into a sink: the sink takes whole mB, a sink with a ledger that took
     * every whole mB is credited the stack's part and takes any whole mB the credit carries,
     * and the stack keeps the rest, or is consumed once it holds nothing.
     *
     * @param stack   the goo stack
     * @param sink    where the goo goes
     * @param consume removes the emptied stack from where it stands
     * @return the whole mB the store took, 0 when nothing moved
     */
    public static int pourInto(ItemStack stack, GooSink sink, Runnable consume) {
        ResourceKey<GooTypeDefinition> type = GooStacks.keyOf(stack);
        if (type == null) { return 0; }
        MixedVolume held = GooStacks.mixedVolumeOf(stack);
        int accepted = sink.accept(type, held.whole());
        if (accepted <= 0) { return 0; }

        IRemainderLedger ledger = sink.ledger();
        PourSettlement settled = ledger == null
                ? new PourSettlement(new MixedVolume(held.whole() - accepted, held.part()), GooFraction.ZERO)
                : settle(held, accepted);
        int carried = bankPart(type, settled.credited(), ledger, sink);
        MixedVolume left = settled.left().plus(MixedVolume.ofWhole(carried));
        if (left.isEmpty()) {
            consume.run();
        } else {
            GooItem.setMixedVolume(stack, left);
        }
        return accepted;
    }

    /**
     * Credits a poured part to the store's ledger and lands any whole mB the credit
     * carries; a whole mB the store then refuses goes back to the pourer.
     * The shape ExactMelt's credit takes: bank the fraction, take the whole it carries.
     *
     * @param type   the goo type
     * @param part   the part to credit, zero for none
     * @param ledger the store's ledger, or null where it keeps none
     * @param sink   how the store takes whole mB
     * @return the whole mB the credit carried that the store refused
     */
    private static int bankPart(ResourceKey<GooTypeDefinition> type, GooFraction part,
                                @Nullable IRemainderLedger ledger, GooSink sink) {
        if (ledger == null || !part.isPositive()) {
            return 0;
        }
        int carried = ledger.creditExact(type, part);
        return carried > 0 ? carried - sink.accept(type, carried) : 0;
    }
}
