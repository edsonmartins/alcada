package app.alcada.esteira.internal;

import app.alcada.esteira.port.AvaliacaoResultado;
import app.alcada.esteira.port.ChecklistDados;
import app.alcada.esteira.port.EntradasEsteira;
import app.alcada.esteira.port.EsteiraDados;
import app.alcada.esteira.port.InstanciaDados;
import app.alcada.esteira.port.PortalInstancia;
import app.alcada.esteira.port.PropostaChecklist;
import io.quarkus.runtime.annotations.RegisterForReflection;

/** DTOs e records de entrada devolvidos/recebidos via {@code Response} — native image. */
@RegisterForReflection(targets = {
        EsteiraDados.class,
        EsteiraDados.EtapaDados.class,
        InstanciaDados.class,
        ChecklistDados.class,
        ChecklistDados.CriterioDados.class,
        PropostaChecklist.class,
        PropostaChecklist.CandidatoCriterio.class,
        AvaliacaoResultado.class,
        EntradasEsteira.NovaEtapa.class,
        EntradasEsteira.NovoCriterio.class,
        EntradasEsteira.ResultadoItem.class,
        EntradasEsteira.ApontamentoItem.class,
        EsteiraResource.CriarEsteira.class,
        EsteiraResource.NovaInstancia.class,
        EsteiraResource.PublicarChecklist.class,
        EsteiraResource.Criada.class,
        EsteiraResource.VersaoPublicada.class,
        EsteiraResource.Problema.class,
        InstanciaResource.AvaliarReq.class,
        PortalInstancia.TokenEmitido.class,
        PortalInstancia.Declaracao.class,
        PortalInstancia.EstadoInstancia.class,
        PortalInstancia.EstadoInstancia.ItemFalta.class,
        PortalInstanciaResource.AutoavaliacaoReq.class,
        PortalInstanciaResource.Problema.class
})
public final class EsteiraReflection {
    private EsteiraReflection() {
    }
}
