import { get,postIdempotente } from "./client";
export interface LinkItem { tipo: string; href: string }
export interface ItemConsultaCompleta { id:string;titulo:string;classe:string;horizonte:string;status:string;quemEspera:string|null;oQueTrava:string|null;valorEmJogo:number|null;origemCanal:string|null;origem:string|null;delegacaoId:string|null;nivel:string|null;estadoDelegacao:string|null;executor:string|null;atividadeEm:string;eventos:number;links:LinkItem[] }
export interface PaginaItens { itens:ItemConsultaCompleta[];pagina:number;tamanho:number;total:number }
export interface DetalheItem { item:ItemConsultaCompleta;trilha:Array<{tipo:string;ator:string;ocorridoEm:string;estadoAnterior:string|null;estadoPosterior:string|null;carga:string|null}> }
export interface PessoaConsulta { id:string;nome:string }
export function buscarItens(params:Record<string,string>):Promise<PaginaItens>{const q=new URLSearchParams(params);return get(`/v1/itens?${q}`)}
export function detalheItem(id:string):Promise<DetalheItem>{return get(`/v1/itens/${id}`)}
export function listarPessoasConsulta():Promise<PessoaConsulta[]>{return get("/v1/pessoas")}
export function decidirRetorno(id:string,decisao:"APLICAR"|"REJEITAR",chave:string):Promise<void>{return postIdempotente(`/v1/retornos/${id}/decisao`,{decisao},chave)}
