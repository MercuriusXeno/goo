package com.mercuriusxeno.goo.registry;

import com.mercuriusxeno.goo.Goo;
import com.mercuriusxeno.goo.ability.program.EntityCounters;
import net.neoforged.neoforge.attachment.AttachmentType;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries;
import java.util.function.Supplier;

/**
 * Registers the data attachments the mod keeps on game objects.
 */
public final class GooAttachments {

    public static final DeferredRegister<AttachmentType<?>> ATTACHMENT_TYPES =
            DeferredRegister.create(NeoForgeRegistries.Keys.ATTACHMENT_TYPES, Goo.MODID);

    /**
     * The counters a struck entity keeps between blob hits, saved with the
     * entity (decision aeon-mob-ritual-drops-spawn-egg).
     */
    public static final Supplier<AttachmentType<EntityCounters>> ENTITY_COUNTERS =
            ATTACHMENT_TYPES.register("entity_counters",
                    () -> AttachmentType.builder(() -> EntityCounters.EMPTY)
                            .serialize(EntityCounters.CODEC)
                            .build());

    private GooAttachments() {
    }
}
