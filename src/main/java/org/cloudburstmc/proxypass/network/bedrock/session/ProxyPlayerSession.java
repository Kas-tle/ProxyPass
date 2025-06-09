package org.cloudburstmc.proxypass.network.bedrock.session;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.network.bedrock.logging.SessionLogger;

import java.nio.file.Path;
import java.security.KeyPair;

@Log4j2
@Getter
public class ProxyPlayerSession {
    private final ProxyServerSession upstream;
    private final ProxyClientSession downstream;
    private final ProxyPass proxy;
    private final AuthData authData;
    private final long timestamp = System.currentTimeMillis();
    @Getter(AccessLevel.PACKAGE)
    private final KeyPair proxyKeyPair;
    private final Path dataPath;
    private final PackDownloader packDownloader;
    private volatile boolean closed = false;

    public final SessionLogger logger;

    public ProxyPlayerSession(ProxyServerSession upstream, ProxyClientSession downstream, ProxyPass proxy, AuthData authData, KeyPair proxyKeyPair) {
        this.upstream = upstream;
        this.downstream = downstream;
        this.proxy = proxy;
        this.authData = authData;
        this.proxyKeyPair = proxyKeyPair;
        this.dataPath = proxy.getSessionsDir().resolve(this.authData.getDisplayName() + '-' + timestamp);
//        this.upstream.addDisconnectHandler(reason -> {
//            if (reason != DisconnectReason.DISCONNECTED) {
//                this.downstream.disconnect();
//            }
//        });
        this.packDownloader = new PackDownloader(this.dataPath);
        this.logger = new SessionLogger(
                proxy,
                this.dataPath
        );
        logger.start();
    }
}
