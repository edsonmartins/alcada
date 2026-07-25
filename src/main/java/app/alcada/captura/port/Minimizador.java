package app.alcada.captura.port;

import java.util.List;

/**
 * Minimiza texto antes de qualquer chamada de modelo (ADR-0020 §3). Vive em
 * {@code captura}; é a fronteira que garante que o gateway nunca recebe texto
 * não minimizado quando a sensibilidade é {@code INTERNA}.
 *
 * <p>Pseudonimiza nomes de pessoas e razões sociais conhecidos (resolução de
 * entidade em produção) por tokens, remove identificadores diretos (CPF/CNPJ,
 * telefone, e-mail) e devolve o mapa efêmero para re-hidratação local.
 */
public interface Minimizador {

    /**
     * @param texto             trecho relevante já isolado (nunca a thread inteira)
     * @param pessoas           nomes de pessoas a pseudonimizar (→ PESSOA_n)
     * @param empresas          razões sociais a pseudonimizar (→ EMPRESA_n)
     */
    Minimizacao minimizar(String texto, List<String> pessoas, List<String> empresas);
}
