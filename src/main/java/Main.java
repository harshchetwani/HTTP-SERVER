import java.io.*;
import java.net.Socket;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public class Main extends Thread {
    private InputStream inputStream;
    private OutputStream outputStream;
    private String fileDir;

    Main(InputStream inputStream, OutputStream outputStream, String fileDir) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
        this.fileDir = fileDir == null ? "" : fileDir + File.separator;
    }

    @Override
    public void run() {
        try {
            // Read the request
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));
            String requestLine = bufferedReader.readLine();
            Map<String, String> requestHeaders = new HashMap<>();
            String header;
            while ((header = bufferedReader.readLine()) != null && !header.isEmpty()) {
                String[] keyVal = header.split(":", 2);
                if (keyVal.length == 2) {
                    requestHeaders.put(keyVal[0].trim(), keyVal[1].trim());
                }
            }

            // Read body
            StringBuilder bodyBuffer = new StringBuilder();
            if (requestHeaders.containsKey("Content-Encoding") &&
                "gzip".equalsIgnoreCase(requestHeaders.get("Content-Encoding"))) {
                GZIPInputStream gzipInputStream = new GZIPInputStream(inputStream);
                BufferedReader gzipReader = new BufferedReader(new InputStreamReader(gzipInputStream));
                String line;
                while ((line = gzipReader.readLine()) != null) {
                    bodyBuffer.append(line);
                }
            } else {
                while (bufferedReader.ready()) {
                    bodyBuffer.append((char) bufferedReader.read());
                }
            }
            String body = bodyBuffer.toString();

            // Process the request
            String[] requestLinePieces = requestLine.split(" ", 3);
            String httpMethod = requestLinePieces[0];
            String requestTarget = requestLinePieces[1];
            String httpVersion = requestLinePieces[2];

            if ("POST".equals(httpMethod)) {
                if (requestTarget.startsWith("/files/")) {
                    File file = new File(fileDir + requestTarget.substring(7));
                    try (FileWriter fileWriter = new FileWriter(file)) {
                        fileWriter.write(body);
                    }
                    sendResponse("HTTP/1.1 201 Created\r\n\r\n");
                } else {
                    sendResponse("HTTP/1.1 404 Not Found\r\n\r\n");
                }
                return;
            }

            if (requestTarget.equals("/")) {
                sendResponse("HTTP/1.1 200 OK\r\n\r\n");
            } else if (requestTarget.startsWith("/echo/")) {
                String echoString = requestTarget.substring(6);
                String outputString = "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/plain\r\n"
                        + "Content-Length: " + echoString.length() +
                        "\r\n"
                        + "\r\n" + echoString;
                sendResponse(outputString);
            } else if (requestTarget.equals("/user-agent")) {
                String userAgent = requestHeaders.getOrDefault("User-Agent", "");
                String outputString =
                        "HTTP/1.1 200 OK\r\n"
                                + "Content-Type: text/plain\r\n"
                                + "Content-Length: " + userAgent.length() +
                                "\r\n"
                                + "\r\n" + userAgent;
                sendResponse(outputString);
            } else if (requestTarget.startsWith("/files/")) {
                String fileName = requestTarget.substring(7);
                File file = new File(fileDir + fileName);
                if (file.exists()) {
                    String content = Files.readString(file.toPath());
                    String outputString = "HTTP/1.1 200 OK\r\n"
                            + "Content-Type: application/octet-stream\r\n"
                            + "Content-Length: " + content.length() +
                            "\r\n\r\n" + content;
                    String acceptEncoding = requestHeaders.get("Accept-Encoding");
                    if (acceptEncoding != null && acceptEncoding.contains("gzip")) {
                        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
                        try (GZIPOutputStream gzipOutputStream = new GZIPOutputStream(byteArrayOutputStream)) {
                            gzipOutputStream.write(content.getBytes());
                        }
                        byte[] compressedContent = byteArrayOutputStream.toByteArray();
                        outputString = "HTTP/1.1 200 OK\r\n"
                                + "Content-Type: application/octet-stream\r\n"
                                + "Content-Length: " + compressedContent.length +
                                "\r\n"
                                + "Content-Encoding: gzip\r\n\r\n";
                        outputStream.write(outputString.getBytes());
                        outputStream.write(compressedContent);
                    } else {
                        outputStream.write(outputString.getBytes());
                    }
                } else {
                    sendResponse("HTTP/1.1 404 Not Found\r\n\r\n");
                }
            } else {
                sendResponse("HTTP/1.1 404 Not Found\r\n\r\n");
            }
            outputStream.flush();
        } catch (IOException e) {
            System.out.println("IOException: " + e.getMessage());
        } finally {
            try {
                inputStream.close();
                outputStream.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void sendResponse(String response) throws IOException {
        OutputStream out = outputStream;
        out.write(response.getBytes());
        out.flush();
    }
}
