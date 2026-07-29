package app.alcada.captura.internal;

import java.text.Normalizer;
import java.util.Arrays;
import java.util.List;

import jakarta.enterprise.context.ApplicationScoped;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Pré-filtro determinístico da janela de grupo (024, F1; ADR-0011 §3). Corta o
 * ruído puro <b>antes</b> de qualquer chamada ao modelo — captura seletiva, não
 * varredura. Uma janela é <b>candidata</b> se:
 *
 * <ol>
 *   <li>já há um item aberto do grupo (segue/cobra algo rastreado), OU</li>
 *   <li>casa com o <b>padrão de decisão</b>: léxico de pedido/prazo/agendamento/
 *       aprovação, ou uma pergunta direcionada ("?").</li>
 * </ol>
 *
 * <p>(A menção direta ao gestor — sinal (a) do design — depende de {@code mentions}
 * no Linktor e entra depois.) O casamento é por radical, sobre texto normalizado
 * (minúsculo, sem acento), para ser robusto a flexão e acentuação. Na dúvida é
 * candidata: perder uma demanda de decisão é pior que uma chamada de modelo a mais.
 */
@ApplicationScoped
public class PreFiltroGrupo {

    /** Léxico base (radicais sem acento). Configurável por implantação. */
    public static final String LEXICO_PADRAO =
            "decid,decis,aprov,autoriz,libera,confirm,assin,"
            + "reuni,marca,agenda,invite,convoc,call,"
            + "prazo,quando,urgent,hoje,amanha,segunda,terca,quarta,quinta,sexta,ate ,"
            + "pode ,poderia,consegue,precisa,preciso,favor, pf,"
            + "responde,retorna,posiciona,resolve,defin,cobr,aguard";

    private final List<String> radicais;

    public PreFiltroGrupo(@ConfigProperty(name = "grupos.prefiltro.lexico",
            defaultValue = LEXICO_PADRAO) String lexico) {
        this.radicais = Arrays.stream(lexico.split(","))
                .map(String::trim).filter(s -> !s.isEmpty()).map(PreFiltroGrupo::normalizar).toList();
    }

    /**
     * @param janelaTexto  a janela "remetente: texto" por linha (não minimizada)
     * @param temItemAberto se já existe pendência aberta deste grupo
     * @return true se vale mandar ao modelo; false descarta como ruído (C2)
     */
    public boolean candidata(String janelaTexto, boolean temItemAberto) {
        if (temItemAberto) {
            return true; // pode ser cobrança/seguimento de algo já rastreado
        }
        if (janelaTexto == null || janelaTexto.isBlank()) {
            return false;
        }
        String n = normalizar(janelaTexto);
        if (n.indexOf('?') >= 0) {
            return true; // pergunta direcionada
        }
        for (String r : radicais) {
            if (n.contains(r)) {
                return true;
            }
        }
        return false;
    }

    /** minúsculo + sem acento (NFD, remove diacríticos). */
    private static String normalizar(String s) {
        String d = Normalizer.normalize(s, Normalizer.Form.NFD);
        return d.replaceAll("\\p{M}+", "").toLowerCase();
    }
}
