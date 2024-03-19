import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.stream.*;

public class Servidor {
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private Path currentDirectory = Paths.get(System.getProperty("user.dir"));
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
        System.out.println("Servidor iniciado en el puerto " + serverSocket.getLocalPort() + ". Esperando clientes...");
        try {
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
            System.out.println("Error al manejar la conexión del cliente: " + e.getMessage());
        } finally {
            stop();
        }
    }
    private void processCommand(String command) {
        String[] parts = command.split(" ", 2);
        String response = "";
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
                    acceptFile(parts.length > 1 ? parts[1] : "");
                    break;
                case "get":
                    sendFile(parts.length > 1 ? parts[1] : "");
                    break;
                default:
                    response = "Comando no reconocido.";
                    break;
            }
        } catch (Exception e) {
            response = "Error al procesar el comando: " + e.getMessage();
        }
        out.println(response); // Envía la respuesta final al cliente
    }

    private String listDirectory(Path directory) throws IOException {
        StringBuilder response = new StringBuilder();
        try (Stream<Path> paths = Files.list(directory)) {
            paths.map(path -> path.getFileName().toString())
                    .forEach(filename -> response.append(filename).append("\n"));
        }
        response.append("END-OF-LIST");
        return response.toString();
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

    private void acceptFile(String fileName) {
        try {
            dataSocket = dataServerSocket.accept();
            InputStream dataIn = new BufferedInputStream(dataSocket.getInputStream());
            Files.copy(dataIn, currentDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
            dataIn.close();
            System.out.println("Archivo " + fileName + " recibido.");
        } catch (IOException e) {
            System.out.println("Error al recibir el archivo: " + e.getMessage());
        } finally {
            try {
                if (dataSocket != null && !dataSocket.isClosed()) {
                    dataSocket.close();
                }
            } catch (IOException e) {
                System.out.println("Error al cerrar la conexión de datos: " + e.getMessage());
            }
        }
    }

    private void sendFile(String fileName) {
        Path filePath = currentDirectory.resolve(fileName);
        if (!Files.exists(filePath)) {
            out.println("Archivo no encontrado.");
            return;
        }
        try {
            dataSocket = dataServerSocket.accept();
            OutputStream dataOut = new BufferedOutputStream(dataSocket.getOutputStream());
            Files.copy(filePath, dataOut);
            dataOut.close();
            System.out.println("Archivo " + fileName + " enviado.");
        } catch (IOException e) {
            System.out.println("Error al enviar el archivo: " + e.getMessage());
        } finally {
            try {
                if (dataSocket != null && !dataSocket.isClosed()) {
                    dataSocket.close();
                }
            } catch (IOException e) {
                System.out.println("Error al cerrar la conexión de datos: " + e.getMessage());
            }
        }
    }

    public void stop() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
            System.out.println("Servidor detenido.");
        } catch (IOException e) {
            System.out.println("Error al cerrar la conexión del servidor: " + e.getMessage());
        }
    }

    public static void main(String[] args) {
        Servidor server = new Servidor(5555, 5556);
        server.start();
    }
}