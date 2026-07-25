package app.alcada.web;

import io.quarkus.vertx.web.Route;
import io.vertx.ext.web.RoutingContext;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Fallback de SPA: rotas do cliente (ex.: {@code /hoje}, {@code /executor})
 * não têm arquivo estático correspondente — sem isto, um refresh/deep-link daria
 * 404. Aqui, GETs fora de {@code /v1}, {@code /q}, {@code /p} e sem extensão de
 * arquivo são reencaminhados para {@code /index.html}; a API, os assets e o
 * health seguem normalmente. Same-origin: nenhuma configuração de CORS.
 */
@ApplicationScoped
public class SpaFallback {

    @Route(methods = Route.HttpMethod.GET, path = "/*", order = 100)
    void fallback(RoutingContext rc) {
        String p = rc.normalizedPath();
        boolean api = p.startsWith("/v1") || p.startsWith("/q") || p.startsWith("/p");
        boolean arquivo = p.equals("/") || p.lastIndexOf('.') > p.lastIndexOf('/');
        if (api || arquivo) {
            rc.next();          // API, health, portal público, raiz e assets: seguem
        } else {
            rc.reroute("/index.html");  // rota do cliente → o SPA resolve
        }
    }
}
