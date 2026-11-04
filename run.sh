#!/usr/bin/env bash
# Compila (javac, sem dependências) e executa: ./run.sh monolith | ./run.sh microservices
set -e
cd "$(dirname "$0")"
build() { mkdir -p "$1/out"; javac -d "$1/out" $(find "$1/src" -name '*.java'); }
case "$1" in
  monolith) build monolith; java -cp monolith/out com.exemplo.monolito.Main ;;
  microservices)
    for s in catalog payment order; do build microservices/$s-service; done
    trap 'kill 0' EXIT
    java -cp microservices/catalog-service/out com.exemplo.catalog.Main &
    java -cp microservices/payment-service/out com.exemplo.payment.Main &
    java -cp microservices/order-service/out com.exemplo.order.Main &
    wait ;;
  *) echo "uso: $0 monolith|microservices"; exit 1 ;;
esac
