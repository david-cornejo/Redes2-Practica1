/*
Práctica 1: Aplicacion de almacenamiento remoto de archivos
Creado por: Cornejo García David y Flores Melo Alan Nicolás
Código: Servidor V1.0
*/

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.stream.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Servidor {
    private ServerSocket serverSocket;
    private Socket clientSocket;
    private PrintWriter out;
    private BufferedReader in;
    private Path currentDirectory = Paths.get(System.getProperty("user.dir"));
    private ServerSocket dataServerSocket;
    private Socket dataSocket;

    //Se crean los sockets del servidor en el puerto 5555 para comandos y 5556 para datos
    public Servidor(int port, int dataPort) {
        try {
            serverSocket = new ServerSocket(port);
            dataServerSocket = new ServerSocket(dataPort);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    //metodo para iniciar el servidor y esperar a que un cliente se conecte
    public void start() {
        System.out.println("Servidor iniciado en el puerto " + serverSocket.getLocalPort() + ". Esperando clientes...");
        try {
            clientSocket = serverSocket.accept();
            System.out.println("Cliente conectado: " + clientSocket.getInetAddress());  // Muestra la dirección IP del cliente conectado
            out = new PrintWriter(clientSocket.getOutputStream(), true); // Se crea el flujo de salida para enviar mensajes al cliente
            in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream())); // Se crea el flujo de entrada para recibir mensajes del cliente
            String inputLine;
            while ((inputLine = in.readLine()) != null) { // Espera a que el cliente envíe un mensaje
                if ("quit".equals(inputLine)) { // Si el cliente envía "quit", se termina la sesión
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

    //procesamiento de los comandos que el cliente envia al servidor
    private void processCommand(String command) {
        String[] parts = command.split(" ", 2);
        String response = "";
        try {
            switch (parts[0]) {
                case "list": // Lista los archivos y directorios del directorio actual
                    response = listDirectory(currentDirectory);
                    break;
                case "mkdir": // Crea un directorio
                    response = createDirectory(parts.length > 1 ? parts[1] : ""); // Si el comando tiene más de una palabra, la segunda palabra es el nombre del directorio
                    break;
                case "rmdir": // Elimina un directorio o archivo
                    response = removeDirectoryOrFile(parts.length > 1 ? parts[1] : "");
                    break;
                case "cd": // Cambia de directorio
                    response = changeDirectory(parts.length > 1 ? parts[1] : "");
                    break;
                case "put": // Recibe un archivo el servidor
                    acceptFile(parts.length > 1 ? parts[1] : "");
                    break;
                case "get": // Envía un archivo al cliente
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

    //metodo para listar los archivos y directorios del directorio actual
    private String listDirectory(Path directory) throws IOException {
        StringBuilder response = new StringBuilder(); // Se crea un StringBuilder para concatenar los nombres de los archivos y directorios
        try (Stream<Path> paths = Files.list(directory)) { // Se obtiene un Stream de los archivos y directorios del directorio actual
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

    //metodo para eliminar un directorio o archivo
    private String removeDirectoryOrFile(String name) {
        if (name.isEmpty()) {
            return "Nombre de archivo o directorio no especificado.";
        }
        Path path = currentDirectory.resolve(name); // Se obtiene el Path del archivo o directorio a eliminar
        try {
            Files.deleteIfExists(path); // Se intenta eliminar el archivo o directorio
            return "Eliminado: " + path;
        } catch (IOException e) {
            return "Error al eliminar: " + e.getMessage();
        }
    }

    //metodo para cambiar de directorio
    private String changeDirectory(String dirName) {
        if (dirName.isEmpty() || "..".equals(dirName)) { // Si el nombre del directorio está vacío o es "..", se sube un nivel
            currentDirectory = currentDirectory.getParent();
            if (currentDirectory == null) {
                currentDirectory = Paths.get(System.getProperty("user.dir"));
            }
            return "Directorio actual: " + currentDirectory;
        }

        Path newPath = currentDirectory.resolve(dirName);
        if (Files.exists(newPath) && Files.isDirectory(newPath)) { // Si el directorio existe y es un directorio, se cambia
            currentDirectory = newPath;
            return "Directorio cambiado a " + newPath;
        } else {
            return "Directorio no encontrado: " + newPath;
        }
    }

    //metodo para recibir un archivo del cliente
    private void acceptFile(String fileName) {
        try { // Se crea un nuevo socket para la conexión de datos
            dataSocket = dataServerSocket.accept();
            InputStream dataIn = new BufferedInputStream(dataSocket.getInputStream()); // Se crea un flujo de entrada para recibir el archivo
            Files.copy(dataIn, currentDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING); // Se copia el archivo al directorio actual
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

    //metodo para enviar un archivo al cliente
    private void sendFile(String fileName) throws IOException {
        Path fileToSend = currentDirectory.resolve(fileName); // Se obtiene el Path del archivo a enviar

        boolean isDirectory = Files.isDirectory(fileToSend); // Se verifica si el archivo es un directorio

        if (!Files.exists(fileToSend)) {
            out.println("Archivo no encontrado.");
        }

        if (isDirectory) { // Si el archivo es un directorio, se comprime
            fileName = fileName + ".zip";
            fileToSend = zipDirectory(fileToSend, fileName);
        }

        // Envía metadatos sobre el archivo a enviar para identificarlo en el cliente
        out.println(fileName);

        // Procede a enviar el archivo
        try {
            dataSocket = dataServerSocket.accept(); // Se crea un nuevo socket para la conexión de datos
            OutputStream dataOut = new BufferedOutputStream(dataSocket.getOutputStream()); // Se crea un flujo de salida para enviar el archivo
            Files.copy(fileToSend, dataOut);
            if(fileName.endsWith(".zip")) { // Si el archivo es un directorio comprimido, se borra el archivo .zip temporal
                Files.delete(fileToSend);
            }
            dataOut.flush();
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

    //metodo para comprimir un directorio
    private Path zipDirectory(Path directory, String zipFileName) throws IOException {
        Path zipFilePath = currentDirectory.resolve(zipFileName);
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipFilePath.toFile()));
             Stream<Path> paths = Files.walk(directory)) { // Se obtiene un Stream de los archivos y directorios del directorio a comprimir
            paths.filter(path -> !Files.isDirectory(path)) // Se filtran los archivos
                    .forEach(path -> { // Se crea un ZipEntry para cada archivo y se copia al archivo .zip
                        ZipEntry zipEntry = new ZipEntry(directory.relativize(path).toString());
                        try {
                            zos.putNextEntry(zipEntry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            System.err.println("Error while zipping: " + e.getMessage());
                        }
                    });
        }
        return zipFilePath; // Se devuelve el Path del archivo .zip
    }

    //metodo para detener el servidor
    public void stop() {
        try { // Se cierran los flujos y sockets
            if (in != null) in.close();
            if (out != null) out.close();
            if (clientSocket != null && !clientSocket.isClosed()) clientSocket.close();
            if (serverSocket != null && !serverSocket.isClosed()) serverSocket.close();
            System.out.println("Servidor detenido.");
        } catch (IOException e) {
            System.out.println("Error al cerrar la conexión del servidor: " + e.getMessage());
        }
    }

    //metodo main para iniciar el servidor donde indicamos los puertos para comandos y datos
    public static void main(String[] args) {
        Servidor server = new Servidor(5555, 5556);
        server.start();
    }
}