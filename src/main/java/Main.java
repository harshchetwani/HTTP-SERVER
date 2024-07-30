import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.io.File;

public class Main {
    public static void main(String[] args) {
        System.out.println("Logs from your program will appear here!");

        try (ServerSocket serverSocket = new ServerSocket(4221)) {
            serverSocket.setReuseAddress(true);
            System.out.println("Server is listening on port 4221");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Accepted new connection");

                // Create a new thread to handle the client connection
                new Thread(new ClientHandler(clientSocket)).start();
            }
        } catch (IOException e) {
            System.out.println("Server exception: " + e.getMessage());
            e.printStackTrace();
        }
    }
}

class ClientHandler implements Runnable {
    private Socket clientSocket;

    public ClientHandler(Socket socket) {
        this.clientSocket = socket;
    }

    @Override
    public void run() {
        try (InputStream input = clientSocket.getInputStream();
             BufferedReader reader = new BufferedReader(new InputStreamReader(input));
             OutputStream output = clientSocket.getOutputStream()) {

            String line = reader.readLine();
            if (line == null) {
                return;
            }
            System.out.println("Received request: " + line); // Debugging line
            String[] HttpRequest = line.split(" ");
            String[] str = HttpRequest[1].split("/");

            if (HttpRequest[1].equals("/")) {
                String response = "HTTP/1.1 200 OK\r\n"
                                + "Content-Type: text/plain\r\n"
                                + "Content-Length: 0\r\n\r\n";
                output.write(response.getBytes());
            } else if (str[1].equals("user-agent")) {
                reader.readLine();
                String useragent = reader.readLine().split("\\s+")[1];
                String reply = String.format(
                    "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: %s\r\n\r\n%s\r\n",
                    useragent.length(), useragent);
                output.write(reply.getBytes());
            } else if (str.length > 2 && str[1].equals("echo")) {
                String responsebody = str[2];
                String finalstr = "HTTP/1.1 200 OK\r\n"
                                + "Content-Type: text/plain\r\n"
                                + "Content-Length: " + responsebody.length() +
                                "\r\n\r\n" + responsebody;
                output.write(finalstr.getBytes());
            } else if (str.length > 1 && str[1].equals("files")) {
                String filename = HttpRequest[1].substring("/files/".length());
                File file = new File(filename);

                System.out.println("Checking file: " + file.getAbsolutePath()); // Debugging line
                if (file.exists() && !file.isDirectory()) {
                    byte[] fileContent = Files.readAllBytes(file.toPath());
                    String responseHeader = "HTTP/1.1 200 OK\r\n"
                                          + "Content-Type: application/octet-stream\r\n"
                                          + "Content-Length: " + fileContent.length + "\r\n\r\n";
                    output.write(responseHeader.getBytes());
                    output.write(fileContent);
                } else {
                    System.out.println("File not found: " + filename);
                    String notFoundResponse = "HTTP/1.1 404 Not Found\r\n\r\n";
                    output.write(notFoundResponse.getBytes());
                }
            } else {
                output.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
            }

            output.flush();
            System.out.println("Handled connection");

        } catch (IOException e) {
            System.out.println("ClientHandler exception: " + e.getMessage());
            e.printStackTrace();
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("ClientSocket close exception: " + e.getMessage());
            }
        }
    }
}
