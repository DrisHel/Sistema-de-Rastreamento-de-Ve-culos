# Sistema de Rastreamento de Veiculos

Aplicacao de console em Java para registrar e consultar localizacoes de veiculos usando MongoDB como historico permanente e Redis como cache temporario.

## Requisitos

- JDK 21 ou superior
- Maven 3.9 ou superior
- Docker Desktop com Docker Compose

## Iniciar os bancos

Na pasta do projeto, execute:

```powershell
docker compose up -d
```

Isso inicia MongoDB na porta `27017` e Redis na porta `6379`. Para parar os servicos, use `docker compose down`. Os dados ficam nos volumes Docker e nao sao apagados ao parar os containers.

## Executar

```powershell
mvn compile exec:java
```

O programa usa estas configuracoes padrao:

| Variavel | Valor padrao |
| --- | --- |
| `MONGODB_URI` | `mongodb://localhost:27017` |
| `MONGODB_DATABASE` | `vehicle_tracking` |
| `REDIS_URL` | `redis://localhost:6379` |

Para usar outros enderecos no PowerShell, defina as variaveis antes de iniciar o Maven:

```powershell
$env:MONGODB_URI = "mongodb://localhost:27017"
$env:MONGODB_DATABASE = "vehicle_tracking"
$env:REDIS_URL = "redis://localhost:6379"
mvn compile exec:java
```

## Operacoes

1. **Registrar localizacao:** informa identificador, latitude e longitude. O registro e salvo no MongoDB e a posicao atual e armazenada no Redis por 5 minutos.
2. **Consultar localizacao:** procura primeiro no Redis. Em caso de cache miss, consulta a posicao mais recente no MongoDB e a recoloca no Redis por 5 minutos.
3. **Limpar historico antigo:** remove do MongoDB os registros anteriores ao numero de dias informado e sincroniza o cache dos veiculos afetados com a posicao mais recente que ainda existir. Se nao restar historico, a chave Redis e removida.

## Testes unitários:

Os testes unitarios nao precisam de Redis ou MongoDB em execucao:

```powershell
mvn test
```

## Testes no terminal:

Execute na pasta do projeto:
```
mvn compile exec:java
```
Digite cada valor no menu quando solicitado; ao terminar um teste, a aplicação volta ao menu. Use outro terminal para os comandos Docker

1. Registrar uma localização (exemplos):
```
Car-001
-23.5505
-46.6333


Car-002
-21.5635
-16.3333

Car-003
-12.2222
-36.4444

```

 Deve confirmar o registro. Confira o TTL do Redis no outro terminal:

```
docker exec rastreamento-redis redis-cli TTL vehicle:location:CAR-001
```
O resultado deve ser de até 300 segundos.

2. Consultar pelo cache

CAR-001

Deve exibir as coordenadas do primeiro teste.

3. Simular cache miss e recuperação pelo MongoDB

Primeiro apague a chave do Redis:

```
docker exec rastreamento-redis redis-cli DEL vehicle:location:CAR-001

```
Depois, no menu da aplicação:

2
CAR-001

Deve exibir a localização recuperada do MongoDB e recriar a chave no Redis.

4.Limpar um registro antigo e remover o cache correspondente

Em outro terminal, crie um registro com 40 dias e uma chave Redis para ele:

```
docker exec rastreamento-mongodb mongosh vehicle_tracking --eval 'db.locations.insertOne({vehicleId:"CAR-ANTIGO",latitude:-23.5,longitude:-46.6,recordedAt:new Date(Date.now()-40*86400000)})'

docker exec rastreamento-redis redis-cli SETEX vehicle:location:CAR-ANTIGO 300 "-23.5|-46.6|2026-08-23T12:00:00Z"

```
No menu, digite 3 e depois 30. Deve remover o registro antigo. Verifique que a chave Redis foi apagada:

```
docker exec rastreamento-redis redis-cli GET vehicle:location:CAR-ANTIGO
```
O resultado esperado é (null).

5. Testar coordenadas inválidas e veículo sem localização

No menu, informe valores fora dos limites; o programa deve pedir novamente:

```
1
CAR-LIMITE
91
-90
-181
180
```
91 e -181 devem ser rejeitados; -90 e 180 devem ser aceitos. Depois, teste um veículo inexistente:

```
2
CAR-INEXISTENTE
```
Deve informar que nenhuma localização foi encontrada.