package Forwarder;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.ByteBuffer;
import java.nio.channels.SelectionKey;
import java.nio.channels.Selector;
import java.nio.channels.ServerSocketChannel;
import java.nio.channels.SocketChannel;
import java.util.Iterator;

public class PortForwarder {
    private final static int BUF_SIZE = 4096;

    private String dstIp;
    private int dstPort = 0;

    private Selector selector;

    public PortForwarder(int srcPort, String dstIp, int dstPort) {
        this.dstIp = dstIp;
        this.dstPort = dstPort;

        try {
            ServerSocketChannel serverSocketChannel = ServerSocketChannel.open();

            selector = Selector.open();

            serverSocketChannel.bind(new InetSocketAddress(srcPort));
            serverSocketChannel.configureBlocking(false);

            serverSocketChannel.register(selector, SelectionKey.OP_ACCEPT);
        } catch (IOException e) {
            System.out.println(e.getMessage());
        }
    }

    //регистируем на запись постояяно, тогда цикл превратится в бесконечный и скушает весь процессор
    //так ли это и почему?

    public void forward() {
        for (;;) {
            try {
                int selectedCount = selector.select();

                if (0 == selectedCount) continue;

                Iterator<SelectionKey> iterator = selector.selectedKeys().iterator();

                while (iterator.hasNext()){
                    SelectionKey key = iterator.next();

                    if (key.isValid()) {
                        if (key.isAcceptable()) {
                            System.out.println("accepted");
                            handleAccept(key);
                        } else if (key.isReadable()) {
                            System.out.println("read");
                            handleRead(key);
                        } else if (key.isWritable()) {
                            System.out.println("write");
                            handleWrite(key);
                        } else if (key.isConnectable()) {
                            System.out.println("connect");
                            handleConnect(key);
                        }
                    }

                    iterator.remove();
                }
            } catch (IOException e) {
                System.out.println(e.getMessage());
            }
        }
    }

    private void handleAccept(SelectionKey key) throws IOException {
        SocketChannel clientChannel = ((ServerSocketChannel) key.channel()).accept();
        SocketChannel serverChannel = SocketChannel.open();

        clientChannel.configureBlocking(false);
        serverChannel.configureBlocking(false);

        ByteBuffer inBuf = ByteBuffer.allocate(BUF_SIZE);
        ByteBuffer outBuf = ByteBuffer.allocate(BUF_SIZE);

        inBuf.flip();
        outBuf.flip();

        serverChannel.register(selector, SelectionKey.OP_CONNECT, new Attachment(clientChannel, inBuf, outBuf));

        serverChannel.connect(new InetSocketAddress(dstIp, dstPort));
    }

    private void handleRead(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        Attachment attachment = (Attachment) key.attachment();

        if (attachment.isClosed()){
            return;
        }

        ByteBuffer buf = ByteBuffer.allocate(BUF_SIZE / 4);

        if (-1 == channel.read(buf)) {
            handleClose(key);
            return;
        }

        attachment.getChannel().keyFor(selector).interestOps(SelectionKey.OP_READ | SelectionKey.OP_WRITE);
        attachment.put(buf.array(), buf.position());
    }

    private void handleWrite(SelectionKey key) throws IOException {
        SocketChannel channel = (SocketChannel) key.channel();
        Attachment attachment = (Attachment) key.attachment();

        channel.write(attachment.getOutBuf());

        if (!attachment.isWritable()) {
            if (attachment.isClosed()){
                handleClose(key);
                return ;
            }
            key.interestOps(SelectionKey.OP_READ);
        }
    }

    private void handleConnect(SelectionKey key) throws IOException {
        SocketChannel serverChannel = (SocketChannel) key.channel();
        Attachment serverAttachment = (Attachment) key.attachment();

        serverChannel.finishConnect();

        if (serverChannel.isConnected()){
            key.interestOps(SelectionKey.OP_READ);

            serverAttachment.getChannel().register(selector, SelectionKey.OP_READ,
                    new Attachment(serverChannel, serverAttachment.getOutBuf(), serverAttachment.getInBuf()));
        } else {
            serverChannel.close();
            key.cancel();
        }
    }

    private void handleClose(SelectionKey key) throws IOException {
        Attachment attachment = (Attachment) key.attachment();

        key.channel().close();
        key.cancel();

        if (!attachment.isClosed() && !attachment.isWritable()) {
            ((Attachment) attachment.getChannel().keyFor(selector).attachment()).close();
            attachment.getChannel().keyFor(selector).interestOps(SelectionKey.OP_WRITE);
            attachment.getChannel().close();
            attachment.getChannel().keyFor(selector).cancel();
        }
    }
}
