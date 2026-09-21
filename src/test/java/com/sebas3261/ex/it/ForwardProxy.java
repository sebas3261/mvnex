package com.sebas3261.ex.it;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.Proxy;
import java.net.ServerSocket;
import java.net.Socket;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A minimal HTTP forward proxy for integration tests: it answers 407 until a request carries the
 * expected Basic credentials, then forwards plain-HTTP GETs (absolute-URI form) to their target.
 */
public final class ForwardProxy implements AutoCloseable {

    private final ServerSocket socket;
    private final String expectedAuthorization;
    private final AtomicInteger forwarded = new AtomicInteger();
    private final AtomicInteger challenged = new AtomicInteger();

    public ForwardProxy(String user, String password) throws IOException {
        this.socket = new ServerSocket(0);
        this.expectedAuthorization = "Basic " + Base64.getEncoder()
                .encodeToString((user + ":" + password).getBytes(StandardCharsets.UTF_8));
        Thread acceptor = new Thread(this::acceptLoop, "it-forward-proxy");
        acceptor.setDaemon(true);
        acceptor.start();
    }

    public int port() {
        return socket.getLocalPort();
    }

    /** Requests forwarded after successful authentication. */
    public int forwarded() {
        return forwarded.get();
    }

    /** Requests answered with 407. */
    public int challenged() {
        return challenged.get();
    }

    private void acceptLoop() {
        while (!socket.isClosed()) {
            try (Socket client = socket.accept()) {
                handle(client);
            } catch (IOException e) {
                // socket closed or client gone
            }
        }
    }

    private void handle(Socket client) throws IOException {
        BufferedReader in = new BufferedReader(new InputStreamReader(client.getInputStream(), StandardCharsets.ISO_8859_1));
        String requestLine = in.readLine();
        if (requestLine == null) {
            return;
        }
        String authorization = null;
        for (String header = in.readLine(); header != null && !header.isEmpty(); header = in.readLine()) {
            if (header.regionMatches(true, 0, "Proxy-Authorization:", 0, 20)) {
                authorization = header.substring(20).trim();
            }
        }
        OutputStream out = client.getOutputStream();
        if (!expectedAuthorization.equals(authorization)) {
            challenged.incrementAndGet();
            out.write(("HTTP/1.1 407 Proxy Authentication Required\r\nProxy-Authenticate: Basic realm=\"it\"\r\n"
                    + "Content-Length: 0\r\nConnection: close\r\n\r\n").getBytes(StandardCharsets.ISO_8859_1));
            return;
        }
        String target = requestLine.split(" ")[1];
        HttpURLConnection connection = (HttpURLConnection) new URL(target).openConnection(Proxy.NO_PROXY);
        int status = connection.getResponseCode();
        byte[] body;
        try (InputStream stream = status >= 400 ? connection.getErrorStream() : connection.getInputStream()) {
            body = stream == null ? new byte[0] : stream.readAllBytes();
        }
        forwarded.incrementAndGet();
        out.write(("HTTP/1.1 " + status + " X\r\nContent-Length: " + body.length + "\r\nConnection: close\r\n\r\n")
                .getBytes(StandardCharsets.ISO_8859_1));
        out.write(body);
    }

    @Override
    public void close() throws IOException {
        socket.close();
    }
}
