# Relatório do Projeto: Chat P2P com Múltiplos Usuários (Java)

## Objetivo
Implementar um sistema de chat descentralizado (P2P) em Java com interface visual, suportando múltiplas conexões, broadcast, identificação de usuários, histórico e descoberta de peers, sem servidor central.

## Arquitetura

### Visão Geral
- Topologia P2P parcialmente conectada, sem coordenador central.
- Cada nó expõe um servidor TCP (porta configurável).
- Conexões são simétricas no protocolo (qualquer lado pode iniciar).
- Comunicação em texto via JSON linha-a-linha.
- Descoberta de peers por UDP Multicast (LAN).

### Componentes
- `PeerNode`:
  - Abre `ServerSocket`, aceita conexões e gerencia `ConnectionHandler` por peer.
  - Mantém mapas de conexões ativas (`connectionsByPeerId`) e catálogo de peers conhecidos (`peersById`).
  - Deduplica mensagens usando `LruSet<UUID>` para evitar loops de broadcast.
  - Eventos para a UI via `UiCallbacks`.

- `ConnectionHandler`:
  - Duas threads por conexão: leitura (blocking) e escrita (fila).
  - Serializa/deserializa mensagens JSON (Gson).
  - Sinaliza início/fim ao `PeerNode`.

- `DiscoveryService`:
  - `MulticastSocket` em `230.0.0.1:4446`, TTL=1.
  - Envia “DISCOVERY” a cada 3s com `{id, name, port}`.
  - Recebe anúncios, notifica UI para facilitar conexão.

- `MessageHistory`:
  - Grava logs de sessão em `./history/`.
  - Apêndice síncrono simples por linha.

- `ChatWindow` (Swing):
  - Configuração (nome e porta), iniciar/parar nó.
  - Conectar por host:porta ou via lista de descobertos.
  - Lista de peers conectados.
  - Área de chat e input de mensagem (broadcast).

### Protocolo
Mensagens JSON terminadas por `\n`:
- `HELLO`: identifica o nó e opcionalmente envia `peers` conhecidos.
- `CHAT`: mensagem com `id` (UUID), `fromId`, `fromName`, `text`, `timestamp`.
- `PEERSHARE`: compartilhamento de lista de peers conhecidos.

A recepção de `CHAT`:
1. Deduplicação por `id`.
2. Exibição local (UI) e atualização de `lastSeen`.
3. Reencaminhamento para todas as conexões exceto a origem (gossip simples).

### Decisões Técnicas
- Java 11+, Swing para evitar dependências externas de GUI.
- Gson para JSON pela simplicidade e robustez.
- Multicast para descoberta local, evitando dependência de servidor bootstrap.
- LRU Set para deduplicação de mensagens (prevenção de loops).
- Threads dedicadas por conexão: facilita fluxo e isolamento de bloqueios.
