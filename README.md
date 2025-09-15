# P2P Chat (Java, Swing)

Um chat P2P com múltiplos usuários, sem servidor central, usando sockets TCP e descoberta de peers via multicast (LAN). Inclui interface gráfica (Swing), broadcast com deduplicação, identificação de usuários, histórico de mensagens e encerramento seguro.

## Requisitos
- Java 11+ (testado com Java 24)
- Maven 3.6+

## Build
```bash
mvn -q -e -DskipTests package
```

O jar resultante estará em:
```
target/p2pchat-1.0.0-shaded.jar
```

## Execução
Em máquinas na mesma LAN:
```bash
java -jar target/p2pchat-1.0.0-shaded.jar
```

Passos sugeridos:
1. Abra em pelo menos dois computadores (ou duas instâncias locais).
2. Em cada instância:
   - Defina seu Nome.
   - Defina a Porta de escuta (ex.: 5000, 5001, ...).
   - Clique "Iniciar".
3. Use a seção "Descobertos (Multicast LAN)" para ver peers da rede e clicar em "Conectar Selecionado".
   - Alternativamente, informe `host` e `porta` manualmente e clique "Conectar".
4. Escreva mensagens na caixa de texto e clique "Enviar (Broadcast)".

## Recursos implementados
- Conexões múltiplas simultâneas (vários peers em malha parcial).
- Identificação de usuários (nome exibido junto à mensagem).
- Broadcast com prevenção de loops (mensagens com UUID e cache LRU de deduplicação).
- Interface gráfica (Swing) simples e responsiva.
- Histórico de mensagens gravado em `./history/*.log`.
- Descoberta de peers por UDP Multicast (grupo 230.0.0.1:4446) na mesma LAN.
- Encerramento seguro: sockets e threads fechados e histórico finalizado.

## Notas de rede
- Descoberta multicast geralmente funciona apenas dentro da mesma sub-rede e pode depender das políticas do roteador/switch.
- Firewalls podem bloquear multicast UDP (porta 4446) ou TCP nas portas configuradas; ajuste regras se necessário.

## Estrutura do código
- `net/PeerNode`: gerencia servidor TCP, conexões e broadcast.
- `net/ConnectionHandler`: I/O por conexão (threads de leitura e escrita).
- `net/DiscoveryService`: Anúncio/escuta via UDP multicast.
- `model/Message`, `model/PeerInfo`: protocolo simples em JSON.
- `gui/ChatWindow`: UI Swing (lista de conectados, descobertos, área de chat).
- `service/MessageHistory`: persistência do histórico.
- `util/LruSet`, `util/Json`: utilitários.

## Protocolo de mensagem (JSON por linha)
- `HELLO`: troca de identificação e lista de peers conhecidos.
- `CHAT`: mensagem de chat com `id` (UUID), `fromId`, `fromName`, `text`, `timestamp`.
- `PEERSHARE`: compartilhamento de peers conhecidos (anti-particionamento simples).

Cada JSON é enviado como uma linha (`\n`) em TCP.

## Demonstração
- Inicie duas instâncias em portas diferentes.
- Conecte-as (via descoberta ou manual).
- Envie mensagens de cada lado e observe:
  - O nome do remetente.
  - A entrega local e reencaminhamento (broadcast).
  - Histórico em `history/`.

## Limitações e melhorias futuras
- Descoberta funciona tipicamente apenas na LAN (multicast).
- Não há criptografia/autenticação (poderia ser adicionada com TLS e troca de chaves).
- Sem persistência de contatos/peers entre execuções (poderia salvar/ler).
- Interface simples; poderia evoluir para JavaFX, perfis, temas, etc.