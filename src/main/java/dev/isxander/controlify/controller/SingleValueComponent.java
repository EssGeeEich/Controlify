package dev.isxander.controlify.controller;

import net.minecraft.resources.ResourceLocation;

public class SingleValueComponent<T> implements ECSComponent {
    private final T value;
    private final ResourceLocation id;

    public SingleValueComponent(T value, ResourceLocation id) {
        this.value = value;
        this.id = id;
    }

    public T value() {
        return this.value;
    }

    @Override
    public ResourceLocation id() {
        return this.id;
    }
}
