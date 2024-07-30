import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
public class Main {
  public static void main(String[] args) {
    // You can use print statements as follows for debugging, they'll be visible
    // when running tests.
    System.out.println("Logs from your program will appear here!");
    // Uncomment this block to pass the first stage
    ServerSocket serverSocket = null;
    Socket clientSocket = null;
    try {
      serverSocket = new ServerSocket(4221);
      serverSocket.setReuseAddress(true);
      clientSocket = serverSocket.accept(); // Wait for connection from client.
      InputStream input = clientSocket.getInputStream();
      BufferedReader reader = new BufferedReader(new InputStreamReader(input));
      String line = reader.readLine();
      String[] HttpRequest = line.split(" ");
      OutputStream output = clientSocket.getOutputStream();
      String[] str = HttpRequest[1].split("/");
      // System.out.println(HttpRequest[1]);
      if (HttpRequest[1].equals("/")) {
        System.out.println("version");
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
      } else if ((str.length > 2 && str[1].equals("echo"))) {
        String responsebody = str[2];
        String finalstr = "HTTP/1.1 200 OK\r\n"
                          + "Content-Type: text/plain\r\n"
                          + "Content-Length: " + responsebody.length() +
                          "\r\n\r\n" + responsebody;
        output.write(finalstr.getBytes());
      } else {
        output.write("HTTP/1.1 404 Not Found\r\n\r\n".getBytes());
      }
      output.flush();
      System.out.println("accepted new connection");
    } catch (IOException e) {
      System.out.println("IOException: " + e.getMessage());
    }
  }
}