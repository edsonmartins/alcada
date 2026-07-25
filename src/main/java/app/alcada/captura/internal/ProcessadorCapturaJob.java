package app.alcada.captura.internal;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.scheduler.port.ExecutorJob;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Liga a captura ao scheduler persistente: a ingestão agenda um job
 * {@code PROCESSAR_CAPTURA} e este executor roda a pipeline. Assim nada é
 * processado no thread do webhook e o processamento sobrevive a reinício.
 */
@ApplicationScoped
public class ProcessadorCapturaJob implements ExecutorJob {

    private static final Pattern EVENTO = Pattern.compile("\"evento_bruto_id\"\\s*:\\s*\"([0-9a-fA-F-]+)\"");

    private final ProcessadorCaptura processador;

    public ProcessadorCapturaJob(ProcessadorCaptura processador) {
        this.processador = processador;
    }

    @Override
    public String tipo() {
        return TiposJob.PROCESSAR_CAPTURA;
    }

    @Override
    public void executar(OrgId org, String chave, String payloadJson) {
        Matcher m = EVENTO.matcher(payloadJson == null ? "" : payloadJson);
        if (!m.find()) {
            return;
        }
        processador.processar(org, UUID.fromString(m.group(1)));
    }
}
