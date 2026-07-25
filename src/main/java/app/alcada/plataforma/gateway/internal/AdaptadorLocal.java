package app.alcada.plataforma.gateway.internal;

import jakarta.enterprise.context.ApplicationScoped;

/**
 * Inferência local (SKU Soberano e classe RESTRITA). Stub nesta fase: a stack
 * local entra quando o SKU Soberano for implementado. O que importa agora é a
 * garantia de <b>roteamento</b>: RESTRITA/Soberano nunca tocam o transporte
 * externo. Por isso este stub não faz chamada externa alguma.
 */
@ApplicationScoped
public class AdaptadorLocal {

    public String inferir(String texto, String schemaJson) {
        throw new UnsupportedOperationException(
                "inferência local não disponível nesta fase (SKU Soberano) — nada saiu para fora");
    }
}
