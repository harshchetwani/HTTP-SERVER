import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        String directory = null;
        if ((args.length == 2) && (args[0].equalsIgnoreCase("--directory"))) {
            directory = args[1];
        }
        try (ServerSocket serverSocket = new ServerSocket(4221)) {
            serverSocket.setReuseAddress(true);
            while (true) {
                Socket clientSocket = serverSocket.accept();
                final String finalDirectory = directory;
                new Thread(() -> handleClient(clientSocket, finalDirectory)).start();
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket, String directory) {
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             OutputStream outputStream = clientSocket.getOutputStream()) {

            String requestLine = reader.readLine();
            if (requestLine == null) {
                return; // Connection closed or invalid request
            }
            String[] requestParts = requestLine.split(" ");
            String method = requestParts[0];
            String path = requestParts[1];
            String userAgent = "";
            String line;

            // Read headers
            while (!(line = reader.readLine()).equals("")) {
                if (line.startsWith("User-Agent: ")) {
                    userAgent = line.substring(12);
                }
            }

            if (method.equals("POST") && path.startsWith("/files/")) {
                String fileName = path.substring(7);
                Path filePath = Paths.get(directory, fileName);

                // Read Content-Length header
                int contentLength = 0;
                while (!(line = reader.readLine()).equals("")) {
                    if (line.startsWith("Content-Length: ")) {
                        contentLength = Integer.parseInt(line.substring(16));
                    }
                }

                // Read the body
                char[] body = new char[contentLength];
                reader.read(body, 0, contentLength);
                String fileContent = new String(body);

                // Write the content to the file
                Files.write(filePath, fileContent.getBytes());

                String response = "HTTP/1.1 201 Created\r\n\r\n";
                outputStream.write(response.getBytes());
            } else if (path.startsWith("/files/")) {
                String fileName = path.substring(7);
                Path filePath = Paths.get(directory, fileName);
                if (Files.exists(filePath)) {
                    byte[] fileBytes = Files.readAllBytes(filePath);
                    String response =
                        "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: " +
                        fileBytes.length + "\r\n\r\n";
                    outputStream.write(response.getBytes());
                    outputStream.write(fileBytes);
                } else {
                    outputStream.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
                }
            } else if (path.startsWith("/user-agent")) {
                String response =
                    "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " +
                    userAgent.length() + "\r\n\r\n" + userAgent;
                outputStream.write(response.getBytes());
            } else if (path.startsWith("/echo/")) {
                String randomString = path.substring(6);
                String response =
                    "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " +
                    randomString.length() + "\r\n\r\n" + randomString;
                outputStream.write(response.getBytes());
            } else if (path.equals("/")) {
                outputStream.write("HTTP/1.1 200 OK\r\n\r\n".getBytes());
            } else {
                outputStream.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
            }

            outputStream.flush();
            System.out.println("Handled connection");

        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("IOException: " + e.getMessage());
            }
        }
    }
}
