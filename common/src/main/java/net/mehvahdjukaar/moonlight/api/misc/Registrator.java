package net.mehvahdjukaar.moonlight.api.misc;

import net.minecraft.resources.Identifier;

@FunctionalInterface
public interface Registrator<T> {

    void register(Identifier name, T instance);

    default void register(String name, T instance) {
        register(Identifier.parse(name), instance);
    }

}
