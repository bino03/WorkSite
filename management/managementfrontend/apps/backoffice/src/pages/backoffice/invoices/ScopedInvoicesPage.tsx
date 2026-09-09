import { useCallback, useEffect, useState } from "react";
import type { FC } from "react";
import { Button, Input } from "antd";
import { PlusOutlined, SearchOutlined } from "@ant-design/icons";

import {
  deleteInvoice,
  getInvoice,
  listCompanyInvoices,
  listUnidentifiedInvoices,
  setInvoiceSentToAccountant,
} from "@/services/invoiceService";
import type { InvoicePage } from "@/services/invoiceService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { useAuth } from "@/hooks/useAuth";
import { useConfirm } from "@/context/ConfirmDialogContext";
import { DEFAULT_PAGE_SIZE } from "@/config/pagination";
import { InvoicesList } from "@/components/invoices/InvoicesList";
import { InvoiceDetailDrawer } from "@/components/invoices/InvoiceDetailDrawer";
import { InvoiceRegisterDrawer } from "@/components/invoices/InvoiceRegisterDrawer";
import TransferInvoiceDrawer from "@/components/invoices/TransferInvoiceDrawer";
import IncidentDrawer from "@/components/invoices/IncidentDrawer";
import InvoicePreviewModal from "@/components/construction/InvoicePreviewModal";
import { toIncidentInvoiceRef } from "@/components/invoices/toIncidentInvoiceRef";
import type { ConstructionInvoice, InvoiceScope } from "@/types/invoice";
import type { IncidentInvoiceRef } from "@/types/incident";

interface Props {
  /** `COMPANY` ou `UNIDENTIFIED` — a lista de obra é a `EnterpriseInvoicesPage`. */
  scope: Exclude<InvoiceScope, "PROJECT">;
  kicker: string;
  title: string;
  /** O que dizer quando não há nada — as duas listas vazias querem dizer coisas diferentes. */
  emptyHint: string;
}

/**
 * As duas listas de faturas que não pertencem a nenhuma obra.
 *
 * São o mesmo ecrã com um `scope` diferente porque a diferença entre elas é
 * mesmo só essa: a quarentena ordena da mais antiga (é uma fila de trabalho) e
 * mostra as notas de quem a recebeu; as despesas da empresa ordenam da mais
 * recente, como qualquer arquivo. Duplicar o ficheiro para isso não se
 * justificava.
 *
 * Ambas são só `ADMIN` — o gate real está no `@PreAuthorize` do backend; o
 * `NavLink` escondido é só para o `EMPLOYEE` não bater num 403.
 */
const ScopedInvoicesPage: FC<Props> = ({ scope, kicker, title, emptyHint }) => {
  const { isAdmin } = useAuth();
  const confirm = useConfirm();

  const [invoices, setInvoices] = useState<ConstructionInvoice[]>([]);
  const [loading, setLoading] = useState(false);
  const [totalElements, setTotalElements] = useState(0);
  const [page, setPage] = useState(0);
  const [pageSize, setPageSize] = useState(DEFAULT_PAGE_SIZE);
  const [query, setQuery] = useState("");
  const [outstanding, setOutstanding] = useState(false);

  const [detailId, setDetailId] = useState<string | null>(null);
  const [transferInvoice, setTransferInvoice] = useState<ConstructionInvoice | null>(null);
  const [registerOpen, setRegisterOpen] = useState(false);
  const [previewInvoice, setPreviewInvoice] = useState<ConstructionInvoice | null>(null);
  const [incidentInvoices, setIncidentInvoices] = useState<IncidentInvoiceRef[] | null>(null);

  const fetch = useCallback(
    async (nextPage: number, nextSize: number, q: string, onlyOutstanding: boolean) => {
      setLoading(true);
      try {
        const params = {
          q: q.trim() || undefined,
          outstanding: onlyOutstanding || undefined,
          page: nextPage,
          size: nextSize,
        };
        const list: InvoicePage =
          scope === "UNIDENTIFIED"
            ? await listUnidentifiedInvoices(params)
            : await listCompanyInvoices(params);
        setInvoices(list.content);
        setTotalElements(list.totalElements);
      } catch (error) {
        ErrorHandler.handle(error);
      } finally {
        setLoading(false);
      }
    },
    [scope]
  );

  useEffect(() => {
    void fetch(0, pageSize, "", false);
    setPage(0);
    setQuery("");
    setOutstanding(false);
    // Trocar de lista pelo nav mantém o componente montado: sem isto ficava-se
    // a olhar para a lista anterior.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [scope]);

  const reload = () => void fetch(page, pageSize, query, outstanding);

  const search = () => {
    setPage(0);
    void fetch(0, pageSize, query, outstanding);
  };

  const toggleOutstanding = () => {
    const next = !outstanding;
    setOutstanding(next);
    setPage(0);
    void fetch(0, pageSize, query, next);
  };

  const handleOpenPreview = async (invoiceId: string) => {
    try {
      // A lista só traz a miniatura; o link assinado do documento vem do detalhe.
      setPreviewInvoice(await getInvoice(invoiceId));
    } catch (error) {
      ErrorHandler.handle(error);
    }
  };

  const handleSendToAccountant = (invoice: ConstructionInvoice) => {
    confirm({
      title: "Contabilidade",
      message: invoice.sentToAccountant
        ? "Desmarcar esta fatura como enviada à contabilidade?"
        : "Marcar esta fatura como enviada à contabilidade?",
      actionLabel: invoice.sentToAccountant ? "Desmarcar" : "Marcar como enviada",
      onConfirm: async () => {
        try {
          await setInvoiceSentToAccountant(invoice.id, !invoice.sentToAccountant);
          notificationService.success("Contabilidade atualizada");
          reload();
        } catch (error) {
          ErrorHandler.handle(error);
        }
      },
    });
  };

  const handleDelete = (invoice: ConstructionInvoice) => {
    confirm({
      message: `Eliminar esta fatura${invoice.invoiceNumber ? ` (${invoice.invoiceNumber})` : ""}? Os documentos vão atrás.`,
      onConfirm: async () => {
        try {
          await deleteInvoice(invoice.id);
          notificationService.success("Fatura eliminada");
          reload();
        } catch (error) {
          ErrorHandler.handle(error);
        }
      },
    });
  };

  return (
    <div>
      <div
        style={{
          display: "flex",
          justifyContent: "space-between",
          alignItems: "flex-end",
          marginBottom: "20.4px",
        }}
      >
        <div>
          <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>{kicker}</h6>
          <h1 style={{ margin: 0 }}>{title}</h1>
        </div>
        {isAdmin() && (
          <Button type="primary" icon={<PlusOutlined />} onClick={() => setRegisterOpen(true)}>
            Registar sem ficheiro
          </Button>
        )}
      </div>

      <div
        style={{
          display: "flex",
          gap: "10.2px",
          alignItems: "center",
          flexWrap: "wrap",
          marginBottom: "13.6px",
        }}
      >
        <Input
          placeholder="Fornecedor, NIF, nº da fatura…"
          prefix={<SearchOutlined style={{ opacity: 0.5 }} />}
          value={query}
          onChange={(e) => setQuery(e.target.value)}
          onPressEnter={search}
          allowClear
          onClear={() => {
            setQuery("");
            setPage(0);
            void fetch(0, pageSize, "", outstanding);
          }}
          style={{ maxWidth: 320 }}
        />
        <Button onClick={search}>Pesquisar</Button>
        <Button size="small" type={outstanding ? "primary" : "default"} onClick={toggleOutstanding}>
          Por liquidar
        </Button>
      </div>

      {!loading && invoices.length === 0 && (
        <p style={{ fontSize: 12, opacity: 0.6 }}>{emptyHint}</p>
      )}

      <InvoicesList
        invoices={invoices}
        loading={loading}
        scope={scope}
        pagination={{ currentPage: page, totalElements, pageSize }}
        selectedIds={[]}
        onSelectionChange={() => undefined}
        onPageChange={(nextPage, nextSize) => {
          setPage(nextPage);
          setPageSize(nextSize);
          void fetch(nextPage, nextSize, query, outstanding);
        }}
        onView={(invoice) => setDetailId(invoice.id)}
        onImageClick={(invoiceId) => void handleOpenPreview(invoiceId)}
        // Fora de uma obra não há rubrica: a lista já esconde as duas ações.
        onAllocate={() => undefined}
        onDeallocate={() => undefined}
        onSendToAccountant={handleSendToAccountant}
        onDelete={handleDelete}
        onTransfer={setTransferInvoice}
      />

      <InvoiceDetailDrawer
        invoiceId={detailId}
        open={detailId !== null}
        onClose={() => setDetailId(null)}
        onChanged={reload}
        onAllocate={() => undefined}
        onIncidentSuggested={(invoice) => setIncidentInvoices([toIncidentInvoiceRef(invoice)])}
      />

      <TransferInvoiceDrawer
        open={transferInvoice !== null}
        invoice={transferInvoice}
        // Na quarentena "atribuir uma obra" é transferir com o âmbito fixo em obra.
        lockToProject={scope === "UNIDENTIFIED"}
        onClose={() => setTransferInvoice(null)}
        onTransferred={(result) => {
          setTransferInvoice(null);
          reload();
          if (result.suggestIncident) {
            setIncidentInvoices([toIncidentInvoiceRef(result.invoice)]);
          }
        }}
      />

      <IncidentDrawer
        open={incidentInvoices !== null}
        presetInvoices={incidentInvoices ?? []}
        onClose={() => setIncidentInvoices(null)}
        onCreated={() => setIncidentInvoices(null)}
      />

      <InvoiceRegisterDrawer
        open={registerOpen}
        scope={scope}
        onClose={() => setRegisterOpen(false)}
        onCreated={reload}
      />

      <InvoicePreviewModal
        open={previewInvoice !== null}
        onClose={() => setPreviewInvoice(null)}
        invoiceUrl={previewInvoice?.fileUrl ?? null}
        mimeType={previewInvoice?.mimeType ?? null}
        filename={previewInvoice?.originalFilename ?? null}
      />
    </div>
  );
};

export default ScopedInvoicesPage;
