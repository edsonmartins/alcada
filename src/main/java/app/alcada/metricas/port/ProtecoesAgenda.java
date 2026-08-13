package app.alcada.metricas.port;
import java.util.UUID;import app.alcada.plataforma.multitenancy.port.OrgId;
/** Retorno do adaptador de calendário para a proteção criada pela revisão. */
public interface ProtecoesAgenda {void agendada(OrgId org,UUID id,String eventoId);void falhou(OrgId org,UUID id);}
