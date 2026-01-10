package org.cloudburstmc.proxypass.network.bedrock.nethernet.codec;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import lombok.extern.log4j.Log4j2;
import org.cloudburstmc.protocol.bedrock.data.PacketCompressionAlgorithm;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.BatchCompression;
import org.cloudburstmc.protocol.bedrock.netty.codec.compression.CompressionStrategy;

@Log4j2
public class NetherNetCompressionEncoder extends MessageToByteEncoder<ByteBuf> {
    public static final String NAME = "nethernet-compression-encoder";

    private final CompressionStrategy strategy;
    private final boolean prefixed;
    private final int threshold;

    public NetherNetCompressionEncoder(CompressionStrategy strategy, boolean prefixed, int threshold) {
        this.strategy = strategy;
        this.prefixed = prefixed;
        this.threshold = threshold;
    }

    @Override
    protected void encode(ChannelHandlerContext ctx, ByteBuf msg, ByteBuf out) throws Exception {
        BatchCompression compression;
        if (msg.readableBytes() > this.threshold) {
            compression = this.strategy.getDefaultCompression();
        } else {
            compression = this.strategy.getCompression(PacketCompressionAlgorithm.NONE);
        }

        ByteBuf compressed = compression.encode(ctx, msg);

        try {
            if (this.prefixed) {
                out.writeByte(switch (compression.getAlgorithm()) {
                    case PacketCompressionAlgorithm.ZLIB -> 0x00;
                    case PacketCompressionAlgorithm.SNAPPY -> 0x01;
                    default -> (byte) 0xff;
                });
            }
            out.writeBytes(compressed);
        } catch (Exception e) {
            log.error("Error during compression encoding", e);
            throw e;
        } finally {
            compressed.release();
        }
    }
}
