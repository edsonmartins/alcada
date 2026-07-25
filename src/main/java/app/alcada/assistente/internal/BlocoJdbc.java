package app.alcada.assistente.internal;

import java.util.ArrayList;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.UUID;

import app.alcada.assistente.port.Bloco;
import app.alcada.assistente.port.BlocoDados;
import app.alcada.assistente.port.RascunhoResultado;
import app.alcada.plataforma.gateway.port.ModelGateway;
import app.alcada.plataforma.gateway.port.Sensibilidade;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaRedacao;
import app.alcada.plataforma.multitenancy.port.OrgId;
import app.alcada.plataforma.outbox.port.MensagemOutbox;
import app.alcada.plataforma.outbox.port.Outbox;
import app.alcada.plataforma.trilha.port.Ator;
import app.alcada.plataforma.trilha.port.EventoTrilha;
import app.alcada.plataforma.trilha.port.TipoEvento;
import app.alcada.plataforma.trilha.port.Trilha;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.NoResultException;

/**
 * Bloco de decisão (RFC-0004). Dossiê determinístico (os dados do próprio item),
 * redação via gateway (proposta editável — INV-10) e decisão determinística
 * (fecha + trilha + outbox). Escopado por org_id (INV-15).
 */
@ApplicationScoped
public class BlocoJdbc implements Bloco {

    private final EntityManager em;
    private final Trilha trilha;
    private final Outbox outbox;
    private final ModelGateway modelo;

    public BlocoJdbc(EntityManager em, Trilha trilha, Outbox outbox, ModelGateway modelo) {
        this.em = em;
        this.trilha = trilha;
        this.outbox = outbox;
        this.modelo = modelo;
    }

    @Override
    public BlocoDados montar(OrgId org, UUID pendenciaId) {
        Object[] p = carregar(org, pendenciaId);
        String titulo = (String) p[0];
        String classe = (String) p[1];
        List<BlocoDados.ItemDossie> dossie = new ArrayList<>();
        add(dossie, "Quem espera", (String) p[2]);
        add(dossie, "O que trava", (String) p[3]);
        if (p[4] != null) {
            add(dossie, "Valor em jogo", "R$ " + ((Number) p[4]).longValue());
        }
        if (p[5] != null) {
            add(dossie, "Prazo", p[5].toString());
        }
        int temperatura = ((Number) p[6]).intValue();
        if (temperatura > 0) {
            add(dossie, "Cobranças", temperatura + (temperatura == 1 ? " cobrança" : " cobranças"));
        }
        return new BlocoDados(pendenciaId.toString(), titulo, classe, dossie, opcoes(classe));
    }

    @Override
    public RascunhoResultado redigir(OrgId org, UUID pendenciaId, String opcao, String tom) {
        Object[] p = carregar(org, pendenciaId);
        String quemEspera = (String) p[2];
        String contexto = "Decisão: " + opcao + ". Item: " + p[0]
                + (quemEspera != null ? ". Quem espera: " + quemEspera : "")
                + (p[3] != null ? ". O que trava: " + p[3] : "");
        try {
            var r = modelo.redigir(new TarefaRedacao(org, Sensibilidade.INTERNA, pendenciaId, contexto, tom));
            return new RascunhoResultado(r.rascunho(), true, null);
        } catch (RuntimeException e) {
            String esqueleto = (quemEspera != null ? quemEspera + ", " : "")
                    + "sobre \"" + p[0] + "\": decidi " + opcao.toLowerCase() + ".\n\n[complete aqui]";
            return new RascunhoResultado(esqueleto, false,
                    "Modelo indisponível — rascunho manual (o texto real sai quando o gateway estiver configurado).");
        }
    }

    @Override
    public void decidir(OrgId org, UUID pendenciaId, String opcao, String texto, UUID por) {
        Object[] p = carregar(org, pendenciaId);
        if ("FECHADA".equals(p[7])) {
            throw new IllegalStateException("pendência já fechada");
        }
        em.createNativeQuery(
                "UPDATE pendencia SET status = 'FECHADA', fechada_em = now() WHERE org_id = ? AND id = ?")
                .setParameter(1, org.valor()).setParameter(2, pendenciaId).executeUpdate();
        Ator ator = por != null ? Ator.humano(por) : Ator.sistemaMotor("bloco");
        trilha.registrar(new EventoTrilha(org, pendenciaId, TipoEvento.DECIDIDA_NO_BLOCO, ator,
                (String) p[7], "FECHADA", null, "{\"opcao\":\"" + json(opcao) + "\"}"));
        // A comunicação sai por outbox (o despachante entrega ao canal) — só FECHADA comunica (INV-09).
        String payload = "{\"pendencia_id\":\"" + pendenciaId + "\",\"opcao\":\"" + json(opcao)
                + "\",\"texto\":\"" + json(texto == null ? "" : texto) + "\"}";
        outbox.publicar(new MensagemOutbox(org, "decisao.comunicada", payload, "pendencia:" + pendenciaId + ":decidida"));
    }

    // ---- auxiliares ----------------------------------------------------------

    private Object[] carregar(OrgId org, UUID pendenciaId) {
        try {
            return (Object[]) em.createNativeQuery("""
                    SELECT titulo, classe, quem_espera, o_que_trava, valor_em_jogo, prazo_implicito,
                           temperatura, status
                    FROM pendencia WHERE org_id = ? AND id = ?
                    """).setParameter(1, org.valor()).setParameter(2, pendenciaId).getSingleResult();
        } catch (NoResultException e) {
            throw new NoSuchElementException("pendência não encontrada");
        }
    }

    private static void add(List<BlocoDados.ItemDossie> dossie, String rotulo, String valor) {
        if (valor != null && !valor.isBlank()) {
            dossie.add(new BlocoDados.ItemDossie(rotulo, valor));
        }
    }

    private static List<BlocoDados.Opcao> opcoes(String classe) {
        return switch (classe) {
            case "BLOQUEIO" -> List.of(
                    new BlocoDados.Opcao("desbloquear", "Desbloquear", "libera quem estava travado; o solicitante é avisado"),
                    new BlocoDados.Opcao("manter", "Manter bloqueio", "segue travado; registra o motivo"));
            case "ESTEIRA" -> List.of(
                    new BlocoDados.Opcao("aprovar_etapa", "Aprovar etapa", "a instância avança na esteira"),
                    new BlocoDados.Opcao("reprovar_etapa", "Reprovar etapa", "volta à contraparte com o motivo"));
            default -> List.of(
                    new BlocoDados.Opcao("aprovar", "Aprovar", "segue a proposta; o solicitante é avisado"),
                    new BlocoDados.Opcao("recusar", "Recusar", "nega; o solicitante é avisado com a justificativa"));
        };
    }

    private static String json(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", "\\n");
    }
}
