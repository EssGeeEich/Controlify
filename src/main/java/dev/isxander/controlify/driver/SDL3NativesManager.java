package dev.isxander.controlify.driver;

import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.config.ControlifyConfig;
import dev.isxander.controlify.gui.screen.DownloadingSDLScreen;
import dev.isxander.controlify.utils.CUtil;
import dev.isxander.controlify.utils.TrackingBodySubscriber;
import dev.isxander.controlify.utils.TrackingConsumer;
import io.github.libsdl4j.api.version.SdlVersionConst;
import io.github.libsdl4j.jna.SdlNativeLibraryLoader;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static io.github.libsdl4j.api.SdlInit.*;
import static io.github.libsdl4j.api.SdlSubSystemConst.*;
import static io.github.libsdl4j.api.error.SdlError.*;
import static io.github.libsdl4j.api.hints.SdlHints.*;
import static io.github.libsdl4j.api.hints.SdlHintConsts.*;

public class SDL3NativesManager {
    private static final String SDL3_VERSION = "3." + SdlVersionConst.SDL_COMMIT;
    private static final Map<Target, NativeFileInfo> NATIVE_LIBRARIES = Map.of(
            new Target(Util.OS.WINDOWS, true, false), new NativeFileInfo("win32-x86-64", "windows64", "dll"),
            new Target(Util.OS.WINDOWS, false, false), new NativeFileInfo("win32-x86", "window32", "dll"),
            new Target(Util.OS.LINUX, true, false), new NativeFileInfo("linux-x86-64", "linux64", "so"),
            new Target(Util.OS.OSX, true, false), new NativeFileInfo("darwin-x86-64", "macos-x86_64", "dylib"),
            new Target(Util.OS.OSX, true, true), new NativeFileInfo("darwin-aarch64", "macos-aarch64", "dylib")
    );
    private static final String NATIVE_LIBRARY_URL = "https://maven.isxander.dev/releases/dev/isxander/libsdl4j-natives/%s/".formatted(SDL3_VERSION);

    private static boolean loaded = false;
    private static boolean attemptedLoad = false;

    private static CompletableFuture<Boolean> initFuture;

    public static CompletableFuture<Boolean> maybeLoad() {
        if (initFuture != null)
            return initFuture;

        if (!Controlify.instance().config().globalSettings().loadVibrationNatives)
            return initFuture = CompletableFuture.completedFuture(false);

        if (attemptedLoad)
            return initFuture = CompletableFuture.completedFuture(loaded);

        attemptedLoad = true;

        if (!isSupportedOnThisPlatform()) {
            CUtil.LOGGER.warn("No native library for current platform, skipping SDL3 load");
            return initFuture = CompletableFuture.completedFuture(false);
        }

        Path localLibraryPath = getNativesFolderPath().resolve(Target.CURRENT.getArtifactName());

        if (Files.exists(localLibraryPath)) {
            boolean success = loadAndStart(localLibraryPath);
            if (success)
                return initFuture = CompletableFuture.completedFuture(true);

            CUtil.LOGGER.warn("Failed to load SDL3 from local file, attempting to re-download");
        }
        return initFuture = downloadAndStart(localLibraryPath);
    }

    private static boolean loadAndStart(Path localLibraryPath) {
        try {
            SdlNativeLibraryLoader.loadLibSDL3FromFilePathNow(localLibraryPath.toAbsolutePath().toString());

            startSDL3();

            loaded = true;
            return true;
        } catch (Throwable e) {
            CUtil.LOGGER.error("Failed to start SDL3", e);
            return false;
        }
    }

    private static void startSDL3() {
        // better rumble
        SDL_SetHint(SDL_HINT_JOYSTICK_HIDAPI_PS3, "1");
        SDL_SetHint(SDL_HINT_JOYSTICK_HIDAPI_PS4_RUMBLE, "1");
        SDL_SetHint("SDL_JOYSTICK_HIDAPI_STEAMDECK", "1");

        // initialise SDL with just joystick and gamecontroller subsystems
        if (SDL_Init(SDL_INIT_JOYSTICK | SDL_INIT_GAMEPAD | SDL_INIT_EVENTS | SDL_INIT_AUDIO) != 0) {
            CUtil.LOGGER.error("Failed to initialise SDL3: " + SDL_GetError());
            throw new RuntimeException("Failed to initialise SDL3: " + SDL_GetError());
        }

        CUtil.LOGGER.info("Initialised SDL4j {}", SDL3_VERSION);
    }

    private static CompletableFuture<Boolean> downloadAndStart(Path localLibraryPath) {
        return downloadLibrary(localLibraryPath)
                .thenCompose(success -> {
                    if (!success) {
                        return CompletableFuture.completedFuture(false);
                    }

                    return CompletableFuture.completedFuture(loadAndStart(localLibraryPath));
                })
                .thenCompose(success -> Minecraft.getInstance().submit(() -> success));
    }

    private static CompletableFuture<Boolean> downloadLibrary(Path path) {
        try {
            Files.deleteIfExists(path);
            Files.createDirectories(path.getParent());
            Files.createFile(path);
        } catch (Exception e) {
            CUtil.LOGGER.error("Failed to delete existing SDL3 native library file", e);
            return CompletableFuture.completedFuture(false);
        }

        String url = NATIVE_LIBRARY_URL + Target.CURRENT.getArtifactName();

        var httpClient = HttpClient.newHttpClient();
        var httpRequest = HttpRequest.newBuilder()
                .GET()
                .uri(URI.create(url))
                .build();

        // send the request asynchronously and track the progress on the download
        AtomicReference<DownloadingSDLScreen> downloadScreen = new AtomicReference<>();
        Minecraft minecraft = Minecraft.getInstance();
        return httpClient.sendAsync(
                httpRequest,
                TrackingBodySubscriber.bodyHandler(
                        HttpResponse.BodyHandlers.ofFileDownload(path.getParent(), StandardOpenOption.WRITE),
                        new TrackingConsumer(
                                total -> {
                                    DownloadingSDLScreen screen = new DownloadingSDLScreen(minecraft.screen, total, path);
                                    downloadScreen.set(screen);
                                    minecraft.execute(() -> minecraft.setScreen(screen));
                                },
                                (received, total) -> downloadScreen.get().updateDownloadProgress(received),
                                error -> {
                                    if (error.isPresent()) {
                                        CUtil.LOGGER.error("Failed to download SDL3 native library", error.get());
                                        minecraft.execute(() -> downloadScreen.get().failDownload(error.get()));
                                    } else {
                                        CUtil.LOGGER.debug("Finished downloading SDL3 native library");
                                        minecraft.execute(() -> downloadScreen.get().finishDownload());
                                    }
                                }
                        )
                )
        ).handle((response, throwable) -> {
            if (throwable != null) {
                CUtil.LOGGER.error("Failed to download SDL3 native library", throwable);
                return false;
            }

            return true;
        });
    }

    public static boolean isLoaded() {
        return loaded;
    }

    public static boolean hasAttemptedLoad() {
        return attemptedLoad;
    }

    public static boolean isSupportedOnThisPlatform() {
        return Target.CURRENT.hasNativeLibrary();
    }

    private static Path getNativesFolderPath() {
        Path nativesFolderPath = FabricLoader.getInstance().getGameDir();
        ControlifyConfig config = Controlify.instance().config();
        String customPath = config.globalSettings().customVibrationNativesPath;
        if (!customPath.isEmpty()) {
            try {
                nativesFolderPath = Path.of(customPath);
            } catch (InvalidPathException e) {
                CUtil.LOGGER.error("Invalid custom SDL3 native library path. Using default and resetting custom path.", e);
                config.globalSettings().customVibrationNativesPath = "";
                config.save();
            }
        }
        return nativesFolderPath.resolve("controlify-natives");
    }

    public record Target(Util.OS os, boolean is64Bit, boolean isARM) {
        public static final Target CURRENT = Util.make(() -> {
            Util.OS os = Util.getPlatform();

            String arch = System.getProperty("os.arch");
            boolean is64bit = arch.contains("64");
            boolean isARM = arch.contains("arm") || arch.contains("aarch");

            return new Target(os, is64bit, isARM);
        });

        public boolean hasNativeLibrary() {
            return NATIVE_LIBRARIES.containsKey(this);
        }

        public String getArtifactName() {
            NativeFileInfo file = NATIVE_LIBRARIES.get(this);
            return "libsdl4j-natives-" + SDL3_VERSION + "-" + file.downloadSuffix + "." + file.fileExtension;
        }

        public boolean isMacArm() {
            return os == Util.OS.OSX && isARM;
        }

        public String formatted() {
            return os().name() + " 64bit=" + is64Bit() + ";isARM=" + isARM();
        }
    }

    public record NativeFileInfo(String folderName, String downloadSuffix, String fileExtension) {
        public Path getNativePath() {
            return getSearchPath()
                    .resolve(folderName)
                    .resolve("SDL3." + fileExtension);
        }

        public Path getSearchPath() {
            return FabricLoader.getInstance().getGameDir()
                    .resolve("controlify-natives")
                    .resolve(SDL3_VERSION);
        }
    }
}
