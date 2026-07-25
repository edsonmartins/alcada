package app.alcada.plataforma.trilha.port;

import java.util.UUID;

/**
 * Ator de um evento de trilha, no formato do anexo do ADR-0016:
 * <pre>
 *   HUMANO:{pessoa_id}
 *   SISTEMA:{motor|regra}:{identificador}
 *   ASSISTENTE:{modelo}@{versao}
 * </pre>
 */
public record Ator(String valor) {

    public static Ator humano(UUID pessoaId) {
        return new Ator("HUMANO:" + pessoaId);
    }

    public static Ator sistemaMotor(String identificador) {
        return new Ator("SISTEMA:motor:" + identificador);
    }

    public static Ator sistemaRegra(String identificador) {
        return new Ator("SISTEMA:regra:" + identificador);
    }

    public static Ator assistente(String modelo, String versao) {
        return new Ator("ASSISTENTE:" + modelo + "@" + versao);
    }
}
