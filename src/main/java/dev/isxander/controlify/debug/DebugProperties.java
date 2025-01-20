package dev.isxander.controlify.debug;

import dev.isxander.controlify.platform.main.PlatformMainUtil;
import dev.isxander.controlify.utils.CUtil;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

public class DebugProperties {
    private static final List<DebugProperty<?>> properties = new ArrayList<>();

    public static final boolean DEBUG_LOGGING = boolProp("controlify.debug.logging", false, true);
    /** Print the VID and PID of every controller connected. */
    public static final boolean PRINT_VID_PID = boolProp("controlify.debug.print_vid_pid", false, true);
    /** Renders debug overlay for vmouse snapping */
    public static final boolean DEBUG_SNAPPING = boolProp("controlify.debug.snapping", false, false);
    /** Forces all gamepads to be treated as a regular joystick */
    public static final boolean FORCE_JOYSTICK = boolProp("controlify.debug.force_joystick", false, false);
    /** Debug dumps after finishing init */
    public static final boolean INIT_DUMP = boolProp("controlify.debug.init_dump", false, true);
    /** Use a custom url to access CEF, you can use remote access here to receive control remotely. **/
    public static final @Nullable String STEAM_DECK_CUSTOM_CEF_URL = strProp("controlify.debug.steam_deck_custom_cef_url", null, null);
    /** Run a mixin audit - useful for last checks before releasing a mod version */
    public static final boolean MIXIN_AUDIT = boolProp("controlify.debug.mixin_audit", false, false);
    /** Enables the currently non-functional HD haptics support */
    public static final boolean ENABLE_HD_HAPTICS = boolProp("controlify.debug.enable_hd_haptics", false, true);
    /** Checks SDL3 native against checksum within the jar, this improves security by only allowing an identical build of SDL3 to be loaded as to the one that this build targets at compile-time. */
    public static final boolean USE_JAR_CHECKSUM = boolProp("controlify.use_jar_checksum", true, false);

    public static void printProperties() {
        if (properties.stream().noneMatch(prop -> prop.state() != prop.def()))
            return;

        String header = "*----------------- Controlify Debug Properties -----------------*";
        CUtil.LOGGER.error(header);

        int maxWidth = properties.stream().mapToInt(prop -> prop.name().length()).max().orElse(0);
        for (var prop : properties) {
            String line = "| %s%s = %s".formatted(prop.name(), " ".repeat(maxWidth - prop.name().length()), prop.state());
            line += " ".repeat(header.length() - line.length() - 1) + "|";

            CUtil.LOGGER.error(line);
        }

        CUtil.LOGGER.error("*---------------------------------------------------------------*");
    }

    private static boolean boolProp(String name, boolean defProd, boolean defDev) {
        boolean def = PlatformMainUtil.isDevEnv() ? defDev : defProd;
        boolean enabled = Boolean.parseBoolean(System.getProperty(name, Boolean.toString(def)));
        properties.add(new DebugProperty<>(name, enabled, def, b -> Boolean.toString(b)));
        return enabled;
    }

    private static String strProp(String name, String defProd, String defDev) {
        String def = PlatformMainUtil.isDevEnv() ? defDev : defProd;
        String enabled = System.getProperty(name, def);
        properties.add(new DebugProperty<>(name, enabled, def, Function.identity()));
        return enabled;
    }

    private record DebugProperty<T>(String name, T state, T def, Function<T, String> typeToString) {
    }
}
