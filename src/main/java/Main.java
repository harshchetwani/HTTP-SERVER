import java.io.*;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class Main extends Thread {
    private Socket sock;
    private String dir;

    public Main(Socket sock, String dir) {
        this.sock = sock;
        this.dir = dir;
    }

    @Override
    public void run() {
        try {
            InputStream in = sock.getInputStream();
            BufferedReader bif = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            String inr;
            List<String> inp = new ArrayList<>();
            while ((inr = bif.readLine()) != null && !inr.isEmpty()) {
                inp.add(inr);
            }

            if (inp.isEmpty()) {
                sendCode(sock, "HTTP/1.1 400 Bad Request\r\n\r\n");
                return;
            }

            String[] re = inp.get(0).split(" ");

            if (re[1].equals("/")) {
                sendCode(sock, "HTTP/1.1 200 OK\r\n\r\n");
            } else if (re[1].startsWith("/echo/")) {
                String ec = re[1].substring("/echo/".length());
                sendCode(
                    sock,
                    "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " +
                        ec.length() + "\r\n\r\n" + ec);
            } else if (re[1].equals("/user-agent")) {
                if (inp.size() > 2) {
                    String[] ua = inp.get(2).split(" ");
                    sendCode(
                        sock,
                        "HTTP/1.1 200 OK\r\nContent-Type: text/plain\r\nContent-Length: " +
                            ua[1].length() + "\r\n\r\n" + ua[1]);
                } else {
                    sendCode(sock, "HTTP/1.1 400 Bad Request\r\n\r\n");
                }
            } else if (re[1].startsWith("/files/")) {
                String fi = re[1].substring("/files/".length());
                Path filePath = Path.of(dir + "/" + fi);
                if (Files.exists(filePath)) {
                    if (re[0].equals("GET")) {
                        String cc = Files.readString(filePath);
                        sendCode(
                            sock,
                            "HTTP/1.1 200 OK\r\nContent-Type: application/octet-stream\r\nContent-Length: " +
                                cc.length() + "\r\n\r\n" + cc);
                    } else if (re[0].equals("POST")) {
                        // Skip headers and read body
                        String[] lengthHeader = inp.stream()
                            .filter(line -> line.startsWith("Content-Length:"))
                            .findFirst()
                            .orElse("Content-Length: 0")
                            .split(" ");
                        int contentLength = Integer.parseInt(lengthHeader[1]);
                        char[] bc = new char[contentLength];
                        bif.read(bc, 0, contentLength);
                        String bd = new String(bc);
                        Files.write(filePath, bd.getBytes(StandardCharsets.UTF_8));
                        sendCode(sock, "HTTP/1.1 201 Created\r\n\r\n");
                    } else {
                        sendCode(sock, "HTTP/1.1 405 Method Not Allowed\r\n\r\n");
                    }
                } else {
                    sendCode(sock, "HTTP/1.1 404 Not Found\r\n\r\n");
                }
            } else {
                sendCode(sock, "HTTP/1.1 404 Not Found\r\n\r\n");
            }
            System.out.println("Accepted new connection");
        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                sock.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    public static void sendCode(Socket sock, String code) throws IOException {
        OutputStream out = sock.getOutputStream();
        out.write(code.getBytes(StandardCharsets.UTF_8));
        out.flush();
        out.close();
    }
}
