package odinclient.module.playerdebug;

import java.io.BufferedInputStream;
import java.io.InputStream;
import java.util.concurrent.atomic.AtomicBoolean;

import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.Clip;
import javax.sound.sampled.LineEvent.Type;

public final class PlayerFinderAlertSound {
    private static final AtomicBoolean PLAYING = new AtomicBoolean();

    private PlayerFinderAlertSound() {}

    public static void play() {
        if (PLAYING.compareAndSet(false, true)) {
            Thread.ofVirtual()
                    .name("odinclient-player-debug-v2-flag-sound")
                    .start(
                            () -> {
                                try {
                                    try (InputStream resource =
                                            PlayerFinderAlertSound.class.getResourceAsStream(
                                                    "/assets/odinclient/sounds/player_debug_v2_flag.wav")) {
                                        if (resource != null) {
                                            try (BufferedInputStream buffered =
                                                            new BufferedInputStream(resource);
                                                    AudioInputStream audio =
                                                            AudioSystem.getAudioInputStream(
                                                                    buffered); ) {
                                                Clip clip = AudioSystem.getClip();
                                                clip.addLineListener(
                                                        event -> {
                                                            if (event.getType() == Type.STOP) {
                                                                clip.close();
                                                                PLAYING.set(false);
                                                            }
                                                        });
                                                clip.open(audio);
                                                clip.start();
                                                return;
                                            }
                                        }

                                        PLAYING.set(false);
                                    }
                                } catch (Exception e) {
                                    PLAYING.set(false);
                                    System.err.println(
                                            "[OdinClient] Could not play V2 flag sound: "
                                                    + e.getMessage());
                                }
                            });
        }
    }
}
