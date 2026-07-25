package app.alcada.captura.internal;

import java.util.HashSet;
import java.util.Set;

/**
 * Similaridade textual por Jaccard sobre tokens — <b>placeholder</b> do
 * deduplicador até entrarem embeddings/pgvector (o {@code gateway.embutir} é
 * stub). O limiar precisará de recalibração quando a similaridade passar a ser
 * por cosseno de embedding.
 */
final class Similaridade {

    private Similaridade() {
    }

    static double jaccard(String a, String b) {
        Set<String> ta = tokens(a);
        Set<String> tb = tokens(b);
        if (ta.isEmpty() && tb.isEmpty()) {
            return 1.0;
        }
        Set<String> intersecao = new HashSet<>(ta);
        intersecao.retainAll(tb);
        Set<String> uniao = new HashSet<>(ta);
        uniao.addAll(tb);
        return uniao.isEmpty() ? 0.0 : (double) intersecao.size() / uniao.size();
    }

    private static Set<String> tokens(String s) {
        Set<String> t = new HashSet<>();
        if (s == null) {
            return t;
        }
        for (String w : s.toLowerCase().split("[^\\p{L}\\p{N}]+")) {
            if (w.length() >= 3) {
                t.add(w);
            }
        }
        return t;
    }
}
