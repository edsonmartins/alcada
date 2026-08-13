package app.alcada.triagem.internal;

import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.scheduler.port.ExecutorJob;
import jakarta.enterprise.context.ApplicationScoped;

@ApplicationScoped
public class VencerPedidoInformacaoJob implements ExecutorJob {
    private static final Pattern ID=Pattern.compile("\"pedido_id\"\\s*:\\s*\"([0-9a-fA-F-]+)\"");
    private final PedidosInformacao pedidos;
    public VencerPedidoInformacaoJob(PedidosInformacao pedidos){this.pedidos=pedidos;}
    @Override public String tipo(){return PedidosInformacao.JOB_VENCER;}
    @Override public void executar(OrgId org,String chave,String payload){Matcher m=ID.matcher(payload==null?"":payload);if(m.find())pedidos.vencer(org,UUID.fromString(m.group(1)));}
}
