package dev.isxander.controlify.mixins.feature.screenop.vanilla;

import dev.isxander.controlify.api.vmousesnapping.SnapPoint;
import dev.isxander.controlify.mixins.feature.virtualmouse.snapping.AbstractContainerScreenMixin;
import dev.isxander.controlify.screenop.ScreenProcessor;
import dev.isxander.controlify.screenop.ScreenProcessorProvider;
import dev.isxander.controlify.screenop.compat.vanilla.RecipeBookScreenProcessor;
import dev.isxander.controlify.virtualmouse.SnapUtils;
import net.minecraft.client.gui.screens.inventory.*;
import net.minecraft.client.gui.screens.recipebook.RecipeBookComponent;
import net.minecraft.client.gui.screens.recipebook.RecipeUpdateListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.AbstractContainerMenu;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;

import java.util.HashSet;
import java.util.Set;

//? if >=1.21.2 {
@Mixin(AbstractRecipeBookScreen.class)
//?} else {
/*@Mixin(value = {
        InventoryScreen.class,
        AbstractFurnaceScreen.class,
        CraftingScreen.class
})
*///?}
public abstract class AbstractRecipeBookScreenMixin<T extends AbstractContainerMenu>
        extends AbstractContainerScreenMixin<T>
        implements ScreenProcessorProvider /*? if <1.21.2 {*/ /*,RecipeUpdateListener *//*?}*/ {

    @Unique
    private final RecipeBookScreenProcessor<?> processor =
            new RecipeBookScreenProcessor<>(/*? if >=1.21.2 {*/ (AbstractRecipeBookScreen<?>) (Object) /*?}*/this);

    protected AbstractRecipeBookScreenMixin(Component title) {
        super(title);
    }

    @Override
    public ScreenProcessor<?> screenProcessor() {
        return processor;
    }
}
