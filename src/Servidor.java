import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.stream.*;

public class Servidor {
    //socket de comandos
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private Path currentDirectory = Paths.get(System.getProperty("user.dir"));
    //Socket de datos
    private ServerSocket dataServerSocket;
    private Socket dataSocket;

    public Servidor(int port, int dataPort) {
        try {
            serverSocket = new ServerSocket(port);
            dataServerSocket = new ServerSocket(dataPort);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void start() {
        try {
            System.out.println("Servidor iniciado en el puerto " + serverSocket.getLocalPort() + ". Esperando clientes...");
            clientSocket = serverSocket.accept();
            System.out.println("Cliente conectado: " + clientSocket.getInetAddress());
            out = new PrintWriter(clientSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));

            String inputLine;
            while ((inputLine = in.readLine()) != null) {
                if ("quit".equals(inputLine)) {
                    out.println("Terminando sesión...");
                    break;
                }
                processCommand(inputLine);
            }
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            stop();
        }
    }

    private void processCommand(String command) {
        String[] parts = command.split(" ", 2);
        String response;
        try {
            switch (parts[0]) {
                case "list":
                    response = listDirectory(currentDirectory);
                    break;
                case "mkdir":
                    response = createDirectory(parts.length > 1 ? parts[1] : "");
                    break;
                case "rmdir":
                    response = removeDirectoryOrFile(parts.length > 1 ? parts[1] : "");
                    break;
                case "cd":
                    response = changeDirectory(parts.length > 1 ? parts[1] : "");
                    break;
                case "put":
                    acceptFile(parts[1]);
                    response = "Archivo recibido.";
                    break;
                case "get":
                    sendFile(parts[1]);
                    response = "Archivo enviado.";
                    break;
                default:
                    response = "Comando no reconocido.";
                    break;
            }
        } catch (Exception e) {
            response = "Error al procesar el comando: " + e.getMessage();
        }
        out.println(response);
    }

    private String listDirectory(Path directory) throws IOException {
        try (Stream<Path> paths = Files.list(directory)) {
            String fileList = paths.map(path -> path.getFileName().toString())
                    .collect(Collectors.joining("\n"));
            return fileList.isEmpty() ? "El directorio está vacío" : fileList;
        }
    }

    private String createDirectory(String dirName) {
        if (dirName.isEmpty()) {
            return "Nombre de directorio no especificado.";
        }
        Path dirPath = currentDirectory.resolve(dirName);
        try {
            Files.createDirectories(dirPath);
            return "Directorio creado: " + dirPath;
        } catch (IOException e) {
            return "Error al crear el directorio: " + e.getMessage();
        }
    }

    private String removeDirectoryOrFile(String name) {
        if (name.isEmpty()) {
            return "Nombre de archivo o directorio no especificado.";
        }
        Path path = currentDirectory.resolve(name);
        try {
            Files.deleteIfExists(path);
            return "Eliminado: " + path;
        } catch (IOException e) {
            return "Error al eliminar: " + e.getMessage();
        }
    }

    private String changeDirectory(String dirName) {
        if (dirName.isEmpty() || "..".equals(dirName)) {
            currentDirectory = currentDirectory.getParent();
            if (currentDirectory == null) {
                currentDirectory = Paths.get(System.getProperty("user.dir"));
            }
            return "Directorio actual: " + currentDirectory;
        }

        Path newPath = currentDirectory.resolve(dirName);
        if (Files.exists(newPath) && Files.isDirectory(newPath)) {
            currentDirectory = newPath;
            return "Directorio cambiado a " + newPath;
        } else {
            return "Directorio no encontrado: " + newPath;
        }
    }

    private void acceptFile(String fileName) throws IOException {
        dataSocket = dataServerSocket.accept();
        InputStream dataIn = dataSocket.getInputStream();
        Files.copy(dataIn, currentDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        dataIn.close();
        dataSocket.close();
    }

    private void sendFile(String fileName) throws IOException {
        Path filePath = currentDirectory.resolve(fileName);
        if (!Files.exists(filePath)) {
            out.println("Archivo no encontrado.");
            return;
        }
        dataSocket = dataServerSocket.accept();
        OutputStream dataOut = dataSocket.getOutputStream();
        Files.copy(filePath, dataOut);
        dataOut.close();
        dataSocket.close();
    }

    public void stop() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null) clientSocket.close();
            if (serverSocket != null) serverSocket.close();
            System.out.println("Servidor detenido.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        Servidor server = new Servidor(5555,5556);
        server.start();
    }
}
