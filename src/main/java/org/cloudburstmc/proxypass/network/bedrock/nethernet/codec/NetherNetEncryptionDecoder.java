package org.cloudburstmc.proxypass.network.bedrock.nethernet.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.CorruptedFrameException;
import io.netty.handler.codec.MessageToMessageDecoder;
import lombok.extern.log4j.Log4j2;
import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

@Log4j2
public class NetherNetEncryptionDecoder extends MessageToMessageDecoder<ByteBuf> {
    public static final String NAME = "nethernet-encryption-decoder";
    private static final boolean VALIDATE = Boolean.getBoolean("cloudburst.validateEncryption");

    private final AtomicLong packetCounter = new AtomicLong();
    private final SecretKey key;
    private final Cipher cipher;

    public NetherNetEncryptionDecoder(SecretKey key, Cipher cipher) {
        this.key = key;
        this.cipher = cipher;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, ByteBuf msg, List<Object> out) throws Exception {
        log.debug("Decrypting packet of size {} with counter {}", msg.readableBytes(), packetCounter.get());
        ByteBuffer inBuffer = msg.nioBuffer();
        ByteBuffer outBuffer = inBuffer.duplicate();
        
        this.cipher.update(inBuffer, outBuffer);

        ByteBuf output = msg.readSlice(msg.readableBytes() - 8);

        if (VALIDATE) {
            ByteBuf trailer = msg.readSlice(8);

            byte[] actual = new byte[8];
            trailer.readBytes(actual);

            byte[] expected = NetherNetEncryptionEncoder.generateTrailer(output, this.key, this.packetCounter);

            if (!Arrays.equals(expected, actual)) {
                throw new CorruptedFrameException("Invalid encryption trailer");
            }
        }

        out.add(output.retain());
    }
}
