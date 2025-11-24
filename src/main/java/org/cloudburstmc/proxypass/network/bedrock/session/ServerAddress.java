package org.cloudburstmc.proxypass.network.bedrock.session;

import lombok.Getter;
import lombok.extern.log4j.Log4j2;
import net.lenni0451.commons.httpclient.HttpClient;
import net.raphimc.minecraftauth.bedrock.model.MinecraftSession;
import org.cloudburstmc.proxypass.Configuration.Destination;
import org.cloudburstmc.proxypass.network.bedrock.request.ExperienceRequest;
import org.cloudburstmc.proxypass.network.bedrock.request.FeaturedServersRequest;
import org.cloudburstmc.proxypass.network.bedrock.request.model.Experience;
import org.cloudburstmc.proxypass.network.bedrock.request.model.FeaturedServers;
import org.cloudburstmc.proxypass.network.bedrock.request.model.featuredservers.FeaturedServer;

import java.io.IOException;
import java.net.InetSocketAddress;

@Log4j2
public class ServerAddress {
    @Getter
    private InetSocketAddress address;
    private String host;
    private int port;

    public ServerAddress(Destination destination, Account account, HttpClient client) {
        this.host = destination.getHost();
        this.port = destination.getPort();
        String experienceId = destination.getExperienceId();
        String featuredServerTitle = destination.getFeaturedServerTitle();

        log.info("Resolving server address for destination: host={}, port={}, experienceId={}, featuredServerTitle={}",
                this.host, this.port, experienceId, featuredServerTitle);

        if (featuredServerTitle != null && client != null) {
            try {
                MinecraftSession session = account.authManager().getMinecraftSession().getUpToDate();
                FeaturedServers servers = client.executeAndHandle(new FeaturedServersRequest(session));
                for (FeaturedServer server : servers.getData().getItems()) {
                    if (server.getTitle().getNeutral().equals(featuredServerTitle)) {
                        if (server.getDisplayProperties().getExperienceId() != null) {
                            experienceId = server.getDisplayProperties().getExperienceId();
                            log.info("Resolved featured server '{}' to properties {}", featuredServerTitle,
                                    experienceId);
                            break;
                        }
                        if (server.getDisplayProperties().getUrl() != null) {
                            this.host = server.getDisplayProperties().getUrl();
                            this.port = server.getDisplayProperties().getPort();
                            log.info("Resolved featured server '{}' to {}:{}", featuredServerTitle, this.host,
                                    this.port);
                            break;
                        }
                    }
                }
            } catch (IOException e) {
                log.error("Failed to fetch featured servers", e);
            }
        }

        if (experienceId != null && client != null) {
            try {
                MinecraftSession session = account.authManager().getMinecraftSession().getUpToDate();
                Experience experience = client.executeAndHandle(new ExperienceRequest(session, experienceId));
                if (!experience.getResult().getNetworkProtocol().equals("Default")) {
                    throw new IllegalArgumentException("Unsupported network protocol: " + experience.getResult().getNetworkProtocol());
                }
                this.host = experience.getResult().getIpV4Address();
                this.port = experience.getResult().getPort();
                log.info("Resolved experience ID {} to {}:{}", experienceId, this.host, this.port);
            } catch (IOException e) {
                log.error("Failed to fetch experience", e);
            }
        }

        try {
            this.address = new InetSocketAddress(this.host, this.port);
        } catch (IllegalArgumentException e) {
            log.error(
                    "Could not find a server address for the provided destination: host={}, port={}, experienceId={}, featuredServerTitle={} ",
                    this.host, this.port, experienceId, featuredServerTitle, e);
        }
    }
}
