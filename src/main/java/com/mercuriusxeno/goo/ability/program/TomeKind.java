package com.mercuriusxeno.goo.ability.program;

import com.mojang.serialization.Codec;

/**
 * The floating tome choreography a self ability plays: Enchant's one book,
 * or Fuse's two merging into one.
 * enchant-book-with-a-purple-afterimage
 * fuse-two-books-for-hex-goo
 */
public enum TomeKind {
    /** One book opens, drinks in glyphs and snaps shut. */
    ENCHANT,
    /** Two books circle each other, spiral together and merge. */
    FUSE;

    /** Codec for the kind as an ability JSON writes it, in lower case. */
    public static final Codec<TomeKind> CODEC = LowerCaseEnumCodec.of(TomeKind.class, "tome kind");
}
