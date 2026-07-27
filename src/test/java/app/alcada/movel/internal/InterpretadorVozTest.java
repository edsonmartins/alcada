package app.alcada.movel.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import app.alcada.consulta.port.Consulta;
import app.alcada.consulta.port.ResultadoConsulta;
import app.alcada.identidade.port.Pessoas;
import app.alcada.identidade.port.Pessoas.PessoaRef;
import app.alcada.movel.internal.InterpretadorVoz.ItemFila;
import app.alcada.plataforma.gateway.port.ModelGateway;
import app.alcada.plataforma.gateway.port.Tarefas.Classificacao;
import app.alcada.plataforma.gateway.port.Tarefas.Embedding;
import app.alcada.plataforma.gateway.port.Tarefas.Extracao;
import app.alcada.plataforma.gateway.port.Tarefas.Redacao;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaClassificacao;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaEmbedding;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaExtracao;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaRedacao;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaTranscricao;
import app.alcada.plataforma.gateway.port.Tarefas.Transcricao;
import app.alcada.plataforma.multitenancy.port.OrgId;
import org.junit.jupiter.api.Test;

/**
 * Resolução de REPASSAR por voz contra o diretório de pessoas (022). O LLM é
 * simulado devolvendo um JSON fixo; o foco é a ponte nome→pessoa_id e o
 * tratamento dos casos (1 match / ≥2 / nome não reconhecido → oferece lista para
 * aprender). INV-10: nunca decide sozinho no ambíguo.
 */
class InterpretadorVozTest {

    private static final OrgId ORG = OrgId.de("2ba5fb51-bd76-4a03-bb33-5a580b7ca7f7");
    private static final UUID GESTOR = UUID.randomUUID();
    private static final List<ItemFila> FILA = List.of(new ItemFila("11", "Reembolso viagem"));

    private InterpretadorVoz com(String json, List<PessoaRef> achados, List<PessoaRef> equipe) {
        return new InterpretadorVoz(new GatewayFixo(json), new ConsultaFixa(),
                new PessoasFixo(achados, equipe));
    }

    @Test
    void repassarComUmMatchResolveDonoEPedeConfirmacao() {
        var uid = UUID.randomUUID();
        var r = com("{\"intencao\":\"REPASSAR\",\"item\":1,\"donoNome\":\"Executor\"}",
                List.of(new PessoaRef(uid, "Executor Piloto")), List.of())
                .interpretar(ORG, GESTOR, "passa o reembolso pro executor", List.of(), FILA);
        assertEquals("REPASSAR", r.intencao());
        assertEquals("11", r.pendenciaId());
        assertEquals(uid.toString(), r.donoId());
        assertEquals("Executor Piloto", r.donoNome());
        assertTrue(r.precisaConfirmar());
        assertTrue(r.candidatosDono().isEmpty());
        assertNull(r.termoFalado(), "match direto não precisa aprender");
    }

    @Test
    void repassarComVariosDevolveCandidatosSemDecidir() {
        var r = com("{\"intencao\":\"REPASSAR\",\"item\":1,\"donoNome\":\"Alexandre\"}",
                List.of(new PessoaRef(UUID.randomUUID(), "Alexandre Silva"),
                        new PessoaRef(UUID.randomUUID(), "Alexandre Souza")), List.of())
                .interpretar(ORG, GESTOR, "repassa pro alexandre", List.of(), FILA);
        assertEquals("REPASSAR", r.intencao());
        assertNull(r.donoId(), "não decide qual quando há mais de um");
        assertFalse(r.precisaConfirmar());
        assertEquals(2, r.candidatosDono().size());
        assertNull(r.termoFalado(), "ambiguidade de nome não vira apelido");
    }

    @Test
    void nomeNaoReconhecidoOfereceListaParaAprender() {
        var uid = UUID.randomUUID();
        var r = com("{\"intencao\":\"REPASSAR\",\"item\":1,\"donoNome\":\"Xandão\"}",
                List.of(), List.of(new PessoaRef(uid, "Alexandre Silva")))
                .interpretar(ORG, GESTOR, "manda pro xandão", List.of(), FILA);
        assertEquals("REPASSAR", r.intencao());
        assertNull(r.donoId());
        assertEquals(1, r.candidatosDono().size());
        assertEquals("Xandão", r.termoFalado(), "app aprende o termo ao escolher");
    }

    @Test
    void semNinguemNaEquipeAvisaQueNaoAchou() {
        var r = com("{\"intencao\":\"REPASSAR\",\"item\":1,\"donoNome\":\"Fulano\"}", List.of(), List.of())
                .interpretar(ORG, GESTOR, "manda pro fulano", List.of(), FILA);
        assertEquals("REPASSAR", r.intencao());
        assertNull(r.donoId());
        assertTrue(r.candidatosDono().isEmpty());
        assertTrue(r.frase().contains("Fulano"));
    }

    @Test
    void itemUnicoResolveMesmoComIndiceZero() {
        var uid = UUID.randomUUID();
        var r = com("{\"intencao\":\"REPASSAR\",\"item\":0,\"donoNome\":\"Executor\"}",
                List.of(new PessoaRef(uid, "Executor Piloto")), List.of())
                .interpretar(ORG, GESTOR, "manda esse pro executor", List.of(), FILA);
        assertEquals("11", r.pendenciaId());
        assertEquals(uid.toString(), r.donoId());
    }

    // ---- fakes -------------------------------------------------------------

    private record PessoasFixo(List<PessoaRef> achados, List<PessoaRef> equipe) implements Pessoas {
        @Override
        public List<PessoaRef> buscarPorNome(OrgId org, UUID gestorId, String termo) {
            return achados;
        }

        @Override
        public List<PessoaRef> listar(OrgId org, UUID gestorId) {
            return equipe;
        }

        @Override
        public void aprender(OrgId org, UUID gestorId, String termo, UUID pessoaId) {
        }
    }

    /** Gateway que só faz extração: aplica o mapeador no JSON fixo. */
    private record GatewayFixo(String json) implements ModelGateway {
        @Override
        public <T> Extracao<T> extrair(TarefaExtracao<T> tarefa) {
            return new Extracao<>(tarefa.mapeador().apply(json), 0.9);
        }

        @Override
        public Redacao redigir(TarefaRedacao t) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Classificacao classificar(TarefaClassificacao t) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Embedding embutir(TarefaEmbedding t) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Transcricao transcrever(TarefaTranscricao t) {
            throw new UnsupportedOperationException();
        }
    }

    private static final class ConsultaFixa implements Consulta {
        @Override
        public ResultadoConsulta consultar(OrgId org, String pergunta) {
            return new ResultadoConsulta(pergunta, "T", "resposta", List.of());
        }
    }
}
