package app.alcada.notificacao.port;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Compromisso a criar na agenda do gestor (RFC-0009).
 *
 * @param gestorId       dono da agenda — o calendário é pessoal, não do tenant
 * @param quando         início, em instante absoluto
 * @param duracao        duração do bloco
 * @param titulo         o que o gestor falou ("Reunião Sharpi")
 * @param idempotencyKey chave do outbox: reprocesso não duplica o evento
 */
public record CriarEvento(UUID gestorId, OffsetDateTime quando, Duration duracao, String titulo,
                          String idempotencyKey) {
}
