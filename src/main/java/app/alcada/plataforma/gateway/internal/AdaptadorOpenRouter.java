package app.alcada.plataforma.gateway.internal;

import java.util.List;

import app.alcada.plataforma.gateway.port.FalhasGateway;
import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Adaptador OpenRouter: aplica a política fixa e traduz o desfecho do
 * transporte. Schema estrito rejeitado pelo provedor vira {@link
 * FalhasGateway.ProvedorSemSchema} — <b>nunca</b> degrada para {@code json_object}.
 */
@ApplicationScoped
public class AdaptadorOpenRouter {

    /** Lista `only` homologada — configuração (começa com um provedor). */
    @ConfigProperty(name = "gateway.only")
    List<String> only;

    @ConfigProperty(name = "gateway.modelo.extracao", defaultValue = "homologado/extrator")
    String modeloExtracao;

    @ConfigProperty(name = "gateway.modelo.redacao", defaultValue = "homologado/redator")
    String modeloRedacao;

    private final TransporteModelo transporte;

    public AdaptadorOpenRouter(TransporteModelo transporte) {
        this.transporte = transporte;
    }

    public PoliticaProvedor politicaFixa() {
        return PoliticaProvedor.fixa(only);
    }

    public String modeloExtracao() {
        return modeloExtracao;
    }

    public String modeloRedacao() {
        return modeloRedacao;
    }

    /** Provedor efetivo para observabilidade — o primeiro homologado da lista. */
    public String provedorEfetivo() {
        return only.isEmpty() ? "?" : only.get(0);
    }

    public ResultadoExterno extrair(String texto, String schemaJson) {
        if (schemaJson == null || schemaJson.isBlank()) {
            throw new IllegalArgumentException("extração exige schema estrito");
        }
        return traduzir(transporte.enviar(
                new TransporteModelo.Requisicao(modeloExtracao, texto, schemaJson, politicaFixa())));
    }

    public ResultadoExterno redigir(String contexto, String tom) {
        String texto = tom == null ? contexto : "[tom:" + tom + "] " + contexto;
        return traduzir(transporte.enviar(
                new TransporteModelo.Requisicao(modeloRedacao, texto, null, politicaFixa())));
    }

    private ResultadoExterno traduzir(TransporteModelo.Resposta r) {
        return switch (r.status()) {
            case OK -> new ResultadoExterno(r.conteudo(), r.tokensIn(), r.tokensOut());
            case SEM_SUPORTE_SCHEMA -> throw new FalhasGateway.ProvedorSemSchema(
                    "provedor sem json_schema estrito — recusado, sem degradar para json_object");
            case INDISPONIVEL -> throw new FalhasGateway.Indisponivel(
                    "provedores homologados indisponíveis (allow_fallbacks:false)");
            case GUARDRAIL_RECUSOU -> throw new FalhasGateway.GuardrailRecusou(
                    "provedor fora da lista `only` recusado por guardrail");
        };
    }

    /** Conteúdo devolvido pelo provedor externo + contagem de tokens. */
    public record ResultadoExterno(String conteudo, int tokensIn, int tokensOut) {
    }
}
