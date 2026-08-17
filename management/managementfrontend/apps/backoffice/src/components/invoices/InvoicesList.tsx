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
import type { ConstructionInvoice } from "@/types/invoice";

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
}) => {
  const { isAdmin } = useAuth();

  const columns: ColumnsType<ConstructionInvoice> = [
    {
      title: "",
      dataIndex: "thumbnailUrl",
      width: 56,
      render: (url: string | null, row) =>
        url ? (
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onImageClick(row.id);
            }}
            style={{
              padding: 0,
              border: "1px solid var(--ind-color-divider)",
              background: "var(--ind-color-surface)",
              cursor: "pointer",
            }}
          >
            <img
              src={url}
              alt={row.originalFilename ?? "fatura"}
              loading="lazy"
              style={{
                width: 40,
                height: 52,
                objectFit: "cover",
                display: "block",
              }}
            />
          </button>
        ) : (
          <button
            type="button"
            onClick={(e) => {
              e.stopPropagation();
              onImageClick(row.id);
            }}
            style={{
              width: 40,
              height: 52,
              display: "grid",
              placeItems: "center",
              border: "1px solid var(--ind-color-divider)",
              color: "var(--ind-color-accent)",
              background: "var(--ind-color-surface)",
              cursor: "pointer",
              padding: 0,
            }}
          >
            <FileTextOutlined />
          </button>
        ),
    },
    {
      title: "Fornecedor",
      dataIndex: "supplierName",
      render: (name: string | null, row) => (
        <span>
          <span style={{ display: "block", fontFamily: "var(--ind-font-heading)", fontWeight: 600 }}>
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
      title: "Rubrica",
      dataIndex: "budgetItemName",
      render: (_: unknown, row) => {
        if (!row.allocated) {
          return (
            <span className="ind-tag ind-tag-outline">
              {row.needsReview ? "por rever" : "por associar"}
            </span>
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
          {isAdmin() &&
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
