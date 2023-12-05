import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class FtpServer{ // multi-client

    public static void main(String[] args) throws IOException {
        ServerSocket server = new ServerSocket(8888);
        int server_dynamic_port = 8889;
        int client_dynamic_port = 9999;
        while (true) {
            Socket initSocket = server.accept();
            System.out.println("Client connected");
            DataOutputStream initDos = new DataOutputStream(initSocket.getOutputStream());
            ServerSocket serverSocket = new ServerSocket(server_dynamic_port);
            initDos.writeInt(server_dynamic_port);
            Socket socket = serverSocket.accept();
            serverSocket.close();
            initDos.close();
            initSocket.close();
            new Thread(new ClientHandler(server_dynamic_port, client_dynamic_port, socket)).start();
            client_dynamic_port += 1;
            server_dynamic_port += 2;
        }
    }
}

class ClientHandler implements Runnable { // multi-request
    Socket commandSocket;
    DataInputStream dis;
    DataOutputStream dos;
    int upload_port_no;
    int download_port_no;
    String downloadPath = "D:\\Lab Practice\\Java2\\Download";
    ArrayList<FileTransferHandler> uploadObjList = new ArrayList<>();
    ArrayList<File> downloadFileList = new ArrayList<>();

    public ClientHandler(int port1, int port2, Socket socket) throws IOException {
        commandSocket = socket;
        upload_port_no = port1 + 1;
        download_port_no = port2;
        dis = new DataInputStream(commandSocket.getInputStream());
        dos = new DataOutputStream(commandSocket.getOutputStream());
    }
    @Override
    public void run() {
        String command;
        try {
            label:
            while (true) {
                command = dis.readUTF();
                switch (command) {
                    // for multithreaded function, send dynamic port number to client side
                    case "exit":
                        break label;
                    case "up-sd":
                        uploadSend();
                        break;
                    case "up-pg":
                        uploadProgress();
                        break;
                    case "up-pa":
                        uploadPause();
                        break;
                    case "up-re":
                        uploadResume();
                        break;
                    case "up-ca":
                        uploadCancel();
                        break;
                    case "dl-ls":
                        downloadLs();
                        break;
                    case "dl-cd":
                        downloadCd();
                        break;
                    case "dl-add":
                        downloadAdd();
                        break;
                    case "dl-sd":
                        downloadSend();
                        break;
                    default:
                        System.out.println("Invalid command received");
                }
            }
            closeAll();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private void uploadSend() throws IOException {
        dos.writeInt(upload_port_no);
        int fileNum = dis.readInt();
        ServerSocket serverSocket = new ServerSocket(upload_port_no);
        for (int i=0; i<fileNum; i++) {
            long fileSize = dis.readLong();
            dos.writeUTF("ready");
            Socket clientSocket = serverSocket.accept();
            FileTransferHandler obj = new FileTransferHandler("upload", null, fileSize, clientSocket);
            uploadObjList.add(obj);
            new Thread(obj).start();
        }
        serverSocket.close();
    }

    private void uploadProgress() throws IOException {
        for (FileTransferHandler obj : uploadObjList) {
            if (obj.byteNum < obj.fileSize) {
                dos.writeUTF(obj.uploadFile.getName() + ": " + obj.byteNum + "/" + obj.fileSize + " bytes sent. " + "Status: " + (obj.paused ? "Paused" : "Sending"));
            }
        }
        dos.writeUTF("stop");
    }

    private void uploadPause() throws IOException {
        String[] fileNames = dis.readUTF().split(",");
        for (FileTransferHandler obj : uploadObjList) {
            if (Arrays.asList(fileNames).contains(obj.uploadFile.getName())) {
                if (obj.byteNum < obj.fileSize) {
                    obj.pause();
                    dos.writeUTF(obj.uploadFile.getName() + " has been paused");
                } else {
                    dos.writeUTF(obj.uploadFile.getName() + " is already uploaded");
                }
            }
        }
        dos.writeUTF("end");
    }

    private void uploadResume() throws IOException {
        String[] fileNames = dis.readUTF().split(",");
        for (FileTransferHandler obj : uploadObjList) {
            if (Arrays.asList(fileNames).contains(obj.uploadFile.getName())) {
                if (!obj.paused) {
                    dos.writeUTF(obj.uploadFile.getName() + " can not be resumed");
                } else {
                    obj.resume();
                    dos.writeUTF(obj.uploadFile.getName() + " has been resumed");
                }
            }
        }
        dos.writeUTF("end");
    }

    private void uploadCancel() throws IOException {
        String[] fileNames = dis.readUTF().split(",");
        ArrayList<FileTransferHandler> toDelete = new ArrayList<>();
        for (FileTransferHandler obj : uploadObjList) {
            if (Arrays.asList(fileNames).contains(obj.uploadFile.getName())) {
                if (obj.byteNum < obj.fileSize) {
                    obj.cancel();
                    obj.uploadFile.delete();
                    toDelete.add(obj);
                    dos.writeUTF(obj.uploadFile.getName() + " has been cancelled uploading");
                } else {
                    dos.writeUTF(obj.uploadFile.getName() + "is already uploaded");
                }
            }
        }
        dos.writeUTF("end");
        uploadObjList.removeAll(toDelete);
    }

    private void downloadLs() throws IOException {
        File file = new File(downloadPath);
        String[] directories = file.list();
        assert directories != null;
        for (String dir:directories) {
            if (new File(downloadPath +"\\"+dir).isDirectory()) {
                dos.writeUTF("Dir: " + dir);
            } else {
                dos.writeUTF("File: " + dir);
            }
        }
        dos.writeUTF("stop");
    }

    private void downloadCd() throws IOException {
        dos.writeUTF(downloadPath + " > ");
        File file = new File(downloadPath);
        String open = dis.readUTF();
        if (open.equals("..")) {
            downloadPath = file.getParent();
            dos.writeUTF("OK");
        } else {
            File tmpFile = new File(downloadPath +"\\"+open);
            if (!tmpFile.exists()) {
                dos.writeUTF("NO");
            } else {
                dos.writeUTF("OK");
                downloadPath = downloadPath + "\\" + open;
            }
        }
    }

    private void downloadAdd() throws IOException {
        File file = new File(downloadPath);
        if (!file.exists()) {
            dos.writeUTF("NO");
        } else {
            dos.writeUTF("OK");
        }
        downloadFileList.add(file);
    }

    private static int getFileCount(List<File> list) {
        int count = 0;
        for (File file : list) {
            if (file.isFile()) {
                count ++;
            } else {
                count += getFileCount(Arrays.asList(Objects.requireNonNull(file.listFiles())));
            }
        }
        return count;
    }

    private void iterSend(List<File> fileList) throws IOException {
        for (File tmpFile : fileList) {
            if (tmpFile.isFile()) {
                // send a file
                dos.writeLong(tmpFile.length());
                String signal = dis.readUTF();
                if (!signal.equals("ready")) {
                    System.out.println("wrong signal");
                }
                String clientIP = commandSocket.getInetAddress().getHostAddress();
                Socket socket = new Socket(clientIP, download_port_no);
                FileTransferHandler obj = new FileTransferHandler("download", tmpFile, 0, socket);
                new Thread(obj).start();
            } else {
                // send a directory
                List<File> fileInDir = Arrays.asList(Objects.requireNonNull(tmpFile.listFiles()));
                iterSend(fileInDir);
            }
        }
    }

    private void downloadSend() throws IOException {
        dos.writeInt(download_port_no);
        int count = getFileCount(downloadFileList);
        dos.writeInt(count);
        iterSend(downloadFileList);

        // clear the fileList to empty and restore path
        downloadFileList = new ArrayList<>();
        downloadPath = "D:\\Lab Practice\\Java2\\Download";
    }

    private void closeAll() throws IOException {
        this.dis.close();
        this.dos.close();
        this.commandSocket.close();
    }
}

class FileTransferHandler implements Runnable { // multi-file
    String function;
    File downloadFile;
    long fileSize;
    Socket clientSocket;
    DataInputStream dis;
    DataOutputStream dos;
    File uploadFile;
    long byteNum = 0;
    final Object pauseLock = new Object();
    volatile boolean paused = false;
    volatile boolean cancelled = false;

    public FileTransferHandler(String function, File file, long fileSize, Socket socket) throws IOException {
        this.function = function;
        this.downloadFile = file;
        this.fileSize = fileSize;
        this.clientSocket = socket;
        this.dis = new DataInputStream(socket.getInputStream());
        this.dos = new DataOutputStream(socket.getOutputStream());
    }

    @Override
    public void run() {
        if (function.equals("upload")) {
            upload();
        } else {
            try {
                download();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        }
        try {
            closeAll();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void upload() {
        try {
            // receive the file path
            String filePath = dis.readUTF();
            uploadFile = extractFile(filePath);
            String fileName = uploadFile.getName();

            // receive file content
            OutputStream os = new FileOutputStream(uploadFile);
            byte[] buffer = new byte[1024];
            int len;
            synchronized (pauseLock) {
                while ((len = dis.read(buffer)) > 0) {
                    while (paused) {
                        pauseLock.wait();
                    }
                    if (cancelled) {
                        os.close();
                        break;
                    }

                    os.write(buffer, 0, len);
                    if (this.byteNum + len < this.fileSize) {
                        this.byteNum += len;
                    } else {
                        this.byteNum = this.fileSize;
                    }
                }
            }
            os.close();
            if (!cancelled) {
                System.out.println("Successfully receive file " + fileName);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }



    private File extractFile(String filePath) throws IOException {
        String temp = filePath.split("Upload" + "\\\\")[1];
        String fileName = filePath.substring(filePath.lastIndexOf("\\") + 1);
        String[] tempArray = temp.split(fileName);
        String dirPath;
        if (tempArray.length > 0) {
            // file is in directory
            dirPath = "D:\\Lab Practice\\Java2\\Storage\\" + tempArray[0];
            File dirFile = new File(dirPath);
            dirFile.mkdirs();
        } else {
            // file is not in directory
            dirPath = "D:\\Lab Practice\\Java2\\Storage\\";
        }

        File file = new File(dirPath + fileName);
        file.createNewFile();
        return file;
    }

    public void pause() {
        paused = true;
    }

    public void resume() {
        synchronized (pauseLock) {
            paused = false;
            pauseLock.notify();
        }
    }

    public void cancel() {
        cancelled = true;
        resume();
    }


    private void download() throws IOException {
        try {
            InputStream is = new FileInputStream(downloadFile);
            // send file name and size
            dos.writeUTF(downloadFile.getAbsolutePath());
            // send file content
            byte[] buffer = new byte[1024];
            int len;
            while((len = is.read(buffer)) > 0){
                dos.write(buffer,0,len);
            }
            is.close();
        } catch (Exception e) {
            if (!e.getMessage().contains("Connection reset")) {
                throw new RuntimeException(e);
            } else {
                closeAll();
            }
        }
    }

    private void closeAll() throws IOException {
        this.dis.close();
        this.dos.close();
        this.clientSocket.close();
    }
}