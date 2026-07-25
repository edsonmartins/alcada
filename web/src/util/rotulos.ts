/** Rótulos legíveis e cores para os enums do domínio (evita "NAO_APLICA" cru na UI). */

const MAPA: Record<string, string> = {
  // classe
  DECISAO: "Decisão",
  BLOQUEIO: "Bloqueio",
  ESTEIRA: "Esteira",
  // resultado de critério
  OK: "OK",
  FALHOU: "Falhou",
  NAO_APLICA: "Não aplica",
  // tipo de critério / apontamento
  OBJETIVO: "Objetivo",
  JULGAMENTO: "Julgamento",
  // status de delegação
  ABERTA: "Aberta",
  PROPOSTA: "Proposta",
  AGUARDANDO_JANELA: "Aguardando janela",
  EXECUTADA: "Executada",
  DEVOLVIDA: "Devolvida",
  ESCALADA: "Escalada",
  // desfecho de avaliação
  APROVADA: "Aprovada",
  REPROVADA: "Reprovada",
  PENDENTE_JULGAMENTO: "Pendente de julgamento",
  // fonte do dossiê
  PENDENCIA: "Pendência",
  MENSAGEM: "Mensagem",
  TRILHA: "Trilha",
  // status de instância
  EM_ANDAMENTO: "Em andamento",
  CONCLUIDA: "Concluída",
};

/** Enum → texto legível. Desconhecido: "SNAKE_CASE" → "Snake case". */
export function rotulo(valor: string | null | undefined): string {
  if (!valor) return "";
  if (MAPA[valor]) return MAPA[valor];
  const s = valor.replace(/_/g, " ").toLowerCase();
  return s.charAt(0).toUpperCase() + s.slice(1);
}

/** Cor Mantine por classe de pendência (mesma linguagem da barra de acento). */
export function corClasse(classe: string): string {
  return classe === "BLOQUEIO" ? "red" : classe === "ESTEIRA" ? "grape" : "blue";
}
