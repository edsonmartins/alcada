package app.alcada.movel.internal;

import java.util.List;
import java.util.Optional;

import app.alcada.movel.internal.InterpretadorVoz.ItemFila;
import app.alcada.plataforma.multitenancy.port.ContextoTenant;
import app.alcada.plataforma.multitenancy.port.OrgId;
import jakarta.ws.rs.POST;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;
import jakarta.ws.rs.core.Response;

/**
 * {@code POST /v1/voz/interpretar} (022): fala livre + contexto da conversa +
 * fila → intenção estruturada (LLM propõe, INV-10). Comandos voltam para o app
 * confirmar; CONSULTAR já traz a resposta. Sem LLM, devolve NENHUMA (o app usa o
 * matcher offline).
 */
@Path("/v1/voz/interpretar")
@Produces(MediaType.APPLICATION_JSON)
public class InterpretadorVozResource {

    private final InterpretadorVoz interpretador;
    private final ContextoTenant contexto;

    public InterpretadorVozResource(InterpretadorVoz interpretador, ContextoTenant contexto) {
        this.interpretador = interpretador;
        this.contexto = contexto;
    }

    @POST
    public Response interpretar(Req req) {
        Optional<OrgId> org = contexto.atual();
        if (org.isEmpty()) {
            return Response.status(400).type("application/problem+json")
                    .entity(new Problema("urn:alcada:org.ausente", "X-Org-Id não resolvido", 400)).build();
        }
        if (req == null || req.texto() == null || req.texto().isBlank()) {
            return Response.status(400).type("application/problem+json")
                    .entity(new Problema("urn:alcada:texto.ausente", "texto é obrigatório", 400)).build();
        }
        List<ItemFila> fila = req.itens() == null ? List.of()
                : req.itens().stream().map(i -> new ItemFila(i.id(), i.titulo())).toList();
        var r = interpretador.interpretar(org.get(), req.texto(),
                req.contexto() == null ? List.of() : req.contexto(), fila);
        return Response.ok(r).build();
    }

    public record Req(String texto, List<String> contexto, List<ItemReq> itens) {
    }

    public record ItemReq(String id, String titulo) {
    }

    public record Problema(String type, String detail, int status) {
    }
}
