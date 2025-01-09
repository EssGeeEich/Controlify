package dev.isxander.controlify.utils;

public enum Platform {
    UNKNOWN,
    MAC,
    LINUX,
    WINDOWS,
    ANDROID,
    IOS;

    private static final Platform currentPlatform;
    static {
        String osName = System.getProperty("platform.name");

        if (osName.startsWith("Linux")) {
            if ("dalvik".equalsIgnoreCase(System.getProperty("java.vm.name"))) {
                currentPlatform = ANDROID;
            } else {
                currentPlatform = LINUX;
            }
        } else if (osName.startsWith("Mac") || osName.startsWith("Darwin")) {
            currentPlatform = MAC;
        } else if (osName.startsWith("Windows")) {
            currentPlatform = WINDOWS;
        } else if (osName.startsWith("iOS")) {
            currentPlatform = IOS;
        } else {
            CUtil.LOGGER.log("Unable to determine platform: " + osName);
            currentPlatform = UNKNOWN;
        }
    }

    public static Platform current() {
        return currentPlatform;
    }
}
