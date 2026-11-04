# Diagramas UML

Cenário: **criar um pedido** (`POST /pedidos?produtoId=1&quantidade=2`).

## Sequência — Monólito

```mermaid
sequenceDiagram
    actor C as Cliente
    participant API as HTTP API
    participant P as PedidoService
    participant K as CatalogoService
    participant G as PagamentoService
    C->>API: POST /pedidos
    API->>P: criar(1, 2)
    P->>K: buscar(1)
    K-->>P: Produto
    P->>G: cobrar(9000)
    G-->>P: true
    P->>K: baixarEstoque(1, 2)
    P-->>API: Pedido CONFIRMADO
    API-->>C: 201 Created
    Note over P,G: Tudo no mesmo processo e mesma transação
```

## Sequência — Microsserviços

```mermaid
sequenceDiagram
    actor C as Cliente
    participant O as order-service
    participant K as catalog-service
    participant G as payment-service
    C->>O: POST /pedidos
    O->>K: GET /produtos/1 (rede)
    K-->>O: preço, estoque
    O->>G: POST /pagamentos?valor=9000 (rede)
    G-->>O: 200 aprovado
    O->>K: POST /produtos/1/baixa (rede)
    K-->>O: 200
    O-->>C: 201 CONFIRMADO
    Note over O,G: 3 chamadas de rede, cada uma pode falhar
```

## Sequência — Falha parcial (Saga com compensação)

```mermaid
sequenceDiagram
    participant O as order-service
    participant K as catalog-service
    participant G as payment-service
    O->>K: reservar estoque
    K-->>O: ok
    O->>G: cobrar
    G--xO: timeout / indisponível
    O->>K: COMPENSAR: liberar estoque
    K-->>O: ok
    O-->>O: pedido = CANCELADO
```

## Classes — Domínio compartilhado

```mermaid
classDiagram
    class Produto {
        +int id
        +String nome
        +double preco
        +int estoque
    }
    class Pedido {
        +int id
        +int produtoId
        +int quantidade
        +double total
        +String status
    }
    class Pagamento {
        +double valor
        +boolean aprovado
    }
    Pedido "*" --> "1" Produto : referencia
    Pedido "1" --> "1" Pagamento : cobrado por
```

## Classes — Acoplamento: monólito × microsserviços

```mermaid
classDiagram
    direction LR
    class PedidoService_M["PedidoService (monólito)"]
    class CatalogoService_M["CatalogoService"]
    PedidoService_M --> CatalogoService_M : método (in-process)

    class OrderService
    class CatalogClient {
        <<HTTP client>>
    }
    class CatalogService
    OrderService --> CatalogClient
    CatalogClient ..> CatalogService : REST (rede)
```

## Estados — Pedido

```mermaid
stateDiagram-v2
    [*] --> Criado
    Criado --> EstoqueReservado : estoque ok
    Criado --> RejeitadoEstoque : sem estoque
    EstoqueReservado --> Confirmado : pagamento aprovado
    EstoqueReservado --> RejeitadoPagamento : pagamento negado
    RejeitadoPagamento --> [*]
    RejeitadoEstoque --> [*]
    Confirmado --> [*]
```

## Atividades — Fluxo de criação de pedido

```mermaid
flowchart TD
    A([Início]) --> B[Buscar produto]
    B --> C{Estoque suficiente?}
    C -- Não --> R1([REJEITADO_ESTOQUE])
    C -- Sim --> D[Cobrar pagamento]
    D --> E{Aprovado?}
    E -- Não --> R2([REJEITADO_PAGAMENTO])
    E -- Sim --> F[Baixar estoque]
    F --> G[Registrar pedido]
    G --> H([CONFIRMADO])
```

## Componentes

```mermaid
flowchart LR
    subgraph Monolito["Monólito (1 artefato)"]
        direction TB
        m1[Catálogo] --- m2[Pedidos] --- m3[Pagamentos]
    end
    subgraph Micro["Microsserviços (3 artefatos)"]
        direction TB
        s1[catalog-service] -. REST .- s2[order-service] -. REST .- s3[payment-service]
    end
```

## Implantação

```mermaid
flowchart LR
    subgraph Mono["Nó único"]
        A["app.jar"]
    end
    subgraph Micros["Vários nós/contêineres"]
        B["catalog.jar :8081"]
        C["payment.jar :8082"]
        D["order.jar :8083"]
    end
    D --> B
    D --> C
```
