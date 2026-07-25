-- ============================================================================
-- Bootstrap de tenant para o piloto (idempotente). NADA fixo: tudo vem de
-- variáveis de ambiente (psql \getenv). Segredo (webhook_secret) é CREDENCIAL —
-- entra só por env, nunca neste arquivo. Rodar:  psql -f deploy/seed.sql
-- Ver deploy/README.md para a lista de variáveis.
-- ============================================================================

-- Aborta em qualquer erro (a guarda abaixo depende disto)
\set ON_ERROR_STOP on

-- Defaults; o env sobrescreve quando presente (\getenv só muda se a var existir)
\set org_id ''
\getenv org_id ALCADA_ORG_ID
\set org_nome 'Organização Piloto'
\getenv org_nome ALCADA_ORG_NOME
\set org_sku 'CLOUD'
\getenv org_sku ALCADA_ORG_SKU

\set gestor_id ''
\getenv gestor_id ALCADA_GESTOR_ID
\set gestor_nome 'Gestor'
\getenv gestor_nome ALCADA_GESTOR_NOME

\set executor_id ''
\getenv executor_id ALCADA_EXECUTOR_ID
\set executor_nome 'Executor'
\getenv executor_nome ALCADA_EXECUTOR_NOME

\set classe 'DECISAO'
\getenv classe ALCADA_CLASSE
\set janela 'PT2M'
\getenv janela ALCADA_JANELA
\set escalonamento 'PT24H'
\getenv escalonamento ALCADA_ESCALONAMENTO

\set fonte_id ''
\getenv fonte_id ALCADA_FONTE_ID
\set fonte_tipo 'WHATSAPP'
\getenv fonte_tipo ALCADA_FONTE_TIPO
\set fonte_ident 'grupo-piloto'
\getenv fonte_ident ALCADA_FONTE_IDENTIFICADOR
\set linktor_channel_id ''
\getenv linktor_channel_id ALCADA_LINKTOR_CHANNEL_ID
\set fonte_segredo ''
\getenv fonte_segredo ALCADA_FONTE_SEGREDO

-- Guarda: calcula um booleano fora de $$ (onde :'var' É substituído), guarda em
-- :ok e, se faltar algo, RAISE numa mensagem fixa (sem :'var', logo o $$ é
-- seguro). ON_ERROR_STOP → aborta antes de qualquer INSERT, com saída não-zero.
SELECT (:'org_id' <> '' AND :'gestor_id' <> '' AND :'executor_id' <> ''
        AND :'fonte_id' <> '' AND :'linktor_channel_id' <> ''
        AND :'fonte_segredo' <> '')::int AS ok \gset
\if :ok
\else
  DO $$ BEGIN RAISE EXCEPTION 'faltam variaveis obrigatorias: ALCADA_ORG_ID, ALCADA_GESTOR_ID, ALCADA_EXECUTOR_ID, ALCADA_FONTE_ID, ALCADA_LINKTOR_CHANNEL_ID, ALCADA_FONTE_SEGREDO (ver deploy/README.md)'; END $$;
\endif

BEGIN;

-- Organização (raiz do tenant)
INSERT INTO organizacao (id, nome, sku)
VALUES (:'org_id'::uuid, :'org_nome', :'org_sku')
ON CONFLICT (id) DO UPDATE SET nome = EXCLUDED.nome, sku = EXCLUDED.sku;

-- Gestor e executor
INSERT INTO pessoa (id, org_id, nome)
VALUES (:'gestor_id'::uuid, :'org_id'::uuid, :'gestor_nome')
ON CONFLICT (id) DO UPDATE SET nome = EXCLUDED.nome;

INSERT INTO pessoa (id, org_id, nome)
VALUES (:'executor_id'::uuid, :'org_id'::uuid, :'executor_nome')
ON CONFLICT (id) DO UPDATE SET nome = EXCLUDED.nome;

-- Classe de decisão com janela CURTA (para o cronômetro disparar na demo).
-- nivel_maximo=N1 → N2 elegível. Re-rodar atualiza a janela.
INSERT INTO classe_decisao (org_id, classe, nivel_padrao, nivel_maximo,
                            janela_reversibilidade, escalonamento, elegivel_n1)
VALUES (:'org_id'::uuid, :'classe', 'N2', 'N1',
        :'janela'::interval, :'escalonamento'::interval, true)
ON CONFLICT (org_id, classe) DO UPDATE
    SET janela_reversibilidade = EXCLUDED.janela_reversibilidade,
        escalonamento = EXCLUDED.escalonamento;

-- Fonte declarada do WhatsApp (ADR-0011): channelId do Linktor + webhook_secret.
INSERT INTO fonte (id, org_id, tipo, identificador, responsavel_id, ativa,
                   segredo, linktor_channel_id)
VALUES (:'fonte_id'::uuid, :'org_id'::uuid, :'fonte_tipo', :'fonte_ident',
        :'gestor_id'::uuid, true, :'fonte_segredo', :'linktor_channel_id')
ON CONFLICT (id) DO UPDATE
    SET segredo = EXCLUDED.segredo,
        linktor_channel_id = EXCLUDED.linktor_channel_id,
        ativa = true;

COMMIT;

\echo 'Bootstrap concluído: organizacao, gestor, executor, classe_decisao (janela' :'janela' '), fonte.'
