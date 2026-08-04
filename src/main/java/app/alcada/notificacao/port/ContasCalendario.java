package app.alcada.notificacao.port;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Contas de calendário conectadas (RFC-0009). Uma por gestor — a agenda é
 * pessoal, não do tenant (INV-15 mantém o escopo por organização mesmo assim).
 * Os tokens são guardados cifrados; quem lê aqui recebe em claro para usar.
 */
public interface ContasCalendario {

    /** Conta do gestor, se ele conectou alguma. */
    Optional<Conta> doGestor(OrgId org, UUID gestorId);

    /** Guarda (ou substitui) a conta conectada do gestor. */
    void salvar(OrgId org, UUID gestorId, Conta conta);

    /** Desconecta: a Alçada esquece os tokens e para de agendar na agenda dele. */
    void revogar(OrgId org, UUID gestorId);

    /**
     * @param provedor     GOOGLE | OUTLOOK
     * @param accessToken  token de acesso (em claro nesta fronteira)
     * @param refreshToken token de renovação, quando o provedor emite
     * @param expiraEm     validade do access token; null = desconhecida
     * @param escopo       escopos concedidos, para auditoria do mínimo necessário
     */
    record Conta(String provedor, String accessToken, String refreshToken,
                 OffsetDateTime expiraEm, String escopo) {

        public boolean vencido() {
            return expiraEm != null && !expiraEm.isAfter(OffsetDateTime.now());
        }
    }
}
