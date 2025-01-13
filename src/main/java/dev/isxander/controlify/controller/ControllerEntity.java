package dev.isxander.controlify.controller;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import dev.isxander.controlify.controller.battery.BatteryLevelComponent;
import dev.isxander.controlify.controller.dualsense.DualSenseComponent;
import dev.isxander.controlify.controller.haptic.HDHapticComponent;
import dev.isxander.controlify.controller.haptic.SimpleHapticComponent;
import dev.isxander.controlify.controller.info.ControllerInfo;
import dev.isxander.controlify.controller.info.DriverNameComponent;
import dev.isxander.controlify.controller.info.GUIDComponent;
import dev.isxander.controlify.controller.info.UIDComponent;
import dev.isxander.controlify.controller.keyboard.NativeKeyboardComponent;
import dev.isxander.controlify.controller.serialization.ConfigHolder;
import dev.isxander.controlify.controller.serialization.IConfig;
import dev.isxander.controlify.controller.gyro.GyroComponent;
import dev.isxander.controlify.controller.impl.ConfigImpl;
import dev.isxander.controlify.controller.impl.ECSEntityImpl;
import dev.isxander.controlify.controller.input.InputComponent;
import dev.isxander.controlify.controller.misc.BluetoothDeviceComponent;
import dev.isxander.controlify.controller.rumble.RumbleComponent;
import dev.isxander.controlify.controller.rumble.TriggerRumbleComponent;
import dev.isxander.controlify.controller.touchpad.TouchpadComponent;
import dev.isxander.controlify.driver.Driver;
import dev.isxander.controlify.utils.CUtil;
import dev.isxander.controlify.utils.log.ControlifyLogger;
import net.minecraft.resources.ResourceLocation;
import org.apache.commons.lang3.SerializationException;
import org.jetbrains.annotations.NotNull;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

public class ControllerEntity extends ECSEntityImpl {
    private final ControllerInfo info;
    private final Driver driver;
    private final ControlifyLogger logger;

    public ControllerEntity(ControllerInfo info, Driver driver, ControlifyLogger logger) {
        this.info = info;
        this.driver = driver;
        this.logger = logger;

        this.setComponent(new ConfigImpl<>(GenericControllerConfig::new, GenericControllerConfig.class));

        driver.addComponents(this);
        this.getAllComponents().values().forEach(ECSComponent::finalise);

        logger.debugLog("Components: {}", this.getAllComponents().keySet().stream().map(ResourceLocation::toString).collect(Collectors.joining(", ")));
    }

    public String uid() {
        return this.<UIDComponent>getComponent(UIDComponent.ID).orElseThrow().value();
    }

    @NotNull
    public String driverName() {
        return this.<DriverNameComponent>getComponent(DriverNameComponent.ID).orElseThrow().value();
    }

    @NotNull
    public String guid() {
        return this.<GUIDComponent>getComponent(GUIDComponent.ID).orElseThrow().value();
    }

    public ControllerInfo info() {
        return this.info;
    }

    @NotNull
    public String name() {
        String nickname = this.genericConfig().config().nickname;
        if (nickname != null)
            return nickname;

        String friendlyName = info().type().friendlyName();
        if (friendlyName != null)
            return friendlyName;

        return driverName();
    }

    public Driver drivers() {
        return driver;
    }

    public Optional<InputComponent> input() {
        return this.getComponent(InputComponent.ID);
    }

    public Optional<RumbleComponent> rumble() {
        return this.getComponent(RumbleComponent.ID);
    }

    public Optional<TriggerRumbleComponent> triggerRumble() {
        return this.getComponent(TriggerRumbleComponent.ID);
    }

    public Optional<GyroComponent> gyro() {
        return this.getComponent(GyroComponent.ID);
    }

    public Optional<TouchpadComponent> touchpad() {
        return this.getComponent(TouchpadComponent.ID);
    }

    public Optional<BatteryLevelComponent> batteryLevel() {
        return this.getComponent(BatteryLevelComponent.ID);
    }

    public Optional<HDHapticComponent> hdHaptics() {
        return this.getComponent(HDHapticComponent.ID);
    }

    public Optional<SimpleHapticComponent> simpleHaptics() {
        return this.getComponent(SimpleHapticComponent.ID);
    }

    public Optional<DualSenseComponent> dualSense() {
        return this.getComponent(DualSenseComponent.ID);
    }

    public IConfig<GenericControllerConfig> genericConfig() {
        return this.<IConfig<GenericControllerConfig>>getComponent(GenericControllerConfig.ID).orElseThrow();
    }

    public Optional<IConfig<GamepadControllerConfig>> gamepadConfig() {
        return this.getComponent(GamepadControllerConfig.ID);
    }

    public Optional<IConfig<JoystickControllerConfig>> joystickConfig() {
        return this.getComponent(JoystickControllerConfig.ID);
    }

    public Optional<BluetoothDeviceComponent> bluetooth() {
        return this.getComponent(BluetoothDeviceComponent.ID);
    }

    public Optional<NativeKeyboardComponent> nativeKeyboard() {
        return this.getComponent(NativeKeyboardComponent.ID);
    }

    public void update(boolean outOfFocus) {
        this.driver.update(this, outOfFocus);
    }

    public Map<ResourceLocation, IConfig<?>> getAllConfigs() {
        Map<ResourceLocation, IConfig<?>> configs = new HashMap<>();

        this.getAllComponents().forEach((id, component) -> {
            if (component instanceof IConfig<?> config) {
                configs.put(id, config);
            }
            if (component instanceof ConfigHolder<?> configHolder) {
                configs.put(id, configHolder.config());
            }
        });

        return configs;
    }

    public void serializeToObject(JsonObject object, Gson gson) throws SerializationException {
        for (var entry : this.getAllConfigs().entrySet()) {
            ResourceLocation key = entry.getKey();
            IConfig<?> config = entry.getValue();

            object.add(key.toString(), config.serialize(gson, this));
        }
    }

    public void deserializeFromObject(JsonObject object, Gson gson) throws SerializationException {
        for (var entry : this.getAllConfigs().entrySet()) {
            ResourceLocation key = entry.getKey();
            IConfig<?> config = entry.getValue();

            JsonElement element = object.remove(key.toString()); // remove the used element
            if (element != null) {
                config.deserialize(element, gson, this);
            } else {
                CUtil.LOGGER.warn("Could not find component config {} whilst deserializing. Ignoring.", key);
            }
        }
    }

    public void resetToDefaultConfig() {
        for (var config : this.getAllConfigs().values()) {
            config.resetToDefault();
        }
    }

    public void close() {
        this.driver.close();
    }

    public ControlifyLogger getLogger() {
        return logger;
    }
}
