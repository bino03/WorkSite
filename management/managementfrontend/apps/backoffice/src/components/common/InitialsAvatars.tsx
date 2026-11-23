import { Tooltip } from "antd";

interface Person {
  id: string;
  name: string;
}

interface Props {
  people: Person[];
  /** Quantos círculos se mostram antes do "+N". */
  max?: number;
}

/** "Carlos Manuel Silva" → "CS": primeira e última palavra, para não passar de duas letras. */
export function initialsOf(name: string): string {
  const words = name.trim().split(/\s+/).filter(Boolean);
  if (words.length === 0) return "?";
  const first = words[0][0];
  const last = words.length > 1 ? words[words.length - 1][0] : "";
  return `${first}${last}`.toUpperCase();
}

const circle = {
  width: 26,
  height: 26,
  borderRadius: "50%",
  display: "flex",
  alignItems: "center",
  justifyContent: "center",
  fontSize: 10,
  fontFamily: "var(--ind-font-heading)",
  border: "1.5px solid var(--ind-color-bg)",
  marginLeft: -6,
} as const;

/**
 * Círculos com as iniciais, sobrepostos, com "+N" quando passam de `max`. O nome inteiro
 * aparece ao passar o rato (e no "+N", a lista dos que ficaram escondidos). Mesmo desenho
 * dos atribuídos das Tarefas.
 */
export function InitialsAvatars({ people, max = 5 }: Props) {
  const shown = people.slice(0, max);
  const hidden = people.slice(max);
  return (
    <div style={{ display: "flex", paddingLeft: 6 }}>
      {shown.map((person) => (
        <Tooltip key={person.id} title={person.name}>
          <div style={{ ...circle, background: "var(--ind-accent-100)", color: "var(--ind-accent-800)" }}>
            {initialsOf(person.name)}
          </div>
        </Tooltip>
      ))}
      {hidden.length > 0 && (
        <Tooltip title={hidden.map((person) => person.name).join(", ")}>
          <div style={{ ...circle, background: "var(--ind-neutral-200)", color: "var(--ind-neutral-700)" }}>
            +{hidden.length}
          </div>
        </Tooltip>
      )}
    </div>
  );
}
