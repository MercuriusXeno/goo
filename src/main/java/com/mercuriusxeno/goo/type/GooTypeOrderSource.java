package com.mercuriusxeno.goo.type;

import net.minecraft.resources.ResourceKey;
import java.util.List;

/**
 * A server or a client connection answering the goo types its registries
 * hold: mixins give both one, read from their own registry access, which
 * stands unchanged for the server's or the connection's life
 * (decision type-package-and-per-server-holders).
 */
public interface GooTypeOrderSource {

    /**
     * Answers the keys of every goo type this side holds.
     *
     * @return the keys, in {@link GooTypes#ORDER}
     */
    List<ResourceKey<GooTypeDefinition>> gooTypeOrder();
}
