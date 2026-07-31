package app.alcada.captura;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import app.alcada.captura.internal.PreFiltroGrupo;
import org.junit.jupiter.api.Test;

/** F1 — o pré-filtro determinístico que corta o ruído antes do modelo (ADR-0011 §3). */
class PreFiltroGrupoTest {

    private final PreFiltroGrupo filtro = new PreFiltroGrupo(PreFiltroGrupo.LEXICO_PADRAO);

    @Test
    void ruido_puro_nao_e_candidato() {
        assertFalse(filtro.candidata("Ana: Bom dia!\nBruno: obrigada 🙏\n", false),
                "saudação/agradecimento é ruído");
    }

    @Test
    void padrao_de_decisao_e_candidato() {
        assertTrue(filtro.candidata("Ana: consegue aprovar o reembolso?\n", false), "pergunta + aprovar");
        assertTrue(filtro.candidata("Bruno: precisamos marcar a reunião de cronograma\n", false),
                "agendamento (sem acento no radical casa 'reunião')");
        assertTrue(filtro.candidata("Ana: manda o invite pf\n", false), "pedido direcionado");
    }

    @Test
    void radical_com_espaco_respeita_limite_de_palavra() {
        // "ate " (com espaço) não pode casar dentro de "atenção" (o trim antigo quebrava isso).
        assertFalse(filtro.candidata("Ana: muita atencao aos detalhes do time\n", false),
                "radical 'ate ' não casa no meio de 'atenção'");
        assertTrue(filtro.candidata("Bruno: vai ate o fim do mes\n", false),
                "mas 'ate ' casa quando é a palavra 'até'");
    }

    @Test
    void item_ja_aberto_do_grupo_e_sempre_candidato() {
        assertTrue(filtro.candidata("Ana: e aí\n", true), "pode ser cobrança de algo já rastreado");
    }

    @Test
    void janela_vazia_nao_e_candidata() {
        assertFalse(filtro.candidata("   ", false));
        assertFalse(filtro.candidata(null, false));
    }
}
