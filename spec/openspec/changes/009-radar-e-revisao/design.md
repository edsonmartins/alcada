# Design — 009 radar e revisão de sexta

## Princípio
Leitura pura. O módulo `metricas` só lê (trilha, pendência, delegação, adiamento) e agrega. Nenhuma
transição de estado, nenhum outbox, nenhuma chamada de modelo (INV-10 não se aplica: não há decisão).

## Fontes de dados (já existentes)
- `pendencia`: `status`, `classe`, `horizonte`, `valor_em_jogo`, `adiado_count`, `quem_espera`,
  `criada_em`.
- `delegacao`: `nivel`, `status`.
- `trilha`: eventos com `tipo` e `ocorrido_em` (vocabulário fechado).
- `adiamento`: histórico (o `adiado_count` da pendência já é o contador de leitura).

## `GET /v1/radar` → RadarDTO
- `dependeDoGestor {qtd,total,pct}` — abertos = `status <> 'FECHADA'`; dependem = `status IN
  ('ENTRADA','AGENDADA')` **ou** `status='DELEGADA'` com delegação ativa `nivel='N3'` (PRODUTO §5:
  N3 e AGENDADA contam).
- `rodandoSemVoce` — nº de pendências `DELEGADA` com delegação ativa `nivel IN ('N1','N2')`.
- `adiados[]` — pendências com `adiado_count >= 3` (id, titulo, adiadoCount, oQueTrava, quemEspera,
  valorEmJogo). É diagnóstico, não priorização.
- `piorEspera {pendenciaId,titulo,dias,quemEspera}` — aberto mais antigo por `criada_em`.
- `autonomia {deliberada,porAusencia,devolvida,escalada,promovida}` — contagem de trilha na janela
  (90 dias): `EXECUTADA`, `EXECUTADA_POR_AUSENCIA`, `DEVOLVIDA_PELO_EXECUTOR`, `ESCALADA`,
  `NIVEL_PROMOVIDO`. Separados (ADR-0024).
- `fechamentoCanal {entregue,falho,impossivel}` — `COMUNICADA`, `FALHA_COMUNICACAO`,
  `COMUNICACAO_IMPOSSIVEL` (ADR-0025), 90 dias.
- `encolhimento[8]` — por semana (8 semanas, ancoradas em America/Sao_Paulo): `entraram` = `CAPTADA`;
  `fecharam` = `RESOLVIDA + EXECUTADA + EXECUTADA_POR_AUSENCIA + DECIDIDA_NO_BLOCO`. É **fluxo**, não
  estoque — o front rotula "entradas × fechamentos por semana", com a leitura do INV-01 (se entram
  mais do que fecham, o gargalo cresce).

## `GET /v1/revisao-semanal` → RevisaoDTO
Roteiro sequencial (o front renderiza passo a passo):
1. `entrada {qtd,itens[]}` — `status='ENTRADA'` (a fila a esvaziar).
2. `adiados[]` — mesmo do radar (`adiado_count >= 3`): decisão é resolver, soltar ou matar.
3. `podeVirarRegra[]` — **dica** (não regra): assinaturas `{classe}` com `>= 3` `RESOLVIDA` nas
   últimas 4 semanas → aponta para a mineração (RFC-0003, pacote futuro). Marcado como dica.
4. `resumoSemana {resolvidas,executadas,delegadas,escaladas,devolvidas,fechadas}` — contagem de
   trilha na semana corrente (seg–dom, SP).

## ADR-0017 no front
Cada card de padrão pessoal traz **ação** (resolver/soltar/repassar/promover) e **causa provável**
("N destes envolvem dizer não a alguém"), sem score, ranking, streak ou comparação entre pessoas.
Título do radar: "Diagnóstico organizacional — não placar pessoal".

## Multi-tenant e guarda (INV-15)
Toda query carrega `org_id` no predicado — passa pelo `GuardaOrgId`. Endpoints resolvem `org_id` do
contexto (header no piloto, claim OIDC em prod).

## Native image
DTOs devolvidos via `Response` → `@RegisterForReflection`. Datas `timestamptz` normalizadas
(helper `toOdt`). Semana em `America/Sao_Paulo` (apresentação); banco em UTC.

## Fora do design (decisões adiadas)
- Snapshot semanal de estoque (curva real de dependência) — quando existir, `encolhimento` troca
  fluxo por estoque sem mudar o contrato do endpoint.
- Ação de "promover a regra" a partir do radar — depende da mineração (RFC-0003).
