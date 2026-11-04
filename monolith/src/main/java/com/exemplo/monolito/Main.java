package com.exemplo.monolito;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * MONÓLITO: catálogo, pedidos e pagamentos no MESMO processo, MESMO deploy e
 * MESMO "banco". Os módulos se comunicam por chamada de método (in-process).
 */
public class Main {

    // ---------- Módulo Catálogo ----------
    record Produto(int id, String nome, double preco, int estoque) {}

    static class CatalogoService {
        private final Map<Integer, Produto> produtos = new ConcurrentHashMap<>(Map.of(
                1, new Produto(1, "Notebook", 4500.0, 10),
                2, new Produto(2, "Mouse", 80.0, 100)));

        Produto buscar(int id) { return produtos.get(id); }

        void baixarEstoque(int id, int qtd) {
            produtos.computeIfPresent(id, (k, p) -> new Produto(p.id(), p.nome(), p.preco(), p.estoque() - qtd));
        }

        List<Produto> listar() { return new ArrayList<>(produtos.values()); }
    }

    // ---------- Módulo Pagamento ----------
    static class PagamentoService {
        boolean cobrar(double valor) { return valor <= 10000.0; }
    }

    // ---------- Módulo Pedidos ----------
    record Pedido(int id, int produtoId, int quantidade, double total, String status) {}

    static class PedidoService {
        private final CatalogoService catalogo;
        private final PagamentoService pagamento;
        private final Map<Integer, Pedido> pedidos = new ConcurrentHashMap<>();
        private final AtomicInteger seq = new AtomicInteger();

        PedidoService(CatalogoService c, PagamentoService p) { this.catalogo = c; this.pagamento = p; }

        // Uma única "transação": tudo acontece no mesmo processo.
        Pedido criar(int produtoId, int qtd) {
            Produto p = catalogo.buscar(produtoId);                       // chamada de método
            if (p == null || p.estoque() < qtd) return new Pedido(0, produtoId, qtd, 0, "REJEITADO_ESTOQUE");
            double total = p.preco() * qtd;
            if (!pagamento.cobrar(total)) return new Pedido(0, produtoId, qtd, total, "REJEITADO_PAGAMENTO");
            catalogo.baixarEstoque(produtoId, qtd);                       // chamada de método
            Pedido ped = new Pedido(seq.incrementAndGet(), produtoId, qtd, total, "CONFIRMADO");
            pedidos.put(ped.id(), ped);
            return ped;
        }

        List<Pedido> listar() { return new ArrayList<>(pedidos.values()); }
    }

    public static void main(String[] args) throws IOException {
        int port = Integer.parseInt(System.getenv().getOrDefault("PORT", "8080"));
        CatalogoService catalogo = new CatalogoService();
        PedidoService pedidos = new PedidoService(catalogo, new PagamentoService());

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/produtos", ex -> {
            StringBuilder sb = new StringBuilder("[");
            for (Produto p : catalogo.listar()) {
                if (sb.length() > 1) sb.append(',');
                sb.append("{\"id\":%d,\"nome\":\"%s\",\"preco\":%s,\"estoque\":%d}"
                        .formatted(p.id(), p.nome(), p.preco(), p.estoque()));
            }
            send(ex, 200, sb.append(']').toString());
        });
        // POST /pedidos?produtoId=1&quantidade=2
        server.createContext("/pedidos", ex -> {
            if ("POST".equals(ex.getRequestMethod())) {
                Map<String, String> q = Query.parse(ex.getRequestURI().getRawQuery());
                Pedido p = pedidos.criar(Integer.parseInt(q.getOrDefault("produtoId", "0")),
                        Integer.parseInt(q.getOrDefault("quantidade", "1")));
                send(ex, "CONFIRMADO".equals(p.status()) ? 201 : 422, json(p));
            } else {
                StringBuilder sb = new StringBuilder("[");
                for (Pedido p : pedidos.listar()) {
                    if (sb.length() > 1) sb.append(',');
                    sb.append(json(p));
                }
                send(ex, 200, sb.append(']').toString());
            }
        });
        server.start();
        System.out.println("Monólito rodando na porta " + port);
    }

    static String json(Pedido p) {
        return "{\"id\":%d,\"produtoId\":%d,\"quantidade\":%d,\"total\":%s,\"status\":\"%s\"}"
                .formatted(p.id(), p.produtoId(), p.quantidade(), p.total(), p.status());
    }

    static void send(HttpExchange ex, int code, String body) throws IOException {
        byte[] b = body.getBytes(StandardCharsets.UTF_8);
        ex.getResponseHeaders().add("Content-Type", "application/json");
        ex.sendResponseHeaders(code, b.length);
        try (var os = ex.getResponseBody()) { os.write(b); }
    }

    static class Query {
        static Map<String, String> parse(String raw) {
            Map<String, String> m = new ConcurrentHashMap<>();
            if (raw == null) return m;
            for (String kv : raw.split("&")) {
                String[] a = kv.split("=", 2);
                if (a.length == 2) m.put(a[0], a[1]);
            }
            return m;
        }
    }
}
