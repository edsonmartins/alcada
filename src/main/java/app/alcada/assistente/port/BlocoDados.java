package app.alcada.assistente.port;

import java.util.List;

/**
 * Bloco de decisão de uma pendência (RFC-0004): dossiê determinístico (fatos do
 * item, com a trilha como fonte navegável) + opções com consequência.
 */
public record BlocoDados(String pendenciaId, String titulo, String classe,
                         List<ItemDossie> dossie, List<Opcao> opcoes) {

    public record ItemDossie(String rotulo, String valor) {
    }

    public record Opcao(String chave, String rotulo, String consequencia) {
    }
}
