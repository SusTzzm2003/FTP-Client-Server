import java.io.*;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.*;

public class FtpClient implements Runnable {
    File uploadFile;
    Socket clientSocket;
    DataInputStream dis;
    DataOutputStream dos;
    File downloadFile;
    long fileSize;
    int byteNum = 0;
    volatile boolean paused = false;
    volatile boolean cancelled = false;
    final Object pauseLock = new Object();

    static Scanner scanner = new Scanner(System.in);
    static Socket commandSocket;
    static DataInputStream commandDis;
    static DataOutputStream commandDos;
    static String uploadPath = "C:\\Users\\ZZM2021\\Desktop\\Upload";
    static ArrayList<File> uploadFileList = new ArrayList<>();
    static ArrayList<FtpClient> downloadObjList = new ArrayList<>();
    static String function;
    static String ip;

    FtpClient(File file, Socket socket, long fileSize) throws IOException {
        this.uploadFile = file;
        this.clientSocket = socket;
        this.fileSize = fileSize;
        dis = new DataInputStream(socket.getInputStream());
        dos = new DataOutputStream(socket.getOutputStream());
    }

    public static void ftpRun() throws Exception {
        System.out.print("Please enter ip address:");
        ip = scanner.next();
        System.out.print("Please enter port number:");
        String port = scanner.next();
        Socket initSocket = new Socket(ip, Integer.parseInt(port));
        DataInputStream initDis = new DataInputStream(initSocket.getInputStream());
        int dynamic_port_no = initDis.readInt();
        initDis.close();
        initSocket.close();
        manageCommand(dynamic_port_no);
    }

    @Override
    public void run() {
        if (function.equals("upload")) {
            try {
                upload();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        } else {
            download();
        }
        try {
            this.closeAll();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
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

    private static void manageCommand(int port_no) throws Exception {
        commandSocket = new Socket(ip, port_no);
        commandDis = new DataInputStream(commandSocket.getInputStream());
        commandDos = new DataOutputStream(commandSocket.getOutputStream());

        label:
        while (true) {
            System.out.println("Enter your command: ");
            String command = scanner.next();
            switch (command) {
                case "0":
                    commandDos.writeUTF("exit");
                    break label;
                case "up-ls":
                    uploadLs();
                    break;
                case "up-cd":
                    uploadCd();
                    break;
                case "up-add":
                    uploadAdd();
                    break;
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
                case "dl-pg":
                    downloadProgress();
                    break;
                case "dl-pa":
                    downloadPause();
                    break;
                case "dl-re":
                    downloadResume();
                    break;
                case "dl-ca":
                    downloadCancel();
                    break;
                default:
                    System.out.println("Invalid command!");
            }
        }
        commandDis.close();
        commandDos.close();
        commandSocket.close();
    }

    private void upload() throws IOException {
        try {
            InputStream is = new FileInputStream(uploadFile);
            // send file name and suffix
            dos.writeUTF(uploadFile.getAbsolutePath());
            // send file content
            byte[] buffer = new byte[1024];
            int len;
            while((len = is.read(buffer)) > 0){
                dos.write(buffer,0,len);
            }
            is.close();
        } catch (Exception e) {
            if (e.getMessage().contains("Connection reset")) {
                System.out.println(uploadFile.getName() + "has been cancelled");
                closeAll();
            } else {
                throw new RuntimeException(e);
            }
        }
    }

    private static void uploadLs() {
        File file = new File(uploadPath);
        String[] directories = file.list();
        assert directories != null;
        for (String dir:directories) {
            if (new File(uploadPath +"\\"+dir).isDirectory()) {
                System.out.println("Dir: "+dir);
            } else {
                System.out.println("File: "+dir);
            }
        }
    }

    private static void uploadCd() {
        System.out.print(uploadPath +" > ");
        System.out.print("Enter file or directory:");
        File file = new File(uploadPath);
        String open;
        open = scanner.next();

        if (open.equals("..")) {
            uploadPath = file.getParent();
        } else {
            File tmpFile = new File(uploadPath +"\\"+open);
            System.out.println(tmpFile.getName());
            if (!tmpFile.exists()) {
                System.out.println("Path incorrect");
            } else {
                uploadPath = uploadPath + "\\" + open;
            }
        }
    }

    private static void uploadAdd() throws Exception {
        File file = new File(uploadPath);
        if (!file.exists()) {
            throw new Exception("File or directory does not exist");
        }
        uploadFileList.add(file);
    }

    private static void uploadSend() throws IOException {
        function = "upload";
        commandDos.writeUTF("up-sd");
        int port = commandDis.readInt();
        int count = getFileCount(uploadFileList);
        commandDos.writeInt(count);
        iterSend(uploadFileList, port);

        // clear the fileList to empty and restore path
        uploadFileList = new ArrayList<>();
        uploadPath = "C:\\Users\\ZZM2021\\Desktop\\Upload";
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

    private static void uploadProgress() throws IOException {
        commandDos.writeUTF("up-pg");
        while(true) {
            String progress = commandDis.readUTF();
            if (progress.equals("stop")) {
                break;
            }
            System.out.println(progress);
        }
    }

    private static void uploadPause() throws IOException {
        commandDos.writeUTF("up-pa");
        System.out.println("Enter the files you want to pause: ");
        String fileNames = scanner.next();
        commandDos.writeUTF(fileNames);
        while (true) {
            String result = commandDis.readUTF();
            if (result.equals("end")) {
                break;
            }
            System.out.println(result);
        }
    }

    private static void uploadResume() throws IOException {
        commandDos.writeUTF("up-re");
        System.out.println("Enter the files you want to resume: ");
        String fileNames = scanner.next();
        commandDos.writeUTF(fileNames);
        while (true) {
            String result = commandDis.readUTF();
            if (result.equals("end")) {
                break;
            }
            System.out.println(result);
        }
    }

    private static void uploadCancel() throws IOException {
        commandDos.writeUTF("up-ca");
        System.out.println("Enter the files you want to cancel: ");
        String fileNames = scanner.next();
        commandDos.writeUTF(fileNames);
    }

    private static void iterSend(List<File> fileList, int port) throws IOException {
        for (File tmpFile : fileList) {
            if (tmpFile.isFile()) {
                // send a file
                commandDos.writeLong(tmpFile.length());
                String signal = commandDis.readUTF();
                if (!signal.equals("ready")) {
                    System.out.println("wrong signal");
                }
                Socket socket = new Socket(ip, port);
                FtpClient temp = new FtpClient(tmpFile, socket, 0);
                new Thread(temp).start();
            } else {
                // send a directory
                List<File> fileInDir = Arrays.asList(Objects.requireNonNull(tmpFile.listFiles()));
                iterSend(fileInDir, port);
            }
        }
    }

    private static File extractFile(String filePath) throws IOException {
        String temp = filePath.split("Download" + "\\\\")[1];
        String fileName = filePath.substring(filePath.lastIndexOf("\\") + 1);
        String[] tempArray = temp.split(fileName);
        String dirPath;
        if (tempArray.length > 0) {
            // file is in directory
            dirPath = "C:\\Users\\ZZM2021\\Desktop\\Resources\\" + tempArray[0];
            File dirFile = new File(dirPath);
            dirFile.mkdirs();
        } else {
            // file is not in directory
            dirPath = "C:\\Users\\ZZM2021\\Desktop\\Resources\\";
        }

        File file = new File(dirPath + fileName);
        file.createNewFile();
        return file;
    }

    private void download() {
        try {
            // receive the file path
            String filePath = dis.readUTF();
            downloadFile = extractFile(filePath);
            String fileName = downloadFile.getName();

            // receive file content
            OutputStream os = new FileOutputStream(downloadFile);
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
                    this.byteNum += len;
                }
            }
            os.close();
            if (!cancelled) {
                System.out.println("Successfully download file " + fileName);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private static void downloadLs() throws IOException {
        commandDos.writeUTF("dl-ls");
        while (true) {
            String ls = commandDis.readUTF();
            if (ls.equals("stop")) {
                break;
            }
            System.out.println(ls);
        }
    }

    private static void downloadCd() throws IOException {
        commandDos.writeUTF("dl-cd");
        String downloadPath = commandDis.readUTF();
        System.out.println(downloadPath);
        System.out.println("Enter file or directory:");
        String open = scanner.next();
        commandDos.writeUTF(open);
        String status = commandDis.readUTF();
        if (status.equals("NO")) {
            System.out.println("Path incorrect");
        }
    }

    private static void downloadAdd() throws Exception {
        commandDos.writeUTF("dl-add");
        String status = commandDis.readUTF();
        if (status.equals("NO")) {
            System.out.println("File or directory does not exist");
        }
    }

    private static void downloadSend() throws IOException {
        function = "download";
        commandDos.writeUTF("dl-sd");
        int port = commandDis.readInt();
        int fileNum = commandDis.readInt();
        ServerSocket serverSocket = new ServerSocket(port);
        for (int i=0; i<fileNum; i++) {
            long fileSize = commandDis.readLong();
            commandDos.writeUTF("ready");
            Socket clientSocket = serverSocket.accept();
            FtpClient obj = new FtpClient(null, clientSocket, fileSize);
            downloadObjList.add(obj);
            new Thread(obj).start();
        }
        serverSocket.close();
    }

    private static void downloadProgress() {
        for (FtpClient obj : downloadObjList) {
            if (obj.byteNum < obj.fileSize) {
                System.out.println(obj.downloadFile.getName() + ": " + obj.byteNum + "/" + obj.fileSize + " bytes sent. " + "Status: " + (obj.paused ? "Paused" : "Sending"));
            }
        }
    }

    private static void downloadPause() {
        System.out.println("Enter the files you want to pause");
        String[] fileNames = scanner.next().split(",");
        for (FtpClient obj : downloadObjList) {
            if (Arrays.asList(fileNames).contains(obj.downloadFile.getName())) {
                obj.pause();
                System.out.println(obj.downloadFile.getName() + " has been paused");
            }
        }
    }

    private static void downloadResume() {
        System.out.println("Enter the files you want to resume");
        String[] fileNames = scanner.next().split(",");
        for (FtpClient obj : downloadObjList) {
            if (Arrays.asList(fileNames).contains(obj.downloadFile.getName())) {
                if (!obj.paused) {
                    System.out.println(obj.downloadFile.getName() + " can not be resumed");
                } else {
                    obj.resume();
                    System.out.println(obj.downloadFile.getName() + " has been resumed");
                }
            }
        }
    }

    private static void downloadCancel() {
        System.out.println("Enter the files you want to cancel uploading");
        String[] fileNames = scanner.next().split(",");
        ArrayList<FtpClient> toDelete = new ArrayList<>();
        for (FtpClient obj : downloadObjList) {
            if (Arrays.asList(fileNames).contains(obj.downloadFile.getName())) {
                obj.cancel();
                obj.downloadFile.delete();
                toDelete.add(obj);
                System.out.println(obj.downloadFile.getName() + "has been cancelled uploading");
            }
        }
        downloadObjList.removeAll(toDelete);
    }
    private void closeAll() throws IOException {
        this.clientSocket.close();
        this.dis.close();
        this.dos.close();
    }

    public static void main(String[] args) throws Exception {
        ftpRun();
    }
}