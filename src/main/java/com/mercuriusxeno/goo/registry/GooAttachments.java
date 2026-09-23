package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.item.SoulBoundStacks;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;

/**
 * Data attachment registration.
 */
public final class GooAttachments {
    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.ATTACHMENT_TYPES, Goo.MODID);

    /**
     * The soul-bound stacks a dead player holds until respawn, serialized so a
     * player who leaves before respawning keeps them (decision
     * exorite-kept-through-death).
     */
    public static final DeferredHolder<AttachmentType<?>, AttachmentType<SoulBoundStacks>> SOUL_BOUND_STACKS =
            ATTACHMENT_TYPES.register("soul_bound_stacks",
                    () -> AttachmentType.builder(() -> SoulBoundStacks.NONE).serialize(SoulBoundStacks.CODEC).build());

    private GooAttachments() {
    }
}
