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
import app.alcada.movel.internal.InterpretadorVoz.Resultado;
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
 * tratamento de 0/1/≥2 candidatos (INV-10 — não decide sozinho no ambíguo).
 */
class InterpretadorVozTest {

    private static final OrgId ORG = OrgId.de("2ba5fb51-bd76-4a03-bb33-5a580b7ca7f7");
    private static final List<ItemFila> FILA = List.of(new ItemFila("11", "Reembolso viagem"));

    private InterpretadorVoz comPessoas(String json, List<PessoaRef> achados) {
        return new InterpretadorVoz(new GatewayFixo(json), new ConsultaFixa(), (org, termo) -> achados);
    }

    @Test
    void repassarComUmMatchResolveDonoEPedeConfirmacao() {
        var uid = UUID.randomUUID();
        var r = comPessoas("{\"intencao\":\"REPASSAR\",\"item\":1,\"donoNome\":\"Executor\"}",
                List.of(new PessoaRef(uid, "Executor Piloto")))
                .interpretar(ORG, "passa o reembolso pro executor", List.of(), FILA);
        assertEquals("REPASSAR", r.intencao());
        assertEquals("11", r.pendenciaId());
        assertEquals(uid.toString(), r.donoId());
        assertEquals("Executor Piloto", r.donoNome());
        assertTrue(r.precisaConfirmar());
        assertTrue(r.candidatosDono().isEmpty());
        assertTrue(r.frase().contains("Executor Piloto"));
    }

    @Test
    void repassarComVariosDevolveCandidatosSemDecidir() {
        var r = comPessoas("{\"intencao\":\"REPASSAR\",\"item\":1,\"donoNome\":\"Alexandre\"}",
                List.of(new PessoaRef(UUID.randomUUID(), "Alexandre Silva"),
                        new PessoaRef(UUID.randomUUID(), "Alexandre Souza")))
                .interpretar(ORG, "repassa pro alexandre", List.of(), FILA);
        assertEquals("REPASSAR", r.intencao());
        assertNull(r.donoId(), "não decide qual quando há mais de um");
        assertFalse(r.precisaConfirmar());
        assertEquals(2, r.candidatosDono().size());
        assertTrue(r.frase().contains("mais de um"));
    }

    @Test
    void itemUnicoResolveMesmoComIndiceZero() {
        // LLM às vezes devolve item=0; com uma fila de um só item, é ele.
        var uid = UUID.randomUUID();
        var r = comPessoas("{\"intencao\":\"REPASSAR\",\"item\":0,\"donoNome\":\"Executor\"}",
                List.of(new PessoaRef(uid, "Executor Piloto")))
                .interpretar(ORG, "manda esse pro executor", List.of(), FILA);
        assertEquals("REPASSAR", r.intencao());
        assertEquals("11", r.pendenciaId());
        assertEquals(uid.toString(), r.donoId());
    }

    @Test
    void repassarSemMatchAvisaQueNaoAchou() {
        var r = comPessoas("{\"intencao\":\"REPASSAR\",\"item\":1,\"donoNome\":\"Fulano\"}", List.of())
                .interpretar(ORG, "manda pro fulano", List.of(), FILA);
        assertEquals("REPASSAR", r.intencao());
        assertNull(r.donoId());
        assertFalse(r.precisaConfirmar());
        assertTrue(r.frase().toLowerCase().contains("não encontrei") || r.frase().contains("Fulano"));
    }

    // ---- fakes -------------------------------------------------------------

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
