package com.exemplo.order;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Microsserviço de Pedidos: orquestra Catálogo e Pagamento via HTTP.
 * Sem transação distribuída: se uma etapa falha no meio, é preciso compensar
 * (padrão Saga) -- veja a documentação.
 */
public class Main {
    static final String CATALOG = System.getenv().getOrDefault("CATALOG_URL", "http://localhost:8081");
    static final String PAYMENT = System.getenv().getOrDefault("PAYMENT_URL", "http://localhost:8082");
    static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();
    static final List<String> PEDIDOS = new CopyOnWriteArrayList<>();
    static final AtomicInteger SEQ = new AtomicInteger();

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8083"));
        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        // POST /pedidos?produtoId=1&quantidade=2
        server.createContext("/pedidos", ex -> {
            if (!"POST".equals(ex.getRequestMethod())) { send(ex, 200, "[" + String.join(",", PEDIDOS) + "]"); return; }
            String[] q = ex.getRequestURI().getRawQuery().split("&");
            int produtoId = Integer.parseInt(q[0].split("=")[1]);
            int qtd = Integer.parseInt(q[1].split("=")[1]);
            try {
                send(ex, 201, criar(produtoId, qtd));
            } catch (Exception e) {
                send(ex, 503, "{\"erro\":\"servico indisponivel\"}");
            }
        });
        server.start();
        System.out.println("order-service na porta " + port);
    }

    static String criar(int produtoId, int qtd) throws Exception {
        String prod = call("GET", CATALOG + "/produtos/" + produtoId).body();          // chamada de rede 1
        double preco = Double.parseDouble(find(prod, "\"preco\":([0-9.]+)"));
        int estoque = Integer.parseInt(find(prod, "\"estoque\":(\\d+)"));
        if (estoque < qtd) return status(0, produtoId, qtd, 0, "REJEITADO_ESTOQUE");
        double total = preco * qtd;
        if (call("POST", PAYMENT + "/pagamentos?valor=" + total).statusCode() != 200) {  // chamada de rede 2
            return status(0, produtoId, qtd, total, "REJEITADO_PAGAMENTO");
        }
        call("POST", CATALOG + "/produtos/" + produtoId + "/baixa?quantidade=" + qtd);   // chamada de rede 3
        String ped = status(SEQ.incrementAndGet(), produtoId, qtd, total, "CONFIRMADO");
        PEDIDOS.add(ped);
        return ped;
    }

    static HttpResponse<String> call(String method, String url) throws Exception {
        HttpRequest r = HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(3))
                .method(method, HttpRequest.BodyPublishers.noBody()).build();
        return HTTP.send(r, HttpResponse.BodyHandlers.ofString());
    }

    static String find(String s, String re) {
        Matcher m = Pattern.compile(re).matcher(s);
        if (!m.find()) throw new IllegalStateException("resposta inesperada");
        return m.group(1);
    }

    static String status(int id, int pid, int qtd, double total, String st) {
        return "{\"id\":%d,\"produtoId\":%d,\"quantidade\":%d,\"total\":%s,\"status\":\"%s\"}".formatted(id, pid, qtd, total, st);
    }

    static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        try (var os = ex.getResponseBody()) { os.write(b); }
    }
}
