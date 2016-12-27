package Forwarder;

import java.nio.ByteBuffer;
import java.nio.channels.SocketChannel;

public class Attachment {
    private SocketChannel channel;
    private ByteBuffer inBuf;
    private ByteBuffer outBuf;
    private boolean isClosed = false;

    Attachment (SocketChannel channel, ByteBuffer inBuf, ByteBuffer outBuf) {
        this.channel = channel;
        this.inBuf = inBuf;
        this.outBuf = outBuf;
    }

    void put(byte[] data, int length) {
        inBuf.compact();
        inBuf.put(data, 0, length);
        inBuf.flip();
    }

    boolean isWritable() {
        return 0 != outBuf.remaining();
    }

    void close() {
        isClosed = true;
    }

    boolean isClosed(){
        return isClosed;
    }

    ByteBuffer getInBuf() {
        return inBuf;
    }

    ByteBuffer getOutBuf() {
        return outBuf;
    }

    SocketChannel getChannel() {
        return channel;
    }
}
