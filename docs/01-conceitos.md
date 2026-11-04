# Conceitos: Monólito × Microsserviços

## Visão geral

```mermaid
flowchart LR
    subgraph M["MONÓLITO"]
        direction TB
        UI1[API] --> L1[Catálogo + Pedidos + Pagamentos]
        L1 --> DB1[(Banco único)]
    end
    subgraph S["MICROSSERVIÇOS"]
        direction TB
        GW[API Gateway] --> A[Pedidos] & B[Catálogo] & C[Pagamentos]
        A --> DA[(DB)]
        B --> DB[(DB)]
        C --> DC[(DB)]
        A -. HTTP .-> B
        A -. HTTP .-> C
    end
```

## Comparativo

| Aspecto | Monólito | Microsserviços |
|---|---|---|
| Deploy | Um artefato, tudo junto | Independente por serviço |
| Comunicação | Chamada de método (ns) | Rede HTTP/mensagens (ms), pode falhar |
| Dados | Banco único, transação ACID | Um banco por serviço, consistência eventual (Saga) |
| Escala | Replica a aplicação inteira | Escala só o serviço gargalo |
| Tecnologia | Uma stack | Poliglota possível |
| Falhas | Uma falha pode derrubar tudo | Isolamento (com circuit breaker) |
| Complexidade operacional | Baixa | Alta (observabilidade, CI/CD, service discovery) |
| Time-to-market inicial | Rápido | Mais lento no início |
| Times | Poucos, coesos | Muitos, autônomos (Lei de Conway) |
| Testes | E2E simples | Contratos + integração distribuída |
| Debug | Stack trace único | Tracing distribuído |

## Quando usar cada um

```mermaid
flowchart TD
    S([Novo sistema]) --> Q1{Domínio bem entendido<br/>e time grande?}
    Q1 -- Não --> M[Comece com Monólito<br/>modular]
    Q1 -- Sim --> Q2{Precisa escalar partes<br/>de forma independente<br/>ou deploys autônomos?}
    Q2 -- Não --> M
    Q2 -- Sim --> MS[Microsserviços]
    M --> Q3{Dores de escala/<br/>acoplamento de times?}
    Q3 -- Sim --> MIG[Extraia serviços aos poucos<br/>Strangler Fig]
    Q3 -- Não --> M
    MIG --> MS
```

## Escalabilidade

```mermaid
flowchart LR
    subgraph Mono["Monólito: escalar = duplicar tudo"]
        direction TB
        m1["Cópia 1<br/>Cat+Ped+Pag"]
        m2["Cópia 2<br/>Cat+Ped+Pag"]
        m3["Cópia 3<br/>Cat+Ped+Pag"]
    end
    subgraph Micro["Microsserviços: escalar só o gargalo"]
        direction TB
        p1["Pedidos x3"]
        p2["Catálogo x1"]
        p3["Pagamentos x1"]
    end
```

## Propagação de falhas

```mermaid
flowchart LR
    subgraph Mono["Monólito"]
        X1["Bug/vazamento de memória<br/>em Pagamentos"] --> X2["Processo inteiro cai ❌"]
    end
    subgraph Micro["Microsserviços"]
        Y1["Pagamentos fora"] --> Y2["Circuit breaker abre"]
        Y2 --> Y3["Catálogo e consulta de pedidos<br/>continuam ✅"]
    end
```

## Estratégia de migração (Strangler Fig)

```mermaid
flowchart LR
    subgraph T0["1. Monólito"]
        a[Monólito]
    end
    subgraph T1["2. Proxy + 1º serviço"]
        P1{Proxy} --> b[Monólito]
        P1 --> b1[payment-service]
    end
    subgraph T2["3. Mais serviços"]
        P2{Proxy} --> c[Monólito menor]
        P2 --> c1[payment]
        P2 --> c2[catalog]
    end
    subgraph T3["4. Monólito aposentado"]
        P3{Gateway} --> d1[payment]
        P3 --> d2[catalog]
        P3 --> d3[order]
    end
    T0 --> T1 --> T2 --> T3
```

## Evolução dos custos

```mermaid
xychart-beta
    title "Complexidade ao longo do crescimento (ilustrativo)"
    x-axis ["Início", "Médio", "Grande", "Muito grande"]
    y-axis "Esforço relativo" 0 --> 100
    line [10, 30, 70, 100]
    line [40, 45, 55, 65]
```

*(linha 1: monólito; linha 2: microsserviços — alto custo inicial, crescimento mais suave)*

## Padrões típicos de microsserviços

```mermaid
mindmap
  root((Microsserviços))
    Comunicação
      REST
      gRPC
      Mensageria
    Resiliência
      Circuit Breaker
      Retry
      Timeout
      Bulkhead
    Dados
      Database per Service
      Saga
      CQRS
      Event Sourcing
    Infra
      API Gateway
      Service Discovery
      Config Server
      Containers e Kubernetes
    Observabilidade
      Logs centralizados
      Métricas
      Tracing distribuído
```
