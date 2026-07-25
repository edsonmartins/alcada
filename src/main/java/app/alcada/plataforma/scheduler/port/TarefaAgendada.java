package app.alcada.plataforma.scheduler.port;

import java.time.OffsetDateTime;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Uma tarefa a executar no futuro, persistida na tabela de jobs.
 *
 * @param org         tenant (INV-15)
 * @param tipo        tipo do job (ex.: VIRADA_JANELA, ESCALONAMENTO, DESPERTAR)
 * @param chave       chave de idempotência dentro do tipo
 *                    — (delegacao_id,transicao) | (pendencia_id,transicao,ocorrencia) — RFC-0002
 * @param executarEm  instante de execução (UTC)
 * @param payloadJson dados do job como JSON
 */
public record TarefaAgendada(
        OrgId org, String tipo, String chave, OffsetDateTime executarEm, String payloadJson) {
}
