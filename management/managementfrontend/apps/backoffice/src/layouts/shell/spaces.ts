/**
 * Os espaços de trabalho do Backoffice — cada um com a sua shell (header, nav,
 * página inicial, cor de destaque). Mudar de espaço muda tudo menos o bloco do
 * utilizador. Ver docs/skills/references/design/backoffice-app-shell-and-auth.md §5.
 */
export type SpaceId = "works" | "team";

export interface SpaceDefinition {
  id: SpaceId;
  name: string;
  description: string;
  basePath: string;
  /** Só ADMIN: os endpoints de assiduidade são todos `hasRole('ADMIN')`. */
  adminOnly: boolean;
}

export const SPACES: SpaceDefinition[] = [
  {
    id: "works",
    name: "Obras",
    description: "Empreendimentos, orçamentos, faturas e tarefas.",
    basePath: "/backoffice",
    adminOnly: false,
  },
  {
    id: "team",
    name: "Equipa",
    description: "Picagens, horas, férias, relatórios e contas.",
    basePath: "/team",
    adminOnly: true,
  },
];

const LAST_SPACE_KEY = "worksite.lastSpace";

export function spaceOfPath(pathname: string): SpaceId {
  return pathname.startsWith("/team") ? "team" : "works";
}

export function visibleSpaces(isAdmin: boolean): SpaceDefinition[] {
  return SPACES.filter((space) => isAdmin || !space.adminOnly);
}

/** Conveniência por browser: se o armazenamento falhar, o login cai em Obras. */
export function rememberSpace(id: SpaceId): void {
  try {
    localStorage.setItem(LAST_SPACE_KEY, id);
  } catch {
    // modo privado ou armazenamento bloqueado — não é crítico
  }
}

/** Onde o login aterra: o último espaço usado, se a role ainda o pode abrir. */
export function landingPath(isAdmin: boolean): string {
  let stored: string | null = null;
  try {
    stored = localStorage.getItem(LAST_SPACE_KEY);
  } catch {
    stored = null;
  }
  const space = visibleSpaces(isAdmin).find((candidate) => candidate.id === stored);
  return (space ?? SPACES[0]).basePath;
}
