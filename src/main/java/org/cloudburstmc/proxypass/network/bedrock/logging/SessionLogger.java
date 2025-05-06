package org.cloudburstmc.proxypass.network.bedrock.logging;

import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectWriter;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.BedrockSession;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.netty.BedrockPacketWrapper;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacket;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.ui.PacketInspector;
import org.jose4j.json.internal.json_simple.JSONObject;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;


@Log4j2
public class SessionLogger {

    private static final String PATTERN_FORMAT = "HH:mm:ss:SSS";
    private static final DateTimeFormatter FORMATTER = DateTimeFormatter.ofPattern(PATTERN_FORMAT)
            .withZone(ZoneId.systemDefault());
    private static final String LOG_FORMAT = "[%s] [%s] - %s";

    private static final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor();

    private final ProxyPass proxy;

    private final Path dataPath;

    private final Path logPath;

    private final Deque<String> logBuffer = new ArrayDeque<>();

    public SessionLogger(ProxyPass proxy, Path sessionsDir, String displayName, long timestamp) {
        this.proxy = proxy;
        this.dataPath = sessionsDir.resolve(displayName + '-' + timestamp);
        this.logPath = dataPath.resolve("packets.log");
    }

    public void start() {
        if (proxy.getConfiguration().isLoggingPackets()){
            if (proxy.getConfiguration().getLogTo().logToFile) {
                log.debug("Packets will be logged under " + logPath.toString());
                try {
                    Files.createDirectories(dataPath);
                } catch (IOException e) {
                    throw new RuntimeException(e);
                }
            }
            executor.scheduleAtFixedRate(this::flushLogBuffer, 5, 5, TimeUnit.SECONDS);
        }
    }

    public void saveImage(String name, BufferedImage image) {
        Path path = dataPath.resolve(name + ".png");
        try (OutputStream stream = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ImageIO.write(image, "png", stream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveJson(String name, JSONObject object) throws IOException {
        ObjectWriter jsonout = ProxyPass.JSON_MAPPER.writer(new DefaultPrettyPrinter());
        jsonout.writeValue(new FileOutputStream(logPath.getParent().resolve(name + ".json").toFile()), object);
    }

    public void saveJson(String name, JsonNode node) throws IOException {
        ObjectWriter jsonout = ProxyPass.JSON_MAPPER.writer(new DefaultPrettyPrinter());
        jsonout.writeValue(new FileOutputStream(logPath.getParent().resolve(name + ".json").toFile()), node);
    }

    public void saveJson(String name, byte[] encodedJsonString) {
        Path geometryPath = dataPath.resolve(name +".json");
        try {
            Files.write(geometryPath, encodedJsonString, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void logPacket(BedrockSession session, BedrockPacket packet, boolean upstream) {
        String logPrefix = getLogPrefix(upstream);
        if (!proxy.isIgnoredPacket(packet.getClass())) {
            if (session.isLogging() && log.isTraceEnabled()) {
                log.trace("{} {}: {}", logPrefix, session.getSocketAddress(), packet);
            }

            String logMessage = String.format(LOG_FORMAT, FORMATTER.format(Instant.now()), logPrefix, packet);
            if (proxy.getConfiguration().isLoggingPackets()) {
                logToBuffer(() -> logMessage);
            }

            if (proxy.getConfiguration().isLoggingPackets() && proxy.getConfiguration().getLogTo().logToConsole) {
                System.out.println(logMessage);
            }

            if (proxy.getConfiguration().isEnableUi()) {
                // Get the raw packet data
                ByteBuf buffer = ByteBufAllocator.DEFAULT.ioBuffer();
                try {
                    BedrockCodecHelper helper = session.getPeer().getCodecHelper();
                    ProxyPass.CODEC.tryEncode(helper, buffer, packet);
                    byte[] data = new byte[buffer.readableBytes()];
                    buffer.getBytes(buffer.readerIndex(), data);
                    
                    // Send to the packet inspector with the original packet
                    PacketInspector.addPacket(packet, data, !upstream, 
                            session.getSocketAddress().toString());
                } catch (Exception e) {
                    log.debug("Failed to capture packet for UI", e);
                } finally {
                    buffer.release();
                }
            }
        }
    }

    public void logPacket(BedrockSession session, BedrockPacketWrapper wrapper, boolean upstream) {
        BedrockPacket packet = wrapper.getPacket();
        if (!proxy.isIgnoredPacket(packet.getClass())) {
            if (session.isLogging() && log.isTraceEnabled()) {
                log.trace("{} {}: {}", getLogPrefix(upstream), session.getSocketAddress(), packet);
            }

            String logMessage = String.format(LOG_FORMAT, FORMATTER.format(Instant.now()), getLogPrefix(upstream), packet);
            if (proxy.getConfiguration().isLoggingPackets()) {
                logToBuffer(() -> logMessage);
            }

            if (proxy.getConfiguration().isLoggingPackets() && proxy.getConfiguration().getLogTo().logToConsole) {
                System.out.println(logMessage);
            }
        }

        if (proxy.getConfiguration().isEnableUi()) {
            ByteBuf packetBuffer = wrapper.getPacketBuffer().slice();
            packetBuffer.skipBytes(wrapper.getHeaderLength()); // skip header
            ByteBuf buffer = packetBuffer.alloc().ioBuffer();
            try {
                buffer.writeInt(wrapper.getPacketId()); // packet ID
                buffer.writeBytes(packetBuffer);
                byte[] data = new byte[buffer.readableBytes()];
                buffer.getBytes(buffer.readerIndex(), data);
                PacketInspector.addPacket(packet, data, !upstream, session.getSocketAddress().toString());
            } catch (Exception e) {
                log.debug("Failed to capture packet for UI", e);
            } finally {
                buffer.release();
            }
        }
    }

    private String getLogPrefix(boolean upstream) {
        return upstream ? "SERVER BOUND" : "CLIENT BOUND";
    }

    private void logToBuffer(Supplier<String> supplier) {
        synchronized (logBuffer) {
            logBuffer.addLast(supplier.get());
        }
    }

    private void flushLogBuffer() {
        synchronized (logBuffer) {
            try {
                if (proxy.getConfiguration().getLogTo().logToFile) {
                    Files.write(logPath, logBuffer, StandardOpenOption.APPEND, StandardOpenOption.CREATE);
                }
                logBuffer.clear();
            } catch (IOException e) {
                log.error("Unable to flush packet log", e);
            }
        }
    }

}
