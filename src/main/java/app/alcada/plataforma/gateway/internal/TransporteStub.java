package app.alcada.plataforma.gateway.internal;

import io.quarkus.arc.DefaultBean;
import io.quarkus.arc.properties.UnlessBuildProperty;
import jakarta.enterprise.context.ApplicationScoped;
import org.jboss.logging.Logger;

/**
 * Transporte de gateway para dev/test — nunca fala com o OpenRouter. É a
 * garantia de que dado de cliente jamais atravessa a fronteira externa fora de
 * {@code prod} (ADR-0020, mesma razão do stub do Linktor).
 *
 * <p>Responde sempre {@code INDISPONIVEL}: na captura, isso deixa o item em
 * baixa confiança ("extração pendente") — nunca uma extração falsa que pareça
 * real. Nos testes, é sobreposto pelo {@code TransporteFake} (não-default), que
 * programa desfechos determinísticos.
 */
@ApplicationScoped
@DefaultBean
@UnlessBuildProperty(name = "gateway.openrouter.enabled", stringValue = "true", enableIfMissing = true)
public class TransporteStub implements TransporteModelo {

    private static final Logger LOG = Logger.getLogger(TransporteStub.class);

    @Override
    public Resposta enviar(Requisicao req) {
        LOG.debugf("gateway stub (fora de prod): sem chamada externa para modelo %s", req.modelo());
        return Resposta.erro(Status.INDISPONIVEL);
    }
}
