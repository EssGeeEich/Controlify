//? if reeses-sodium-options {
package dev.isxander.controlify.compatibility.rso.mixins;

import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.AbstractFrame;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.tab.Tab;
import me.flashyreese.mods.reeses_sodium_options.client.gui.frame.tab.TabFrame;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;
import java.util.Optional;

@Mixin(value = TabFrame.class, remap = false)
public interface TabFrameAccessor {
    @Accessor
    List<Tab<?>> getTabs();

    @Accessor
    //? if sodium: >=0.6 {
    Optional<Tab<?>>
    //?} else {
    /*Tab<?>
    *///?}
    getSelectedTab();

    @Accessor
    AbstractFrame getSelectedFrame();
}
//?}
