package lftp.client;

/**
 * Điểm khởi chạy chính của ứng dụng LFTP Client.
 */
public class ClientMain {

    public static void main(String[] args) {
        ClientConfig config = new ClientConfig();

        if (args.length >= 1) {
            config.setServerHost(args[0]);
        }
        if (args.length >= 2) {
            config.setServerPort(Integer.parseInt(args[1]));
        }

        ResumeManager resumeManager = new ResumeManager(config.getClientStateDir());
        TransferClient client = new TransferClient(config, resumeManager);
        ConsoleUI ui = new ConsoleUI(config, client, resumeManager);

        // Khởi động giao diện dòng lệnh tương tác trên luồng chính
        ui.start();
    }
}

