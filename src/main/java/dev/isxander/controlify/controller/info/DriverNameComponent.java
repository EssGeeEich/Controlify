package dev.isxander.controlify.controller.info;

import dev.isxander.controlify.controller.SingleValueComponent;
import dev.isxander.controlify.utils.CUtil;
import net.minecraft.resources.ResourceLocation;

public class DriverNameComponent extends SingleValueComponent<String> {
    public static final ResourceLocation ID = CUtil.rl("driver_name");

    public DriverNameComponent(String value) {
        super(value, ID);
    }
}
