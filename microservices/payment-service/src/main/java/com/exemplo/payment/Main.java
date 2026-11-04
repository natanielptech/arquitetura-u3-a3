package com.exemplo.payment;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

/** Microsserviço de Pagamento: POST /pagamentos?valor=123.0 */
public class Main {
    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8082"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/pagamentos", ex -> {
            String q = ex.getRequestURI().getRawQuery();
            double valor = Double.parseDouble(q.substring(q.indexOf('=') + 1));
            boolean ok = valor <= 10000.0;
            send(ex, ok ? 200 : 402, "{\"aprovado\":" + ok + "}");
        });
        server.start();
        System.out.println("payment-service na porta " + port);
    }

    static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        try (var os = ex.getResponseBody()) { os.write(b); }
    }
}
