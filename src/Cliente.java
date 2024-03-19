import java.io.*;
import java.net.*;
import java.nio.file.*;

public class Cliente {
    private Socket commandSocket;
    private PrintWriter out;
    private BufferedReader in;
    private Socket dataSocket;
    private Path currentDirectory = Paths.get(System.getProperty("user.dir"));
    private final String serverAddress;
    private final int serverPort;
    private final int dataPort;

    public Cliente(String ip, int port, int dataPort) {
        this.serverAddress = ip;
        this.serverPort = port;
        this.dataPort = dataPort;
    }

    public void startConnection() {
        try {
            commandSocket = new Socket(serverAddress, serverPort);
            out = new PrintWriter(commandSocket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(commandSocket.getInputStream()));
            System.out.println("Conectado al servidor.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void sendCommand(String command) {
        out.println(command);
    }

    private void sendData(File file) throws IOException {
        OutputStream outStream = dataSocket.getOutputStream();
        Files.copy(file.toPath(), outStream);
        outStream.close();
    }

    private void receiveData(String fileName) throws IOException {
        InputStream inStream = dataSocket.getInputStream();
        Files.copy(inStream, currentDirectory.resolve(fileName), StandardCopyOption.REPLACE_EXISTING);
        inStream.close();
    }

    public void handleCommand(String command) {
        try {
            if (command.startsWith("list")) {
                sendCommand(command);
                String line;
                while (!(line = in.readLine()).equals("END-OF-LIST")) {
                    System.out.println(line);
                }
            } else if (command.startsWith("put")) {
                String[] parts = command.split(" ", 2);
                if (parts.length > 1) {
                    sendCommand(command);
                    dataSocket = new Socket(serverAddress, dataPort);
                    File file = currentDirectory.resolve(parts[1]).toFile();
                    if (file.exists()) {
                        sendData(file);
                        System.out.println("Archivo enviado al servidor.");
                    } else {
                        dataSocket.close();
                        System.out.println("Archivo no encontrado.");
                    }
                    dataSocket.close();
                }
            } else if (command.startsWith("get")) {
                String[] parts = command.split(" ", 2);
                if (parts.length > 1) {
                    sendCommand(command);
                    dataSocket = new Socket(serverAddress, dataPort);
                    receiveData(parts[1]);
                    System.out.println("Archivo recibido del servidor.");
                    dataSocket.close();
                }
            } else {
                sendCommand(command);
                System.out.println(in.readLine());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public void stopConnection() {
        try {
            if (in != null) in.close();
            if (out != null) out.close();
            if (commandSocket != null) commandSocket.close();
            System.out.println("Desconectado del servidor.");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

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
