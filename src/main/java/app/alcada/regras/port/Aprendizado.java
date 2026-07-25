package app.alcada.regras.port;

import java.util.List;
import java.util.UUID;

import app.alcada.plataforma.multitenancy.port.OrgId;

/** Laço de aprendizado: gera/lista perguntas abertas e registra a resposta. */
public interface Aprendizado {

    /** Gera perguntas sob demanda (disciplina do RFC) e retorna as abertas com evidência. */
    List<PerguntaAprendizado> perguntasAbertas(OrgId org);

    void responder(OrgId org, UUID perguntaId, Resposta resposta, UUID por);
}
