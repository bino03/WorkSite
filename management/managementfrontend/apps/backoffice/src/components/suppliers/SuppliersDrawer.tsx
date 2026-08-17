import { useCallback, useEffect, useState } from "react";
import type { CSSProperties, FC } from "react";
import { Button, Drawer, Empty, Input, Space, Spin, Tabs } from "antd";
import { DeleteOutlined, SearchOutlined } from "@ant-design/icons";

import {
  createSupplier,
  deleteSupplier,
  listSuppliers,
  listUnknownSupplierNifs,
  updateSupplier,
} from "@/services/supplierService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { useAuth } from "@/hooks/useAuth";
import { useConfirm } from "@/context/ConfirmDialogContext";
import { formatDate } from "@/utils/formatters";
import type { Supplier, UnknownSupplierNif } from "@/types/supplier";

/**
 * Avisa quem estiver a mostrar faturas de que os nomes mudaram.
 *
 * A drawer vive no cabeçalho, longe da lista de faturas — sem isto, dar nome a
 * um NIF só se via depois de recarregar a página. Um evento de janela evita
 * ligar o layout à página das faturas só para isto (mesmo padrão do
 * `auth:refresh-success` do `api.ts`).
 */
export const SUPPLIERS_CHANGED_EVENT = "suppliers:changed";

interface Props {
  open: boolean;
  onClose: () => void;
}

/**
 * Fornecedores: dar nome às empresas que as faturas só identificam por NIF.
 *
 * O ecrã tem duas metades e a ordem é intencional — primeiro o que falta
 * fazer ("por identificar", vindo das próprias faturas, do NIF mais frequente
 * para o menos), depois o catálogo já feito. Dar nome a um NIF preenche as
 * faturas desse NIF que estejam sem nome; as futuras nascem já identificadas.
 */
export const SuppliersDrawer: FC<Props> = ({ open, onClose }) => {
  const { isAdmin } = useAuth();
  const confirm = useConfirm();
  const editable = isAdmin();

  const [unknownNifs, setUnknownNifs] = useState<UnknownSupplierNif[]>([]);
  const [suppliers, setSuppliers] = useState<Supplier[]>([]);
  const [loading, setLoading] = useState(false);
  /** Chave da linha a gravar (NIF ou id do fornecedor) — só uma de cada vez. */
  const [savingKey, setSavingKey] = useState<string | null>(null);

  /** Nome a atribuir a cada NIF por identificar, enquanto se escreve. */
  const [draftNames, setDraftNames] = useState<Record<string, string>>({});
  /** Nome em edição de cada fornecedor do catálogo. */
  const [editedNames, setEditedNames] = useState<Record<string, string>>({});
  const [query, setQuery] = useState("");
  const [tab, setTab] = useState<TabKey>("pending");

  const load = useCallback(async (): Promise<UnknownSupplierNif[]> => {
    setLoading(true);
    try {
      const [pending, known] = await Promise.all([
        listUnknownSupplierNifs(),
        listSuppliers(),
      ]);
      setUnknownNifs(pending);
      setSuppliers(known);
      // O nome já escrito à mão nalguma fatura entra pré-preenchido: o trabalho
      // de o descobrir já foi feito uma vez, não se pede outra.
      setDraftNames(
        Object.fromEntries(pending.map((row) => [row.nif, row.suggestedName ?? ""]))
      );
      setEditedNames(Object.fromEntries(known.map((supplier) => [supplier.id, supplier.name])));
      return pending;
    } catch (error) {
      ErrorHandler.handle(error);
      return [];
    } finally {
      setLoading(false);
    }
  }, []);

  // O separador de arranque decide-se só ao abrir, e não a cada gravação: com
  // nada por identificar não faz sentido abrir num ecrã vazio, mas mudar de
  // separador debaixo dos pés de quem está a meio de uma lista, sim.
  useEffect(() => {
    if (!open) return;
    setQuery("");
    load().then((pending) => setTab(pending.length > 0 ? "pending" : "known"));
  }, [open, load]);

  /** Recarrega tudo e avisa as listas de faturas abertas. */
  const afterSave = async (updated: number) => {
    notificationService.success(
      "Fornecedores",
      updated === 0
        ? "Fornecedor guardado."
        : `Fornecedor guardado — ${updated} fatura${updated > 1 ? "s" : ""} ficou${
            updated > 1 ? "aram" : ""
          } com o nome.`
    );
    window.dispatchEvent(new Event(SUPPLIERS_CHANGED_EVENT));
    await load();
  };

  const handleIdentify = async (nif: string) => {
    const name = (draftNames[nif] ?? "").trim();
    if (!name) return;

    setSavingKey(nif);
    try {
      const result = await createSupplier({ nif, name });
      await afterSave(result.invoicesUpdated);
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSavingKey(null);
    }
  };

  const handleRename = async (supplier: Supplier) => {
    const name = (editedNames[supplier.id] ?? "").trim();
    if (!name || name === supplier.name) return;

    setSavingKey(supplier.id);
    try {
      const result = await updateSupplier(supplier.id, { nif: supplier.nif, name });
      await afterSave(result.invoicesUpdated);
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSavingKey(null);
    }
  };

  const handleDelete = (supplier: Supplier) => {
    confirm({
      title: "Remover do catálogo",
      message: `Remover "${supplier.name}" do catálogo? As faturas que já têm este nome escrito ficam como estão — deixa é de ser preenchido sozinho nas faturas novas deste NIF.`,
      actionLabel: "Remover",
      onConfirm: async () => {
        try {
          await deleteSupplier(supplier.id);
          notificationService.success("Fornecedores", "Fornecedor removido do catálogo.");
          await load();
        } catch (error) {
          ErrorHandler.handle(error);
        }
      },
    });
  };

  const term = query.trim().toLowerCase();
  const visibleSuppliers = term
    ? suppliers.filter(
        (supplier) =>
          supplier.name.toLowerCase().includes(term) || supplier.nif.toLowerCase().includes(term)
      )
    : suppliers;

  /* ── Por identificar ─────────────────────────────────────── */
  const pendingTab = (
    <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
      <p style={{ fontSize: 13, opacity: 0.75, margin: 0, lineHeight: 1.5 }}>
        O QR das faturas traz o NIF do fornecedor, mas nunca o nome da empresa — não existe esse
        campo na especificação da AT. Dê nome a um NIF uma vez: as faturas desse NIF que estejam
        sem nome ficam preenchidas, e as próximas já entram identificadas.
      </p>

      {unknownNifs.length === 0 ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description="Todos os NIFs das faturas já têm empresa."
        />
      ) : (
        <ul style={LIST}>
          {unknownNifs.map((row, index) => (
            <li key={row.nif} style={rowStyle(index)}>
              <div style={ROW_HEAD}>
                <span style={{ fontFamily: "var(--ind-font-heading)", fontSize: 16 }}>
                  NIF {row.nif}
                </span>
                <span style={{ fontSize: 12, opacity: 0.7 }}>
                  {row.invoiceCount} fatura{row.invoiceCount > 1 ? "s" : ""}
                  {row.lastInvoiceDate && <> · última em {formatDate(row.lastInvoiceDate)}</>}
                </span>
              </div>

              <div style={{ display: "flex", gap: 8 }}>
                <Input
                  size="large"
                  placeholder="Nome da empresa"
                  aria-label={`Nome da empresa do NIF ${row.nif}`}
                  disabled={!editable}
                  value={draftNames[row.nif] ?? ""}
                  onChange={(e) =>
                    setDraftNames((prev) => ({ ...prev, [row.nif]: e.target.value }))
                  }
                  onPressEnter={() => handleIdentify(row.nif)}
                />
                {editable && (
                  <Button
                    type="primary"
                    size="large"
                    loading={savingKey === row.nif}
                    disabled={!(draftNames[row.nif] ?? "").trim()}
                    onClick={() => handleIdentify(row.nif)}
                  >
                    Guardar
                  </Button>
                )}
              </div>
            </li>
          ))}
        </ul>
      )}
    </div>
  );

  /* ── Catálogo ────────────────────────────────────────────── */
  const knownTab = (
    <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
      {suppliers.length > 0 && (
        <Input
          placeholder="Procurar por nome ou NIF"
          aria-label="Procurar fornecedor por nome ou NIF"
          prefix={<SearchOutlined style={{ opacity: 0.5 }} />}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          allowClear
        />
      )}

      {visibleSuppliers.length === 0 ? (
        <Empty
          image={Empty.PRESENTED_IMAGE_SIMPLE}
          description={
            suppliers.length === 0
              ? "Ainda nenhuma empresa identificada."
              : `Nenhuma empresa para "${query.trim()}".`
          }
        />
      ) : (
        <ul style={LIST}>
          {visibleSuppliers.map((supplier, index) => {
            const edited = editedNames[supplier.id] ?? supplier.name;
            const dirty = edited.trim() !== supplier.name && edited.trim().length > 0;

            return (
              <li key={supplier.id} style={rowStyle(index)}>
                <div style={{ display: "flex", gap: 8, alignItems: "center" }}>
                  <Input
                    aria-label={`Nome da empresa do NIF ${supplier.nif}`}
                    disabled={!editable}
                    value={edited}
                    onChange={(e) =>
                      setEditedNames((prev) => ({ ...prev, [supplier.id]: e.target.value }))
                    }
                    onPressEnter={() => handleRename(supplier)}
                  />
                  {editable && (
                    <>
                      {/* Só aparece com alterações por gravar: um botão morto em
                          cada linha de uma lista de trinta é ruído. */}
                      {dirty && (
                        <Button
                          type="primary"
                          loading={savingKey === supplier.id}
                          onClick={() => handleRename(supplier)}
                        >
                          Guardar
                        </Button>
                      )}
                      <Button
                        type="text"
                        danger
                        icon={<DeleteOutlined />}
                        aria-label={`Remover ${supplier.name} do catálogo`}
                        onClick={() => handleDelete(supplier)}
                      />
                    </>
                  )}
                </div>
                <div style={{ fontSize: 12, opacity: 0.7 }}>
                  NIF {supplier.nif} · {supplier.invoiceCount} fatura
                  {supplier.invoiceCount === 1 ? "" : "s"}
                </div>
              </li>
            );
          })}
        </ul>
      )}
    </div>
  );

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width={640}
      title={
        <div>
          <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>Faturas</h6>
          <h2 style={{ margin: 0 }}>Fornecedores</h2>
        </div>
      }
      footer={
        <Space style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button onClick={onClose}>Fechar</Button>
        </Space>
      }
    >
      <Spin spinning={loading}>
        {/* Dois separadores e não duas secções empilhadas: são duas listas sem
            limite de tamanho, e uma obra com quarenta NIFs por identificar
            empurrava o catálogo para fora do ecrã. O número em cada etiqueta é
            também o que diz, de relance, quanto falta fazer. */}
        <Tabs
          activeKey={tab}
          onChange={(key) => setTab(key as TabKey)}
          items={[
            {
              key: "pending",
              label: `Por identificar${unknownNifs.length ? ` (${unknownNifs.length})` : ""}`,
              children: pendingTab,
            },
            {
              key: "known",
              label: `Empresas${suppliers.length ? ` (${suppliers.length})` : ""}`,
              children: knownTab,
            },
          ]}
        />
      </Spin>
    </Drawer>
  );
};

type TabKey = "pending" | "known";

/** Lista com divisórias — trinta linhas não são trinta caixas. */
const LIST: CSSProperties = { listStyle: "none", margin: 0, padding: 0 };

const ROW_HEAD: CSSProperties = {
  display: "flex",
  justifyContent: "space-between",
  gap: 10,
  alignItems: "baseline",
  flexWrap: "wrap",
};

const rowStyle = (index: number): CSSProperties => ({
  display: "flex",
  flexDirection: "column",
  gap: 6,
  padding: "10.2px 0",
  borderTop: index === 0 ? undefined : "1px solid var(--ind-color-divider)",
});
