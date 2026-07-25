package app.alcada.plataforma.scheduler;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.scheduler.port.ExecutorJob;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Executor de teste para o tipo {@code TESTE}. Conta execuções por chave, para
 * provar que um job sobrevive ao "reinício" e roda exatamente uma vez.
 */
@ApplicationScoped
public class ExecutorFake implements ExecutorJob {

    public static final String TIPO = "TESTE";

    private final Map<String, Integer> execucoesPorChave = new ConcurrentHashMap<>();

    @Override
    public String tipo() {
        return TIPO;
    }

    @Override
    public void executar(OrgId org, String chave, String payloadJson) {
        execucoesPorChave.merge(chave, 1, Integer::sum);
    }

    public int execucoes(String chave) {
        return execucoesPorChave.getOrDefault(chave, 0);
    }

    public void limpar() {
        execucoesPorChave.clear();
    }
}
