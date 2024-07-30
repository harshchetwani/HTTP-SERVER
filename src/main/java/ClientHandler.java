import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
enum ResponseType { SUCCESS, FAILURE, FILE, CREATED }
enum MethodTypes { GET, POST, PUT, DELETE }
public class ClientHandler implements Runnable {
  private final Socket clientSocket;
  private final Path directory;
  private final Map<String, MethodTypes> Methods = new HashMap<>() {
    {
      put("GET", MethodTypes.GET);
      put("POST", MethodTypes.POST);
      put("PUT", MethodTypes.PUT);
      put("DELETE", MethodTypes.DELETE);
    }
  };
  private final Map<String, String> headers = new HashMap<>();
  public ClientHandler(Socket socket, Path baseDirectory) {
    this.clientSocket = socket;
    this.directory = baseDirectory;
  }
  @Override
  public void run() {
    try {
      handleRequest(clientSocket);
      clientSocket.close();
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }
  private void handleRequest(Socket clientSocket) throws IOException {
    String responseBody = "Hello, world!";
    String errorBody = "Something went wrong!";
    BufferedReader in = new BufferedReader(
        new InputStreamReader(clientSocket.getInputStream()));
    String requestLine = in.readLine();
    String headerLine;
    // Parse request headers
    while ((headerLine = in.readLine()) != null && !headerLine.isEmpty()) {
      String[] headerParts = headerLine.split(":", 2);
      if (headerParts.length == 2) {
        headers.put(headerParts[0], headerParts[1].trim());
      }
    }
    // Parse the request line to extract the URL path
    String urlPath = "";
    MethodTypes method = MethodTypes.GET;
    if (requestLine != null && !requestLine.isEmpty()) {
      String[] requestParts = requestLine.split(" ");
      if (requestParts.length > 1) {
        method = Methods.get(requestParts[0]);
        urlPath = requestParts[1];
      }
    }
    switch (method) {
    case POST:
      handlePostRequest(clientSocket, urlPath, responseBody, errorBody, in);
      break;
    default:
      handleGetRequest(clientSocket, urlPath, responseBody, errorBody);
    }
  }
  private void handlePostRequest(Socket clientSocket, String urlPath,
                                 String responseBody, String errorBody,
                                 BufferedReader in) throws IOException {
    switch (urlPath) {
    case String p when p.startsWith("/files/"):
      String filePathString = urlPath.substring(7);
      Path filePath =
          this.directory.resolve(filePathString).toAbsolutePath().normalize();
      // Ensure the file is within the base directory
      if (!filePath.startsWith(this.directory)) {
        errorBody = "Error: Access denied";
        sendResponse(ResponseType.FAILURE, clientSocket, errorBody.getBytes(),
                     "text/plain");
        return;
      }
      // Read the request body
      StringBuilder payload = new StringBuilder();
      while (in.ready()) {
        payload.append((char)in.read());
      }
      String content = payload.toString();
      // Write the contents to the file
      Files.writeString(filePath, content);
      // Respond with 201 Created
      String successBody = "File created: " + filePath.getFileName().toString();
      sendResponse(ResponseType.CREATED, clientSocket, successBody.getBytes(),
                   "text/plain");
    default:
      sendResponse(ResponseType.FAILURE, clientSocket, errorBody.getBytes(),
                   "text/plain");
      break;
    }
  }
  private void handleGetRequest(Socket clientSocket, String urlPath,
                                String responseBody, String errorBody)
      throws IOException {
    switch (urlPath) {
    case "/":
      sendResponse(ResponseType.SUCCESS, clientSocket, responseBody.getBytes(),
                   "text/plain");
      break;
    case String p when p.startsWith("/echo/"):
      sendResponse(ResponseType.SUCCESS, clientSocket,
                   urlPath.substring(6).getBytes(), "text/plain");
      break;
    case String p when p.startsWith("/user-agent"):
      sendResponse(
          ResponseType.SUCCESS, clientSocket,
          headers.getOrDefault("User-Agent", "Unknown User-Agent").getBytes(),
          "text/plain");
      break;
    case String p when p.startsWith("/files/"):
      String filePathString = urlPath.substring(7);
      Path filePath =
          this.directory.resolve(filePathString).toAbsolutePath().normalize();
      if (Files.exists(filePath)) {
        byte[] fileResponseBody = Files.readAllBytes(filePath);
        sendResponse(ResponseType.FILE, clientSocket, fileResponseBody,
                     "application/octet-stream");
      } else {
        errorBody = "Error: File not found";
        sendResponse(ResponseType.FAILURE, clientSocket, errorBody.getBytes(),
                     "text/plain");
      }
      break;
    default:
      sendResponse(ResponseType.FAILURE, clientSocket, errorBody.getBytes(),
                   "text/plain");
      break;
    }
  }
  private void sendResponse(ResponseType responseType, Socket clientSocket,
                            byte[] responseBody, String contentType)
      throws IOException {
    OutputStream outputStream = clientSocket.getOutputStream();
    PrintWriter out = new PrintWriter(outputStream, true);
    String isEncoded = headers.getOrDefault("Accept-Encoding", "none");
    switch (responseType) {
    case SUCCESS:
      out.print("HTTP/1.1 200 OK\r\n");
      break;
    case FAILURE:
      out.print("HTTP/1.1 404 Not Found\r\n");
      break;
    case FILE:
      out.print("HTTP/1.1 200 OK\r\n");
      contentType = "application/octet-stream";
      break;
    case CREATED:
      out.print("HTTP/1.1 201 Created\r\n");
      break;
    }
    out.print("Content-Type: " + contentType + "\r\n");
    out.print("Content-Length: " + responseBody.length + "\r\n");
    if (isEncoded.equals("gzip")) {
      out.print("Content-Encoding: " + isEncoded + "\r\n");
    if (isEncoded.contains("gzip")) {
      out.print("Content-Encoding: gzip"
                + "\r\n");
    }
    out.print("\r\n");
    out.flush();
    outputStream.write(responseBody);
    outputStream.flush();
  }
      }
    }