package dev.isxander.controlify.driver.sdl;

import dev.isxander.controlify.Controlify;
import dev.isxander.controlify.config.ControlifyConfig;
import dev.isxander.controlify.debug.DebugProperties;
import dev.isxander.controlify.gui.screen.DownloadingSDLScreen;
import dev.isxander.controlify.platform.main.PlatformMainUtil;
import dev.isxander.controlify.utils.CUtil;
import dev.isxander.controlify.utils.Platform;
import dev.isxander.controlify.utils.TrackingBodySubscriber;
import dev.isxander.controlify.utils.TrackingConsumer;
import dev.isxander.controlify.utils.log.ControlifyLogger;
import dev.isxander.sdl3java.api.version.SdlVersionConst;
import dev.isxander.sdl3java.api.version.SdlVersionRecord;
import dev.isxander.sdl3java.jna.SdlNativeLibraryLoader;
import net.minecraft.Util;
import net.minecraft.client.Minecraft;
import org.apache.commons.codec.digest.DigestUtils;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static dev.isxander.sdl3java.api.SdlInit.*;
import static dev.isxander.sdl3java.api.SdlSubSystemConst.*;
import static dev.isxander.sdl3java.api.error.SdlError.*;
import static dev.isxander.sdl3java.api.hints.SdlHints.*;
import static dev.isxander.sdl3java.api.hints.SdlHintConsts.*;
import static dev.isxander.sdl3java.api.version.SdlVersion.*;

public class SDL3NativesManager {
    private static final String SDL3_VERSION = getJavaBindingsVersion() + "." + SdlVersionConst.SDL_COMMIT;
    private static final Map<Target, NativeFileInfo> NATIVE_LIBRARIES = Map.of(
            new Target(Platform.WINDOWS, true, false), new NativeFileInfo("win32-x86-64", "windows-x86_64", "dll"),
            new Target(Platform.WINDOWS, false, false), new NativeFileInfo("win32-x86", "window-x86", "dll"),
            new Target(Platform.LINUX, true, false), new NativeFileInfo("linux-x86-64", "linux-x86_64", "so"),
            new Target(Platform.LINUX, true, true), new NativeFileInfo("linux-aarch64", "linux-aarch64", "so"),
            new Target(Platform.MAC, true, false), new NativeFileInfo("darwin-x86-64", "macos-universal", "dylib"),
            new Target(Platform.MAC, true, true), new NativeFileInfo("darwin-aarch64", "macos-universal", "dylib")
    );
    private static final String NATIVE_LIBRARY_URL = "https://maven.isxander.dev/releases/dev/isxander/libsdl4j-natives/%s/".formatted(SDL3_VERSION);

    private static boolean loaded = false;
    private static boolean attemptedLoad = false;

    private static CompletableFuture<Boolean> initFuture;

    private static final ControlifyLogger logger = CUtil.LOGGER.createSubLogger("SDL3NativesManager");

    public static CompletableFuture<Boolean> maybeLoad() {
        if (initFuture != null)
            return initFuture;

        if (!Controlify.instance().config().globalSettings().loadVibrationNatives)
            return initFuture = CompletableFuture.completedFuture(false);

        if (attemptedLoad)
            return initFuture = CompletableFuture.completedFuture(loaded);

        attemptedLoad = true;

        if (tryOfflineLoadAndStart()) {
            return initFuture = CompletableFuture.completedFuture(true);
        }

        if (!isSupportedOnThisPlatform()) {
            CUtil.LOGGER.warn("No native library for current platform, skipping SDL3 load");
            return initFuture = CompletableFuture.completedFuture(false);
        }

        Path nativesFolder = getNativesFolderPath();
        Path localLibraryPath = nativesFolder.resolve(Target.CURRENT.getArtifactName());
        Path checksumPath = nativesFolder.resolve(Target.CURRENT.getArtifactMD5Name());

        if (Files.exists(localLibraryPath)) {
            // downloadAndStart asynchronously downloads checksum along with lib
            // only download manually here if the lib is already downloaded
            if (Files.notExists(checksumPath)) {
                logger.log("Downloading checksum for existing SDL natives");
                downloadChecksum(checksumPath);
            }

            if (verifyFileMd5(localLibraryPath, checksumPath, true)
                    && loadAndStart(localLibraryPath))
                return initFuture = CompletableFuture.completedFuture(true);

            CUtil.LOGGER.warn("Failed to load SDL3 from local file, attempting to re-download");
        }
        return initFuture = downloadAndStart(localLibraryPath);
    }

    public static boolean tryOfflineLoadAndStart() {
        if (initFuture != null) {
            throw new IllegalStateException("Tried to start offline mode but initialization already in progress.");
        }

        String path = "SDL3";
        if (CUtil.IS_POJAV_LAUNCHER) {
            logger.log("Detected PojavLauncher.");
            String nativesFolderName = System.getenv("POJAV_NATIVEDIR");
            Path libsLocation = Path.of(nativesFolderName).toAbsolutePath();
            path = libsLocation.resolve("libSDL3.so").toString();
        }

        try {
            // TODO: this load is not checked by jar checksum - it could be getting it from an unknown source
            SdlNativeLibraryLoader.loadLibSDL3FromFilePathNow(path);
        } catch (UnsatisfiedLinkError e) {
            if (CUtil.IS_POJAV_LAUNCHER) {
                logger.error("Failed to find SDL3, even though PojavLauncher should provide it. Is it up to date?");
            }

            return false;
        }

        initFuture = new CompletableFuture<>();

        try {
            startSDL3();
            loaded = true;
            initFuture.complete(true);
        } catch (Throwable t) {
            CUtil.LOGGER.error("Failed to start SDL3", t);
            initFuture.complete(false);
            return false;
        }

        return true;
    }

    private static boolean loadAndStart(Path localLibraryPath) {
        try {
            if (!verifyJarMd5(localLibraryPath)) {
                throw new IllegalStateException("SDL3 native library jar checksum did not match.");
            }

            SdlNativeLibraryLoader.loadLibSDL3FromFilePathNow(localLibraryPath.toAbsolutePath().toString());

            startSDL3();

            loaded = true;
            return true;
        } catch (Throwable e) {
            CUtil.LOGGER.error("Failed to start SDL3", e);
            return false;
        }
    }

    /**
     * Initialises SDL3 after it has been loaded by JNA.
     */
    private static void startSDL3() {
        SDL_SetHint(SDL_HINT_JOYSTICK_HIDAPI, "1");
        SDL_SetHint(SDL_HINT_JOYSTICK_ENHANCED_REPORTS, "1");
        SDL_SetHint(SDL_HINT_JOYSTICK_HIDAPI_STEAM, "1");
        SDL_SetHint(SDL_HINT_JOYSTICK_ROG_CHAKRAM, "1");
        SDL_SetHint(SDL_HINT_JOYSTICK_ALLOW_BACKGROUND_EVENTS, "1");
        SDL_SetHint(SDL_HINT_JOYSTICK_LINUX_DEADZONES, "1");

        SdlVersionRecord nativesVersion = SdlVersionRecord.fromPacked(SDL_GetVersion());
        SdlVersionRecord javaVersion = SDL_GetJavaBindingsVersion();
        logger.log("Loading SDL3 version: {}. Java bindings targeting: {}", nativesVersion, javaVersion);
        if (!nativesVersion.equals(javaVersion)) {
            logger.warn("SDL3 NATIVE LIBRARY VERSION MISMATCH! Java bindings are targeting a different version of SDL3 than the loaded native library. This may cause issues.");
        }

        // initialise SDL with just joystick and gamecontroller subsystems
        if (!SDL_Init(SDL_INIT_JOYSTICK | SDL_INIT_GAMEPAD | SDL_INIT_EVENTS | SDL_INIT_AUDIO)) {
            CUtil.LOGGER.error("Failed to initialise SDL3: {}", SDL_GetError());
            throw new RuntimeException("Failed to initialise SDL3: " + SDL_GetError());
        }

        logger.log("Successfully initialised SDL subsystems");
    }

    /**
     * Downloads the SDL3 native library and its checksum, then loads and starts it.
     */
    private static CompletableFuture<Boolean> downloadAndStart(Path localLibraryPath) {
        return downloadLibrary(localLibraryPath.getParent())
                .thenCompose(success -> {
                    if (!success) {
                        return CompletableFuture.completedFuture(false);
                    }

                    return CompletableFuture.completedFuture(loadAndStart(localLibraryPath));
                })
                .thenCompose(success -> Minecraft.getInstance().submit(() -> success));
    }

    /**
     * Downloads the SDL3 native library, and its checksum, to the target folder.
     */
    private static CompletableFuture<Boolean> downloadLibrary(Path targetFolder) {
        String artifactName = Target.CURRENT.getArtifactName();
        String md5Name = Target.CURRENT.getArtifactMD5Name();

        Path artifactPath = targetFolder.resolve(artifactName);
        Path md5Path = targetFolder.resolve(md5Name);

        try {
            Files.deleteIfExists(artifactPath);
            Files.deleteIfExists(md5Path);

            Files.createDirectories(targetFolder);

            Files.createFile(artifactPath);
            Files.createFile(md5Path);
        } catch (Exception e) {
            CUtil.LOGGER.error("Failed to delete existing SDL3 native library file", e);
            return CompletableFuture.completedFuture(false);
        }

        String url = NATIVE_LIBRARY_URL + artifactName;
        String md5Url = NATIVE_LIBRARY_URL + md5Name;

        var httpClient = HttpClient.newHttpClient();
        var libRequest = HttpRequest.newBuilder(URI.create(url)).build();
        var hashRequest = HttpRequest.newBuilder(URI.create(md5Url)).build();

        // send the request asynchronously and track the progress on the download
        Minecraft minecraft = Minecraft.getInstance();
        DownloadingSDLScreen downloadScreen = new DownloadingSDLScreen(minecraft.screen, 0, artifactPath);
        minecraft.setScreen(downloadScreen);

        CompletableFuture<?> libFuture = downloadTracked(httpClient, libRequest, downloadScreen, targetFolder, minecraft);
        CompletableFuture<?> hashFuture = downloadTracked(httpClient, hashRequest, downloadScreen, targetFolder, minecraft);

        return CompletableFuture.allOf(libFuture, hashFuture)
                .handle((response, throwable) -> {
                    if (throwable != null) {
                        CUtil.LOGGER.error("Failed to download SDL3 native library", throwable);
                        return false;
                    }

                    CUtil.LOGGER.log("Finished downloading SDL3 native library");
                    minecraft.execute(downloadScreen::finishDownload);

                    return verifyFileMd5(artifactPath, md5Path, true);
                });
    }

    /**
     * Compares a file to an md5 checksum.
     */
    private static boolean verifyMd5(Path filePath, InputStream md5Hash) {
        try {
            String fileMd5 = DigestUtils.md5Hex(Files.newInputStream(filePath));
            String checksum = new String(md5Hash.readAllBytes()).trim();

            if (!fileMd5.equals(checksum)) {
                throw new Exception("Checksum did not match");
            }
        } catch (Exception e) {
            CUtil.LOGGER.error("Failed to verify checksum for " + filePath, e);

            return false;
        }

        return true;
    }

    /**
     * Checks the natives against an md5 checksum stored in the file system.
     */
    private static boolean verifyFileMd5(Path localLibraryPath, Path checksumPath, boolean deleteOnFail) {
        try (InputStream md5Stream = Files.newInputStream(checksumPath)) {
            boolean verified = verifyMd5(localLibraryPath, md5Stream);

            if (!verified && deleteOnFail) {
                Files.deleteIfExists(localLibraryPath);
            }

            return verified;
        } catch (IOException e) {
            CUtil.LOGGER.error("Failed to read SDL3 native library checksum", e);
            return false;
        }
    }

    /**
     * Checks the natives against the md5 checksum that is stored within the Controlify mod jar.
     */
    private static boolean verifyJarMd5(Path localLibraryPath) {
        if (!DebugProperties.USE_JAR_CHECKSUM) {
            if (!PlatformMainUtil.isDevEnv()) {
                CUtil.LOGGER.warn("Jar checksum verification is disabled in production environment. Only enable this setting if you really know what you're doing. You're leaving yourself open to security vulnerabilities.");
            }
            return true;
        }

        String md5Name = Target.CURRENT.getArtifactMD5Name();
        try (InputStream md5Stream = SDL3NativesManager.class.getResourceAsStream("/sdl3-hashes/" + md5Name)) {
            if (md5Stream == null) {
                CUtil.LOGGER.error("Failed to find SDL3 native library checksum in jar");
                return false;
            }

            return verifyMd5(localLibraryPath, md5Stream);
        } catch (IOException e) {
            CUtil.LOGGER.error("Failed to read SDL3 native library checksum from jar", e);
            return false;
        }
    }

    private static CompletableFuture<?> downloadTracked(HttpClient client, HttpRequest request, DownloadingSDLScreen downloadScreen, Path folder, Minecraft minecraft) {
        return client.sendAsync(
                request,
                TrackingBodySubscriber.bodyHandler(
                        HttpResponse.BodyHandlers.ofFileDownload(folder, StandardOpenOption.WRITE),
                        new TrackingConsumer(
                                downloadScreen::increaseTotal,
                                (received, total) -> downloadScreen.updateDownloadProgress(received),
                                error -> {
                                    if (error.isPresent()) {
                                        CUtil.LOGGER.error("Failed to download SDL3 native library", error.get());
                                        minecraft.execute(() -> downloadScreen.failDownload(error.get()));
                                    }
                                }
                        )
                )
        );
    }

    private static void downloadChecksum(Path checksumPath) {
        try {
            Path nativesFolder = checksumPath.getParent();

            Files.deleteIfExists(checksumPath);
            Files.createDirectories(nativesFolder);
            Files.createFile(checksumPath);

            HttpClient client = HttpClient.newHttpClient();
            HttpRequest request = HttpRequest.newBuilder(
                    URI.create(NATIVE_LIBRARY_URL + Target.CURRENT.getArtifactMD5Name())
            ).build();

            client.send(
                    request,
                    HttpResponse.BodyHandlers.ofFileDownload(nativesFolder, StandardOpenOption.WRITE)
            );
        } catch (Exception e) {
            CUtil.LOGGER.error("Failed to download checksum", e);
        }
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
        Path nativesFolderPath = PlatformMainUtil.getGameDir();
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

    private static SdlVersionRecord getJavaBindingsVersion() {
        return new SdlVersionRecord(
                SdlVersionConst.SDL_MAJOR_VERSION,
                SdlVersionConst.SDL_MINOR_VERSION,
                SdlVersionConst.SDL_MICRO_VERSION
        );
    }

    public record Target(Platform platform, boolean is64Bit, boolean isARM) {
        public static final Target CURRENT = Util.make(() -> {
            Platform platform = Platform.current();

            String arch = System.getProperty("os.arch");
            boolean is64bit = arch.contains("64");
            boolean isARM = arch.contains("arm") || arch.contains("aarch");

            return new Target(platform, is64bit, isARM);
        });

        public boolean hasNativeLibrary() {
            return NATIVE_LIBRARIES.containsKey(this);
        }

        public String getArtifactName() {
            NativeFileInfo file = NATIVE_LIBRARIES.get(this);
            return "libsdl4j-natives-" + SDL3_VERSION + "-" + file.downloadSuffix + "." + file.fileExtension;
        }

        public String getArtifactMD5Name() {
            return this.getArtifactName() + ".md5";
        }

        public String formatted() {
            return platform().name() + " 64bit=" + is64Bit() + ";isARM=" + isARM();
        }
    }

    public record NativeFileInfo(String folderName, String downloadSuffix, String fileExtension) {
        public Path getNativePath() {
            return getSearchPath()
                    .resolve(folderName)
                    .resolve("SDL3." + fileExtension);
        }

        public Path getSearchPath() {
            return PlatformMainUtil.getGameDir()
                    .resolve("controlify-natives")
                    .resolve(SDL3_VERSION);
        }
    }
}
