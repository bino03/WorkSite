import { useEffect, useState } from "react";
import { Input, Select } from "antd";
import { REASON_OTHER, TIME_ENTRY_REASONS } from "@/types/attendance";

interface Props {
  /** O texto que vai para a API — "" enquanto não estiver escolhido. */
  value: string;
  onChange: (reason: string) => void;
  disabled?: boolean;
}

const OPTIONS = [
  ...TIME_ENTRY_REASONS.map((reason) => ({ value: reason, label: reason })),
  { value: REASON_OTHER, label: "Outro (escrever)" },
];

/**
 * O motivo de mexer numa picagem: um seletor com os casos que se repetem e, em
 * "Outro", um campo livre. Componente controlado por `string` (não ligado ao
 * React Hook Form) para servir tanto o drawer de correção como o modal de
 * anular/restaurar, que não tem formulário nenhum.
 */
export function ReasonField({ value, onChange, disabled }: Props) {
  // Um valor que não está na lista só pode ter vindo do campo livre.
  const isKnown = (TIME_ENTRY_REASONS as readonly string[]).includes(value);
  const [other, setOther] = useState(isKnown ? false : !!value);

  useEffect(() => {
    if (!value) setOther(false);
  }, [value]);

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: 8 }}>
      <Select
        value={other ? REASON_OTHER : value || undefined}
        onChange={(chosen) => {
          if (chosen === REASON_OTHER) {
            setOther(true);
            onChange("");
          } else {
            setOther(false);
            onChange(chosen);
          }
        }}
        options={OPTIONS}
        placeholder="Escolher motivo"
        disabled={disabled}
        style={{ width: "100%" }}
      />
      {other && (
        <Input.TextArea
          value={value}
          onChange={(event) => onChange(event.target.value)}
          rows={2}
          maxLength={500}
          disabled={disabled}
          placeholder="Escrever o motivo"
          autoFocus
        />
      )}
    </div>
  );
}
