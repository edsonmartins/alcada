package app.alcada.plataforma.trilha.port;

/**
 * Vocabulário fechado da trilha — anexo normativo do ADR-0016 (29 tipos).
 * Nenhum módulo grava tipo fora desta lista sem emenda ao ADR. O CHECK em
 * {@code V1__identidade_e_trilha.sql} espelha exatamente estes valores.
 */
public enum TipoEvento {
    // Captura (descarte por irrelevância NÃO gera trilha — vai para métrica de captura)
    CAPTADA, FUNDIDA, DESFUNDIDA, ROTEADA_POR_REGRA,

    // Triagem
    RESOLVIDA, REPASSADA, RESERVADA, REPOUSADA, ADIADA, DESPERTADA, DESCARTADA,

    // Autonomia
    PROPOSTA_REGISTRADA, JANELA_INICIADA, EXECUTADA, EXECUTADA_POR_AUSENCIA,
    DESFEITA_NA_JANELA, INTERROMPIDA, ESCALADA, CONVERTIDA_POR_AUSENCIA, NIVEL_PROMOVIDO,
    // DEVOLVIDA_PELO_EXECUTOR: executor recusou deliberadamente (ADR-0024) — ≠ ESCALADA
    DEVOLVIDA_PELO_EXECUTOR,

    // Bloco
    BLOCO_AGENDADO, DOSSIE_MONTADO, DECIDIDA_NO_BLOCO,

    // Comunicação
    COMUNICADA, FALHA_COMUNICACAO,
    // COMUNICACAO_IMPOSSIVEL: não havia canal (item sem conversa inbound) — ADR-0025, ≠ FALHA_COMUNICACAO
    COMUNICACAO_IMPOSSIVEL,

    // Assistente (INV-10: registra proposta e desfecho, nunca execução)
    SUGESTAO_EMITIDA, SUGESTAO_ACEITA, SUGESTAO_RECUSADA, SUGESTAO_SILENCIADA,

    // Correção — único mecanismo de correção; referencia o evento compensado
    COMPENSACAO
}
