import { get, post } from "./client";
import type { PropostaRegra } from "./regras";

export interface ItemAdiado {
  id: string;
  titulo: string;
  adiadoCount: number;
  oQueTrava: string | null;
  quemEspera: string | null;
  valorEmJogo: number | null;
}

export interface RadarDados {
  dependeDoGestor: { qtd: number; total: number; pct: number };
  rodandoSemVoce: number;
  adiados: ItemAdiado[];
  piorEspera: { pendenciaId: string; titulo: string; dias: number; quemEspera: string | null } | null;
  autonomia: { deliberada: number; porAusencia: number; devolvida: number; escalada: number; promovida: number };
  fechamentoCanal: { entregue: number; falho: number; impossivel: number };
  encolhimento: Array<{ semana: string; entraram: number; fecharam: number }>;
  saudeGateway: { chamadas: number; falhas: number; custo: number };
}

export interface RevisaoDados {
  entrada: { qtd: number; itens: Array<{ id: string; titulo: string; quemEspera: string | null }> };
  adiados: ItemAdiado[];
  podeVirarRegra: Array<{ classe: string; ocorrencias: number }>;
  resumoSemana: {
    resolvidas: number;
    executadas: number;
    delegadas: number;
    escaladas: number;
    devolvidas: number;
    fechadas: number;
  };
  conducao: { entrada: string; adiados: string; regras: string; resumo: string };
}

export function getRadar(): Promise<RadarDados> {
  return get<RadarDados>("/v1/radar");
}

export function getRevisao(): Promise<RevisaoDados> {
  return get<RevisaoDados>("/v1/revisao-semanal");
}

export interface SessaoRevisao {
  id:string;status:"ABERTA"|"CONCLUIDA";iniciadaEm:string;concluidaEm:string|null;revisao:RevisaoDados;
  propostas:PropostaRegra[];
  candidatasNivel:Array<{classe:string;donoId:string;dono:string;nivelAtual:string;nivelSugerido:string;ocorrencias:number;fontes:Array<{pendenciaId:string;titulo:string;href:string}>}>;
  trimestre:{quantidade:number;valorEmJogo:number|null;fontes:Array<{pendenciaId:string;titulo:string;href:string}>;acaoHref:string};
  resumo:null|{dependenciasRemovidas:number;continuamDependendo:number;regrasAceitas:number;regrasRecusadas:number;regrasObservadas:number;niveisPromovidos:number;improdutiva:boolean;remanescentes:Array<{pendenciaId:string;titulo:string;href:string}>};
}
export const iniciarSessaoRevisao=():Promise<SessaoRevisao>=>post("/v1/revisao-semanal/sessoes",{});
export const obterSessaoRevisao=(id:string):Promise<SessaoRevisao>=>get(`/v1/revisao-semanal/sessoes/${id}`);
export const concluirSessaoRevisao=(id:string):Promise<SessaoRevisao>=>post(`/v1/revisao-semanal/sessoes/${id}/concluir`,{});
export const deliberarRegraRevisao=(id:string,classe:string,acao:"aceitar"|"recusar"|"observar"):Promise<void>=>post(`/v1/revisao-semanal/sessoes/${id}/regras/${classe}/${acao}`,{});
export const promoverNivelRevisao=(id:string,c:{classe:string;donoId:string;nivelAtual:string}):Promise<void>=>post(`/v1/revisao-semanal/sessoes/${id}/promocoes`,c);
export const protegerAgendaRevisao=(id:string,inicio:string,duracaoMinutos=120):Promise<{id:string}>=>post(`/v1/revisao-semanal/sessoes/${id}/protecao-agenda`,{inicio,duracaoMinutos});
