package app.alcada.movel.internal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.UUID;

import app.alcada.autonomia.port.ContatosExternos;
import app.alcada.autonomia.port.ContatosExternos.ContatoExterno;
import app.alcada.consulta.port.Consulta;
import app.alcada.consulta.port.ResultadoConsulta;
import java.util.Optional;

import app.alcada.identidade.port.Pessoas;
import app.alcada.identidade.port.Pessoas.PessoaRef;
import app.alcada.identidade.port.Preferencias;
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
        return comPreferencia(json, achados, equipe, null);
    }

    private InterpretadorVoz comPreferencia(String json, List<PessoaRef> achados,
            List<PessoaRef> equipe, String nivelPref) {
        return montar(json, achados, equipe, List.of(), List.of(), nivelPref);
    }

    /** Interpretador com os dois diretórios: pessoas e contatos externos (RFC-0008). */
    private InterpretadorVoz comContatos(String json, List<PessoaRef> achados, List<PessoaRef> equipe,
            List<ContatoExterno> contatosAchados, List<ContatoExterno> contatosConhecidos) {
        return montar(json, achados, equipe, contatosAchados, contatosConhecidos, null);
    }

    private InterpretadorVoz montar(String json, List<PessoaRef> achados, List<PessoaRef> equipe,
            List<ContatoExterno> contatosAchados, List<ContatoExterno> contatosConhecidos, String nivelPref) {
        return new InterpretadorVoz(new GatewayFixo(json), new ConsultaFixa(),
                new PessoasFixo(achados, equipe), new ContatosFixo(contatosAchados, contatosConhecidos),
                new PreferenciasFixo(nivelPref));
    }

    private static ContatoExterno contato(String nome, String canal) {
        return new ContatoExterno(UUID.randomUUID(), nome, canal, "+5521999990000");
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
        assertTrue(r.podeRegistrarContato(), "sem ninguém conhecido, resta registrar um contato");
    }

    // C19 — nome falado casa com um contato externo: propõe o repasse dizendo o canal
    @Test
    void repassarParaContatoExternoResolveCanalEPedeConfirmacao() {
        ContatoExterno marcello = contato("Marcello Andrade", "WHATSAPP");
        var r = comContatos("{\"intencao\":\"REPASSAR\",\"item\":1,\"donoNome\":\"Marcello\"}",
                List.of(), List.of(), List.of(marcello), List.of(marcello))
                .interpretar(ORG, GESTOR, "repassa o reembolso pro marcello", List.of(), FILA);

        assertEquals("REPASSAR", r.intencao());
        assertEquals(marcello.id().toString(), r.contatoId());
        assertEquals("WHATSAPP", r.contatoCanal());
        assertNull(r.donoId(), "destino externo não vai como pessoa");
        assertEquals("Marcello Andrade", r.donoNome());
        assertTrue(r.precisaConfirmar());
        assertTrue(r.frase().contains("no WhatsApp"), r.frase());
        assertEquals("N2", r.nivel());
        assertFalse(r.podeRegistrarContato(), "o contato já existe");
    }

    // C19 — contato de e-mail: a fala diz por onde o aviso sai
    @Test
    void contatoDeEmailAvisaQueVaiPorEmail() {
        ContatoExterno paulo = contato("Paulo Cesar", "EMAIL");
        var r = comContatos("{\"intencao\":\"REPASSAR\",\"item\":1,\"donoNome\":\"Paulo\"}",
                List.of(), List.of(), List.of(paulo), List.of(paulo))
                .interpretar(ORG, GESTOR, "repassa pro paulo", List.of(), FILA);

        assertEquals("EMAIL", r.contatoCanal());
        assertTrue(r.frase().contains("por e-mail"), r.frase());
    }

    // C19 — homônimos entre pessoa e contato: devolve a lista mista, não decide (INV-10)
    @Test
    void pessoaEContatoHomonimosDevolvemListaMistaSemDecidir() {
        var uid = UUID.randomUUID();
        ContatoExterno externo = contato("Marcello Andrade", "WHATSAPP");
        var r = comContatos("{\"intencao\":\"REPASSAR\",\"item\":1,\"donoNome\":\"Marcello\"}",
                List.of(new PessoaRef(uid, "Marcello Interno")), List.of(), List.of(externo), List.of(externo))
                .interpretar(ORG, GESTOR, "repassa pro marcello", List.of(), FILA);

        assertNull(r.donoId(), "não decide qual quando há mais de um");
        assertNull(r.contatoId());
        assertEquals(2, r.candidatosDono().size());
        assertEquals("PESSOA", r.candidatosDono().get(0).tipo(), "pessoas primeiro");
        assertEquals("CONTATO", r.candidatosDono().get(1).tipo());
        assertEquals("WHATSAPP", r.candidatosDono().get(1).canal(), "o app diz por onde o aviso sai");
        assertNull(r.termoFalado(), "ambiguidade de nome não vira apelido");
    }

    // C20 — nome novo: oferece equipe + contatos conhecidos e permite registrar o contato
    @Test
    void nomeNovoOfereceListaMistaEPermiteRegistrarContato() {
        var uid = UUID.randomUUID();
        ContatoExterno conhecido = contato("Clécia Souza", "WHATSAPP");
        var r = comContatos("{\"intencao\":\"REPASSAR\",\"item\":1,\"donoNome\":\"Marcello\"}",
                List.of(), List.of(new PessoaRef(uid, "Daniel Marinho")), List.of(), List.of(conhecido))
                .interpretar(ORG, GESTOR, "repassa pro marcello", List.of(), FILA);

        assertNull(r.donoId());
        assertNull(r.contatoId());
        assertEquals(2, r.candidatosDono().size(), "equipe + contatos conhecidos");
        assertEquals("Marcello", r.termoFalado(), "app aprende o termo ao escolher da lista");
        assertTrue(r.podeRegistrarContato(), "o app pode oferecer registrar Marcello como contato");
    }

    @Test
    void nivelUsaPreferenciaQuandoNaoFalado() {
        var uid = UUID.randomUUID();
        // fala sem nível → usa o hábito aprendido (N1), não o padrão N2.
        var r = comPreferencia("{\"intencao\":\"REPASSAR\",\"item\":1,\"donoNome\":\"Executor\"}",
                List.of(new PessoaRef(uid, "Executor Piloto")), List.of(), "N1")
                .interpretar(ORG, GESTOR, "passa esse pro executor", List.of(), FILA);
        assertEquals("N1", r.nivel());
    }

    @Test
    void nivelFaladoTemPrioridadeSobreAPreferenciaEeNormalizado() {
        var uid = UUID.randomUUID();
        // o LLM às vezes devolve "3" em vez de "N3" — normaliza; e vence a preferência N1.
        var r = comPreferencia("{\"intencao\":\"REPASSAR\",\"item\":1,\"donoNome\":\"Executor\",\"nivel\":\"3\"}",
                List.of(new PessoaRef(uid, "Executor Piloto")), List.of(), "N1")
                .interpretar(ORG, GESTOR, "passa esse pro executor no n3", List.of(), FILA);
        assertEquals("N3", r.nivel());
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

    private record ContatosFixo(List<ContatoExterno> achados, List<ContatoExterno> conhecidos)
            implements ContatosExternos {
        @Override
        public UUID registrar(OrgId org, String nome, String canal, String endereco, UUID gestorId) {
            throw new UnsupportedOperationException("o interpretador não registra nada (INV-10)");
        }

        @Override
        public boolean atualizar(OrgId org, UUID id, String nome, String canal, String endereco) {
            throw new UnsupportedOperationException("o interpretador não edita nada (INV-10)");
        }

        @Override
        public List<ContatoExterno> listar(OrgId org) {
            return conhecidos;
        }

        @Override
        public Optional<ContatoExterno> buscar(OrgId org, UUID contatoId) {
            return conhecidos.stream().filter(c -> c.id().equals(contatoId)).findFirst();
        }

        @Override
        public List<ContatoExterno> buscarPorNome(OrgId org, String termo) {
            return achados;
        }
    }

    private record PreferenciasFixo(String nivel) implements Preferencias {
        @Override
        public Optional<String> nivelRepasse(OrgId org, UUID gestorId) {
            return Optional.ofNullable(nivel);
        }

        @Override
        public void registrarNivelRepasse(OrgId org, UUID gestorId, String nivel) {
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
        public ResultadoConsulta consultar(OrgId org, UUID gestor, String pergunta) {
            return new ResultadoConsulta(pergunta, "T", "resposta", List.of());
        }
    }
}
