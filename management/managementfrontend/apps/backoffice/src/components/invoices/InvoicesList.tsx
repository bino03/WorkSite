import type { FC } from "react";
import { Empty, Pagination, Spin, Table, Tooltip } from "antd";
import type { ColumnsType } from "antd/es/table";
import { FileTextOutlined } from "@ant-design/icons";

import {
  ListActions,
  ListActionDanger,
  ListActionPrimary,
  ListActionSecondary,
} from "@/components/common/ListActions";
import { useAuth } from "@/hooks/useAuth";
import { formatCurrency, formatDate } from "@/utils/formatters";
import type {
  ConstructionInvoice,
  InvoiceDocumentStatus,
  InvoiceScope,
  PaymentStatus,
} from "@/types/invoice";

/**
 * O selo do estado do papel. `ARCHIVED` não está aqui de propósito: é o caso
 * normal e um selo em todas as linhas não distingue nada — o que interessa ver
 * de relance é o que ainda falta pedir ou imprimir.
 */
const DOCUMENT_STATUS: Partial<Record<InvoiceDocumentStatus, { label: string; cls: string }>> = {
  MISSING: { label: "sem ficheiro", cls: "ind-tag-outline" },
  TO_PRINT: { label: "por imprimir", cls: "ind-tag-accent" },
  TO_REQUEST: { label: "por pedir", cls: "ind-tag-accent" },
};

/** Estado de pagamento (derivado no backend). */
const PAYMENT_STATUS: Record<PaymentStatus, { label: string; cls: string }> = {
  UNPAID: { label: "por liquidar", cls: "ind-tag-outline" },
  PARTIAL: { label: "parcial", cls: "ind-tag-accent" },
  PAID: { label: "paga", cls: "ind-tag-accent-2" },
};

interface Props {
  invoices: ConstructionInvoice[];
  loading: boolean;
  pagination: { currentPage: number; totalElements: number; pageSize: number };
  selectedIds: string[];
  onSelectionChange: (ids: string[]) => void;
  onPageChange: (page: number, size: number) => void;
  onView: (invoice: ConstructionInvoice) => void;
  onImageClick: (invoiceId: string) => void;
  onAllocate: (invoice: ConstructionInvoice) => void;
  onDeallocate: (invoice: ConstructionInvoice) => void;
  onSendToAccountant: (invoice: ConstructionInvoice) => void;
  onDelete: (invoice: ConstructionInvoice) => void;
  /**
   * Onde esta lista está a ser mostrada. Muda as colunas, não os dados: a
   * rubrica só existe numa obra, e as notas de "de quem será?" só na
   * quarentena. Por omissão, obra — é a lista que já existia.
   */
  scope?: InvoiceScope;
}

/**
 * A caixa de entrada.
 *
 * A miniatura é a primeira coluna porque numa fatura mal preenchida é ela que
 * a identifica — reconhecer o papel é mais rápido do que ler campos vazios.
 * Vem já pronta do backend com ~20 KB; o documento completo só é pedido quando
 * alguém abre o detalhe.
 */
export const InvoicesList: FC<Props> = ({
  invoices,
  loading,
  pagination,
  selectedIds,
  onSelectionChange,
  onPageChange,
  onView,
  onImageClick,
  onAllocate,
  onDeallocate,
  onSendToAccountant,
  onDelete,
  scope = "PROJECT",
}) => {
  const { isAdmin } = useAuth();
  const isProject = scope === "PROJECT";
  const isQuarantine = scope === "UNIDENTIFIED";

  const columns: ColumnsType<ConstructionInvoice> = [
    {
      title: "",
      dataIndex: "thumbnailUrl",
      width: 76,
      render: (url: string | null, row) => {
        const extra = row.documents.length - 1;
        const status = DOCUMENT_STATUS[row.documentStatus];
        return (
          <div style={{ display: "flex", flexDirection: "column", gap: 4, alignItems: "flex-start" }}>
            <div style={{ position: "relative", lineHeight: 0 }}>
              <button
                type="button"
                onClick={(e) => {
                  e.stopPropagation();
                  onImageClick(row.id);
                }}
                style={{
                  padding: 0,
                  width: 40,
                  height: 52,
                  border: "1px solid var(--ind-color-divider)",
                  background: "var(--ind-color-surface)",
                  cursor: "pointer",
                  display: "grid",
                  placeItems: "center",
                  color: "var(--ind-color-accent)",
                }}
              >
                {url ? (
                  <img
                    src={url}
                    alt={row.originalFilename ?? "fatura"}
                    loading="lazy"
                    style={{ width: 40, height: 52, objectFit: "cover", display: "block" }}
                  />
                ) : (
                  <FileTextOutlined />
                )}
              </button>
              {/* A miniatura é sempre a do primeiro documento; o resto conta-se. */}
              {extra > 0 && (
                <Tooltip title={`${row.documents.length} documentos nesta fatura`}>
                  <span
                    className="ind-tag ind-tag-neutral"
                    style={{ position: "absolute", right: -6, bottom: -6, fontSize: 10 }}
                  >
                    +{extra}
                  </span>
                </Tooltip>
              )}
            </div>
            {/* Arquivada é o caso normal e não vale um selo; o resto é trabalho por fazer. */}
            {status && <span className={`ind-tag ${status.cls}`} style={{ fontSize: 10 }}>{status.label}</span>}
          </div>
        );
      },
    },
    {
      title: "Fornecedor",
      dataIndex: "supplierName",
      render: (name: string | null, row) => (
        <span>
          <span style={{ display: "block", fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>
            {/* Uma NC tem o valor gravado em positivo como qualquer fatura; sem
                este selo lia-se como mais uma despesa em vez de um abatimento. */}
            {row.documentType === "CREDIT_NOTE" && (
              <span className="ind-tag ind-tag-neutral" style={{ fontSize: 10, marginRight: 6 }}>
                NC
              </span>
            )}
            {name ?? row.supplierNif ?? row.originalFilename ?? "—"}
          </span>
          <span style={{ display: "block", fontSize: 11, opacity: 0.55 }}>
            {[row.invoiceNumber, row.supplierNif ? `NIF ${row.supplierNif}` : null]
              .filter(Boolean)
              .join(" · ") || "sem identificação"}
          </span>
        </span>
      ),
    },
    {
      title: "Data",
      dataIndex: "invoiceDate",
      width: 108,
      render: (date: string | null) => (date ? formatDate(date) : "—"),
    },
    {
      title: "Total",
      dataIndex: "totalAmount",
      width: 120,
      render: (value: number | null) => (
        <span style={{ fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>
          {value != null ? formatCurrency(value) : "—"}
        </span>
      ),
    },
    {
      title: "Pagamento",
      dataIndex: "paymentStatus",
      width: 116,
      render: (status: PaymentStatus) => (
        <span className={`ind-tag ${PAYMENT_STATUS[status].cls}`}>{PAYMENT_STATUS[status].label}</span>
      ),
    },
    // A rubrica só existe numa obra: uma despesa da empresa não entra em
    // orçamento nenhum, e a quarentena nem obra tem.
    ...(isProject
      ? ([
          {
            title: "Rubrica",
            dataIndex: "budgetItemName",
            render: (_: unknown, row: ConstructionInvoice) => {
              if (!row.allocated) {
                return (
                  <span className="ind-tag ind-tag-outline">
                    {row.needsReview ? "por rever" : "por associar"}
                  </span>
                );
              }
              // Repartida: os campos singulares vêm a null de propósito (o
              // backend recusa-se a apontar para a primeira e mentir sobre as
              // outras). Mostra-se a contagem, e a lista completa no tooltip.
              if (row.allocations.length > 1) {
                return (
                  <Tooltip
                    title={row.allocations
                      .map((a) => `${a.budgetItemCode ?? a.budgetItemName} · ${formatCurrency(a.amount ?? 0)}`)
                      .join("\n")}
                  >
                    <span className="ind-tag ind-tag-accent-2">
                      {row.allocations.length} rubricas
                    </span>
                  </Tooltip>
                );
              }
              return (
                <Tooltip title={row.budgetItemName}>
                  <span className="ind-tag ind-tag-accent">
                    {row.budgetItemCode ?? row.budgetItemName}
                  </span>
                </Tooltip>
              );
            },
          },
        ] as ColumnsType<ConstructionInvoice>)
      : []),
    // As duas notas que alguém escreveu ao receber a fatura sem saber de quem
    // era, e há quanto tempo ela está à espera — é o que torna esta lista uma
    // fila de trabalho em vez de um arquivo.
    ...(isQuarantine
      ? ([
          {
            title: "Talvez de",
            dataIndex: "possibleEnterprises",
            render: (value: string | null) => value ?? "—",
          },
          {
            title: "Perguntar a",
            dataIndex: "askWhom",
            width: 140,
            render: (value: string | null) => value ?? "—",
          },
          {
            title: "Aqui desde",
            dataIndex: "createdAt",
            width: 108,
            render: (value: string) => formatDate(value),
          },
        ] as ColumnsType<ConstructionInvoice>)
      : []),
    {
      title: "Contabilidade",
      dataIndex: "sentToAccountant",
      width: 120,
      render: (sent: boolean) => (
        <span className={`ind-tag ${sent ? "ind-tag-accent-2" : "ind-tag-neutral"}`}>
          {sent ? "enviada" : "por enviar"}
        </span>
      ),
    },
    {
      title: "",
      key: "actions",
      width: 200,
      render: (_: unknown, row) => (
        <ListActions>
          <ListActionPrimary onClick={() => onView(row)}>Ver detalhes</ListActionPrimary>
          {/* Fora de uma obra não há rubrica a que associar — o backend
              recusa com INVOICE_013, e esconder o botão poupa a viagem. */}
          {isAdmin() &&
            isProject &&
            (row.allocated ? (
              <ListActionSecondary onClick={() => onDeallocate(row)}>
                Desassociar
              </ListActionSecondary>
            ) : (
              <Tooltip
                title={row.needsReview ? "Preencha a data e o total antes de associar." : undefined}
              >
                {/* O span mantém o tooltip vivo com o botão desativado. */}
                <span>
                  <ListActionSecondary onClick={() => onAllocate(row)} disabled={row.needsReview}>
                    Associar
                  </ListActionSecondary>
                </span>
              </Tooltip>
            ))}
          {isAdmin() && (
            <ListActionSecondary onClick={() => onSendToAccountant(row)}>
              {row.sentToAccountant ? "Desmarcar envio" : "Enviar"}
            </ListActionSecondary>
          )}
          {isAdmin() && <ListActionDanger onClick={() => onDelete(row)}>Eliminar</ListActionDanger>}
        </ListActions>
      ),
    },
  ];

  return (
    <Spin spinning={loading}>
      <Table<ConstructionInvoice>
        rowKey="id"
        columns={columns}
        dataSource={invoices}
        pagination={false}
        size="small"
        rowSelection={
          isAdmin()
            ? {
                selectedRowKeys: selectedIds,
                onChange: (keys) => onSelectionChange(keys as string[]),
                // Só faz sentido associar em bloco o que está por associar e
                // tem dados suficientes para virar lançamento.
                getCheckboxProps: (row) => ({ disabled: row.allocated || row.needsReview }),
              }
            : undefined
        }
        onRow={(row) => ({ onClick: () => onView(row), style: { cursor: "pointer" } })}
        locale={{
          emptyText: (
            <Empty
              image={Empty.PRESENTED_IMAGE_SIMPLE}
              description="Nenhuma fatura corresponde a estes filtros."
            />
          ),
        }}
      />

      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "center",
          marginTop: "13.6px",
          paddingTop: "10.2px",
          borderTop: "1px solid var(--ind-color-divider)",
        }}
      >
        <span style={{ fontSize: 12, opacity: 0.6 }}>
          {pagination.totalElements} resultado{pagination.totalElements === 1 ? "" : "s"}
        </span>
        <Pagination
          current={pagination.currentPage + 1}
          total={pagination.totalElements}
          pageSize={pagination.pageSize}
          showSizeChanger
          onChange={(page, size) => onPageChange(page - 1, size)}
        />
      </div>
    </Spin>
  );
};
