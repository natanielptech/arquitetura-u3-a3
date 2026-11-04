package com.exemplo.catalog;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/** Microsserviço de Catálogo: dono do seu próprio dado (estoque). */
public class Main {
    record Produto(int id, String nome, double preco, int estoque) {
        String json() {
            return "{\"id\":%d,\"nome\":\"%s\",\"preco\":%s,\"estoque\":%d}".formatted(id, nome, preco, estoque);
        }
    }

    static final Map<Integer, Produto> DB = new ConcurrentHashMap<>(Map.of(
            1, new Produto(1, "Notebook", 4500.0, 10),
            2, new Produto(2, "Mouse", 80.0, 100)));

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8081"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        // GET /produtos/{id}  |  POST /produtos/{id}/baixa?quantidade=N
        server.createContext("/produtos", ex -> {
            String[] parts = ex.getRequestURI().getPath().split("/");
            if (parts.length < 3) {
                send(ex, 200, "[" + String.join(",", DB.values().stream().map(Produto::json).toList()) + "]");
                return;
            }
            Produto p = DB.get(Integer.parseInt(parts[2]));
            if (p == null) { send(ex, 404, "{\"erro\":\"nao encontrado\"}"); return; }
            if (parts.length == 4 && "POST".equals(ex.getRequestMethod())) {
                String q = ex.getRequestURI().getRawQuery();
                int qtd = Integer.parseInt(q.substring(q.indexOf('=') + 1));
                Produto novo = DB.compute(p.id(), (k, v) -> new Produto(v.id(), v.nome(), v.preco(), v.estoque() - qtd));
                send(ex, 200, novo.json());
            } else {
                send(ex, 200, p.json());
            }
        });
        server.start();
        System.out.println("catalog-service na porta " + port);
    }

    static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        try (var os = ex.getResponseBody()) { os.write(b); }
    }
}
