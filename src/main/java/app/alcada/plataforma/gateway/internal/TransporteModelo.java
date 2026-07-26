package app.alcada.plataforma.gateway.internal;

/**
 * Seam de transporte para o provedor externo. Isola a chamada HTTP para que o
 * adaptador seja testável sem OpenRouter real. O transporte recebe a requisição
 * já com a política fixa e devolve o desfecho como {@link Resposta}.
 */
public interface TransporteModelo {

    Resposta enviar(Requisicao requisicao);

    /** Transcrição de áudio (STT). Default indisponível (dev/test/stub não falam fora). */
    default Resposta transcrever(RequisicaoAudio req) {
        return Resposta.erro(Status.INDISPONIVEL);
    }

    /** Requisição a um provedor. Carrega a política fixa, para inspeção/aplicação. */
    record Requisicao(String modelo, String texto, String schemaJson, PoliticaProvedor politica) {
    }

    /** Requisição de transcrição: áudio em base64 + formato + idioma (ex.: pt). */
    record RequisicaoAudio(String modelo, String audioBase64, String formato, String idioma) {
    }

    enum Status {
        /** Sucesso; {@code conteudo} tem o JSON válido. */
        OK,
        /** Provedor não suporta json_schema estrito — deve falhar, nunca degradar. */
        SEM_SUPORTE_SCHEMA,
        /** Provedor(es) indisponível(is) dentro da lista `only`. */
        INDISPONIVEL,
        /** Guardrail recusou (provedor fora da lista). */
        GUARDRAIL_RECUSOU
    }

    record Resposta(Status status, String conteudo, int tokensIn, int tokensOut) {
        public static Resposta ok(String conteudo, int in, int out) {
            return new Resposta(Status.OK, conteudo, in, out);
        }

        public static Resposta erro(Status status) {
            return new Resposta(status, null, 0, 0);
        }
    }
}
