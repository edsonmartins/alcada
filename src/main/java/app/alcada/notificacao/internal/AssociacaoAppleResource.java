package app.alcada.notificacao.internal;

import jakarta.ws.rs.GET;
import jakarta.ws.rs.Path;
import jakarta.ws.rs.Produces;
import jakarta.ws.rs.core.MediaType;

/** Associação HTTPS que permite ao iOS abrir a delegação diretamente no app. */
@Path("/.well-known/apple-app-site-association")
public class AssociacaoAppleResource {

    @GET
    @Produces(MediaType.APPLICATION_JSON)
    public String associacao() {
        return """
                {
                  "applinks": {
                    "details": [{
                      "appIDs": ["T8N7A2P3TQ.app.alcada.alcadaMobile"],
                      "components": [{ "/": "/app/delegacoes/*", "comment": "Repasse interno autenticado" }]
                    }]
                  }
                }
                """;
    }
}
