package app.alcada.plataforma.gateway.port;

import app.alcada.plataforma.gateway.port.Tarefas.Classificacao;
import app.alcada.plataforma.gateway.port.Tarefas.Embedding;
import app.alcada.plataforma.gateway.port.Tarefas.Extracao;
import app.alcada.plataforma.gateway.port.Tarefas.Redacao;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaClassificacao;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaEmbedding;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaExtracao;
import app.alcada.plataforma.gateway.port.Tarefas.TarefaRedacao;

/**
 * Porta única de inferência (RFC-0007). Política e roteamento vivem no gateway,
 * não no chamador. Nenhum módulo de domínio conhece OpenRouter, provedor ou
 * modelo. INV-10: o gateway propõe (extrai, redige, classifica); quem decide
 * efeito externo é regra determinística.
 */
public interface ModelGateway {

    /**
     * Extração com schema estrito obrigatório. Em indisponibilidade, enfileira
     * reprocesso e devolve {@link Extracao#pendente()} — captura nunca é perdida.
     */
    <T> Extracao<T> extrair(TarefaExtracao<T> tarefa);

    /** Redação. Indisponibilidade falha de forma visível — não degrada. */
    Redacao redigir(TarefaRedacao tarefa);

    Classificacao classificar(TarefaClassificacao tarefa);

    Embedding embutir(TarefaEmbedding tarefa);
}
