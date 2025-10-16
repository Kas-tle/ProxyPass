package org.cloudburstmc.proxypass.network.bedrock.session;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.raphimc.minecraftauth.step.bedrock.StepMCChain.MCChain;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.auth.AuthType;
import org.cloudburstmc.protocol.bedrock.data.auth.CertificateChainPayload;
import org.cloudburstmc.protocol.bedrock.packet.BedrockPacketHandler;
import org.cloudburstmc.protocol.bedrock.packet.LoginPacket;
import org.cloudburstmc.protocol.bedrock.packet.NetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.PlayStatusPacket;
import org.cloudburstmc.protocol.bedrock.packet.RequestNetworkSettingsPacket;
import org.cloudburstmc.protocol.bedrock.packet.ResourcePackClientResponsePacket;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
import org.cloudburstmc.protocol.bedrock.util.JsonUtils;
import org.cloudburstmc.protocol.common.PacketSignal;
import org.cloudburstmc.proxypass.ProxyPass;
import org.cloudburstmc.proxypass.network.bedrock.util.ForgeryUtils;
import org.jose4j.json.JsonUtil;
import org.jose4j.json.internal.json_simple.JSONObject;
import org.jose4j.jws.JsonWebSignature;
import org.jose4j.lang.JoseException;

import java.security.KeyPair;
import java.security.PublicKey;
import java.security.interfaces.ECPublicKey;
import java.util.List;
import java.util.Map;

@Log4j2
@RequiredArgsConstructor
public class UpstreamPacketHandler implements BedrockPacketHandler {
    private final ProxyServerSession session;
    private final ProxyPass proxy;
    private final Account account;
    private JSONObject skinData;
    private JSONObject extraData;
    private List<String> chainData;
    private AuthData authData;
    private ProxyPlayerSession player;

    private static ECPublicKey mojangPublicKey;
    private static List<String> onlineLoginChain;

    private static boolean verifyJwt(String jwt, PublicKey key) throws JoseException {
        JsonWebSignature jws = new JsonWebSignature();
        jws.setKey(key);
        jws.setCompactSerialization(jwt);

        return jws.verifySignature();
    }

    @Override
    public PacketSignal handle(RequestNetworkSettingsPacket packet) {
        int protocolVersion = packet.getProtocolVersion();

        if (protocolVersion != ProxyPass.PROTOCOL_VERSION) {
            PlayStatusPacket status = new PlayStatusPacket();
            if (protocolVersion > ProxyPass.PROTOCOL_VERSION) {
                status.setStatus(PlayStatusPacket.Status.LOGIN_FAILED_SERVER_OLD);
            } else {
                status.setStatus(PlayStatusPacket.Status.LOGIN_FAILED_CLIENT_OLD);
            }

            session.sendPacketImmediately(status);
            return PacketSignal.HANDLED;
        }
        session.setCodec(ProxyPass.CODEC);

        NetworkSettingsPacket networkSettingsPacket = new NetworkSettingsPacket();
        networkSettingsPacket.setCompressionThreshold(0);
        networkSettingsPacket.setCompressionAlgorithm(PacketCompressionAlgorithm.ZLIB);

        session.sendPacketImmediately(networkSettingsPacket);
        session.setCompression(PacketCompressionAlgorithm.ZLIB);
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(LoginPacket packet) {
        try {
            ChainValidationResult chain = EncryptionUtils.validatePayload(packet.getAuthPayload());

            JsonNode payload = ProxyPass.JSON_MAPPER.valueToTree(chain.rawIdentityClaims());

            ECPublicKey identityPublicKey;

            if (chain.identityClaims().identityPublicKey != null) {
                identityPublicKey = EncryptionUtils.parseKey(chain.identityClaims().identityPublicKey);
            } else if (payload.get("identityPublicKey") != null && payload.get("identityPublicKey").getNodeType() == JsonNodeType.STRING) {
                identityPublicKey = EncryptionUtils.parseKey(payload.get("identityPublicKey").textValue());
            } else {
                throw new RuntimeException("Identity Public Key was not found!");
            }

            String clientJwt = packet.getClientJwt();
            verifyJwt(clientJwt, identityPublicKey);
            JsonWebSignature jws = new JsonWebSignature();
            jws.setCompactSerialization(clientJwt);

            skinData = new JSONObject(JsonUtil.parseJson(jws.getUnverifiedPayload()));

            if (skinData.get("ServerAddress") != null) {
                session.setConnectedViaAddress(skinData.get("ServerAddress").toString());
            }

            if (account == null) {
                if (payload.get("extraData") == null) {
                    throw new RuntimeException("New R21U9 login for offline mode is not yet supported!");
                }

                if (payload.get("extraData").getNodeType() != JsonNodeType.OBJECT) {
                    throw new RuntimeException("AuthData was not found!");
                }

                extraData = new JSONObject(JsonUtils.childAsType(chain.rawIdentityClaims(), "extraData", Map.class));

                this.authData = new AuthData(chain.identityClaims().extraData.displayName,
                    chain.identityClaims().extraData.identity, chain.identityClaims().extraData.xuid);
                chainData = ((CertificateChainPayload) packet.getAuthPayload()).getChain();

                initializeOfflineProxySession();
            } else {
                MCChain mcChain = account.bedrockSession().getMcChain();
                this.authData = new AuthData(mcChain.getDisplayName(), mcChain.getId(), mcChain.getXuid());

                initializeOnlineProxySession();
            }
        } catch (Exception e) {
            session.disconnect("disconnectionScreen.internalError.cantConnect");
            throw new RuntimeException("Unable to complete login", e);
        }
        return PacketSignal.HANDLED;
    }

    @Override
    public PacketSignal handle(ResourcePackClientResponsePacket packet) {
        if (!this.proxy.getConfiguration().isDownloadPacks()) {
            return PacketSignal.UNHANDLED;
        }
        if (packet.getStatus() != ResourcePackClientResponsePacket.Status.COMPLETED) {
            return PacketSignal.UNHANDLED;
        };

        player.getPackDownloader().processPacks();

        return PacketSignal.UNHANDLED;
    }

    private void initializeOfflineProxySession() {
        log.debug("Initializing proxy session");
        this.proxy.newClient(this.proxy.getTargetAddress(), downstream -> {
            downstream.setCodec(ProxyPass.CODEC);
            downstream.setSendSession(this.session);
            this.session.setSendSession(downstream);

            ProxyPlayerSession proxySession = new ProxyPlayerSession(
                this.session, 
                downstream, 
                this.proxy, 
                this.authData, 
                EncryptionUtils.createKeyPair());
            this.player = proxySession;

            downstream.setPlayer(proxySession);
            this.session.setPlayer(proxySession);

            try {
                String jwt = chainData.get(chainData.size() - 1);
                JsonWebSignature jws = new JsonWebSignature();
                jws.setCompactSerialization(jwt);
                player.getLogger().saveJson("chainData", new JSONObject(JsonUtil.parseJson(jws.getUnverifiedPayload())));
                player.getLogger().saveJson("skinData", this.skinData);
            } catch (Exception e) {
                log.error("JSON output error: " + e.getMessage(), e);
            }
            String authData = ForgeryUtils.forgeOfflineAuthData(proxySession.getProxyKeyPair(), extraData);
            String skinData = ForgeryUtils.forgeOfflineSkinData(proxySession.getProxyKeyPair(), this.skinData);
            chainData.remove(chainData.size() - 1);
            chainData.add(authData);

            LoginPacket login = new LoginPacket();
            login.setClientJwt(skinData);
            login.setAuthPayload(new CertificateChainPayload(chainData, AuthType.FULL));
            login.setProtocolVersion(ProxyPass.PROTOCOL_VERSION);

            downstream.setPacketHandler(new DownstreamInitialPacketHandler(downstream, proxySession, this.proxy, login));
            downstream.setLogging(true);

            RequestNetworkSettingsPacket packet = new RequestNetworkSettingsPacket();
            packet.setProtocolVersion(ProxyPass.PROTOCOL_VERSION);
            downstream.sendPacketImmediately(packet);
            this.player.logger.logPacket(this.session, packet, true);

            //SkinUtils.saveSkin(proxySession, this.skinData);
        });
    }

    private void initializeOnlineProxySession() {
        log.debug("Initializing proxy session");
        this.proxy.newClient(this.proxy.getTargetAddress(), downstream -> {
            MCChain mcChain = account.bedrockSession().getMcChain();
            try {
                if (mojangPublicKey == null) {
                    mojangPublicKey = ForgeryUtils.forgeMojangPublicKey();
                }
                if (onlineLoginChain == null) {
                    onlineLoginChain = ForgeryUtils.forgeOnlineAuthData(mcChain, mojangPublicKey);
                }
            } catch (Exception e) {
                log.error("Failed to get login chain", e);
            }

            downstream.setCodec(ProxyPass.CODEC);
            downstream.setSendSession(this.session);
            this.session.setSendSession(downstream);

            ProxyPlayerSession proxySession = new ProxyPlayerSession(
                this.session, 
                downstream, 
                this.proxy, 
                this.authData, 
                new KeyPair(mcChain.getPublicKey(), mcChain.getPrivateKey()));
            this.player = proxySession;

            downstream.setPlayer(proxySession);
            this.session.setPlayer(proxySession);

            String skinData = ForgeryUtils.forgeOnlineSkinData(account, this.skinData, this.proxy.getTargetAddress());

            try {
                player.getLogger().saveJson("skinData", this.skinData);
            } catch (Exception e) {
                log.error("JSON output error: " + e.getMessage(), e);
            }

            LoginPacket login = new LoginPacket();
            login.setClientJwt(skinData);
            login.setAuthPayload(new CertificateChainPayload(onlineLoginChain, AuthType.FULL));
            login.setProtocolVersion(ProxyPass.PROTOCOL_VERSION);

            downstream.setPacketHandler(new DownstreamInitialPacketHandler(downstream, proxySession, this.proxy, login));
            downstream.setLogging(true);

            RequestNetworkSettingsPacket packet = new RequestNetworkSettingsPacket();
            packet.setProtocolVersion(ProxyPass.PROTOCOL_VERSION);
            downstream.sendPacketImmediately(packet);
            this.player.logger.logPacket(this.session, packet, true);

            //SkinUtils.saveSkin(proxySession, this.skinData);
        });
    }

    @Override
    public void onDisconnect(CharSequence reason) {
        if (this.session.getSendSession().isConnected()) {
            this.session.getSendSession().disconnect(reason);
        }
    }
}
