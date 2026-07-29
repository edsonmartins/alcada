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
    void item_ja_aberto_do_grupo_e_sempre_candidato() {
        assertTrue(filtro.candidata("Ana: e aí\n", true), "pode ser cobrança de algo já rastreado");
    }

    @Test
    void janela_vazia_nao_e_candidata() {
        assertFalse(filtro.candidata("   ", false));
        assertFalse(filtro.candidata(null, false));
    }
}
