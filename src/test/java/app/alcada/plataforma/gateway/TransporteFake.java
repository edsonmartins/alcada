package app.alcada.plataforma.gateway;

import java.util.concurrent.atomic.AtomicInteger;

import app.alcada.plataforma.gateway.internal.TransporteModelo;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Transporte de teste: substitui o {@code @DefaultBean} real (TransporteHttp).
 * Programa o desfecho, conta invocações e guarda a última requisição — para
 * asserções de política fixa e de "não sai para fora".
 */
@ApplicationScoped
public class TransporteFake implements TransporteModelo {

    private volatile Status status = Status.OK;
    private volatile String conteudo = "{\"ok\":true}";
    private final AtomicInteger chamadas = new AtomicInteger();
    private volatile Requisicao ultima;

    @Override
    public Resposta enviar(Requisicao req) {
        chamadas.incrementAndGet();
        ultima = req;
        if (status == Status.OK) {
            return Resposta.ok(conteudo, 10, 5);
        }
        return Resposta.erro(status);
    }

    public void programar(Status status, String conteudo) {
        this.status = status;
        this.conteudo = conteudo;
    }

    public int chamadas() {
        return chamadas.get();
    }

    public Requisicao ultima() {
        return ultima;
    }

    public void reset() {
        status = Status.OK;
        conteudo = "{\"ok\":true}";
        chamadas.set(0);
        ultima = null;
    }
}
