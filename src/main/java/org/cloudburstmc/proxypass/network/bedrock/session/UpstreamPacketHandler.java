package org.cloudburstmc.proxypass.network.bedrock.session;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.raphimc.minecraftauth.bedrock.model.MinecraftMultiplayerToken;
import org.cloudburstmc.protocol.bedrock.codec.BedrockCodec;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.data.auth.*;
import org.cloudburstmc.protocol.bedrock.packet.*;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult;
import org.cloudburstmc.protocol.bedrock.util.ChainValidationResult.IdentityData;
import org.cloudburstmc.protocol.bedrock.util.EncryptionUtils;
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
    private static AuthPayload authPayload;

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
            status.setStatus(protocolVersion > ProxyPass.PROTOCOL_VERSION 
                ? PlayStatusPacket.Status.LOGIN_FAILED_SERVER_OLD 
                : PlayStatusPacket.Status.LOGIN_FAILED_CLIENT_OLD);

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
            ECPublicKey identityPublicKey;

            switch (packet.getAuthPayload()) {
                case DualPayload p -> {
                    identityPublicKey = EncryptionUtils.parseKey(chain.identityClaims().identityPublicKey);
                    chainData = p.getChain();
                }
                case TokenPayload p -> {
                    identityPublicKey = EncryptionUtils.parseKey(chain.identityClaims().identityPublicKey);
                }
                case CertificateChainPayload p -> {
                    JsonNode payload = ProxyPass.JSON_MAPPER.valueToTree(chain.rawIdentityClaims());
                    identityPublicKey = EncryptionUtils.parseKey(payload.get("identityPublicKey").textValue());
                    chainData = p.getChain();
                }
                default -> throw new IllegalStateException("Unexpected value: " + packet.getAuthPayload());
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
                IdentityData identityData = chain.identityClaims().extraData;
                this.authData = new AuthData(identityData.displayName, identityData.identity, identityData.xuid);
            } else {
                MinecraftMultiplayerToken token = account.authManager().getMinecraftMultiplayerToken().getCached();
                this.authData = new AuthData(token.getDisplayName(), token.getUuid(), token.getXuid());
            }
            
            initializeProxySession();

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
        }

        player.getPackDownloader().processPacks();
        return PacketSignal.UNHANDLED;
    }

    private void initializeProxySession() {
        log.debug("Initializing proxy session");

        this.proxy.newClient(this.proxy.getTargetAddress(), downstream -> {
            BedrockCodec.Builder codecBuilder = ProxyPass.CODEC.toBuilder();
            if (this.proxy.getConfiguration().isForceCertificateChain()) {
                codecBuilder = codecBuilder.updateSerializer(LoginPacket.class, ProxyPass.CERTIFICATE_PRIORITY_SERIALIZER);
            }
            downstream.setCodec(codecBuilder.build());

            downstream.setSendSession(this.session);
            this.session.setSendSession(downstream);

            KeyPair sessionKeyPair = (account != null) 
                ? account.authManager().getSessionKeyPair() 
                : EncryptionUtils.createKeyPair();

            ProxyPlayerSession proxySession = new ProxyPlayerSession(
                this.session,
                downstream,
                this.proxy,
                this.authData,
                sessionKeyPair
            );
            this.player = proxySession;

            downstream.setPlayer(proxySession);
            this.session.setPlayer(proxySession);

            LoginPacket login = prepareLoginPacket(proxySession);

            downstream.setPacketHandler(new DownstreamInitialPacketHandler(downstream, proxySession, this.proxy, login));
            downstream.setLogging(true);

            RequestNetworkSettingsPacket packet = new RequestNetworkSettingsPacket();
            packet.setProtocolVersion(ProxyPass.PROTOCOL_VERSION);
            downstream.sendPacketImmediately(packet);
            this.player.getLogger().logPacket(this.session, packet, true);
        });
    }

    private LoginPacket prepareLoginPacket(ProxyPlayerSession proxySession) {
        String jwtSkinData;
        AuthPayload payload;

        if (account == null) {
            try {
                String jwt = chainData.get(chainData.size() - 1);
                JsonWebSignature jws = new JsonWebSignature();
                jws.setCompactSerialization(jwt);
                player.getLogger().saveJson("chainData", new JSONObject(JsonUtil.parseJson(jws.getUnverifiedPayload())));
                player.getLogger().saveJson("skinData", this.skinData);
            } catch (Exception e) {
                log.error("JSON output error: " + e.getMessage(), e);
            }

            String forgedAuth = ForgeryUtils.forgeOfflineAuthData(proxySession.getProxyKeyPair(), extraData);
            jwtSkinData = ForgeryUtils.forgeOfflineSkinData(proxySession.getProxyKeyPair(), this.skinData);
            
            chainData.remove(chainData.size() - 1);
            chainData.add(forgedAuth);
            payload = new CertificateChainPayload(chainData, AuthType.FULL);

        } else {
            try {
                if (mojangPublicKey == null) {
                    mojangPublicKey = ForgeryUtils.forgeMojangPublicKey();
                }
                if (authPayload == null) {
                    authPayload = ForgeryUtils.forgeOnlineAuthData(account.authManager(), mojangPublicKey);
                }
            } catch (Exception e) {
                log.error("Failed to get login chain", e);
            }

            jwtSkinData = ForgeryUtils.forgeOnlineSkinData(account, this.skinData, this.proxy.getTargetAddress());

            try {
                player.getLogger().saveJson("skinData", this.skinData);
            } catch (Exception e) {
                log.error("JSON output error: " + e.getMessage(), e);
            }
            
            payload = authPayload;
        }

        LoginPacket login = new LoginPacket();
        login.setClientJwt(jwtSkinData);
        login.setAuthPayload(payload);
        login.setProtocolVersion(ProxyPass.PROTOCOL_VERSION);
        return login;
    }

    @Override
    public void onDisconnect(CharSequence reason) {
        if (this.session.getSendSession().isConnected()) {
            this.session.getSendSession().disconnect(reason);
        }
    }
}