package org.cloudburstmc.proxypass.xbox;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import io.netty.bootstrap.Bootstrap;
import io.netty.channel.*;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioSocketChannel;
import io.netty.handler.codec.http.DefaultHttpHeaders;
import io.netty.handler.codec.http.HttpClientCodec;
import io.netty.handler.codec.http.HttpObjectAggregator;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslContext;
import io.netty.handler.ssl.SslContextBuilder;
import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.bedrock.BedrockAuthManager;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.network.bedrock.request.SessionDirectoryRequest;
import org.cloudburstmc.proxypass.network.bedrock.request.SessionHandleRequest;
import org.cloudburstmc.proxypass.network.bedrock.request.UserPresenceRequest;
import org.cloudburstmc.proxypass.network.bedrock.request.model.sessiondirectory.SessionHandleData;
import org.cloudburstmc.proxypass.network.bedrock.request.model.sessiondirectory.SessionRequestData;

import java.net.URI;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

@Log4j2
@RequiredArgsConstructor
public class XboxSessionManager {

    private static final String SCID = "4fc10100-5f7a-4470-899b-280835760c07";
    private static final String TEMPLATE = "MinecraftLobby";
    private static final Gson GSON = new Gson();

    private final BedrockAuthManager authManager;
    private final HttpClient httpClient;
    private final ScheduledExecutorService scheduler = Executors.newSingleThreadScheduledExecutor();

    private String rtaConnectionId;
    private String rtaSubscriptionId;
    private String sessionId;

    @Getter
    private long netherNetId;

    public void setupConnection() throws Exception {
        String xuid = authManager.getMinecraftMultiplayerToken().getUpToDate().getXuid();

        this.rtaConnectionId = connectRtaAndGetId(
            authManager.getXboxLiveXstsToken().getUpToDate().getAuthorizationHeader(),
            xuid
        ).join();
        this.rtaSubscriptionId = UUID.randomUUID().toString();
        log.info("RTA Connected. ConnID: {}, SubID: {}", rtaConnectionId, rtaSubscriptionId);

        this.netherNetId = ThreadLocalRandom.current().nextLong(1, Long.MAX_VALUE);
        log.info("Generated NetherNet ID: {}", netherNetId);
    }

    public void startSession() throws Exception {
        String xuid = authManager.getMinecraftMultiplayerToken().getUpToDate().getXuid();

        this.sessionId = UUID.randomUUID().toString();

        int protocol = ProxyPass.CODEC.getProtocolVersion();
        String version = ProxyPass.CODEC.getMinecraftVersion();

        SessionRequestData sessionData = SessionRequestData.builder()
                .connectionId(rtaConnectionId)
                .subscriptionId(rtaSubscriptionId)
                .xuid(xuid)
                .netherNetId(netherNetId)
                .maxPlayers(20)
                .currentPlayers(1)
                .sessionName("ProxyPass Server")
                .version(version)
                .protocol(protocol)
                .build();

        SessionDirectoryRequest directoryRequest = new SessionDirectoryRequest(
                authManager.getXboxLiveXstsToken().getUpToDate(), SCID, TEMPLATE, sessionId, sessionData
        );
        httpClient.execute(directoryRequest);

        SessionHandleData handleData = SessionHandleData.builder()
                .scid(SCID)
                .templateName(TEMPLATE)
                .sessionName(sessionId)
                .build();
        
        httpClient.execute(new SessionHandleRequest(authManager.getXboxLiveXstsToken().getUpToDate(), handleData));

        httpClient.execute(new UserPresenceRequest(
            authManager.getXboxLiveXstsToken().getUpToDate(), 
            xuid
        ));

        scheduler.scheduleAtFixedRate(() -> {
            try {
                httpClient.execute(new UserPresenceRequest(authManager.getXboxLiveXstsToken().getUpToDate(), xuid));
                httpClient.execute(new SessionDirectoryRequest(authManager.getXboxLiveXstsToken().getUpToDate(), SCID, TEMPLATE, sessionId, sessionData));
            } catch (Exception e) {
                log.error("Failed to heartbeat session", e);
            }
        }, 30, 30, TimeUnit.SECONDS);
    }

    private CompletableFuture<String> connectRtaAndGetId(String xstsToken, String xuid) {
        CompletableFuture<String> future = new CompletableFuture<>();
        URI uri = URI.create("wss://rta.xboxlive.com/connect");
        EventLoopGroup group = new NioEventLoopGroup();

        try {
            SslContext sslCtx = SslContextBuilder.forClient().build();
            DefaultHttpHeaders headers = new DefaultHttpHeaders();
            headers.add("Authorization", xstsToken);

            WebSocketClientHandshaker handshaker = WebSocketClientHandshakerFactory.newHandshaker(
                    uri, WebSocketVersion.V13, null, false, headers
            );

            Bootstrap b = new Bootstrap();
            b.group(group)
                    .channel(NioSocketChannel.class)
                    .handler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        protected void initChannel(SocketChannel ch) {
                            ChannelPipeline p = ch.pipeline();
                            p.addLast(sslCtx.newHandler(ch.alloc(), uri.getHost(), 443));
                            p.addLast(new HttpClientCodec(), new HttpObjectAggregator(8192));
                            p.addLast(new WebSocketClientProtocolHandler(handshaker));
                            p.addLast(new SimpleChannelInboundHandler<TextWebSocketFrame>() {
                                @Override
                                public void userEventTriggered(ChannelHandlerContext ctx, Object evt) throws Exception {
                                    if (evt == WebSocketClientProtocolHandler.ClientHandshakeStateEvent.HANDSHAKE_COMPLETE) {
                                        // Subscribe to connections
                                        ctx.writeAndFlush(new TextWebSocketFrame("[1,1,\"https://sessiondirectory.xboxlive.com/connections/\"]"));
                                    } else {
                                        super.userEventTriggered(ctx, evt);
                                    }
                                }

                                @Override
                                protected void channelRead0(ChannelHandlerContext ctx, TextWebSocketFrame frame) {
                                    String text = frame.text();
                                    try {
                                        JsonArray arr = GSON.fromJson(text, JsonArray.class);
                                        if (arr.size() >= 5) {
                                            JsonObject data = arr.get(4).getAsJsonObject();
                                            if (data.has("ConnectionId")) {
                                                String connId = data.get("ConnectionId").getAsString();
                                                future.complete(connId);
                                                String friendSub = String.format("[1,2,\"https://social.xboxlive.com/users/xuid(%s)/friends\"]", xuid);
                                                ctx.writeAndFlush(new TextWebSocketFrame(friendSub));
                                            }
                                        }
                                    } catch (Exception ignored) {}
                                }
                                @Override
                                public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) {
                                    if (!future.isDone()) future.completeExceptionally(cause);
                                    ctx.close();
                                }
                            });
                        }
                    });
            b.connect(uri.getHost(), 443).sync();
        } catch (Exception e) {
            future.completeExceptionally(e);
        }
        return future;
    }

    public void stop() {
        scheduler.shutdown();
    }
}
