package dev.isxander.controlify.controller.haptic;

import javax.sound.sampled.AudioFormat;

public record CompleteSoundData(byte[] audio, AudioFormat format) {
}
