package dev.isxander.controlify.config.gui;

import com.google.common.collect.Iterables;
import com.google.common.collect.Lists;
import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.api.bind.ControllerBinding;
import dev.isxander.controlify.bindings.ControllerBindingImpl;
import dev.isxander.controlify.bindings.IBind;
import dev.isxander.controlify.config.GlobalSettings;
import dev.isxander.controlify.controller.Controller;
import dev.isxander.controlify.controller.ControllerConfig;
import dev.isxander.controlify.controller.ControllerState;
import dev.isxander.controlify.controller.gamepad.GamepadController;
import dev.isxander.controlify.controller.gamepad.GamepadState;
import dev.isxander.controlify.controller.gamepad.BuiltinGamepadTheme;
import dev.isxander.controlify.controller.joystick.JoystickController;
import dev.isxander.controlify.controller.joystick.SingleJoystickController;
import dev.isxander.controlify.controller.joystick.JoystickState;
import dev.isxander.controlify.controller.joystick.mapping.JoystickMapping;
import dev.isxander.controlify.gui.screen.ControllerDeadzoneCalibrationScreen;
import dev.isxander.controlify.reacharound.ReachAroundMode;
import dev.isxander.controlify.rumble.BasicRumbleEffect;
import dev.isxander.controlify.rumble.RumbleSource;
import dev.isxander.controlify.rumble.RumbleState;
import dev.isxander.yacl.api.*;
import dev.isxander.yacl.gui.controllers.ActionController;
import dev.isxander.yacl.gui.controllers.BooleanController;
import dev.isxander.yacl.gui.controllers.TickBoxController;
import dev.isxander.yacl.gui.controllers.cycling.CyclingListController;
import dev.isxander.yacl.gui.controllers.cycling.EnumController;
import dev.isxander.yacl.gui.controllers.slider.FloatSliderController;
import dev.isxander.yacl.gui.controllers.slider.IntegerSliderController;
import dev.isxander.yacl.gui.controllers.string.StringController;
import net.minecraft.ChatFormatting;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;

import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class YACLHelper {
    private static final Function<Float, Component> percentFormatter = v -> Component.literal(String.format("%.0f%%", v*100));
    private static final Function<Float, Component> percentOrOffFormatter = v -> v == 0 ? CommonComponents.OPTION_OFF : percentFormatter.apply(v);

    public static Screen generateConfigScreen(Screen parent) {
        var controlify = Controlify.instance();

        var yacl = YetAnotherConfigLib.createBuilder()
                .title(Component.literal("Controlify"))
                .save(() -> controlify.config().save());

        Option<Boolean> globalVibrationOption;

        var globalSettings = Controlify.instance().config().globalSettings();
        var globalCategory = ConfigCategory.createBuilder()
                .name(Component.translatable("controlify.gui.category.global"))
                .option(Option.createBuilder((Class<Controller<?, ?>>) (Class<?>) Controller.class)
                        .name(Component.translatable("controlify.gui.current_controller"))
                        .tooltip(Component.translatable("controlify.gui.current_controller.tooltip"))
                        .binding(Controlify.instance().getCurrentController().orElse(Controller.DUMMY), () -> Controlify.instance().getCurrentController().orElse(Controller.DUMMY), v -> Controlify.instance().setCurrentController(v))
                        .controller(opt -> new CyclingListController<>(opt, Iterables.concat(List.of(Controller.DUMMY), Controller.CONTROLLERS.values().stream().filter(Controller::canBeUsed).toList()), c -> Component.literal(c == Controller.DUMMY ? "Disabled" : c.name())))
                        .build())
                .option(globalVibrationOption = Option.createBuilder(boolean.class)
                        .name(Component.translatable("controlify.gui.load_vibration_natives"))
                        .tooltip(Component.translatable("controlify.gui.load_vibration_natives.tooltip"))
                        .tooltip(Component.translatable("controlify.gui.load_vibration_natives.tooltip.warning").withStyle(ChatFormatting.RED))
                        .binding(GlobalSettings.DEFAULT.loadVibrationNatives, () -> globalSettings.loadVibrationNatives, v -> globalSettings.loadVibrationNatives = v)
                        .controller(opt -> new BooleanController(opt, BooleanController.YES_NO_FORMATTER, false))
                        .flag(OptionFlag.GAME_RESTART)
                        .build())
                .option(Option.createBuilder(ReachAroundMode.class)
                        .name(Component.translatable("controlify.gui.reach_around"))
                        .tooltip(Component.translatable("controlify.gui.reach_around.tooltip"))
                        .tooltip(Component.translatable("controlify.gui.reach_around.tooltip.parity").withStyle(ChatFormatting.GRAY))
                        .tooltip(state -> state == ReachAroundMode.EVERYWHERE ? Component.translatable("controlify.gui.reach_around.tooltip.warning").withStyle(ChatFormatting.RED) : Component.empty())
                        .binding(GlobalSettings.DEFAULT.reachAround, () -> globalSettings.reachAround, v -> globalSettings.reachAround = v)
                        .controller(EnumController::new)
                        .build())
                .option(Option.createBuilder(boolean.class)
                        .name(Component.translatable("controlify.gui.ui_sounds"))
                        .tooltip(Component.translatable("controlify.gui.ui_sounds.tooltip"))
                        .binding(GlobalSettings.DEFAULT.uiSounds, () -> globalSettings.uiSounds, v -> globalSettings.uiSounds = v)
                        .controller(TickBoxController::new)
                        .build())
                .option(Option.createBuilder(boolean.class)
                        .name(Component.translatable("controlify.gui.out_of_focus_input"))
                        .tooltip(Component.translatable("controlify.gui.out_of_focus_input.tooltip"))
                        .binding(GlobalSettings.DEFAULT.outOfFocusInput, () -> globalSettings.outOfFocusInput, v -> globalSettings.outOfFocusInput = v)
                        .controller(TickBoxController::new)
                        .build())
                .option(Option.createBuilder(boolean.class)
                        .name(Component.translatable("controlify.gui.keyboard_movement"))
                        .tooltip(Component.translatable("controlify.gui.keyboard_movement.tooltip"))
                        .binding(GlobalSettings.DEFAULT.keyboardMovement, () -> globalSettings.keyboardMovement, v -> globalSettings.keyboardMovement = v)
                        .controller(TickBoxController::new)
                        .build())
                .option(ButtonOption.createBuilder()
                        .name(Component.translatable("controlify.gui.open_issue_tracker"))
                        .action((screen, button) -> Util.getPlatform().openUri("https://github.com/isxander/controlify/issues"))
                        .controller(opt -> new ActionController(opt, Component.translatable("controlify.gui.format.open")))
                        .build());

        yacl.category(globalCategory.build());

        for (var controller : Controller.CONTROLLERS.values()) {
            yacl.category(createControllerCategory(controller, globalVibrationOption));
        }

        return yacl.build().generateScreen(parent);
    }

    private static ConfigCategory createControllerCategory(Controller<?, ?> controller, Option<Boolean> globalVibrationOption) {
        if (!controller.canBeUsed()) {
            return PlaceholderCategory.createBuilder()
                    .name(Component.literal(controller.name()))
                    .tooltip(Component.translatable("controlify.gui.controller_unavailable"))
                    .screen((minecraft, yacl) -> yacl)
                    .build();
        }

        var category = ConfigCategory.createBuilder();

        category.name(Component.literal(controller.name()));

        var config = controller.config();
        var def = controller.defaultConfig();

        var basicGroup = OptionGroup.createBuilder()
                .name(Component.translatable("controlify.gui.group.basic"))
                .tooltip(Component.translatable("controlify.gui.group.basic.tooltip"))
                .option(Option.createBuilder(float.class)
                        .name(Component.translatable("controlify.gui.horizontal_look_sensitivity"))
                        .tooltip(Component.translatable("controlify.gui.horizontal_look_sensitivity.tooltip"))
                        .binding(def.horizontalLookSensitivity, () -> config.horizontalLookSensitivity, v -> config.horizontalLookSensitivity = v)
                        .controller(opt -> new FloatSliderController(opt, 0.1f, 2f, 0.05f, percentFormatter))
                        .build())
                .option(Option.createBuilder(float.class)
                        .name(Component.translatable("controlify.gui.vertical_look_sensitivity"))
                        .tooltip(Component.translatable("controlify.gui.vertical_look_sensitivity.tooltip"))
                        .binding(def.verticalLookSensitivity, () -> config.verticalLookSensitivity, v -> config.verticalLookSensitivity = v)
                        .controller(opt -> new FloatSliderController(opt, 0.1f, 2f, 0.05f, percentFormatter))
                        .build())
                .option(Option.createBuilder(boolean.class)
                        .name(Component.translatable("controlify.gui.toggle_sprint"))
                        .tooltip(Component.translatable("controlify.gui.toggle_sprint.tooltip"))
                        .binding(def.toggleSprint, () -> config.toggleSprint, v -> config.toggleSprint = v)
                        .controller(opt -> new BooleanController(opt, v -> Component.translatable("controlify.gui.format.hold_toggle." + (v ? "toggle" : "hold")), false))
                        .build())
                .option(Option.createBuilder(boolean.class)
                        .name(Component.translatable("controlify.gui.toggle_sneak"))
                        .tooltip(Component.translatable("controlify.gui.toggle_sneak.tooltip"))
                        .binding(def.toggleSneak, () -> config.toggleSneak, v -> config.toggleSneak = v)
                        .controller(opt -> new BooleanController(opt, v -> Component.translatable("controlify.gui.format.hold_toggle." + (v ? "toggle" : "hold")), false))
                        .build())
                .option(Option.createBuilder(boolean.class)
                        .name(Component.translatable("controlify.gui.auto_jump"))
                        .tooltip(Component.translatable("controlify.gui.auto_jump.tooltip"))
                        .binding(def.autoJump, () -> config.autoJump, v -> config.autoJump = v)
                        .controller(BooleanController::new)
                        .build())
                .option(Option.createBuilder(boolean.class)
                        .name(Component.translatable("controlify.gui.show_ingame_guide"))
                        .tooltip(Component.translatable("controlify.gui.show_ingame_guide.tooltip"))
                        .binding(def.showIngameGuide, () -> config.showIngameGuide, v -> config.showIngameGuide = v)
                        .controller(TickBoxController::new)
                        .build())
                .option(Option.createBuilder(boolean.class)
                        .name(Component.translatable("controlify.gui.show_screen_guide"))
                        .tooltip(Component.translatable("controlify.gui.show_screen_guide.tooltip"))
                        .binding(def.showScreenGuide, () -> config.showScreenGuide, v -> config.showScreenGuide = v)
                        .controller(TickBoxController::new)
                        .build())
                .option(Option.createBuilder(float.class)
                        .name(Component.translatable("controlify.gui.vmouse_sensitivity"))
                        .tooltip(Component.translatable("controlify.gui.vmouse_sensitivity.tooltip"))
                        .binding(def.virtualMouseSensitivity, () -> config.virtualMouseSensitivity, v -> config.virtualMouseSensitivity = v)
                        .controller(opt -> new FloatSliderController(opt, 0.1f, 2f, 0.05f, percentFormatter))
                        .build())
                .option(Option.createBuilder(float.class)
                        .name(Component.translatable("controlify.gui.chat_screen_offset"))
                        .tooltip(Component.translatable("controlify.gui.chat_screen_offset.tooltip"))
                        .binding(def.chatKeyboardHeight, () -> config.chatKeyboardHeight, v -> config.chatKeyboardHeight = v)
                        .controller(opt -> new FloatSliderController(opt, 0f, 0.8f, 0.1f, percentFormatter))
                        .build())
                .option(Option.createBuilder(boolean.class)
                        .name(Component.translatable("controlify.gui.reduce_aiming_sensitivity"))
                        .tooltip(Component.translatable("controlify.gui.reduce_aiming_sensitivity.tooltip"))
                        .binding(def.reduceAimingSensitivity, () -> config.reduceAimingSensitivity, v -> config.reduceAimingSensitivity = v)
                        .controller(TickBoxController::new)
                        .build());

        if (controller instanceof GamepadController gamepad) {
            var gamepadConfig = gamepad.config();
            var defaultGamepadConfig = gamepad.defaultConfig();

            basicGroup.option(Option.createBuilder(BuiltinGamepadTheme.class)
                    .name(Component.translatable("controlify.gui.controller_theme"))
                    .tooltip(Component.translatable("controlify.gui.controller_theme.tooltip"))
                    .binding(defaultGamepadConfig.theme, () -> gamepadConfig.theme, v -> gamepadConfig.theme = v)
                    .controller(EnumController::new)
                    .instant(true)
                    .build());
        }

        basicGroup
                .option(Option.createBuilder(String.class)
                        .name(Component.translatable("controlify.gui.custom_name"))
                        .tooltip(Component.translatable("controlify.gui.custom_name.tooltip"))
                        .binding(def.customName == null ? "" : def.customName, () -> config.customName == null ? "" : config.customName, v -> config.customName = (v.equals("") ? null : v))
                        .controller(StringController::new)
                        .build());
        category.group(basicGroup.build());

        if (controller.canRumble()) {
            category.group(makeVibrationGroup(globalVibrationOption, config, def));
        }

        category.group(makeGyroGroup(controller));

        var advancedGroup = OptionGroup.createBuilder()
                .name(Component.translatable("controlify.gui.group.advanced"))
                .tooltip(Component.translatable("controlify.gui.group.advanced.tooltip"))
                .collapsed(true);

        addDeadzoneOptions(controller, advancedGroup);

        advancedGroup
                .option(ButtonOption.createBuilder()
                        .name(Component.translatable("controlify.gui.auto_calibration"))
                        .tooltip(Component.translatable("controlify.gui.auto_calibration.tooltip"))
                        .action((screen, button) -> Minecraft.getInstance().setScreen(new ControllerDeadzoneCalibrationScreen(controller, screen)))
                        .controller(ActionController::new)
                        .build())
                .option(Option.createBuilder(float.class)
                        .name(Component.translatable("controlify.gui.button_activation_threshold"))
                        .tooltip(Component.translatable("controlify.gui.button_activation_threshold.tooltip"))
                        .binding(def.buttonActivationThreshold, () -> config.buttonActivationThreshold, v -> config.buttonActivationThreshold = v)
                        .controller(opt -> new FloatSliderController(opt, 0, 1, 0.05f, v -> Component.literal(String.format("%.0f%%", v*100))))
                        .build())
                .option(ButtonOption.createBuilder()
                        .name(Component.translatable("controlify.gui.test_vibration"))
                        .tooltip(Component.translatable("controlify.gui.test_vibration.tooltip"))
                        .controller(ActionController::new)
                        .action((screen, btn) -> {
                            controller.rumbleManager().play(
                                    RumbleSource.MASTER,
                                    BasicRumbleEffect.byTime(t -> new RumbleState(0f, t), 20)
                                            .join(BasicRumbleEffect.byTime(t -> new RumbleState(0f, 1 - t), 20))
                                            .repeat(3)
                                            .join(BasicRumbleEffect.constant(1f, 0f, 5)
                                                    .join(BasicRumbleEffect.constant(0f, 1f, 5))
                                                    .repeat(10)
                                            )
                                            .earlyFinish(BasicRumbleEffect.finishOnScreenChange())
                            );
                        })
                        .build());

        category.group(advancedGroup.build());

        var controlsGroup = OptionGroup.createBuilder()
                .name(Component.translatable("controlify.gui.group.controls"));
        groupBindings(controller.bindings().registry().values()).forEach((categoryName, bindGroup) -> {
            controlsGroup.option(LabelOption.create(categoryName));
            controlsGroup.options(bindGroup.stream().map(ControllerBinding::generateYACLOption).toList());
        });

        category.group(controlsGroup.build());

        return category.build();
    }

    private static OptionGroup makeVibrationGroup(Option<Boolean> globalVibrationOption, ControllerConfig config, ControllerConfig def) {
        var vibrationGroup = OptionGroup.createBuilder()
                .name(Component.translatable("controlify.gui.group.vibration"))
                .tooltip(Component.translatable("controlify.gui.group.vibration.tooltip"));
        List<Option<Float>> strengthOptions = new ArrayList<>();
        Option<Boolean> allowVibrationOption;
        vibrationGroup.option(allowVibrationOption = Option.createBuilder(boolean.class)
                .name(Component.translatable("controlify.gui.allow_vibrations"))
                .tooltip(Component.translatable("controlify.gui.allow_vibrations.tooltip"))
                .binding(globalVibrationOption.pendingValue(), () -> config.allowVibrations && globalVibrationOption.pendingValue(), v -> config.allowVibrations = v)
                .available(globalVibrationOption.pendingValue())
                .listener((opt, allowVibration) -> strengthOptions.forEach(so -> so.setAvailable(allowVibration)))
                .controller(TickBoxController::new)
                .build());
        for (RumbleSource source : RumbleSource.values()) {
            var option = Option.createBuilder(float.class)
                    .name(Component.translatable("controlify.vibration_strength." + source.id().getNamespace() + "." + source.id().getPath()))
                    .tooltip(Component.translatable("controlify.vibration_strength." + source.id().getNamespace() + "." + source.id().getPath() + ".tooltip"))
                    .binding(
                            def.getRumbleStrength(source),
                            () -> config.getRumbleStrength(source),
                            v -> config.setRumbleStrength(source, v)
                    )
                    .controller(opt -> new FloatSliderController(opt, 0f, 1f, 0.05f, percentOrOffFormatter))
                    .available(allowVibrationOption.pendingValue())
                    .build();
            strengthOptions.add(option);
            vibrationGroup.option(option);
        }
        return vibrationGroup.build();
    }

    private static OptionGroup makeGyroGroup(Controller<?, ?> controller) {
        GamepadController gamepad = (controller instanceof GamepadController) ? (GamepadController) controller : null;
        boolean hasGyro = gamepad != null && gamepad.hasGyro();

        var gpCfg = gamepad != null ? gamepad.config() : null;
        var gpCfgDef = gamepad != null ? gamepad.defaultConfig() : null;

        Component noGyroTooltip = Component.translatable("controlify.gui.group.gyro.no_gyro.tooltip").withStyle(ChatFormatting.RED);

        Option<Float> gyroSensitivity;
        List<Option<?>> gyroOptions = new ArrayList<>();
        var gyroGroup = OptionGroup.createBuilder()
                .name(Component.translatable("controlify.gui.group.gyro"))
                .tooltip(Component.translatable("controlify.gui.group.gyro.tooltip"))
                .collapsed(!hasGyro)
                .option(gyroSensitivity = Option.createBuilder(float.class)
                        .name(Component.translatable("controlify.gui.gyro_look_sensitivity"))
                        .tooltip(Component.translatable("controlify.gui.gyro_look_sensitivity.tooltip"))
                        .tooltip(hasGyro ? Component.empty() : noGyroTooltip)
                        .available(hasGyro)
                        .binding(hasGyro ? gpCfgDef.gyroLookSensitivity : 0, () -> hasGyro ? gpCfg.gyroLookSensitivity : 0, v -> gpCfg.gyroLookSensitivity = v)
                        .controller(opt -> new FloatSliderController(opt, 0f, 1f, 0.05f, percentOrOffFormatter))
                        .listener((opt, sensitivity) -> gyroOptions.forEach(o -> {
                            o.setAvailable(sensitivity > 0);
                            o.requestSetDefault();
                        }))
                        .build())
                .option(Util.make(() -> {
                    var opt = Option.createBuilder(boolean.class)
                            .name(Component.translatable("controlify.gui.gyro_requires_button"))
                            .tooltip(Component.translatable("controlify.gui.gyro_requires_button.tooltip"))
                            .tooltip(hasGyro ? Component.empty() : noGyroTooltip)
                            .available(hasGyro)
                            .binding(hasGyro ? gpCfgDef.gyroRequiresButton : false, () -> hasGyro ? gpCfg.gyroRequiresButton : false, v -> gpCfg.gyroRequiresButton = v)
                            .controller(TickBoxController::new)
                            .available(gyroSensitivity.pendingValue() > 0)
                            .build();
                    gyroOptions.add(opt);
                    return opt;
                }))
                .option(Util.make(() -> {
                    var opt = Option.createBuilder(boolean.class)
                            .name(Component.translatable("controlify.gui.flick_stick"))
                            .tooltip(Component.translatable("controlify.gui.flick_stick.tooltip"))
                            .tooltip(hasGyro ? Component.empty() : noGyroTooltip)
                            .available(hasGyro)
                            .binding(hasGyro ? gpCfgDef.flickStick : false, () -> hasGyro ? gpCfg.flickStick : false, v -> gpCfg.flickStick = v)
                            .controller(TickBoxController::new)
                            .available(gyroSensitivity.pendingValue() > 0)
                            .build();
                    gyroOptions.add(opt);
                    return opt;
                }));

        return gyroGroup.build();
    }

    private static void addDeadzoneOptions(Controller<?, ?> controller, OptionGroup.Builder group) {
        if (controller instanceof GamepadController gamepad) {
            var gpCfg = gamepad.config();
            var gpCfgDef = gamepad.defaultConfig();
            group
                    .option(Option.createBuilder(float.class)
                            .name(Component.translatable("controlify.gui.axis_deadzone", Component.translatable("controlify.gui.left_stick")))
                            .tooltip(Component.translatable("controlify.gui.axis_deadzone.tooltip", Component.translatable("controlify.gui.left_stick")))
                            .tooltip(Component.translatable("controlify.gui.stickdrift_warning").withStyle(ChatFormatting.RED))
                            .binding(
                                    Math.max(gpCfgDef.leftStickDeadzoneX, gpCfgDef.leftStickDeadzoneY),
                                    () -> Math.max(gpCfg.leftStickDeadzoneX, gpCfgDef.leftStickDeadzoneY),
                                    v -> gpCfg.leftStickDeadzoneX = gpCfg.leftStickDeadzoneY = v
                            )
                            .controller(opt -> new FloatSliderController(opt, 0, 1, 0.01f, v -> Component.literal(String.format("%.0f%%", v*100))))
                            .build())
                    .option(Option.createBuilder(float.class)
                            .name(Component.translatable("controlify.gui.axis_deadzone", Component.translatable("controlify.gui.right_stick")))
                            .tooltip(Component.translatable("controlify.gui.axis_deadzone.tooltip", Component.translatable("controlify.gui.right_stick")))
                            .tooltip(Component.translatable("controlify.gui.stickdrift_warning").withStyle(ChatFormatting.RED))
                            .binding(
                                    Math.max(gpCfgDef.rightStickDeadzoneX, gpCfgDef.rightStickDeadzoneY),
                                    () -> Math.max(gpCfg.rightStickDeadzoneX, gpCfgDef.rightStickDeadzoneY),
                                    v -> gpCfg.rightStickDeadzoneX = gpCfg.rightStickDeadzoneY = v
                            )
                            .controller(opt -> new FloatSliderController(opt, 0, 1, 0.01f, v -> Component.literal(String.format("%.0f%%", v*100))))
                            .build());
        } else if (controller instanceof SingleJoystickController joystick) {
            JoystickMapping.Axis[] axes = joystick.mapping().axes();
            Collection<Integer> deadzoneAxes = IntStream.range(0, axes.length)
                    .filter(i -> axes[i].requiresDeadzone())
                    .boxed()
                    .collect(Collectors.toMap(
                            i -> axes[i].identifier(),
                            i -> i,
                            (x, y) -> x,
                            LinkedHashMap::new
                    ))
                    .values();
            var jsCfg = joystick.config();
            var jsCfgDef = joystick.defaultConfig();

            for (int i : deadzoneAxes) {
                var axis = axes[i];

                group.option(Option.createBuilder(float.class)
                        .name(Component.translatable("controlify.gui.joystick_axis_deadzone", axis.name()))
                        .tooltip(Component.translatable("controlify.gui.joystick_axis_deadzone.tooltip", axis.name()))
                        .tooltip(Component.translatable("controlify.gui.stickdrift_warning").withStyle(ChatFormatting.RED))
                        .binding(jsCfgDef.getDeadzone(i), () -> jsCfg.getDeadzone(i), v -> jsCfg.setDeadzone(i, v))
                        .controller(opt -> new FloatSliderController(opt, 0, 1, 0.01f, v -> Component.literal(String.format("%.0f%%", v*100))))
                        .build());
            }
        }
    }

    private static <T extends ControllerState> Map<Component, List<ControllerBinding>> groupBindings(Collection<ControllerBinding> bindings) {
        return bindings.stream()
                .collect(Collectors.groupingBy(ControllerBinding::category, LinkedHashMap::new, Collectors.toList()));
    }
}
