package lftp.server;

import lftp.common.FileInfo;
import lftp.common.Protocol;
import lftp.common.RequestException;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.RandomAccessFile;

/**
 * Xử lý luồng tải xuống: mở file chính thức trên đĩa, đọc tuần tự từng chunk 64 KiB
 * gửi về cho client.
 */
public class DownloadManager implements AutoCloseable {

    private final FileInfo fileInfo;
    private final FileStorage fileStorage;
    private RandomAccessFile raf;

    public DownloadManager(FileInfo fileInfo, FileStorage fileStorage) throws FileNotFoundException {
        this.fileInfo = fileInfo;
        this.fileStorage = fileStorage;
        this.raf = fileStorage.openForDownload(fileInfo.getFileId());
    }

    public FileInfo getFileInfo() {
        return fileInfo;
    }

    /**
     * Đọc một phần dữ liệu từ đĩa.
     */
    public synchronized byte[] readChunk(long offset, int requestedBytes)
            throws RequestException, IOException {
        if (raf == null) {
            throw new RequestException(Protocol.STATUS_INTERNAL_ERROR, "Tệp đã bị đóng!");
        }

        if (offset < 0 || offset >= fileInfo.getFileSize()) {
            throw new RequestException(Protocol.STATUS_BAD_REQUEST, "Vị trí offset yêu cầu không hợp lệ: " + offset);
        }

        int toRead = Math.min(requestedBytes, Protocol.CHUNK_SIZE);
        long remaining = fileInfo.getFileSize() - offset;
        if (toRead > remaining) {
            toRead = (int) remaining;
        }

        byte[] buffer = new byte[toRead];
        raf.seek(offset);
        raf.readFully(buffer);
        return buffer;
    }

    @Override
    public synchronized void close() {
        if (raf != null) {
            try {
                raf.close();
            } catch (IOException ignored) {
            }
            raf = null;
        }
    }
}

