package lftp.server;

import lftp.common.Protocol;

import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;

/**
 * Lắng nghe kết nối TCP trên cổng 5000 và phân phối socket cho ThreadPoolExecutor.
 * Khống chế tối đa 20 worker threads để tránh cạn kiệt bộ nhớ native stack của hệ điều hành.
 */
public class ConnectionListener implements Runnable {

    private final ServerConfig config;
    private final FileManager fileManager;
    private final FileStorage fileStorage;
    private final SessionManager sessionManager;

    private ServerSocket serverSocket;
    private ThreadPoolExecutor threadPool;
    private final Map<String, ClientHandler> activeClients = new ConcurrentHashMap<>();
    private volatile boolean running = true;

    public ConnectionListener(ServerConfig config, FileManager fileManager,
                              FileStorage fileStorage, SessionManager sessionManager) {
        this.config = config;
        this.fileManager = fileManager;
        this.fileStorage = fileStorage;
        this.sessionManager = sessionManager;
    }

    public void start() throws IOException {
        serverSocket = new ServerSocket(config.getTcpPort(), 50);

        // Khởi tạo ThreadPoolExecutor có kiểm soát: Tối đa 20 worker threads
        threadPool = new ThreadPoolExecutor(
                5,
                config.getMaxClients(),
                60L, TimeUnit.SECONDS,
                new ArrayBlockingQueue<>(config.getMaxClients()),
                new ThreadPoolExecutor.AbortPolicy()
        );

        Thread listenThread = new Thread(this, "lftp-conn-listener");
        listenThread.start();
    }

    @Override
    public void run() {
        System.out.printf("[ConnectionListener] Đang lắng nghe kết nối TCP trên cổng %d (Tối đa %d clients)...%n",
                config.getTcpPort(), config.getMaxClients());

        while (running) {
            try {
                Socket clientSocket = serverSocket.accept();

                // Kiểm tra giới hạn kết nối đồng thời
                if (activeClients.size() >= config.getMaxClients()) {
                    System.out.println("[ConnectionListener] Server quá tải kết nối, từ chối socket mới!");
                    clientSocket.close();
                    continue;
                }

                ClientHandler handler = new ClientHandler(
                        clientSocket, config, fileManager, fileStorage, sessionManager, this);
                activeClients.put(handler.getConnectionId(), handler);

                try {
                    threadPool.execute(handler);
                } catch (RejectedExecutionException ree) {
                    System.out.println("[ConnectionListener] Hàng đợi thread pool đầy, đóng socket!");
                    activeClients.remove(handler.getConnectionId());
                    handler.close();
                }

            } catch (IOException e) {
                if (!running) {
                    break;
                }
                System.err.println("[ConnectionListener] Lỗi accept socket: " + e.getMessage());
            }
        }
    }

    public void onClientDisconnected(ClientHandler handler) {
        activeClients.remove(handler.getConnectionId());
    }

    /**
     * Quét và ngắt các kết nối không hoạt động quá 60 giây.
     */
    public void checkClientTimeouts() {
        long now = System.currentTimeMillis();
        List<ClientHandler> toClose = new ArrayList<>();

        for (ClientHandler h : activeClients.values()) {
            if (now - h.getLastActiveTimeMs() > Protocol.TIMEOUT_READ_MS) {
                toClose.add(h);
            }
        }

        for (ClientHandler h : toClose) {
            System.out.printf("[ConnectionListener] Ngắt kết nối socket bị đơ (timeout 60s): ConnId=%s%n",
                    h.getConnectionId());
            h.close();
        }
    }

    public void stop() {
        running = false;
        try {
            if (serverSocket != null && !serverSocket.isClosed()) {
                serverSocket.close();
            }
        } catch (IOException ignored) {
        }

        // Đóng toàn bộ các client đang kết nối
        for (ClientHandler h : activeClients.values()) {
            h.close();
        }
        activeClients.clear();

        if (threadPool != null) {
            threadPool.shutdownNow();
        }
    }
}

