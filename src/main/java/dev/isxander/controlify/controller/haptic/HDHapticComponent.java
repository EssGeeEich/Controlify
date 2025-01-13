package dev.isxander.controlify.controller.haptic;

import com.mojang.blaze3d.audio.SoundBuffer;
import dev.isxander.controlify.controller.serialization.ConfigClass;
import dev.isxander.controlify.controller.serialization.ConfigHolder;
import dev.isxander.controlify.controller.ECSComponent;
import dev.isxander.controlify.controller.serialization.IConfig;
import dev.isxander.controlify.controller.impl.ConfigImpl;
import dev.isxander.controlify.mixins.feature.hdhaptics.SoundBufferAccessor;
import dev.isxander.controlify.mixins.feature.hdhaptics.SoundEngineAccessor;
import dev.isxander.controlify.mixins.feature.hdhaptics.SoundManagerAccessor;
import dev.isxander.controlify.utils.CUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.client.sounds.SoundBufferLibrary;
import net.minecraft.client.sounds.SoundEngine;
import net.minecraft.client.sounds.SoundManager;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.util.RandomSource;

import javax.sound.sampled.AudioFormat;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public class HDHapticComponent implements ECSComponent, ConfigHolder<HDHapticComponent.Config> {
    public static final ResourceLocation ID = CUtil.rl("hd_haptics");

    private final IConfig<Config> config = new ConfigImpl<>(Config::new, Config.class);
    private Consumer<CompleteSoundData> playHapticConsumer;
    private final RandomSource randomSource;

    // the existing sound buffer library in the sound engine works on a ResourceProvider for registered sounds only
    // haptics are not sounds.
    private static final SoundBufferLibrary hapticBufferLibrary = new SoundBufferLibrary(Minecraft.getInstance().getResourceManager());

    public HDHapticComponent() {
        this.randomSource = RandomSource.create();
    }

    public void playHaptic(ResourceLocation haptic) {
        if (!confObj().enabled || playHapticConsumer == null) return;

        getSoundData(hapticBufferLibrary.getCompleteBuffer(haptic))
                .thenAccept(playHapticConsumer)
                .exceptionally(throwable -> {
                    throwable.printStackTrace();
                    return null;
                });
    }

    public void playHaptic(SoundEvent sound) {
        ResourceLocation location = Minecraft.getInstance().getSoundManager()
                .getSoundEvent(/*? if >=1.21.2 {*/ sound.location() /*?} else {*/ /*sound.getLocation() *//*?}*/)
                .getSound(randomSource).getLocation();

        SoundManager soundManager = Minecraft.getInstance().getSoundManager();
        SoundEngine soundEngine = ((SoundManagerAccessor) soundManager).getSoundEngine();
        SoundBufferLibrary bufferLibrary = ((SoundEngineAccessor) soundEngine).getSoundBuffers();

        ResourceLocation soundId = CUtil.rl(location.getNamespace(), "sounds/" + location.getPath() + ".ogg");

        getSoundData(bufferLibrary.getCompleteBuffer(soundId))
                .thenAccept(playHapticConsumer)
                .exceptionally(throwable -> {
                    throwable.printStackTrace();
                    return null;
                });
    }

    public void acceptPlayHaptic(Consumer<CompleteSoundData> consumer) {
        this.playHapticConsumer = consumer;
    }

    private CompletableFuture<CompleteSoundData> getSoundData(CompletableFuture<SoundBuffer> sound) {
        // TODO: this recomputes on every play
        return sound
                .thenApply(soundBuffer -> {
                    var accessor = (SoundBufferAccessor) soundBuffer;
                    ByteBuffer bytes = accessor.getData();
                    AudioFormat format = accessor.getFormat();

                    if (bytes == null) {
                        return null;
                    }

                    bytes.rewind();

                    byte[] audio = new byte[bytes.remaining()];

                    bytes.get(audio);

                    return new CompleteSoundData(audio, format);
                });
    }

    @Override
    public IConfig<Config> config() {
        return config;
    }

    @Override
    public ResourceLocation id() {
        return ID;
    }

    public static class Config implements ConfigClass {
        public boolean enabled = true;
    }
}
