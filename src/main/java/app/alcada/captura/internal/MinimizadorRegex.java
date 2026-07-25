package app.alcada.captura.internal;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import app.alcada.captura.port.Minimizacao;
import app.alcada.captura.port.Minimizador;
import jakarta.enterprise.context.ApplicationScoped;

/**
 * Minimizador determinístico (ADR-0020 §3). Pseudonimiza nomes conhecidos e
 * remove identificadores diretos por regex. O mapa de re-hidratação é criado
 * por chamada e nunca sai desta invocação.
 */
@ApplicationScoped
public class MinimizadorRegex implements Minimizador {

    private static final Pattern CPF = Pattern.compile("\\b\\d{3}\\.?\\d{3}\\.?\\d{3}-?\\d{2}\\b");
    private static final Pattern CNPJ =
            Pattern.compile("\\b\\d{2}\\.?\\d{3}\\.?\\d{3}/?\\d{4}-?\\d{2}\\b");
    private static final Pattern EMAIL = Pattern.compile("[\\w.+-]+@[\\w-]+\\.[\\w.-]+");
    private static final Pattern TELEFONE =
            Pattern.compile("\\(?\\d{2}\\)?\\s?9?\\d{4}-?\\d{4}\\b");
    private static final String REDIGIDO = "[REDIGIDO]";

    @Override
    public Minimizacao minimizar(String texto, List<String> pessoas, List<String> empresas) {
        Map<String, String> pseudoParaReal = new LinkedHashMap<>();
        String t = texto;

        // Pseudonimização de entidades conhecidas — as maiores primeiro, para
        // não deixar sobra de nome composto.
        t = pseudonimizar(t, ordenarPorTamanho(pessoas), "PESSOA_", pseudoParaReal);
        t = pseudonimizar(t, ordenarPorTamanho(empresas), "EMPRESA_", pseudoParaReal);

        // Remoção de identificadores diretos (ordem: CNPJ antes de CPF/telefone,
        // por serem mais longos e evitarem casamento parcial).
        t = CNPJ.matcher(t).replaceAll(REDIGIDO);
        t = CPF.matcher(t).replaceAll(REDIGIDO);
        t = EMAIL.matcher(t).replaceAll(REDIGIDO);
        t = TELEFONE.matcher(t).replaceAll(REDIGIDO);

        return new Minimizacao(t, pseudoParaReal);
    }

    private static List<String> ordenarPorTamanho(List<String> nomes) {
        return nomes.stream()
                .filter(n -> n != null && !n.isBlank())
                .sorted((a, b) -> Integer.compare(b.length(), a.length()))
                .toList();
    }

    private String pseudonimizar(String texto, List<String> nomes, String prefixo,
                                 Map<String, String> mapa) {
        String t = texto;
        int i = 1;
        for (String nome : nomes) {
            if (t.contains(nome)) {
                String token = prefixo + i++;
                t = t.replace(nome, token);
                mapa.put(token, nome);
            }
        }
        return t;
    }
}
