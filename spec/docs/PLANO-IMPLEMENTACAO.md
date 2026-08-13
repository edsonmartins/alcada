# Plano de implementação — fechamento do ciclo diário

> Plano mestre para as próximas entregas da Alçada. Este documento organiza o trabalho; cada
> funcionalidade só entra em implementação depois de ganhar RFC/ADR quando necessário e um pacote
> OpenSpec aprovado, conforme `spec/CLAUDE.md`.

## 1. Objetivo

Fechar o ciclo entre captura, decisão, execução e retorno sem exigir que o gestor lembre, copie,
cobre ou reconcilie manualmente. A expansão deve melhorar ao menos uma das métricas constitucionais:

- **taxa de desbloqueio**;
- **fração autônoma**;
- **encolhimento** do que depende do gestor.

O plano não transforma a Alçada em gerenciador de tarefas, CRM, central de notificações ou
dashboard executivo.

## 2. Princípios de execução

1. **Validar antes de ampliar.** G2 (aceitação do N2) e G7 (confiança na captura) condicionam as
   apostas de autonomia e expansão de canais.
2. **Uma entrega vertical por pacote.** Banco, porta, regra determinística, trilha, API, superfície e
   testes entram juntos quando fizerem parte da mesma jornada.
3. **Nenhuma segunda fila.** Retornos, lembretes e consultas convergem para a Pendência existente ou
   para a Entrada; não ganham caixas próprias.
4. **Ação junto do diagnóstico.** Revisão e métricas precisam permitir corrigir o problema mostrado.
5. **Efeito externo pela outbox.** Com idempotência, janela de reversibilidade e trilha.
6. **MVP estreito.** Primeiro o caso que ocorre no piloto; provedores e variações adicionais só
   entram depois de uso medido.

## 3. Sequência de ondas

| Onda | Resultado | Condição de saída |
|---|---|---|
| **0 — verdade operacional** | produto, documentação e piloto descrevem o mesmo sistema | baseline coletada e gates instrumentados |
| **1 — fechar o ciclo** | delegação e pedido de informação não exigem cobrança manual | retorno recebido e correlacionado em uso real |
| **2 — recuperar e agir** | usuário encontra qualquer decisão e conclui a revisão sem jardinagem | revisão termina com transições e busca responde casos reais |
| **3 — confiar e sincronizar** | falhas de captura e agenda ficam verificáveis | cobertura medida e calendário reconciliado |
| **4 — encolher com segurança** | regras removem categorias inteiras da fila com risco controlado | promoção N3 → N2 → N1 medida sem reversões críticas |
| **5 — ampliar somente com evidência** | novos canais/provedores atendem demanda comprovada | caso comercial e volume justificam manutenção |

---

## 4. Onda 0 — verdade operacional e piloto

### P027 — Saneamento do estado do produto

**Resultado:** uma única fotografia confiável do que está implementado, escrito, validado ao vivo e
pendente.

**Escopo:**

- corrigir status defasados em README, produto, índice OpenSpec e decisões abertas;
- distinguir `implementado`, `escrito`, `testado localmente`, `validado ao vivo` e `adotado`;
- reconciliar tarefas abertas que o índice já apresenta como concluídas;
- criar matriz de jornadas por persona e ambiente;
- registrar dívida de UX do piloto, como campos que ainda pedem IDs técnicos.

**Aceite:** nenhum pacote aparece como concluído se depende de teste ao vivo ou tarefa funcional
aberta sem anotação explícita.

### P028 — Instrumentação do piloto G2/G7

**Resultado:** decisão objetiva sobre aceitação do N2 e confiança na captura.

**Escopo MVP:**

- relatório diário de dependência, fração autônoma, intervenção N2, reversão e escape manual;
- amostragem auditável de descartes;
- reconciliação guiada na sexta: decisões que ocorreram fora da fila;
- registro do motivo de intervenção no N2, com seleção curta e comentário opcional;
- relatório de encerramento do piloto, sem ranking individual;
- alertas operacionais de fonte sem eventos e falha persistente do gateway.

**Fora do escopo:** dashboard de produtividade, comparação entre pessoas e automação de regra.

**Gate de saída:** evidência suficiente para encerrar G2 e revisar os limites provisórios de G7.

### P029 — Repasse sem identificador técnico

**Resultado:** gestor repassa em segundos usando pessoas reconhecíveis.

**Escopo MVP:**

- busca por nome, apelido e contato;
- recentes e destinos usados naquela classe;
- destino interno ou externo no mesmo seletor;
- nível e prazo sugeridos a partir do último contrato equivalente, sempre confirmados pelo humano;
- cadastro de contato externo apenas como consequência do repasse, não como manutenção prévia;
- deduplicação de contato por organização e endereço normalizado.

**Aceite:** nenhum fluxo comum de repasse exige copiar UUID ou abrir a configuração de contatos.

---

## 5. Onda 1 — fechar o ciclo com executores e terceiros

### P030 — Retorno correlacionado de executor e contato externo

**Resultado:** respostas no canal voltam à Pendência correta e podem destravar a próxima ação.

**Escopo MVP:**

- identificador opaco de correlação em toda comunicação de repasse;
- ingestão de respostas pelo Linktor;
- classificação proposta: `INFORMACAO`, `PROPOSTA`, `RESULTADO`, `COBRANCA`, `CONTESTACAO`,
  `PEDIDO_PRAZO` ou `SEM_EFEITO`;
- anexação do trecho minimizado à trilha/dossiê;
- regra determinística para transições permitidas;
- ambiguidade volta para a Entrada com explicação, nunca executa efeito externo;
- idempotência para mensagem repetida e threading imperfeito;
- opt-out e base legal do contato externo.

**Risco principal:** correlacionar resposta ao item errado. O MVP deve preferir “não correlacionar” a
fundir silenciosamente.

**Aceite:** no cenário do piloto, uma resposta do terceiro atualiza o item sem cópia manual e sem
criar pendência duplicada.

### P031 — Pedido estruturado de informação

**Resultado:** quando falta um insumo, a bola vai para quem o possui e retorna automaticamente.

**Fluxo:**

1. gestor escolhe `Falta insumo` ou `Falta terceiro`;
2. sistema sugere destinatário e pergunta objetiva;
3. humano confirma o texto;
4. pedido sai pela outbox e o item repousa com condição de retorno;
5. resposta correlacionada desperta o item com a evidência anexada.

**Aceite:** o item não permanece falsamente como dependente do gestor enquanto outra pessoa deve
fornecer o insumo.

### P032 — Prazos úteis e lembretes por exceção

**Resultado:** o sistema acompanha o contrato sem o gestor vigiar delegações.

**Escopo MVP:**

- calendário comercial por organização: fuso, dias úteis, horário e feriados configurados pelo admin;
- lembrete ao executor quando 50% do prazo passa sem proposta;
- aviso ao gestor quando 90% da janela N2 passa, apenas se ainda houver ação possível;
- escalonamento por silêncio de ambos;
- resumo de exceções no início e no fim do dia, sem alerta de “novo item”;
- preferência de canal e horário por gestor.

**Aceite:** lembretes não disparam fora da política, são deduplicados e toda notificação oferece uma
ação concreta.

---

## 6. Onda 2 — recuperação e revisão resolutiva

### P033 — Consulta completa de pendências e decisões

**Resultado:** localizar o estado e a história de qualquer decisão sem criar uma fila paralela.

**Escopo MVP:**

- rota `/itens` somente para consulta;
- busca textual por título, contraparte, origem, executor e conteúdo minimizado;
- filtros fixos por estado, classe, nível, pessoa, período e origem;
- decisões fechadas e trilha no mesmo resultado;
- consultas naturais reutilizam a mesma política e devolvem fontes clicáveis;
- links profundos para bloco, delegação, regra ou instância.

**Restrições:** sem drag-and-drop, etiquetas, prioridade manual, edição em massa de metadados ou
salvamento de múltiplas “visões”.

**Aceite:** cinco perguntas operacionais definidas no `spec.md` são respondidas em menos de três
interações e com isolamento por organização testado.

### P034 — Revisão de sexta com execução incorporada

**Resultado:** a revisão termina com menos dependência, não apenas com diagnóstico.

**Escopo MVP:**

- triar a Entrada dentro do roteiro;
- resolver, repassar, repousar ou abrir bloco para adiados recorrentes;
- aceitar, recusar ou observar proposta de regra com evidência;
- revisar delegações N3 candidatas a N2 e N2 candidatas a N1;
- mostrar impacto no horizonte trimestral e oferecer proteção de agenda;
- resumo final das transições e do que continuará dependendo do gestor.

**Aceite:** cada etapa possui próxima ação e a sessão registra quantas dependências foram removidas.

### P035 — Resumo diário por exceção

**Resultado:** o usuário sabe onde intervir sem abrir o sistema para varrer filas.

**Conteúdo máximo:**

- três itens de Hoje;
- N2 prestes a executar;
- retornos que exigem decisão;
- escalonamentos;
- estimativa de tempo de despacho baseada em mediana histórica, nunca em pontuação manual.

**Aceite:** um resumo por período e usuário, deduplicado; nenhuma mensagem por entrada individual.

---

## 7. Onda 3 — confiança e sincronização

### P036 — Central de confiança da captura

**Resultado:** gestor e admin conseguem verificar e corrigir cobertura sem ler todo o canal.

**Escopo MVP:**

- revisão amostral de descartes e baixa confiança;
- ações `deveria entrar` e `não deveria entrar`;
- evidência de origem e explicação da extração;
- saúde por Fonte: último evento, latência, falhas e proporção processada;
- reconciliação semanal integrada ao P034;
- métricas de recall estimado, precisão de BLOQUEIO e escape com ação corretiva.

**Restrições:** conteúdo bruto respeita retenção; aprendizado não cruza organização; modelo apenas
propõe ajuste.

### P037 — Calendário bidirecional

**Resultado:** mudança ou cancelamento no calendário não deixa a Alçada divergente.

**Escopo MVP:**

- webhook/poll incremental do Google Calendar;
- mover evento recalcula o despertar no fuso aplicável;
- cancelar evento oferece cancelar ou devolver o lembrete à Entrada;
- resolução determinística de concorrência, registrada na trilha;
- estado de sincronização visível no item;
- teste ao vivo antes de marcar o pacote como validado.

**Depois do MVP:** adaptador Outlook no mesmo contrato e política explícita para gestor viajando.

---

## 8. Onda 4 — autonomia progressiva e segura

### P038 — Simulador de regra de autonomia

**Resultado:** o gestor entende o impacto e o risco antes de promover uma classe.

**Escopo MVP:**

- replay determinístico dos últimos 90 dias;
- quantidade de itens que deixaria de tocar o gestor;
- divergências, intervenções e reversões no histórico;
- limites sugeridos com evidência: pessoa, valor, classe e condição;
- nenhum efeito externo durante simulação.

### P039 — Observação e implantação gradual de regra

**Resultado:** promover N3 → N2 → N1 com contenção de risco.

**Modos:**

- `OBSERVAR`: registra o que a regra faria;
- `N2_RESTRITO`: aplica a uma pessoa, limite ou período;
- `N1_ATIVO`: executa dentro do contrato aprovado.

**Guardrails:** expiração opcional, limite financeiro, classe recusável, pausa imediata, reversão
crítica suspendendo a regra e trilha de todas as sugestões/decisões.

**Gate de saída:** aumento mensurável da fração autônoma sem ultrapassar o limite de reversão definido
no piloto.

### P040 — Memória operacional situada

**Resultado:** decidir sem procurar contexto em outros sistemas.

**Escopo:** últimos desfechos da contraparte, compromissos abertos, regras aplicáveis, exceções e
fontes relevantes dentro do bloco de decisão. Não inclui cadastro comercial ou visão 360° de CRM.

---

## 9. Onda 5 — expansões condicionadas

Estes itens ficam no backlog, sem compromisso de implementação até haver evidência:

- Outlook/Microsoft 365 após uso real do Google Calendar;
- Slack ou Teams após cliente demandante e desenho de captura seletiva;
- novos tipos de esteira após validação do gargalo do integrador;
- recorrência de lembretes somente se não introduzir gestão de tarefas;
- novos provedores de modelo após G9 contratual;
- novas superfícies de voz após o gate PT-BR com ruído real.

## 10. Dependências

```text
P027 ─┬─> P028 ────────────────> decisão G2/G7 ──> P038 ─> P039
      └─> P029 ─> P030 ─┬─> P031
                         └─> P032 ─> P035

P033 ──────────┬─> P034 ─> P036
P028 ──────────┘

P037 depende da validação ao vivo do calendário atual
P040 depende de P030 e P033 para contexto recuperável consistente
```

## 11. Ordem recomendada de abertura dos pacotes

1. **P027 — saneamento**, porque corrige a base usada para planejar.
2. **P028 e P029**, em paralelo lógico: medição do piloto e remoção da maior fricção de UX.
3. **P030**, espinha dorsal do retorno de terceiros.
4. **P031 e P032**, que usam a correlação e completam o acompanhamento.
5. **P033 e P034**, recuperação e revisão com ação.
6. **P035 e P036**, resumo por exceção e confiança operacional.
7. **P037**, depois do teste real da integração atual.
8. **P038 e P039**, somente com G2 encerrado positivamente e histórico suficiente.
9. **P040**, quando a base de correlação e busca estiver estável.

## 12. Definition of Done de cada pacote

Um pacote só está concluído quando:

- `proposal.md`, `design.md`, `spec.md` e `tasks.md` foram aprovados;
- decisões novas ganharam ADR/RFC quando necessário;
- migrations são aditivas e compatíveis com rollback de aplicação;
- toda query carrega `org_id` e possui teste de isolamento;
- toda escrita externa usa outbox e chave idempotente;
- toda transição relevante entra na trilha;
- cada cenário WHEN/THEN possui teste rastreável;
- web/mobile têm estados vazio, carregando, erro, sucesso e desfazer quando aplicável;
- acessibilidade por teclado é verificada no web;
- métricas de resultado e operação foram instrumentadas;
- documentação da API e status do índice foram atualizados;
- o fluxo crítico foi testado em ambiente integrado; quando depender de provedor, também ao vivo;
- foi medido o impacto no teto de manutenção de 5 min/dia + 20 min/semana.

## 13. Métricas e gates do programa

| Métrica | Baseline | Gate inicial | Decisão associada |
|---|---:|---:|---|
| Recall estimado da captura | coletar no P028 | hipótese atual ≥ 85% | ampliar captura ou corrigir filtro |
| Escape manual | coletar no P028 | hipótese atual < 10% | confiança na captura |
| Intervenção em N2 | coletar no P028 | sem meta antes do piloto | seguir ou recalibrar proposta |
| Reversão após N2 | coletar no P028 | limite definido com piloto | promoção segura |
| Fração autônoma | coletar semanalmente | tendência crescente | P038/P039 |
| Recobrança | coletar antes do P030 | queda após P030/P031 | valor da correlação |
| Retorno correlacionado | zero/baseline | meta após teste real | qualidade do P030 |
| Tempo até desbloqueio | coletar por classe | tendência decrescente | prioridade entre pacotes |
| Manutenção diária | entrevista + eventos | ≤ 5 min/dia | rejeitar fricção adicional |
| Dependência do gestor | série semanal | queda em 90 dias | sucesso do produto |

Metas finais serão fixadas com dados do primeiro piloto. Números provisórios não devem virar SLA ou
promessa comercial sem amostra real.

## 14. Primeiro ciclo de execução

O primeiro ciclo recomendado contém somente:

1. abrir e executar P027;
2. escrever os pacotes OpenSpec P028 e P029;
3. levantar baseline do piloto;
4. desenhar a RFC de retorno correlacionado (P030) com o contrato real do Linktor;
5. decidir P030 somente depois de validar envelope, threading e capacidades de resposta do canal.

Esse ciclo entrega aprendizado e utilidade imediata sem comprometer o produto com toda a expansão de
uma vez.
