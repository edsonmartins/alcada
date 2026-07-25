# Prompt de abertura — Claude Code

Três prompts, na ordem. Não junte: cada um tem um ponto de parada onde você confere antes de seguir.

---

## Sessão 1 — Reconhecimento e esqueleto

> Cole isto na raiz do repositório, com o corpus já presente.

```
Este repositório é o projeto Alçada — um plano de controle de decisões para gestores.
Ele é spec-driven: a especificação já existe e é a fonte da verdade. Você não vai
inventar produto, vai executar o que está escrito.

PRIMEIRO, leia nesta ordem e não escreva nada até terminar:
1. CLAUDE.md
2. CONSTITUTION.md
3. docs/GLOSSARIO.md
4. docs/PRODUTO.md (seções 4, 5 e 8)
5. docs/API.md
6. adr/ADR-0023 (stack vigente), ADR-0016 (trilha), ADR-0020 (gateway), ADR-0021 (Linktor)
   — ignore o ADR-0015, foi substituído
7. openspec/README.md

Depois de ler, me devolva — em no máximo 30 linhas:
(a) o modelo de domínio como você entendeu, em uma lista de agregados
(b) a máquina de estados da pendência
(c) os 5 pontos onde você acha que a especificação está ambígua ou incompleta o
    bastante para travar a implementação
(d) a ordem de execução que você propõe para os pacotes OpenSpec de F1

Não escreva código ainda. Se algo na especificação parecer contraditório, aponte —
não resolva por conta própria.
```

**Ponto de parada.** Leia o item (c) com atenção: é o retorno mais valioso da sessão. Ambiguidade
apontada agora custa uma linha de ADR; descoberta na implementação custa uma semana.

---

## Sessão 2 — Bootstrap do projeto

```
Vamos criar o esqueleto. Escopo desta sessão: estrutura, não funcionalidade.

Crie:
1. Projeto Quarkus (linha LTS) sobre Java 25, Maven, monólito modular com os módulos
   de CLAUDE.md §5. Cada módulo com fronteira real: pacote próprio, porta publicada,
   e teste ArchUnit que falha se um módulo acessar internals de outro.
2. PostgreSQL + Flyway. Migration inicial apenas com: organizacao, pessoa, e a tabela
   trilha (append-only, particionada por mês, sem UPDATE/DELETE — garanta por trigger).
3. Outbox transacional + worker usando SELECT ... FOR UPDATE SKIP LOCKED, com teste
   que prova: efeito externo não sai fora da transação e é entregue ao menos uma vez
   com idempotência.
4. Scheduler persistente em tabela de jobs, com claim por lock. Sem timer em memória
   e sem depender do @Scheduled para estado.
5. Contexto multi-tenant: org_id resolvido do token, propagado, e um teste que falha
   se alguma query de repositório for emitida sem filtro de org_id.
6. docker-compose de desenvolvimento: Postgres apenas. NÃO adicione Redis, Kafka ou
   qualquer outra infraestrutura — ADR-0023 fixou Postgres como dependência única.
7. Dois perfis de build: JVM para desenvolvimento e testes, nativo para release.
   Dockerfile do nativo com imagem final mínima.
8. CI: build JVM, testes, ArchUnit, Flyway validate. Build nativo só no release.

Regras: nada de regra de negócio nesta sessão. Nenhum endpoint de domínio.
Evite qualquer coisa que exija reflexão em runtime — vamos compilar nativo.
Ao final, me diga o RSS medido da aplicação em nativo. Alvo: até 120 MB.

Me mostre o plano de arquivos e migrations ANTES de criar, e espere eu confirmar.
```

**Ponto de parada.** Três verificações à mão: o teste que falha quando falta `org_id`, o trigger que
bloqueia `UPDATE` na trilha, e o RSS do binário nativo. Os dois primeiros sustentam INV-11 e INV-15
pelo resto do projeto; o terceiro é o que decide se o VPS pequeno era viável mesmo.

---

## Sessão 3 — Primeiro pacote de verdade

Os pacotes 004 (trilha) e 018 (gateway) ainda não têm spec escrita, e a captura depende dos dois.
Peça a escrita antes da implementação:

```
Escreva os pacotes OpenSpec 004-trilha-imutavel e 018-gateway-de-modelos, seguindo
exatamente o formato de openspec/changes/001-captura-multicanal (proposal, design,
tasks, spec com cenários WHEN/THEN).

Fontes: ADR-0016 para o 004; ADR-0020 e RFC-0007 para o 018.

Requisitos que precisam aparecer como cenário testável no 018:
- provedor sem suporte a json_schema estrito deve FALHAR, nunca degradar para json_object
- allow_fallbacks:false converte indisponibilidade em erro tratado, e a tarefa vai para
  fila de reprocesso — captura nunca é perdida
- tenant com SKU Soberano nunca sai para o gateway externo
- minimizador: nenhum identificador direto atravessa a fronteira (teste com corpus)
- re-hidratação não vaza token entre itens

Não implemente. Escreva as specs e me mostre para revisão.
```

Depois de aprovar, a execução segue o ciclo do CLAUDE.md §6, um pacote por vez, nesta ordem:

```
004 trilha → 018 gateway → 001 captura → 002 motor N2 → 003 triagem web → 005 executor
```

---

## Prompt curto para retomar sessão

```
Contexto: projeto Alçada, spec-driven. Leia CLAUDE.md e o pacote
openspec/changes/<NNN>-<nome>/ inteiro antes de qualquer coisa.
Estamos executando esse pacote. Verifique tasks.md para ver o que já está feito,
me proponha o próximo passo e espere confirmação.
```

---

## Como corrigir o agente quando ele desviar

Os desvios previsíveis, e a frase que resolve cada um:

| Desvio | Correção |
|---|---|
| Adiciona feature não especificada | "Isso não está em nenhum pacote OpenSpec. Se acha necessário, escreva a proposta primeiro." |
| Chama o modelo no caminho de execução | "INV-10: LLM propõe, código executa. Separe as camadas." |
| Faz `UPDATE` na trilha | "INV-11: append-only. Isso é evento de compensação." |
| Cria tela com arrastar-e-soltar | "ADR-0018 proíbe. A interface empurra, não oferece jardim." |
| Contorna a constituição para entregar | "Pare. Escreva o ADR de revogação e me mostre o impacto." |
| Implementa vários pacotes de uma vez | "Um pacote por vez. Termine e feche o atual." |
| Adiciona Redis, Kafka ou outro serviço | "ADR-0023: Postgres é a dependência única. Resolva com SKIP LOCKED." |
| Usa Archbase ou biblioteca interna | "ADR-0023: este produto não usa os frameworks da casa." |
| Introduz reflexão em runtime | "Vamos compilar nativo. Serialização explícita." |

---

## Antes da primeira linha de código

Três coisas que nenhum agente resolve e que mudam o que vai ser construído
(`docs/DECISOES-ABERTAS.md`):

- **G2** — o gestor-piloto aceita N2, com silêncio valendo aprovação? Se não, o roadmap muda.
- **G7** — qual o recall mínimo aceitável da captura? Sem número, não existe critério de falha.
- **G9** — qual a lista fechada de provedores no gateway? Ela é anexo contratual, não configuração.
