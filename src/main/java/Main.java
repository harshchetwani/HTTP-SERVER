import java.io.*;
import java.nio.file.Files;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.HashMap;
import java.util.Map;
import java.util.zip.DeflaterOutputStream;
import java.util.zip.GZIPOutputStream;

public class Main {
    private static String directory;

    public static void main(String[] args) {
        if (args.length > 1 && args[0].equals("--directory")) {
            directory = args[1];
        }
        System.out.println("Logs from your program will appear here!");
        try (ServerSocket serverSocket = new ServerSocket(4221)) {
            serverSocket.setReuseAddress(true);
            while (true) {
                Socket clientSocket = serverSocket.accept(); // Wait for connection from client.
                System.out.println("Accepted new connection");
                // Handle each client connection in a separate thread.
                new Thread(() -> handleClient(clientSocket)).start();
            }
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        }
    }

    private static void handleClient(Socket clientSocket) {
        try {
            BufferedReader inputStream = new BufferedReader(
                    new InputStreamReader(clientSocket.getInputStream()));
            // Read the request line
            String requestLine = inputStream.readLine();
            String httpMethod = requestLine.split(" ")[0];
            // Read all the headers from the HTTP request.
            Map<String, String> headers = new HashMap<>();
            String headerLine;
            while (!(headerLine = inputStream.readLine()).isEmpty()) {
                String[] headerParts = headerLine.split(": ");
                headers.put(headerParts[0], headerParts[1]);
            }
            // Extract the URL path from the request line.
            String urlPath = requestLine.split(" ")[1];
            OutputStream outputStream = clientSocket.getOutputStream();
            // Write the HTTP response to the output stream.
            String httpResponse = getHttpResponse(httpMethod, urlPath, headers, inputStream);
            System.out.println("Sending response: " + httpResponse);
            outputStream.write(httpResponse.getBytes("UTF-8"));
            // Close the input and output streams.
            inputStream.close();
            outputStream.close();
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        } finally {
            // Close the client socket.
            try {
                if (clientSocket != null) {
                    clientSocket.close();
                }
            } catch (IOException e) {
                System.out.println("IOException: " + e.getMessage());
            }
        }
    }

    private static String getHttpResponse(String httpMethod, String urlPath,
                                          Map<String, String> headers,
                                          BufferedReader inputStream)
            throws IOException {
        String responseBody;
        String status;
        String contentType = "text/plain";
        boolean gzip = headers.getOrDefault("Accept-Encoding", "").contains("gzip");
        boolean deflate = headers.getOrDefault("Accept-Encoding", "").contains("deflate");

        if ("GET".equals(httpMethod)) {
            if ("/".equals(urlPath)) {
                responseBody = "Hello, World!";
                status = "200 OK";
            } else if (urlPath.startsWith("/echo/")) {
                responseBody = urlPath.substring(6); // Extract the string after "/echo/"
                status = "200 OK";
            } else if ("/user-agent".equals(urlPath)) {
                responseBody = headers.getOrDefault("User-Agent", "Unknown User-Agent");
                status = "200 OK";
            } else if (urlPath.startsWith("/files/")) {
                String filename = urlPath.substring(7); // Extract the filename after "/files/"
                File file = new File(directory, filename);
                if (file.exists()) {
                    responseBody = new String(Files.readAllBytes(file.toPath()));
                    contentType = "application/octet-stream";
                    status = "200 OK";
                } else {
                    responseBody = "";
                    status = "404 Not Found";
                }
            } else {
                responseBody = "";
                status = "404 Not Found";
            }
        } else if ("POST".equals(httpMethod) && urlPath.startsWith("/files/")) {
            String filename = urlPath.substring(7); // Extract the filename after "/files/"
            System.out.println("filename: " + filename);
            File file = new File(directory, filename);
            if (!file.getCanonicalPath().startsWith(new File(directory).getCanonicalPath())) {
                responseBody = "";
                status = "403 Forbidden";
            } else {
                // Get the length of the request body
                int contentLength = Integer.parseInt(headers.get("Content-Length"));
                char[] buffer = new char[contentLength];
                int bytesRead = inputStream.read(buffer, 0, contentLength);
                if (bytesRead == contentLength) {
                    // Write the request body to the file
                    try (BufferedWriter writer = new BufferedWriter(new FileWriter(file))) {
                        writer.write(buffer, 0, bytesRead);
                    }
                    responseBody = "";
                    status = "201 Created";
                } else {
                    responseBody = "";
                    status = "500 Internal Server Error";
                }
            }
        } else {
            responseBody = "";
            status = "404 Not Found";
        }

        byte[] compressedResponseBody = compressResponse(responseBody, gzip, deflate);
        String header = createHttpHeader(status, contentType, compressedResponseBody, gzip, deflate);
        return header + new String(compressedResponseBody, "UTF-8");
    }

    private static byte[] compressResponse(String responseBody, boolean gzip, boolean deflate) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        OutputStream os = baos;

        if (gzip) {
            os = new GZIPOutputStream(baos);
        } else if (deflate) {
            os = new DeflaterOutputStream(baos);
        }

        try {
            os.write(responseBody.getBytes("UTF-8"));
        } finally {
            os.close(); // Ensure streams are closed properly
        }

        return baos.toByteArray();
    }

    private static String createHttpHeader(String status, String contentType, byte[] responseBody, boolean gzip, boolean deflate) {
        StringBuilder response = new StringBuilder();
        response.append("HTTP/1.1 ").append(status).append("\r\n");
        response.append("Content-Type: ").append(contentType).append("\r\n");
        response.append("Content-Length: ").append(responseBody.length).append("\r\n");

        if (gzip) {
            response.append("Content-Encoding: gzip\r\n");
        } else if (deflate) {
            response.append("Content-Encoding: deflate\r\n");
        }

        response.append("\r\n");
        return response.toString();
    }
}
