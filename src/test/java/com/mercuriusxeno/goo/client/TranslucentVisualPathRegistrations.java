package com.mercuriusxeno.goo.client;

import com.mercuriusxeno.goo.client.TranslucentVisual.SortedDraw;
import java.util.List;

/**
 * Reads TranslucentVisualPath's registrations and its far-to-near order for
 * tests outside the path's package.
 */
public final class TranslucentVisualPathRegistrations {

    private TranslucentVisualPathRegistrations() {
    }

    /**
     * The visuals registered on a route, in registration order.
     *
     * @param route the route
     * @return each registration's visual class
     */
    public static List<Class<?>> visualsOn(SortRoute route) {
        return TranslucentVisualPath.REGISTRATIONS.stream().filter(registration -> registration.route() == route)
                .<Class<?>>map(TranslucentVisualPath.Registration::visual).toList();
    }

    /**
     * The path's far-to-near order over the draws given.
     *
     * @param draws the draws
     * @return the draws, farthest first
     */
    public static List<SortedDraw> farToNear(List<SortedDraw> draws) {
        return TranslucentVisualPath.farToNear(draws);
    }
}
