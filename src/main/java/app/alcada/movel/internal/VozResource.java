package app.alcada.movel.internal;

import java.util.Optional;

import app.alcada.plataforma.gateway.port.ModelGateway;
import app.alcada.plataforma.gateway.port.Sensibilidade;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaTranscricao;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * {@code POST /v1/voz/transcrever} — STT na nuvem (RFC-0005/ADR-0026), via gateway
 * (chave só no servidor). Recebe áudio em base64 e devolve o texto. Em
 * indisponibilidade responde 503 — o app degrada para o STT on-device (INV-13).
 */
@Path("/v1/voz/transcrever")
@Produces(MediaType.APPLICATION_JSON)
public class VozResource {

    private final ModelGateway modelo;
    private final ContextoTenant contexto;

    public VozResource(ModelGateway modelo, ContextoTenant contexto) {
        this.modelo = modelo;
        this.contexto = contexto;
    }

    @POST
    public Response transcrever(Req req) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return erro(400, "org.ausente", "X-Org-Id não resolvido");
        }
        if (req == null || req.audioBase64() == null || req.audioBase64().isBlank()) {
            return erro(400, "audio.ausente", "audioBase64 é obrigatório");
        }
        String formato = req.formato() == null || req.formato().isBlank() ? "wav" : req.formato();
        String idioma = req.idioma() == null || req.idioma().isBlank() ? "pt" : req.idioma();
        try {
            var t = modelo.transcrever(new TarefaTranscricao(
                    org.get(), Sensibilidade.INTERNA, null, req.audioBase64(), formato, idioma));
            return Response.ok(new Resp(t.texto())).build();
        } catch (RuntimeException indisponivel) {
            // STT na nuvem falhou/indisponível → 503; o app usa o STT do aparelho.
            return erro(503, "transcricao.indisponivel", "transcrição na nuvem indisponível");
        }
    }

    private static Response erro(int status, String tipo, String detalhe) {
        return Response.status(status).type("application/problem+json")
                .entity(new Problema("urn:alcada:" + tipo, detalhe, status)).build();
    }

    public record Req(String audioBase64, String formato, String idioma) {
    }

    public record Resp(String texto) {
    }

    public record Problema(String type, String detail, int status) {
    }
}
