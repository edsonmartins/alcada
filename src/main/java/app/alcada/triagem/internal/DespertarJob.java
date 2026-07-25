package app.alcada.triagem.internal;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.scheduler.port.ExecutorJob;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Desperta pendências repousadas/adiadas na data (`volta_em`). Idempotente por
 * {@code (pendencia_id, transicao, ocorrencia)} — chave do job + guarda de
 * ocorrência no serviço evitam despertar obsoleto.
 */
@ApplicationScoped
public class DespertarJob implements ExecutorJob {

    private static final Pattern PEND = Pattern.compile("\"pendencia_id\"\\s*:\\s*\"([0-9a-fA-F-]+)\"");
    private static final Pattern OCOR = Pattern.compile("\"ocorrencia\"\\s*:\\s*(\\d+)");

    private final TriagemService triagem;

    public DespertarJob(TriagemService triagem) {
        this.triagem = triagem;
    }

    @Override
    public String tipo() {
        return TriagemService.JOB_DESPERTAR;
    }

    @Override
    public void executar(OrgId org, String chave, String payloadJson) {
        String payload = payloadJson == null ? "" : payloadJson;
        Matcher mp = PEND.matcher(payload);
        Matcher mo = OCOR.matcher(payload);
        if (mp.find() && mo.find()) {
            triagem.aoDespertar(org, UUID.fromString(mp.group(1)), Integer.parseInt(mo.group(1)));
        }
    }
}
