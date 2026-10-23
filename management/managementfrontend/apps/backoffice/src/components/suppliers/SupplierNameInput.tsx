import { useEffect, useRef, useState } from "react";
import type { FC } from "react";
import { AutoComplete, Input } from "antd";

import { listSuppliers } from "@/services/supplierService";
import type { Supplier } from "@/types/supplier";

interface Props {
  id?: string;
  value: string;
  onChange: (value: string) => void;
  /** Escolheu-se um fornecedor do catálogo: quem usa o campo preenche o NIF. */
  onSelectSupplier: (supplier: Supplier) => void;
  disabled?: boolean;
  size?: "large" | "middle" | "small";
  placeholder?: string;
  maxLength?: number;
}

/**
 * O nome do fornecedor com pesquisa no catálogo (`GET /suppliers?q=`).
 *
 * Escrever continua livre — um fornecedor que ainda não está no catálogo tem de
 * poder entrar na mesma. A pesquisa só poupa a escrita do NIF, que é o que se
 * engana a copiar do papel.
 */
export const SupplierNameInput: FC<Props> = ({
  id,
  value,
  onChange,
  onSelectSupplier,
  disabled,
  size,
  placeholder,
  maxLength = 255,
}) => {
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [searching, setSearching] = useState(false);
  /** Só pesquisa quando é o utilizador a escrever, não quando o valor chega de fora. */
  const [query, setQuery] = useState<string | null>(null);
  const requestId = useRef(0);

  useEffect(() => {
    if (query === null) return;
    const trimmed = query.trim();
    if (trimmed.length < 2) {
      setSuppliers([]);
      return;
    }
    const current = ++requestId.current;
    const timer = setTimeout(() => {
      setSearching(true);
      listSuppliers(trimmed)
        // Uma resposta lenta de uma pesquisa antiga não pode tapar a atual.
        .then((found) => current === requestId.current && setSuppliers(found))
        .catch(() => current === requestId.current && setSuppliers([]))
        .finally(() => current === requestId.current && setSearching(false));
    }, 300);
    return () => clearTimeout(timer);
  }, [query]);

  const options = suppliers.map((s) => ({
    value: s.id,
    label: (
      <div style={{ display: "flex", justifyContent: "space-between", gap: 8 }}>
        <span>{s.name}</span>
        <span style={{ opacity: 0.55, fontVariantNumeric: "tabular-nums" }}>{s.nif}</span>
      </div>
    ),
  }));

  return (
    <AutoComplete
      id={id}
      value={value}
      options={options}
      disabled={disabled}
      style={{ width: "100%" }}
      notFoundContent={searching ? "A pesquisar…" : null}
      onSearch={(text) => setQuery(text)}
      onChange={(text: string) => {
        // Ao escolher, o AutoComplete devolve o `value` da opção (o id) — esse
        // não é texto do utilizador, trata-o o `onSelect`.
        if (!suppliers.some((s) => s.id === text)) onChange(text);
      }}
      onSelect={(supplierId: string) => {
        const chosen = suppliers.find((s) => s.id === supplierId);
        if (!chosen) return;
        onChange(chosen.name);
        onSelectSupplier(chosen);
        setQuery(null);
        setSuppliers([]);
      }}
    >
      <Input size={size} placeholder={placeholder} maxLength={maxLength} />
    </AutoComplete>
  );
};

export default SupplierNameInput;
