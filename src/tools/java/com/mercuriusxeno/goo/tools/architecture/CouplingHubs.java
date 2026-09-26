package com.mercuriusxeno.goo.tools.architecture;

import java.util.List;

/**
 * The coupling hubs and the thresholds that graded them.
 *
 * @param fanInThreshold  the fan-in all but a twentieth of the set hold at or under
 * @param fanOutThreshold the fan-out all but a twentieth of the set hold at or under
 * @param hubs            the types above either threshold, most coupled first
 */
public record CouplingHubs(int fanInThreshold, int fanOutThreshold, List<CouplingHub> hubs) {
}