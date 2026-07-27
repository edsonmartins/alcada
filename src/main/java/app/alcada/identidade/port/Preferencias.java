package app.alcada.identidade.port;

import java.util.Optional;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/**
 * Preferências do gestor aprendidas do uso (022, memória durável). Escopadas por
 * (org, gestor) — INV-15. Não é cadastro (INV-02): cada preferência é gravada a
 * partir de uma ação real do gestor e usada depois para preencher um padrão sem
 * perguntar. Primeiro uso: o nível de repasse habitual.
 */
public interface Preferencias {

    /** Nível de repasse habitual do gestor (N1/N2/N3), se já houver histórico. */
    Optional<String> nivelRepasse(OrgId org, UUID gestorId);

    /** Registra o nível usado num repasse como o padrão do gestor (último vence). */
    void registrarNivelRepasse(OrgId org, UUID gestorId, String nivel);
}
