package app.alcada.plataforma.trilha.port;

/**
 * Vocabulário fechado da trilha — anexo normativo do ADR-0016, emendado por
 * ADR-0024 (devolução), ADR-0025 (comunicação) e RFC-0009 (lembrete/compromisso).
 * Nenhum módulo grava tipo fora desta lista sem emenda ao ADR. O CHECK vigente
 * está na última migration que o redefine ({@code V35__lembrete_datado.sql}).
 */
public enum TipoEvento {
    // Captura (descarte por irrelevância NÃO gera trilha — vai para métrica de captura)
    CAPTADA, FUNDIDA, DESFUNDIDA, ROTEADA_POR_REGRA,

    // Triagem
    RESOLVIDA, REPASSADA, RESERVADA, REPOUSADA, ADIADA, DESPERTADA, DESCARTADA,
    PEDIDO_INFORMACAO_CRIADO, PEDIDO_INFORMACAO_RESPONDIDO, PEDIDO_INFORMACAO_VENCIDO,

    // Lembrete e compromisso (RFC-0009): LEMBRETE_CRIADO fica na pendência de
    // origem e aponta para o lembrete; os dois seguintes são a entrega no
    // calendário do gestor (F2.3).
    LEMBRETE_CRIADO, COMPROMISSO_AGENDADO, FALHA_COMPROMISSO,

    // Autonomia
    PROPOSTA_REGISTRADA, JANELA_INICIADA, EXECUTADA, EXECUTADA_POR_AUSENCIA,
    DESFEITA_NA_JANELA, INTERROMPIDA, ESCALADA, CONVERTIDA_POR_AUSENCIA, NIVEL_PROMOVIDO,
    // DEVOLVIDA_PELO_EXECUTOR: executor recusou deliberadamente (ADR-0024) — ≠ ESCALADA
    DEVOLVIDA_PELO_EXECUTOR, RETORNO_RECEBIDO, RETORNO_AVALIADO,

    // Bloco
    BLOCO_AGENDADO, DOSSIE_MONTADO, DECIDIDA_NO_BLOCO,

    // Comunicação
    COMUNICADA, FALHA_COMUNICACAO,
    // COMUNICACAO_IMPOSSIVEL: não havia canal (item sem conversa inbound) — ADR-0025, ≠ FALHA_COMUNICACAO
    COMUNICACAO_IMPOSSIVEL,

    // Assistente (INV-10: registra proposta e desfecho, nunca execução)
    SUGESTAO_EMITIDA, SUGESTAO_ACEITA, SUGESTAO_RECUSADA, SUGESTAO_SILENCIADA, SUGESTAO_OBSERVADA,

    // Correção — único mecanismo de correção; referencia o evento compensado
    COMPENSACAO
}
