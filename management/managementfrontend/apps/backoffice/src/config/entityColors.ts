import type { CSSProperties } from "react";

// Paleta Anthropic — extraída de PropertiesList/EmployeesList/BuildingsList para evitar duplicação em listas novas.
export const D = {
  parchment: "#f5f4ed",
  ivory: "#faf9f5",
  nearBlack: "#141413",
  terracotta: "#c96442",
  coral: "#d97757",
  oliveGray: "#5e5d59",
  stoneGray: "#87867f",
  warmSand: "#e8e6dc",
  charcoalWarm: "#4d4c48",
  borderCream: "#f0eee6",
  borderWarm: "#e8e6dc",
  whisper: "rgba(0,0,0,0.05) 0px 4px 24px",
};

/**
 * Estilo base dos botões da coluna "Ações" em listas/tabelas — botões
 * empilhados de largura total (ver PropertiesList/LeadsList/TasksList).
 * Combinar com as cores de `D`: terracotta (ação principal/"ver"),
 * warmSand + borderWarm (ações secundárias), '#b53333' (destrutiva).
 */
export const actionButtonBaseStyle: CSSProperties = {
  display: 'flex',
  alignItems: 'center',
  justifyContent: 'flex-start',
  width: '100%',
  height: '32px',
  borderRadius: '8px',
  fontSize: '12px',
  fontWeight: 500,
  padding: '0 12px',
  transition: 'all 0.2s ease',
};
