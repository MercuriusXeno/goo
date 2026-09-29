package com.mercuriusxeno.goo.data;

/**
 * A client object that answers the goo values its connection holds: the
 * client level and the client connection implement it through mixins, so
 * common code reaches the client's values by the level without naming a
 * client class (decision type-package-and-per-server-holders).
 */
public interface GooValueSource {

    /**
     * Answers the goo values the connection last received.
     *
     * @return the values, empty before the first sync
     */
    IGooValueLookup gooValueLookup();
}
