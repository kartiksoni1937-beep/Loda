package odinclient.module.discord;

import java.io.EOFException;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.locks.LockSupport;

public final class DiscordIpcClient {
    private static final String CLIENT_ID = "1537649590495481927";
    private static final String APP_NAME = "OdinClient";
    private static final String LARGE_IMAGE_URL =
            "https://cdn.discordapp.com/app-icons/1537649590495481927/1845638fdc79555ea73cd11848511cbc.png?size=256";
    private static final String INVITE_URL = "https://discord.gg/8sRkdm8XcD";
    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME = 1;
    private static final int CLOSE = 2;
    private static final int PING = 3;
    private static final int OP_PONG = 4;
    private static final int MAX_FRAME_SIZE = 1048576;
    private static final long ACTIVITY_REFRESH_MS = 5000L;
    private static final long RECONNECT_DELAY_MS = 5000L;
    private static final Object STATE_LOCK = new Object();
    private static final Object IO_LOCK = new Object();
    private static volatile boolean running;
    private static volatile long sessionId;
    private static volatile RandomAccessFile pipe;
    private static volatile Thread workerThread;

    private DiscordIpcClient() {}

    public static void start() {
        stop();
        long startSeconds = System.currentTimeMillis() / 1000L;
        synchronized (STATE_LOCK) {
            running = true;
            long id = sessionId + 1L;
            sessionId = id;
            Runnable task = () -> runPresenceLoop(id, startSeconds);
            workerThread =
                    Thread.ofPlatform()
                            .name("OdinClient-Discord-Presence")
                            .daemon(true)
                            .start(task);
        }
    }

    public static void stop() {
        RandomAccessFile activePipe;
        Thread thread;
        synchronized (STATE_LOCK) {
            running = false;
            sessionId++;
            activePipe = pipe;
            pipe = null;
            thread = workerThread;
            workerThread = null;
        }

        if (activePipe != null) {
            try {
                sendActivity(activePipe, null);
            } catch (IOException ignored) {
            }

            closeQuietly(activePipe);
        }

        if (thread != null) {
            thread.interrupt();
        }
    }

    private static void runPresenceLoop(long id, long startSeconds) {
        while (isCurrentSession(id)) {
            RandomAccessFile connection = null;

            try {
                connection = openPipe();
                sendHandshake(connection);
                if (readFrame(connection).opcode == 2) {
                    throw new IOException("Discord rejected the IPC handshake");
                }

                if (!isCurrentSession(id)) {
                    return;
                }

                sendActivity(connection, startSeconds);
                FileInputStream input = new FileInputStream(connection.getFD());
                long nextActivity = System.currentTimeMillis() + 5000L;
                synchronized (STATE_LOCK) {
                    if (!isCurrentSession(id)) {
                        return;
                    }

                    pipe = connection;
                }

                while (isCurrentSession(id)) {
                    long now = System.currentTimeMillis();
                    if (now >= nextActivity) {
                        sendActivity(connection, startSeconds);
                        nextActivity = now + 5000L;
                    }

                    if (input.available() >= 8) {
                        DiscordIpcClient.IpcFrame frame = readFrame(connection);
                        if (frame.opcode == 3) {
                            writeFrame(connection, 4, frame.payload);
                        } else if (frame.opcode == 2) {
                            break;
                        }
                    } else {
                        LockSupport.parkNanos(250_000_000L);
                        if (Thread.currentThread().isInterrupted()) {
                            return;
                        }
                    }
                }
            } catch (IOException ignored) {
            } finally {
                synchronized (STATE_LOCK) {
                    if (pipe == connection) {
                        pipe = null;
                    }
                }

                closeQuietly(connection);
            }

            if (isCurrentSession(id)) {
                LockSupport.parkNanos(5_000_000_000L);
                if (Thread.currentThread().isInterrupted()) {
                    return;
                }
            }
        }
    }

    private static boolean isCurrentSession(long id) {
        return running && sessionId == id;
    }

    private static RandomAccessFile openPipe() throws IOException {
        IOException last = null;
        String[] prefixes = {"\\\\?\\pipe\\discord-ipc-", "\\\\.\\pipe\\discord-ipc-"};

        for (String prefix : prefixes) {
            for (int i = 0; i < 10; i++) {
                try {
                    return new RandomAccessFile(prefix + i, "rw");
                } catch (IOException e) {
                    last = e;
                }
            }
        }

        throw last == null ? new IOException("Discord IPC pipe not found") : last;
    }

    private static void sendHandshake(RandomAccessFile out) throws IOException {
        writeFrame(
                out,
                0,
                "{\"v\":1,\"client_id\":\"1537649590495481927\"}".getBytes(StandardCharsets.UTF_8));
    }

    private static void sendActivity(RandomAccessFile out, Long startSeconds) throws IOException {
        long pid = ProcessHandle.current().pid();
        String activity;
        if (startSeconds == null) {
            activity = "null";
        } else {
            activity =
                    "{\"name\":\"OdinClient\",\"details\":\"Playing Minecraft"
                        + " 1.21.11\",\"state\":\"Using"
                        + " OdinClient\",\"assets\":{\"large_image\":\"https://cdn.discordapp.com/app-icons/1537649590495481927/1845638fdc79555ea73cd11848511cbc.png?size=256\",\"large_text\":\"OdinClient\"},\"timestamps\":{\"start\":"
                            + startSeconds
                            + "},\"buttons\":[{\"label\":\"OdinClient"
                            + " Discord\",\"url\":\"https://discord.gg/8sRkdm8XcD\"}]}";
        }

        writeFrame(
                out,
                1,
                ("{\"cmd\":\"SET_ACTIVITY\",\"args\":{\"pid\":"
                                + pid
                                + ",\"activity\":"
                                + activity
                                + "},\"nonce\":\""
                                + UUID.randomUUID()
                                + "\"}")
                        .getBytes(StandardCharsets.UTF_8));
    }

    private static void writeFrame(RandomAccessFile out, int opcode, byte[] payload)
            throws IOException {
        synchronized (IO_LOCK) {
            out.writeInt(Integer.reverseBytes(opcode));
            out.writeInt(Integer.reverseBytes(payload.length));
            out.write(payload);
        }
    }

    private static DiscordIpcClient.IpcFrame readFrame(RandomAccessFile in) throws IOException {
        synchronized (IO_LOCK) {
            int opcode;
            int length;
            try {
                opcode = Integer.reverseBytes(in.readInt());
                length = Integer.reverseBytes(in.readInt());
            } catch (EOFException e) {
                throw new IOException("Discord IPC connection closed", e);
            }

            if (length >= 0 && length <= 1048576) {
                byte[] payload = new byte[length];
                in.readFully(payload);
                return new DiscordIpcClient.IpcFrame(opcode, payload);
            } else {
                throw new IOException("Invalid Discord IPC frame length: " + length);
            }
        }
    }

    private static void closeQuietly(RandomAccessFile file) {
        if (file != null) {
            try {
                file.close();
            } catch (IOException ignored) {
            }
        }
    }

    private record IpcFrame(int opcode, byte[] payload) {}
}
