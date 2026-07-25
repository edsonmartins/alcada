package app.alcada.plataforma.scheduler.port;

/**
 * Porta de agendamento persistente. Sem timer em memória: o estado do que
 * executar e quando vive na tabela de jobs (CLAUDE.md §4).
 */
public interface Agenda {

    /**
     * Agenda uma tarefa. Idempotente por {@code (tipo, chave)}: agendar a mesma
     * combinação duas vezes não cria job duplicado.
     */
    void agendar(TarefaAgendada tarefa);
}
