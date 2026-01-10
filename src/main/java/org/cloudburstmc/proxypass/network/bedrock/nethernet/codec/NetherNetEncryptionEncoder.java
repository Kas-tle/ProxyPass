package org.cloudburstmc.proxypass.network.bedrock.nethernet.codec;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.util.concurrent.FastThreadLocal;
import lombok.extern.log4j.Log4j2;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import java.nio.ByteBuffer;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.atomic.AtomicLong;

@Log4j2
public class NetherNetEncryptionEncoder extends MessageToByteEncoder<ByteBuf> {
    public static final String NAME = "nethernet-encryption-encoder";

    private static final FastThreadLocal<MessageDigest> DIGEST = new FastThreadLocal<MessageDigest>() {
        @Override
        protected MessageDigest initialValue() {
            try {
                return MessageDigest.getInstance("SHA-256");
            } catch (Exception e) {
                throw new AssertionError(e);
            }
        }
    };

    private final AtomicLong packetCounter = new AtomicLong();
    private final SecretKey key;
    private final Cipher cipher;

    public NetherNetEncryptionEncoder(SecretKey key, Cipher cipher) {
        this.key = key;
        this.cipher = cipher;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        ByteBuf buf = ctx.alloc().ioBuffer(msg.readableBytes() + 8);
        try {
            ByteBuffer trailer = ByteBuffer.wrap(generateTrailer(msg, this.key, this.packetCounter));
            ByteBuffer inBuffer = msg.nioBuffer();
            ByteBuffer outBuffer = buf.nioBuffer(0, msg.readableBytes() + 8);

            int index = this.cipher.update(inBuffer, outBuffer);
            index += this.cipher.update(trailer, outBuffer);

            buf.writerIndex(index);
            
            out.writeBytes(buf.retain());
        } finally {
            buf.release();
        }
    }

    static byte[] generateTrailer(ByteBuf buf, SecretKey key, AtomicLong counter) {
        try {
            MessageDigest digest = DIGEST.get();
            ByteBuf counterBuf = ByteBufAllocator.DEFAULT.directBuffer(8);
            byte[] result;
            try {
                counterBuf.writeLongLE(counter.getAndIncrement());
                ByteBuffer keyBuffer = ByteBuffer.wrap(key.getEncoded());
                
                digest.update(counterBuf.nioBuffer(0, 8));
                digest.update(buf.nioBuffer(buf.readerIndex(), buf.readableBytes()));
                digest.update(keyBuffer);
                byte[] hash = digest.digest();
                result = Arrays.copyOf(hash, 8);
            } finally {
                counterBuf.release();
                digest.reset();
            }
            return result;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}