package app.alcada.captura;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.Optional;
import java.util.UUID;

import app.alcada.captura.internal.Compromisso;
import app.alcada.captura.internal.ExtratorGrupo;
import app.alcada.plataforma.gateway.port.ModelGateway;
import app.alcada.plataforma.gateway.port.Tarefas;
import app.alcada.plataforma.multitenancy.port.OrgId;
import org.junit.jupiter.api.Test;

/** F2 — o extrator de compromissos de grupo (caso C1 = reunião do Marcello). */
class ExtratorGrupoTest {

    @Test
    void extrai_compromisso_de_reuniao_da_janela() {
        String saida = """
                {"dependeDoGestor":true,"tipo":"REUNIAO",
                 "assunto":"cronograma atualizado e próximos passos",
                 "quemPede":"P1",
                 "quando":{"textoOriginal":"próxima segunda 14h","resolvido":"2026-08-03T14:00"},
                 "acaoPendente":"reunião acordada; invite solicitado","possivelmenteFeito":true,
                 "confianca":0.82,"sourceMessageSeqs":[2,3,6,8,9,11]}""";

        Optional<Compromisso> c = new ExtratorGrupo(gatewayQueRetorna(saida)).extrair(
                new OrgId(UUID.randomUUID()), UUID.randomUUID(),
                "P1: vamos marcar?\nP2: 14h, manda invite pf", "P2", s -> s);

        assertTrue(c.isPresent(), "deve extrair o compromisso");
        Compromisso k = c.get();
        assertTrue(k.dependeDoGestor());
        assertEquals("REUNIAO", k.tipo());
        assertEquals("cronograma atualizado e próximos passos", k.assunto());
        assertEquals("2026-08-03T14:00", k.quandoResolvido());
        assertTrue(k.possivelmenteFeito(), "houve 'enviei!' no fio");
    }

    @Test
    void nao_depende_do_gestor_e_parseado_para_o_chamador_decidir() {
        String saida = "{\"dependeDoGestor\":false,\"tipo\":\"OUTRO\",\"assunto\":\"papo\",\"confianca\":0.9}";
        Optional<Compromisso> c = new ExtratorGrupo(gatewayQueRetorna(saida)).extrair(
                new OrgId(UUID.randomUUID()), UUID.randomUUID(), "P1: bom dia", "P2", s -> s);
        assertTrue(c.isPresent());
        assertFalse(c.get().dependeDoGestor(), "o chamador não cria pendência");
    }

    @Test
    void saida_fora_do_schema_reprocessa_e_se_persistir_retorna_vazio() {
        Optional<Compromisso> c = new ExtratorGrupo(gatewayQueRetorna("{\"tipo\":\"INVALIDO\"}")).extrair(
                new OrgId(UUID.randomUUID()), UUID.randomUUID(), "x", "P2", s -> s);
        assertTrue(c.isEmpty());
    }

    @Test
    void indisponivel_retorna_vazio() {
        Optional<Compromisso> c = new ExtratorGrupo(gatewayPendente()).extrair(
                new OrgId(UUID.randomUUID()), UUID.randomUUID(), "x", "P2", s -> s);
        assertTrue(c.isEmpty());
    }

    // ---- fakes -------------------------------------------------------------

    private static ModelGateway gatewayQueRetorna(String json) {
        return new FakeGateway(json, 1.0);
    }

    private static ModelGateway gatewayPendente() {
        return new FakeGateway(null, null);
    }

    /** Gateway de teste: devolve um JSON canado (aplicando o mapeador da tarefa). */
    private record FakeGateway(String json, Double confianca) implements ModelGateway {
        @Override
        @SuppressWarnings("unchecked")
        public <T> Tarefas.Extracao<T> extrair(Tarefas.TarefaExtracao<T> t) {
            if (confianca == null) {
                return Tarefas.Extracao.pendente();
            }
            return new Tarefas.Extracao<>(t.mapeador().apply(json), confianca);
        }

        @Override
        public Tarefas.Redacao redigir(Tarefas.TarefaRedacao t) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Tarefas.Classificacao classificar(Tarefas.TarefaClassificacao t) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Tarefas.Embedding embutir(Tarefas.TarefaEmbedding t) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Tarefas.Transcricao transcrever(Tarefas.TarefaTranscricao t) {
            throw new UnsupportedOperationException();
        }
    }
}
