# C4 Model — Microsserviços

Código: [`microservices/`](../microservices).

## Nível 1 — Contexto

```mermaid
C4Context
    title Contexto — Loja Online (Microsserviços)
    Person(cliente, "Cliente", "Consulta produtos e faz pedidos")
    System(loja, "Plataforma da Loja", "Conjunto de serviços independentes")
    System_Ext(gateway, "Gateway de Pagamento", "Processadora externa (simulada)")
    Rel(cliente, loja, "Usa", "HTTP/JSON")
    Rel(loja, gateway, "Cobra", "HTTPS")
```

## Nível 2 — Contêineres

```mermaid
C4Container
    title Contêineres — Microsserviços
    Person(cliente, "Cliente")
    System_Boundary(b, "Plataforma da Loja") {
        Container(order, "order-service", "Java, :8083", "Cria e lista pedidos; orquestra os demais")
        Container(catalog, "catalog-service", "Java, :8081", "Produtos e estoque")
        Container(payment, "payment-service", "Java, :8082", "Aprova pagamentos")
        ContainerDb(odb, "orders-db", "Banco do Pedido", "Dado privado")
        ContainerDb(cdb, "catalog-db", "Banco do Catálogo", "Dado privado")
    }
    Rel(cliente, order, "POST /pedidos", "HTTP/JSON")
    Rel(order, catalog, "GET produto / baixa estoque", "HTTP/JSON")
    Rel(order, payment, "POST /pagamentos", "HTTP/JSON")
    Rel(order, odb, "lê/escreve")
    Rel(catalog, cdb, "lê/escreve")
```

## Nível 3 — Componentes do order-service

```mermaid
C4Component
    title Componentes — order-service
    Container_Boundary(o, "order-service") {
        Component(api, "Pedido Controller", "HttpServer", "POST/GET /pedidos")
        Component(orq, "CriarPedido (orquestração)", "Java", "Valida estoque, cobra, baixa estoque")
        Component(cc, "CatalogClient", "java.net.http", "Cliente HTTP do catálogo")
        Component(pc, "PaymentClient", "java.net.http", "Cliente HTTP de pagamento")
    }
    Container(catalog, "catalog-service", "Java")
    Container(payment, "payment-service", "Java")
    Rel(api, orq, "usa")
    Rel(orq, cc, "usa")
    Rel(orq, pc, "usa")
    Rel(cc, catalog, "HTTP")
    Rel(pc, payment, "HTTP")
```

## Deployment

```mermaid
flowchart TB
    LB["API Gateway / Load Balancer"]
    subgraph K8s["Cluster (ex.: Kubernetes)"]
        subgraph O["order-service (3 réplicas)"]
            o1[Pod] --- o2[Pod] --- o3[Pod]
        end
        subgraph C["catalog-service (2 réplicas)"]
            c1[Pod] --- c2[Pod]
        end
        subgraph P["payment-service (1 réplica)"]
            p1[Pod]
        end
    end
    LB --> O
    O --> C
    O --> P
    O --> ODB[("orders-db")]
    C --> CDB[("catalog-db")]
```

> Note: cada serviço escala de forma independente (ex.: pedidos tem 3 réplicas, pagamento 1).

## Variante com mensageria (assíncrona)

```mermaid
flowchart LR
    Order[order-service] -- "publica PedidoCriado" --> Broker{{"Message Broker<br/>(Kafka/RabbitMQ)"}}
    Broker -- "PedidoCriado" --> Payment[payment-service]
    Broker -- "PedidoCriado" --> Catalog[catalog-service]
    Payment -- "PagamentoAprovado" --> Broker
    Broker -- "PagamentoAprovado" --> Order
```
