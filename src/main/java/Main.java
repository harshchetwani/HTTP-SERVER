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
        String httpResponse;
        StringBuilder responseBody = new StringBuilder();
        String acceptEncoding = headers.getOrDefault("Accept-Encoding", "");
        boolean gzip = acceptEncoding.contains("gzip");
        boolean deflate = acceptEncoding.contains("deflate");

        if ("GET".equals(httpMethod)) {
            if ("/".equals(urlPath)) {
                responseBody.append("Hello, World!");
                httpResponse = generateHttpResponse("200 OK", "text/plain", responseBody.toString(), gzip, deflate);
            } else if (urlPath.startsWith("/echo/")) {
                String echoStr = urlPath.substring(6); // Extract the string after "/echo/"
                responseBody.append(echoStr);
                httpResponse = generateHttpResponse("200 OK", "text/plain", responseBody.toString(), gzip, deflate);
            } else if ("/user-agent".equals(urlPath)) {
                String userAgent = headers.getOrDefault("User-Agent", "Unknown User-Agent");
                responseBody.append(userAgent);
                httpResponse = generateHttpResponse("200 OK", "text/plain", responseBody.toString(), gzip, deflate);
            } else if (urlPath.startsWith("/files/")) {
                String filename = urlPath.substring(7); // Extract the filename after "/files/"
                File file = new File(directory, filename);
                if (file.exists()) {
                    byte[] fileContent = Files.readAllBytes(file.toPath());
                    responseBody.append(new String(fileContent));
                    httpResponse = generateHttpResponse("200 OK", "application/octet-stream", responseBody.toString(), gzip, deflate);
                } else {
                    httpResponse = "HTTP/1.1 404 Not Found\r\n\r\n";
                }
            } else {
                httpResponse = "HTTP/1.1 404 Not Found\r\n\r\n";
            }
        } else if ("POST".equals(httpMethod) && urlPath.startsWith("/files/")) {
            String filename = urlPath.substring(7); // Extract the filename after "/files/"
            System.out.println("filename: " + filename);
            File file = new File(directory, filename);
            if (!file.getCanonicalPath().startsWith(new File(directory).getCanonicalPath())) {
                httpResponse = "HTTP/1.1 403 Forbidden\r\n\r\n";
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
                    httpResponse = "HTTP/1.1 201 Created\r\n\r\n";
                } else {
                    httpResponse = "HTTP/1.1 500 Internal Server Error\r\n\r\n";
                }
            }
        } else {
            httpResponse = "HTTP/1.1 404 Not Found\r\n\r\n";
        }
        return httpResponse;
    }

    private static String generateHttpResponse(String status, String contentType, String responseBody, boolean gzip, boolean deflate) throws IOException {
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
            os.close();
        }

        byte[] compressedResponseBody = baos.toByteArray();
        StringBuilder response = new StringBuilder();
        response.append("HTTP/1.1 ").append(status).append("\r\n");
        response.append("Content-Type: ").append(contentType).append("\r\n");
        response.append("Content-Length: ").append(compressedResponseBody.length).append("\r\n");

        if (gzip) {
            response.append("Content-Encoding: gzip\r\n");
        } else if (deflate) {
            response.append("Content-Encoding: deflate\r\n");
        }

        response.append("\r\n");
        return response.append(new String(compressedResponseBody, "UTF-8")).toString();
    }
}
