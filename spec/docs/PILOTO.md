# Piloto de 2 semanas — pacote de validação

O sistema (F1 + F2 + Linktor real) é o **instrumento de validação**, não o produto final. Este
documento é o que falta para usá-lo: o que o piloto responde, o mínimo para rodar, como observar sem
interferir, como medir o G7 sem baseline manual exaustivo, e o checklist de setup no ambiente do
cliente.

> **A pergunta que vem antes de tudo:** o gestor-piloto já viu o N2 rodar? Se não, é isto que o piloto
> existe para descobrir. Não se entra em F3 (inteligência de encolhimento) sem esta resposta vinda do
> **uso real**, não da suíte de testes.

---

## 1. O que o piloto responde

| Gate | Pergunta | Como se responde |
|---|---|---|
| **G2 (primário, bloqueante)** | O gestor aceita N2 — deixa o silêncio valer como aprovação? | Observação de uso + entrevista de fechamento. Se ele intervém em toda delegação por pânico, G2 falhou e o produto entrega ~30%. |
| **G7 (secundário)** | A captura passiva tem recall aceitável? | Triangulação de quatro fontes baratas (§4). Alvo provisório: recall ≥ 85%, escape < 10%. |
| **INV-01 (o teste do produto)** | O nº de itens que dependem do gestor cai? | Curva de dependência semanal (§3). Em 2 semanas é cedo para a curva, mas mede-se a **fração autônoma** já. |

**G2 é o que decide a direção.** G7 e INV-01 são medidas de qualidade e de tendência. G2 é sim/não
sobre a premissa do produto inteiro.

## 2. O mínimo para o cliente rodar

Nada de código novo — é configuração de um sistema pronto:

- **Um grupo real declarado como fonte** (ADR-0011): consentimento explícito, finalidade registrada,
  captura seletiva por **menção ao bot** (nunca varredura completa).
- **Linktor conectado a esse grupo** (WhatsApp), via QR (guia do Linktor).
- **1 gestor + ao menos 1 executor** cadastrados (a superfície do executor, 005, precisa de alguém do
  outro lado do N2).
- **Ao menos uma `classe_decisao`** configurada (janela de reversibilidade, escalonamento, nível
  máximo) — sem ela, não há N2 elegível para validar.
- **Deploy**: container nativo + Postgres (docker-compose + Traefik) num VPS pequeno. Binário ~71 MB
  RSS; o VPS de 2 GB do ADR-0023 basta.

## 3. Observar o uso sem interferir

O facilitador **lê, não age** — agir na fila contamina a medida de G2.

- **A trilha é append-only** (INV-11): tudo que aconteceu está lá, com ator e timestamp. É a fonte
  primária de observação — prova, não anedota.
- **Consultas read-only** (sem tocar a fila): estado da fila (`pendencia`), descartes
  (`descarte_captura`), cobranças/temperatura (`cobranca`), delegações e desfechos (`delegacao` +
  trilha de autonomia), comunicação (`COMUNICADA`/`FALHA_COMUNICACAO`/`COMUNICACAO_IMPOSSIVEL`).
- **Curva de dependência** (INV-01): por semana, contar itens que **dependem do gestor** —
  `ENTRADA` + `AGENDADA` + `DELEGADA` nível `N3`. A tendência de queda é o sinal do produto; em 2
  semanas mede-se o ponto de partida e a **fração autônoma** (`EXECUTADA_POR_AUSENCIA` / total
  fechado).
- Nenhuma tela nova é necessária: um relatório SQL diário sobre essas tabelas basta. As métricas
  formais (radar, `GET /v1/metricas/*`) são F3 — não são pré-requisito do piloto.

## 4. Medir o G7 sem baseline manual exaustivo

Recall exaustivo exigiria um humano lendo cada mensagem e rotulando o que "deveria" virar pendência —
caro e contra a premissa passiva. Em vez de um número falsamente preciso, **triangular quatro fontes
baratas** e reportar as quatro:

1. **Piso por escape manual.** Cada vez que o gestor usa o escape (`POST /v1/pendencias`) para
   adicionar algo que a captura não pegou, é um miss **confirmado**. `escape_rate = escapes /
   (capturados + escapes)`. É piso (só pega o que ele notou e se importou de adicionar). Alvo: < 10%.
2. **Auditoria amostral do descarte.** Amostrar N mensagens de `descarte_captura` (motivo
   `SEM_RELEVANCIA`) e verificar quantas eram pendências reais. Mede **diretamente o recall do filtro**
   — o ponto onde a captura mais perde — sem baseline completo.
3. **Reconciliação de sexta** (revisão semanal com facilitador humano, estilo F0). Pergunta única:
   "que decisões você tomou esta semana que **não** estavam na fila?" Cada uma é um miss que o gestor
   lembra. Barato, humano no laço.
4. **Dupla-codificação de 1 dia.** Em um dia aleatório da 2ª semana, um humano lê o grupo inteiro e
   lista o que deveria ter virado pendência; compara com o capturado naquele dia. Dá uma
   **ponto-estimativa** de recall com baseline real, porém pequeno — reportar com o intervalo largo
   que 1 dia merece, nunca como o recall "do piloto".

O recall estimado é a **leitura conjunta** das quatro, não a média. Se apontarem consistentemente
abaixo de 85%, aí — e só aí — a qualidade da extração vira decisão (§6).

## 5. Critérios de saída (o que conta como validado)

- **G2 validado** se, ao fim das 2 semanas: houve ao menos uma `EXECUTADA_POR_AUSENCIA` que o gestor
  **deixou** acontecer sem intervir em pânico, **e** na entrevista de fechamento ele declara conforto
  com "silêncio vale aprovação". Intervenção em 100% das delegações = G2 falhou.
- **G7 provisoriamente ok** se a triangulação (§4) não contradiz recall ≥ 85% e escape < 10%.
- **INV-01 indicativo** se a fração autônoma > 0 e a curva de dependência não sobe.

Nenhum destes é código. São observações do uso real.

## 6. Quando o OpenRouter real entra — e o fato que muda o cálculo

**Atenção, fato de qualidade não óbvio:** no build atual **não existe extração "com stub" em
produção.** O stub do gateway (`TransporteFake`) só vive nos testes. Em deploy real, o `ModelGateway`
usa o `TransporteHttp` (OpenRouter real), que exige **chave de API + a lista `only` homologada (G9)**.
Sem isso, toda tarefa de extração cai como indisponível → o item entra com `confianca = null`
(**extração pendente / baixa confiança**), e o gestor tria o **trecho cru**, sem campos estruturados.

Consequência para o piloto:
- **G2 pode ser testado com captura degradada.** Delegar N2 e deixar o silêncio valer não depende de
  extração boa — depende de haver itens na fila. Trechos crus bastam para exercer a delegação.
- **G7 NÃO pode ser medido com captura degradada.** Medir recall/qualidade de uma extração que não
  roda é medir só o filtro de relevância. Para G7 honesto, é preciso a extração real.

Portanto, o OpenRouter real **não é polimento de roadmap** — é **pré-requisito para medir G7**, e
opcional se o objetivo desta rodada for **só G2**. E ele arrasta o **G9** (lista fechada de
provedores) como dependência contratual. A decisão é sua e do cliente:

- **Piloto só-G2:** roda sem OpenRouter; itens em baixa confiança; valida a premissa do produto.
- **Piloto G2+G7:** exige OpenRouter real + G9 fechado antes de ligar a captura.

Não construo o gateway real por conta própria — é decisão de qualidade de captura, não de roadmap.

## 7. Checklist de setup no ambiente do cliente

**Infra**
- [ ] Provisionar VPS pequeno (2 GB); instalar Docker + Traefik.
- [ ] Subir Postgres (docker-compose) e a Alçada (container nativo). Confirmar `GET /q/health` = 200.
- [ ] Rodar as 12 migrations (automático na subida). Conferir versão 12.

**Tenant e pessoas**
- [ ] Criar `organizacao` (SKU `CLOUD` ou `SOBERANO`) e as `pessoa` do gestor e do(s) executor(es).
- [ ] Configurar OIDC (ou, se OIDC ainda não estiver ligado, o contexto por header no ambiente controlado).

**Canal (Linktor)**
- [ ] Criar o canal WhatsApp no Linktor apontando para o **grupo real declarado**; conectar por QR.
- [ ] Guardar o `channelId` e gerar o `webhook_secret` do canal.
- [ ] Registrar a `fonte` na Alçada: `tipo=WHATSAPP`, `linktor_channel_id=<channelId>`,
      `segredo=<webhook_secret>`, `finalidade`, `ativa=true` (ADR-0011: fonte declarada + consentimento).
- [ ] Apontar o `webhook_url` do canal no Linktor para `https://<alcada>/v1/captura/linktor`.
- [ ] Definir o **marcador de menção ao bot** (`captura.mencao-bot`) combinado com o grupo.

**Autonomia**
- [ ] Configurar ao menos uma `classe_decisao` (janela `PT4H`, escalonamento `PT24H`, nível máximo).
- [ ] (Se G2+G7) configurar `linktor.api.key` + a lista `only` (G9) do OpenRouter, e o SKU adequado.

**Verificação ponta a ponta (antes de abrir ao gestor)**
- [ ] Mensagem de teste no grupo mencionando o bot → item aparece na fila em < 30 s.
- [ ] Delegar N2 a um executor → executor vê em `GET /v1/delegacoes` → conclui → solicitante recebe
      fechamento no canal (ou `COMUNICACAO_IMPOSSIVEL` registrado, se sem conversa).
- [ ] Recobrança no grupo → temperatura sobe, sem criar item duplicado.

**Observação**
- [ ] Preparar o relatório SQL diário (§3) e agendar a reconciliação de sexta (§4.3).
- [ ] Combinar o dia da dupla-codificação (§4.4) na 2ª semana.

---

**Regra do piloto:** o objetivo não é o sistema parecer bom — é **descobrir se o gestor larga o
controle do N2**. Se ele larga, F3 tem premissa. Se não larga, nenhuma inteligência por cima resolve —
e é melhor saber em 2 semanas do que na renovação.
