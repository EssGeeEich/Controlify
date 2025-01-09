package dev.isxander.controlify.controller.info;

import dev.isxander.controlify.controller.SingleValueComponent;
import dev.isxander.controlify.utils.CUtil;
import net.minecraft.resources.ResourceLocation;

public class UIDComponent extends SingleValueComponent<String> {
    public static final ResourceLocation ID = CUtil.rl("uid");

    public UIDComponent(String value) {
        super(value, ID);
    }
}
