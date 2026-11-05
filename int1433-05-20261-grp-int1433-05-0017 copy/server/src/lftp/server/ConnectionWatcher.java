package lftp.server;

import lftp.common.Protocol;

/**
 * Luồng chạy nền định kỳ để phát hiện và xử lý:
 * 1. Các kết nối TCP không hoạt động quá 60 giây (Read Timeout).
 * 2. Các phiên tải lên dở bị mất kết nối quá 30 phút (quét file tạm và hoàn trả quota).
 */
public class ConnectionWatcher implements Runnable {

    private final ConnectionListener connectionListener;
    private final SessionManager sessionManager;
    private volatile boolean running = true;

    public ConnectionWatcher(ConnectionListener connectionListener, SessionManager sessionManager) {
        this.connectionListener = connectionListener;
        this.sessionManager = sessionManager;
    }

    public void stop() {
        this.running = false;
    }

    @Override
    public void run() {
        while (running) {
            try {
                Thread.sleep(10000); // Quét định kỳ mỗi 10 giây
                if (!running) break;

                // 1. Kiểm tra timeout các kết nối socket không gửi dữ liệu
                connectionListener.checkClientTimeouts();

                // 2. Thu hồi các phiên upload dở quá 30 phút
                sessionManager.removeExpiredSessions();

            } catch (InterruptedException e) {
                break;
            } catch (Exception e) {
                System.err.println("[ConnectionWatcher] Lỗi khi quét định kỳ: " + e.getMessage());
            }
        }
    }
}

