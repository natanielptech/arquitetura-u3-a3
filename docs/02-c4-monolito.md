# C4 Model — Monólito

Diagramas C4 em Mermaid (renderizam nativamente no GitHub). Código: [`monolith/`](../monolith).

## Nível 1 — Contexto

```mermaid
C4Context
    title Contexto — Loja Online (Monólito)
    Person(cliente, "Cliente", "Consulta produtos e faz pedidos")
    System(loja, "Loja Monolítica", "Catálogo, pedidos e pagamentos em um único deploy")
    System_Ext(gateway, "Gateway de Pagamento", "Processadora externa (simulada)")
    Rel(cliente, loja, "Usa", "HTTP/JSON")
    Rel(loja, gateway, "Cobra", "HTTPS")
```

## Nível 2 — Contêineres

```mermaid
C4Container
    title Contêineres — Monólito
    Person(cliente, "Cliente")
    System_Boundary(b, "Loja Monolítica") {
        Container(app, "Aplicação Java", "Java 17+, JDK HttpServer", "Um único processo com todos os módulos")
        ContainerDb(db, "Banco Único", "Relacional (simulado em memória)", "Produtos, pedidos, pagamentos")
    }
    Rel(cliente, app, "HTTP/JSON")
    Rel(app, db, "SQL / transação ACID única")
```

## Nível 3 — Componentes

```mermaid
C4Component
    title Componentes — Aplicação Monolítica
    Person(cliente, "Cliente")
    Container_Boundary(app, "Aplicação Java") {
        Component(api, "HTTP API", "HttpServer", "Rotas /produtos e /pedidos")
        Component(ped, "PedidoService", "Java", "Orquestra a criação do pedido")
        Component(cat, "CatalogoService", "Java", "Produtos e estoque")
        Component(pag, "PagamentoService", "Java", "Cobrança")
    }
    Rel(cliente, api, "HTTP")
    Rel(api, ped, "chamada de método")
    Rel(ped, cat, "chamada de método")
    Rel(ped, pag, "chamada de método")
```

## Nível 4 — Código (classes)

```mermaid
classDiagram
    class Main
    class PedidoService {
        +criar(produtoId, qtd) Pedido
        +listar() List~Pedido~
    }
    class CatalogoService {
        +buscar(id) Produto
        +baixarEstoque(id, qtd)
        +listar() List~Produto~
    }
    class PagamentoService {
        +cobrar(valor) boolean
    }
    class Produto {
        <<record>>
    }
    class Pedido {
        <<record>>
    }
    Main --> PedidoService
    PedidoService --> CatalogoService
    PedidoService --> PagamentoService
    CatalogoService o-- Produto
    PedidoService o-- Pedido
```

## Deployment

```mermaid
flowchart LR
    subgraph Servidor["Servidor / VM / Container"]
        JVM["JVM<br/>app.jar<br/>(todos os módulos)"]
    end
    LB["Load Balancer"] --> Servidor
    Servidor --> DB[("Banco único")]
    Servidor2["Réplica idêntica<br/>(escala o app inteiro)"] -.-> DB
    LB -.-> Servidor2
```
