package com.mercuriusxeno.goo.gametest;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a gametest class carrying its own registrations in a static
 * {@code register(RegisterEvent.RegisterHelper<Consumer<GameTestHelper>>)}, which
 * GooTestFunctions finds through the mod's annotation scan and calls, so no list
 * of gametest classes is kept by hand (decision gametests-discovered-not-listed).
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface RegistersGameTests {
}
