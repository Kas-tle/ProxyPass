package com.nukkitx.proxypass.network.bedrock.session;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeType;
import com.google.common.base.Preconditions;
import com.nimbusds.jose.JOSEException;
import com.nimbusds.jose.JWSObject;
import com.nimbusds.jose.crypto.factories.DefaultJWSVerifierFactory;
import com.nimbusds.jwt.SignedJWT;
import com.nukkitx.protocol.bedrock.BedrockServerSession;
import com.nukkitx.protocol.bedrock.handler.BedrockPacketHandler;
import com.nukkitx.protocol.bedrock.packet.LoginPacket;
import com.nukkitx.protocol.bedrock.packet.PlayStatusPacket;
import com.nukkitx.protocol.bedrock.util.EncryptionUtils;
import com.nukkitx.proxypass.ProxyPass;
import com.nukkitx.proxypass.network.bedrock.util.ForgeryUtils;
import io.netty.util.AsciiString;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import net.minidev.json.JSONObject;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.interfaces.ECPublicKey;
import java.util.Base64;
import java.util.UUID;

@Log4j2
@RequiredArgsConstructor
public class UpstreamPacketHandler implements BedrockPacketHandler {

    private static final int PIXEL_SIZE = 4;

    public static final int SINGLE_SKIN_SIZE = 64 * 32 * PIXEL_SIZE;
    public static final int DOUBLE_SKIN_SIZE = 64 * 64 * PIXEL_SIZE;
    public static final int SKIN_128_64_SIZE = 128 * 64 * PIXEL_SIZE;
    public static final int SKIN_128_128_SIZE = 128 * 128 * PIXEL_SIZE;

    private final BedrockServerSession session;
    private final ProxyPass proxy;
    private JSONObject skinData;
    private JSONObject extraData;
    private ArrayNode chainData;
    private AuthData authData;

    private static boolean validateChainData(JsonNode data) throws Exception {
        ECPublicKey lastKey = null;
        boolean validChain = false;
        for (JsonNode node : data) {
            JWSObject jwt = JWSObject.parse(node.asText());

            if (!validChain) {
                validChain = verifyJwt(jwt, EncryptionUtils.getMojangPublicKey());
            }

            if (lastKey != null) {
                verifyJwt(jwt, lastKey);
            }

            JsonNode payloadNode = ProxyPass.JSON_MAPPER.readTree(jwt.getPayload().toString());
            JsonNode ipkNode = payloadNode.get("identityPublicKey");
            Preconditions.checkState(ipkNode != null && ipkNode.getNodeType() == JsonNodeType.STRING, "identityPublicKey node is missing in chain");
            lastKey = EncryptionUtils.generateKey(ipkNode.asText());
        }
        return validChain;
    }

    private static boolean verifyJwt(JWSObject jwt, ECPublicKey key) throws JOSEException {
        return jwt.verify(new DefaultJWSVerifierFactory().createJWSVerifier(jwt.getHeader(), key));
    }

    @Override
    public boolean handle(LoginPacket packet) {
        int protocolVersion = packet.getProtocolVersion();

        if (protocolVersion != ProxyPass.PROTOCOL_VERSION) {
            PlayStatusPacket status = new PlayStatusPacket();
            if (protocolVersion > ProxyPass.PROTOCOL_VERSION) {
                status.setStatus(PlayStatusPacket.Status.FAILED_SERVER);
            } else {
                status.setStatus(PlayStatusPacket.Status.FAILED_CLIENT);
            }
        }
        session.setPacketCodec(ProxyPass.CODEC);

        JsonNode certData;
        try {
            certData = ProxyPass.JSON_MAPPER.readTree(packet.getChainData().toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Certificate JSON can not be read.");
        }

        JsonNode certChainData = certData.get("chain");
        if (certChainData.getNodeType() != JsonNodeType.ARRAY) {
            throw new RuntimeException("Certificate data is not valid");
        }
        chainData = (ArrayNode) certChainData;

        boolean validChain;
        try {
            validChain = validateChainData(certChainData);

            log.debug("Is player data valid? {}", validChain);
            JWSObject jwt = JWSObject.parse(certChainData.get(certChainData.size() - 1).asText());
            JsonNode payload = ProxyPass.JSON_MAPPER.readTree(jwt.getPayload().toBytes());

            if (payload.get("extraData").getNodeType() != JsonNodeType.OBJECT) {
                throw new RuntimeException("AuthData was not found!");
            }

            extraData = (JSONObject) jwt.getPayload().toJSONObject().get("extraData");

            this.authData = new AuthData(extraData.getAsString("displayName"),
                    UUID.fromString(extraData.getAsString("identity")), extraData.getAsString("XUID"));

            if (payload.get("identityPublicKey").getNodeType() != JsonNodeType.STRING) {
                throw new RuntimeException("Identity Public Key was not found!");
            }
            ECPublicKey identityPublicKey = EncryptionUtils.generateKey(payload.get("identityPublicKey").textValue());

            JWSObject clientJwt = JWSObject.parse(packet.getSkinData().toString());
            verifyJwt(clientJwt, identityPublicKey);
            skinData = clientJwt.getPayload().toJSONObject();

            initializeProxySession();
        } catch (Exception e) {
            session.disconnect("disconnectionScreen.internalError.cantConnect");
            throw new RuntimeException("Unable to complete login", e);
        }
        return true;
    }

    private void initializeProxySession() {
        log.debug("Initializing proxy session");
        proxy.newClient().connect(proxy.getTargetAddress()).whenComplete((downstream, throwable) -> {
            if (throwable != null) {
                log.error("Unable to connect to downstream server", throwable);
                return;
            }
            downstream.setPacketCodec(ProxyPass.CODEC);
            ProxyPlayerSession proxySession = new ProxyPlayerSession(this.session, downstream, this.proxy, this.authData);

            SignedJWT authData = ForgeryUtils.forgeAuthData(proxySession.getProxyKeyPair(), extraData);
            JWSObject skinData = ForgeryUtils.forgeSkinData(proxySession.getProxyKeyPair(), this.skinData);
            chainData.remove(chainData.size() - 1);
            chainData.add(authData.serialize());
            JsonNode json = ProxyPass.JSON_MAPPER.createObjectNode().set("chain", chainData);
            AsciiString chainData;
            try {
                chainData = new AsciiString(ProxyPass.JSON_MAPPER.writeValueAsBytes(json));
            } catch (JsonProcessingException e) {
                throw new RuntimeException(e);
            }

            LoginPacket login = new LoginPacket();
            login.setChainData(chainData);
            login.setSkinData(AsciiString.of(skinData.serialize()));
            login.setProtocolVersion(ProxyPass.PROTOCOL_VERSION);

            downstream.sendPacketImmediately(login);
            this.session.setBatchedHandler(proxySession.getUpstreamBatchHandler());
            downstream.setBatchedHandler(proxySession.getDownstreamTailHandler());
            downstream.setLogging(true);
            downstream.setPacketHandler(new DownstreamPacketHandler(downstream, proxySession, this.proxy));

            log.debug("Downstream connected");

            //saveSkin(proxySession);
        });
    }

    private void saveSkin(ProxyPlayerSession session) {
        byte[] skin = Base64.getDecoder().decode(skinData.getAsString("SkinData"));
        int width, height;
        if (skin.length == SINGLE_SKIN_SIZE) {
            width = 64;
            height = 32;
        } else if (skin.length == DOUBLE_SKIN_SIZE) {
            width = 64;
            height = 64;
        } else if (skin.length == SKIN_128_64_SIZE) {
            width = 128;
            height = 64;
        } else if (skin.length == SKIN_128_128_SIZE) {
            width = 128;
            height = 128;
        } else {
            throw new IllegalStateException("Invalid skin");
        }
        saveSkin(session, width, height, skin, "skin");

        byte[] cape = Base64.getDecoder().decode(skinData.getAsString("CapeData"));

        saveSkin(session, 64, 32, cape, "cape");

        Path geometryPath = session.getDataPath().resolve("geometry.json");
        byte[] geometry = Base64.getDecoder().decode(skinData.getAsString("SkinGeometry"));
        try {
            Files.write(geometryPath, geometry, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
        } catch (IOException e) {
            throw new RuntimeException(e);
}
    }

    private static void saveSkin(ProxyPlayerSession session, int width, int height, byte[] bytes, String name) {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_4BYTE_ABGR);

        ByteArrayInputStream data = new ByteArrayInputStream(bytes);

        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                Color color = new Color(data.read(), data.read(), data.read(), data.read());
                image.setRGB(x, y, color.getRGB());
            }
        }

        Path path = session.getDataPath().resolve(name + ".png");
        try (OutputStream stream = Files.newOutputStream(path, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING)) {
            ImageIO.write(image, "png", stream);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }
}