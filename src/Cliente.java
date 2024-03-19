/*
Práctica 1: Aplicacion de almacenamiento remoto de archivos
Creado por: Cornejo García David y Flores Melo Alan Nicolás
Código: Cliente V1.0
*/

import java.io.*;
import java.net.*;
import java.nio.file.*;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public class Cliente {
    private Socket commandSocket;
    private PrintWriter out;
    private BufferedReader in;
    private Socket dataSocket;
    private Path currentDirectory = Paths.get(System.getProperty("user.dir"));
    private final String serverAddress;
    private final int serverPort;
    private final int dataPort;

    // Constructor de la clase Cliente con los parámetros de dirección IP, puerto de comandos y puerto de datos
    public Cliente(String ip, int port, int dataPort) {
        this.serverAddress = ip;
        this.serverPort = port;
        this.dataPort = dataPort;
    }

    // Método para iniciar la conexión con el servidor y crear los flujos de entrada y salida
    public void startConnection() {
        try { // Intenta crear el socket de comandos y los flujos de entrada y salida
            commandSocket = new Socket(serverAddress, serverPort);
            out = new PrintWriter(commandSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(commandSocket.getInputStream()));
            System.out.println("Conectado al servidor.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Método para enviar comandos al servidor a través del flujo de salida del socket de comandos
    public void sendCommand(String command) {
        out.println(command);
    }

    // Método para enviar archivos al servidor a través del socket de datos
    private void sendData(File file) throws IOException {
        OutputStream outStream = dataSocket.getOutputStream();
        Files.copy(file.toPath(), outStream); // Copia el archivo al flujo de salida del socket
        outStream.close();
    }

    // Método para recibir archivos o directorios del servidor a través del socket de datos
    private void receiveData(String fileName) throws IOException {
        Path filePath = currentDirectory.resolve(fileName);
        try (InputStream inStream = dataSocket.getInputStream()) { // Crea un flujo de entrada para recibir el archivo
            Files.copy(inStream, filePath, StandardCopyOption.REPLACE_EXISTING);
        }
        if (fileName.endsWith(".zip")) {
            System.out.println("Carpeta recibida desde el servidor.");
        } else {
            System.out.println("Archivo recibido desde el servidor.");;
        }
    }

    // Método para manejar los comandos ingresados por el usuario
    public void handleCommand(String command) {
        try {
            if (command.startsWith("list")) { // Si el comando es "list", se envía al servidor y se imprime la lista de archivos
                sendCommand(command);
                String line;
                while (!(line = in.readLine()).equals("END-OF-LIST")) {
                    System.out.println(line);
                }
            } else if (command.startsWith("put")) { // Si el comando es "put", se envía el archivo al servidor a través del socket de datos
                String[] parts = command.split(" ", 2); // Divide el comando en dos partes, el comando y el nombre del archivo
                if (parts.length > 1) {
                    dataSocket = new Socket(serverAddress, dataPort);
                    File file = currentDirectory.resolve(parts[1]).toFile(); // Obtiene el archivo a partir del nombre ingresado
                    if (file.exists()) {
                        if (file.isDirectory()) { // Si el archivo es un directorio, se comprime y se envía al servidor
                            String zipFileName = file.getName() + ".zip";
                            Path zipPath = currentDirectory.resolve(zipFileName);
                            compressDirectory(file.toPath(), zipPath);

                            // Envía el comando con el nombre del archivo .zip
                            sendCommand("put " + zipFileName);

                            sendData(zipPath.toFile());
                            System.out.println("Directorio comprimido y enviado al servidor.");
                            Files.delete(zipPath); // Borra el archivo .zip temporal

                        } else { // Si el archivo es un archivo regular, se envía al servidor
                            sendCommand(command);
                            sendData(file);
                            System.out.println("Archivo enviado al servidor.");;
                        }
                        dataSocket.close();
                    } else {
                        System.out.println("Archivo no encontrado.");
                    }
                }
            } else if (command.startsWith("get")) { // Si el comando es "get", se recibe el archivo del servidor a través del socket de datos
                String[] parts = command.split(" ", 2);
                if (parts.length > 1) {
                    sendCommand(command);
                    String fileInfo = in.readLine();
                    dataSocket = new Socket(serverAddress, dataPort);
                    receiveData(fileInfo);
                    dataSocket.close();

                }
            } else { // Si el comando no es "list", "put" o "get", se envía al servidor y se imprime la respuesta
                sendCommand(command);
                System.out.println(in.readLine());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Método de compresión de directorio a archivo .zip
    private void compressDirectory(Path directoryToZip, Path zipPath) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(new FileOutputStream(zipPath.toFile()));
             Stream<Path> paths = Files.walk(directoryToZip)) {
            paths.filter(path -> !Files.isDirectory(path))
                    .forEach(path -> {
                        ZipEntry zipEntry = new ZipEntry(directoryToZip.relativize(path).toString());
                        try {
                            zos.putNextEntry(zipEntry);
                            Files.copy(path, zos);
                            zos.closeEntry();
                        } catch (IOException e) {
                            System.err.println("Error while zipping: " + e.getMessage());
                        }
                    });
        }
    }

    // Método para detener la conexión con el servidor
    public void stopConnection() {
        try { // Intenta cerrar los flujos de entrada y salida y el socket de comandos
            if (in != null) in.close();
            if (out != null) out.close();
            if (commandSocket != null) commandSocket.close();
            System.out.println("Desconectado del servidor.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    // Método principal de la clase Cliente en donde se crea un objeto Cliente y se manejan los comandos ingresados por el usuario
    public static void main(String[] args) {
        Cliente client = new Cliente("127.0.0.1", 5555, 5556);
        client.startConnection();

        try (BufferedReader consoleReader = new BufferedReader(new InputStreamReader(System.in))) {
            String userInput;
            System.out.println("Ingrese comandos (quit para terminar):");
            while (!(userInput = consoleReader.readLine()).equals("quit")) {
                client.handleCommand(userInput);
            }
            client.sendCommand("quit");
            //System.out.println("Respuesta del servidor: " + client.in.readLine());
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            client.stopConnection();
        }
    }
}
