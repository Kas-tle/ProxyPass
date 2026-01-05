package org.cloudburstmc.proxypass;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.core.Version;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.core.util.DefaultIndenter;
import com.fasterxml.jackson.core.util.DefaultPrettyPrinter;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.module.SimpleModule;
import com.fasterxml.jackson.dataformat.yaml.YAMLMapper;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import dev.kastle.netty.channel.nethernet.NetherNetChannelFactory;
import dev.kastle.netty.channel.nethernet.config.NetherNetAddress;
import dev.kastle.netty.channel.nethernet.signaling.NetherNetClientSignaling;
import dev.kastle.netty.channel.nethernet.signaling.NetherNetDiscoverySignaling;
import dev.kastle.netty.channel.nethernet.signaling.NetherNetServerSignaling;
import dev.kastle.netty.channel.nethernet.signaling.NetherNetXboxSignaling;
import dev.kastle.webrtc.PeerConnectionFactory;
import io.netty.bootstrap.Bootstrap;
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFactory;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.util.ResourceLeakDetector;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.log4j.Log4j2;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.MinecraftAuth;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import net.raphimc.minecraftauth.msa.model.MsaDeviceCode;
import net.raphimc.minecraftauth.msa.service.impl.DeviceCodeMsaAuthService;
import net.raphimc.minecraftauth.util.MinecraftAuth4To5Migrator;
import org.cloudburstmc.nbt.*;
import org.cloudburstmc.netty.channel.raknet.RakChannelFactory;
import org.cloudburstmc.netty.channel.raknet.config.RakChannelOption;
import org.cloudburstmc.netty.handler.codec.raknet.server.RakServerRateLimiter;
import org.cloudburstmc.protocol.bedrock.BedrockPeer;
import org.cloudburstmc.protocol.bedrock.BedrockPong;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodecHelper;
import org.cloudburstmc.protocol.bedrock.codec.v898.Bedrock_v898;
import org.cloudburstmc.protocol.bedrock.data.EncodingSettings;
import org.cloudburstmc.protocol.bedrock.data.definitions.BlockDefinition;
import org.cloudburstmc.protocol.bedrock.netty.initializer.BedrockChannelInitializer;
import org.cloudburstmc.protocol.common.DefinitionRegistry;
import org.cloudburstmc.proxypass.network.bedrock.jackson.ColorDeserializer;
import org.cloudburstmc.proxypass.network.bedrock.jackson.ColorSerializer;
import org.cloudburstmc.proxypass.network.bedrock.jackson.NbtDefinitionSerializer;
import org.cloudburstmc.proxypass.network.bedrock.nethernet.initializer.NetherNetBedrockChannelInitializer;
import org.cloudburstmc.proxypass.network.bedrock.session.Account;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyClientSession;
import org.cloudburstmc.proxypass.network.bedrock.session.ProxyServerSession;
import org.cloudburstmc.proxypass.network.bedrock.session.ServerAddress;
import org.cloudburstmc.proxypass.network.bedrock.session.UpstreamPacketHandler;
import org.cloudburstmc.proxypass.network.bedrock.util.NbtBlockDefinitionRegistry;
import org.cloudburstmc.proxypass.network.bedrock.util.UnknownBlockDefinitionRegistry;
import org.cloudburstmc.proxypass.ui.PacketInspector;
import org.cloudburstmc.proxypass.xbox.XboxSessionManager;

import java.awt.Color;
import java.awt.Desktop;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

@Log4j2
@Getter
public class ProxyPass {
    public static final ObjectMapper JSON_MAPPER;
    private static final SimpleModule MODULE = new SimpleModule("ProxyPass", Version.unknownVersion())
            .addSerializer(Color.class, new ColorSerializer())
            .addDeserializer(Color.class, new ColorDeserializer())
            .addSerializer(NbtBlockDefinitionRegistry.NbtBlockDefinition.class, new NbtDefinitionSerializer());
    public static final YAMLMapper YAML_MAPPER = (YAMLMapper) new YAMLMapper().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
    public static final String MINECRAFT_VERSION;

    public static final BedrockCodecHelper HELPER = Bedrock_v898.CODEC.createHelper();
    public static final BedrockCodec CODEC = Bedrock_v898.CODEC
        .toBuilder()
        .protocolVersion(898)
        .minecraftVersion("1.21.130")
        .helper(() -> HELPER).build();
        
    public static final int PROTOCOL_VERSION = CODEC.getProtocolVersion();
    private static final BedrockPong ADVERTISEMENT = new BedrockPong()
            .edition("MCPE")
            .gameType("Survival")
            .version(ProxyPass.MINECRAFT_VERSION)
            .protocolVersion(ProxyPass.PROTOCOL_VERSION)
            .motd("ProxyPass")
            .playerCount(0)
            .maximumPlayerCount(20)
            .subMotd("https://github.com/CloudburstMC/ProxyPass")
            .nintendoLimited(false);
    private static final DefaultPrettyPrinter PRETTY_PRINTER;
    public static Map<Integer, String> legacyIdMap = new HashMap<>();

    static {
        PRETTY_PRINTER = new DefaultPrettyPrinter() {
            @Override
            public DefaultPrettyPrinter createInstance() {
                return this;
            }

            @SuppressWarnings("NullableProblems")
            @Override
            public void writeObjectFieldValueSeparator(JsonGenerator generator) throws IOException {
                generator.writeRaw(": ");
            }
        };

        DefaultIndenter indenter = new DefaultIndenter("    ", "\n");
        PRETTY_PRINTER.indentArraysWith(indenter);
        PRETTY_PRINTER.indentObjectsWith(indenter);

        JSON_MAPPER = new ObjectMapper().registerModule(MODULE).disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).setDefaultPrettyPrinter(PRETTY_PRINTER);
        MINECRAFT_VERSION = CODEC.getMinecraftVersion();

        HELPER.setEncodingSettings(EncodingSettings.builder()
            .maxListSize(Integer.MAX_VALUE)
            .maxByteArraySize(Integer.MAX_VALUE)
            .maxNetworkNBTSize(Integer.MAX_VALUE)
            .maxItemNBTSize(Integer.MAX_VALUE)
            .maxStringLength(Integer.MAX_VALUE)
            .build());
    }

    private final AtomicBoolean running = new AtomicBoolean(true);

    private final NioEventLoopGroup eventLoopGroup = new NioEventLoopGroup();
    
    private final Set<Channel> clients = ConcurrentHashMap.newKeySet();
    @Getter(AccessLevel.NONE)
    private final Set<Class<?>> ignoredPackets = Collections.newSetFromMap(new IdentityHashMap<>());
    private Channel server;
    private int maxClients = 0;
    private boolean onlineMode = false;
    private boolean saveAuthDetails = false;
    @Setter
    private SocketAddress targetAddress;
    private InetSocketAddress proxyAddress;
    private Configuration configuration;
    private Path baseDir;
    private Path sessionsDir;
    private Path dataDir;
    private DefinitionRegistry<BlockDefinition> blockDefinitions;
    private DefinitionRegistry<BlockDefinition> blockDefinitionsHashed;
    private static Account account;
    private XboxSessionManager xboxSessionManager;

    public static void main(String[] args) {
        ResourceLeakDetector.setLevel(ResourceLeakDetector.Level.DISABLED);
        
        // dev.kastle.webrtc.logging.Logging.addLogSink(dev.kastle.webrtc.logging.Logging.Severity.ERROR, (severity, message) -> {
        //     System.out.println("[WebRTC Native] " + message);
        // });

        ProxyPass proxy = new ProxyPass();
        try {
            proxy.boot();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void boot() throws IOException {
        log.info("Loading configuration...");
        Path configPath = Paths.get(".").resolve("config.yml");
        if (Files.notExists(configPath) || !Files.isRegularFile(configPath)) {
            Files.copy(ProxyPass.class.getClassLoader().getResourceAsStream("config.yml"), configPath, StandardCopyOption.REPLACE_EXISTING);
        }

        configuration = Configuration.load(configPath);

        if (configuration.isEnableUi()) {
            log.info("Starting Packet Inspector UI...");
            PacketInspector.launchUI();
            log.info("Packet Inspector UI started");
        }

        proxyAddress = configuration.getProxy().getAddress();
        maxClients = configuration.getMaxClients();
        onlineMode = configuration.isOnlineMode();
        saveAuthDetails = configuration.isSaveAuthDetails();

        configuration.getIgnoredPackets().forEach(s -> {
            try {
                ignoredPackets.add(Class.forName("org.cloudburstmc.protocol.bedrock.packet." + s));
            } catch (ClassNotFoundException e) {
                log.warn("No packet with name {}", s);
            }
        });

        baseDir = Paths.get(".").toAbsolutePath();
        sessionsDir = baseDir.resolve("sessions");
        dataDir = baseDir.resolve("data");
        Files.createDirectories(sessionsDir);
        Files.createDirectories(dataDir);

        HttpClient client = null;
        if (onlineMode) {
            log.info("Online mode is enabled. Starting auth process...");
            try {
                client = MinecraftAuth.createHttpClient();
                account = getAuthenticatedAccount(saveAuthDetails, client);
                log.info("Successfully logged in as {}", account.authManager().getMinecraftMultiplayerToken().getCached().getDisplayName());
            } catch (Exception e) {
                log.error("Setting to offline mode due to failure to get login chain:", e);
                onlineMode = false;
            }
        }

        ServerAddress serverAddress = new ServerAddress(configuration.getDestination(), account, client);
        targetAddress = serverAddress.getAddress();

        Object object = this.loadGzipNBT("block_palette.nbt");

        if (object instanceof NbtMap map) {
            this.blockDefinitions = new NbtBlockDefinitionRegistry(map.getList("blocks", NbtType.COMPOUND), false);
            this.blockDefinitionsHashed = new NbtBlockDefinitionRegistry(map.getList("blocks", NbtType.COMPOUND), true);
        } else {
            this.blockDefinitions = this.blockDefinitionsHashed = new UnknownBlockDefinitionRegistry();
            log.warn(
                    "Failed to load block palette. Blocks will appear as runtime IDs in packet traces and creative_content.json!");
        }

        log.info("Loading server...");

        String listenerTransport = configuration.getProxy().getTransport();
        boolean isNetherNetListener = "nethernet".equalsIgnoreCase(listenerTransport);

        ADVERTISEMENT.ipv4Port(this.proxyAddress.getPort())
                .ipv6Port(this.proxyAddress.getPort());

        if (isNetherNetListener) {
            NetherNetServerSignaling signaling = null;

            if (configuration.isBroadcastSession() && onlineMode) {
                try {
                    log.info("Starting Xbox Session Broadcast...");
                    this.xboxSessionManager = new XboxSessionManager(account.authManager(), client);
                    this.xboxSessionManager.setupConnection();

                    signaling = new NetherNetXboxSignaling(
                        this.xboxSessionManager.getNetherNetId(), 
                        account.authManager().getMinecraftSession().getUpToDate().getAuthorizationHeader()
                    );
                    log.info("Using Xbox Signaling for incoming connections");
                } catch (Exception e) {
                    log.error("Failed to start Xbox Session", e);
                    log.info("Falling back to Discovery Signaling for incoming connections");
                }
            }

            if (signaling == null) {
                signaling = new NetherNetDiscoverySignaling();
                signaling.setAdvertisementData(
                    new NetherNetServerSignaling.PongData.Builder()
                        .setServerName("NetherNet Server")
                        .setLevelName("World")
                        .setGameType(0)
                        .setPlayerCount(0)
                        .setMaxPlayerCount(10)
                        .build()
                );
            }

            this.server = new ServerBootstrap()
                    .group(this.eventLoopGroup)
                    .channelFactory(NetherNetChannelFactory.server(new PeerConnectionFactory(), signaling))
                    .childHandler(new NetherNetBedrockChannelInitializer<ProxyServerSession>() {
                        @Override
                        protected ProxyServerSession createSession0(BedrockPeer peer, int subClientId) {
                            return new ProxyServerSession(peer, subClientId, ProxyPass.this);
                        }

                        @Override
                        protected void initSession(ProxyServerSession session) {
                            session.setPacketHandler(new UpstreamPacketHandler(session, ProxyPass.this, account));
                        }
                    })
                    .bind(this.proxyAddress)
                    .awaitUninterruptibly()
                    .channel();

            if (this.xboxSessionManager != null) {
                try {
                    this.xboxSessionManager.startSession();
                    log.info("Xbox Session Broadcast started successfully.");
                } catch (Exception e) {
                    log.error("Failed to start Xbox Session", e);
                }
            }
            
            log.info("NetherNet server started on {}", proxyAddress);

        } else {
            this.server = new ServerBootstrap()
                    .group(this.eventLoopGroup)
                    .channelFactory(RakChannelFactory.server(NioDatagramChannel.class))
                    .option(RakChannelOption.RAK_ADVERTISEMENT, ADVERTISEMENT.toByteBuf())
                    .option(RakChannelOption.RAK_IP_DONT_FRAGMENT, true)
                    .childHandler(new BedrockChannelInitializer<ProxyServerSession>() {
                        @Override
                        protected ProxyServerSession createSession0(BedrockPeer peer, int subClientId) {
                            return new ProxyServerSession(peer, subClientId, ProxyPass.this);
                        }

                        @Override
                        protected void initSession(ProxyServerSession session) {
                            session.setPacketHandler(new UpstreamPacketHandler(session, ProxyPass.this, account));
                        }
                    })
                    .bind(this.proxyAddress)
                    .awaitUninterruptibly()
                    .channel();
            this.server.pipeline().remove(RakServerRateLimiter.NAME);
            log.info("Bedrock server started on {}", proxyAddress);
        }

        loop();
    }

    public void newClient(SocketAddress socketAddress, Consumer<ProxyClientSession> sessionConsumer) {
        String transport = this.configuration.getDestination().getTransport();
        boolean isNetherNet = "nethernet".equalsIgnoreCase(transport);

        ChannelFactory<? extends Channel> channelFactory;

        if (isNetherNet) {
            NetherNetClientSignaling signaling;

            if (socketAddress instanceof NetherNetAddress) {
                signaling = new NetherNetXboxSignaling(
                    account.authManager().getMinecraftSession().getCached().getAuthorizationHeader()
                );
            } else {
                signaling = new NetherNetDiscoverySignaling();
            }

            channelFactory = NetherNetChannelFactory.client(new PeerConnectionFactory(), signaling);
        } else {
            channelFactory = RakChannelFactory.client(NioDatagramChannel.class);
        }

        Bootstrap bootstrap = new Bootstrap()
                .group(this.eventLoopGroup)
                .channelFactory(channelFactory);

        if (!isNetherNet) {
            bootstrap
                .option(RakChannelOption.RAK_PROTOCOL_VERSION, ProxyPass.CODEC.getRaknetProtocolVersion())
                .option(RakChannelOption.RAK_COMPATIBILITY_MODE, true)
                .option(RakChannelOption.RAK_IP_DONT_FRAGMENT, true)
                .option(RakChannelOption.RAK_MTU_SIZES, new Integer[]{1492, 1200, 576})
                .option(RakChannelOption.RAK_CLIENT_INTERNAL_ADDRESSES, 20)
                .option(RakChannelOption.RAK_TIME_BETWEEN_SEND_CONNECTION_ATTEMPTS_MS, 500)
                .option(RakChannelOption.RAK_GUID, ThreadLocalRandom.current().nextLong())
                .handler(new BedrockChannelInitializer<ProxyClientSession>() {
                    @Override
                    protected ProxyClientSession createSession0(BedrockPeer peer, int subClientId) {
                        return new ProxyClientSession(peer, subClientId, ProxyPass.this);
                    }

                    @Override
                    protected void initSession(ProxyClientSession session) {
                        sessionConsumer.accept(session);
                    }
                });
        } else {
            bootstrap
                .handler(new NetherNetBedrockChannelInitializer<ProxyClientSession>() {
                    @Override
                    protected ProxyClientSession createSession0(BedrockPeer peer, int subClientId) {
                        return new ProxyClientSession(peer, subClientId, ProxyPass.this);
                    }

                    @Override
                    protected void initSession(ProxyClientSession session) {
                        sessionConsumer.accept(session);
                    }
                });
        }

        Channel channel = bootstrap
                .connect(socketAddress)
                .awaitUninterruptibly()
                .channel();

        this.clients.add(channel);
    }

    private void loop() {
        while (running.get()) {
            try {
                synchronized (this) {
                    this.wait();
                }
            } catch (InterruptedException e) {
                // ignore
            }

        }

        this.clients.forEach(Channel::disconnect);
        this.server.disconnect();
        
        this.eventLoopGroup.shutdownGracefully();
    }

    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            synchronized (this) {
                this.notify();
            }
        }
    }
    
    public void saveCompressedNBT(String dataName, Object dataTag) {
        Path path = dataDir.resolve(dataName + ".nbt");
        try (OutputStream outputStream = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
             NBTOutputStream nbtOutputStream = NbtUtils.createGZIPWriter(outputStream)) {
            nbtOutputStream.writeTag(dataTag);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveNBT(String dataName, Object dataTag) {
        Path path = dataDir.resolve(dataName + ".dat");
        try (OutputStream outputStream = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
             NBTOutputStream nbtOutputStream = NbtUtils.createNetworkWriter(outputStream)) {
            nbtOutputStream.writeTag(dataTag);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Object loadNBT(String dataName) {
        Path path = dataDir.resolve(dataName + ".dat");
        try (InputStream inputStream = Files.newInputStream(path);
            NBTInputStream nbtInputStream = NbtUtils.createNetworkReader(inputStream)) {
            return nbtInputStream.readTag();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public Object loadGzipNBT(String dataName) {
        Path path = dataDir.resolve(dataName);
        try (InputStream inputStream = Files.newInputStream(path);
            NBTInputStream nbtInputStream = NbtUtils.createGZIPReader(inputStream)) {
            return nbtInputStream.readTag();
        } catch (IOException e) {
            return null;
        }
    }

    public void saveJson(String name, Object object) {
        Path outPath = dataDir.resolve(name);
        try (OutputStream outputStream = Files.newOutputStream(outPath, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE)) {
            ProxyPass.JSON_MAPPER.writer(PRETTY_PRINTER).writeValue(outputStream, object);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public <T> T loadJson(String name, TypeReference<T> reference) {
        Path path = dataDir.resolve(name);
        try (InputStream inputStream = Files.newInputStream(path, StandardOpenOption.READ)) {
            return ProxyPass.JSON_MAPPER.readValue(inputStream, reference);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public void saveMojangson(String name, NbtMap nbt) {
        Path outPath = dataDir.resolve(name);
        try {
            Files.writeString(outPath, nbt.toString(), StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.CREATE);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public boolean isIgnoredPacket(Class<?> clazz) {
        return this.configuration.isInvertIgnoredList() != this.ignoredPackets.contains(clazz);
    }

    public boolean isFull() {
        return maxClients > 0 && this.clients.size() >= maxClients;
    }

    private Account getAuthenticatedAccount(boolean saveAuthDetails, HttpClient client) throws Exception {
        Path authPath = Paths.get(".").resolve("auth.json");
        BedrockAuthManager.Builder authManagerBuilder = BedrockAuthManager.create(client, CODEC.getMinecraftVersion());
        Account account;

        if (Files.exists(authPath) && Files.isRegularFile(authPath) && saveAuthDetails) {
            String accountString = new String(Files.readAllBytes(authPath), StandardCharsets.UTF_8);
            JsonObject accountJson = JsonParser.parseString(accountString).getAsJsonObject();
            if (accountJson.has("mcChain")) {
                accountJson = MinecraftAuth4To5Migrator.migrateBedrockSave(accountJson);
            }
            account = new Account(accountJson, client, CODEC.getMinecraftVersion());
            account.refresh();
            Files.write(authPath, account.toJson().toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);

            return account;
        }

        BedrockAuthManager authManager = authManagerBuilder.login(DeviceCodeMsaAuthService::new, new Consumer<MsaDeviceCode>() {
            @Override
            public void accept(MsaDeviceCode msaDeviceCode) {
                log.info("Go to " + msaDeviceCode.getVerificationUri());
                log.info("Enter code " + msaDeviceCode.getUserCode());

                if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                    try {
                        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
                        clipboard.setContents(new StringSelection(msaDeviceCode.getUserCode()), null);
                        log.info("Copied code to clipboard");
                        Desktop.getDesktop().browse(new URI(msaDeviceCode.getVerificationUri()));
                    } catch (IOException | URISyntaxException e) {
                        log.error("Failed to open browser", e);
                    }
                }
            }
        });
        account = new Account(authManager);
        account.refresh();

        if (saveAuthDetails) {
            Files.write(authPath, account.toJson().toString().getBytes(StandardCharsets.UTF_8), StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        }

        return account;
    }
}