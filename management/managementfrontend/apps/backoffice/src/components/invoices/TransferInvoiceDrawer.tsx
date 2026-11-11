import { useEffect, useState } from "react";
import type { FC } from "react";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Alert, Button, Drawer, Input, Radio, Select, Space, Typography } from "antd";
import { useTranslation } from "react-i18next";

import { TransferInvoiceSchema } from "@/components/invoices/transferFormSchema";
import type { TransferInvoiceForm } from "@/components/invoices/transferFormSchema";
import { setInvoiceBudget, splitInvoice, transferInvoice } from "@/services/invoiceService";
import { searchEnterprises, type EnterpriseOption } from "@/services/enterpriseService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import type { ConstructionInvoice, InvoiceScope, InvoiceTransferResult } from "@/types/invoice";
import type { BudgetItemSearchResult } from "@/types/budget";
import { RubricSearchField } from "@/components/invoices/RubricSearchField";

const { Text } = Typography;

interface Props {
  open: boolean;
  invoice: ConstructionInvoice | null;
  onClose: () => void;
  /**
   * Depois de transferir — o pai fecha o detalhe, recarrega, e trata do
   * `suggestIncident`. `targetLotId` só vem preenchido se o destino tiver
   * lotes e o utilizador tiver escolhido um — a fatura fica gravada nesse lote
   * logo a seguir à transferência, e o pai pode abrir logo a classificação nele.
   */
  onTransferred: (result: InvoiceTransferResult, targetLotId?: string | null) => void;
}

const SCOPE_LABEL: Record<InvoiceScope, string> = {
  PROJECT: "invoices.transfer.scope.PROJECT",
  COMPANY: "invoices.transfer.scope.COMPANY",
  UNIDENTIFIED: "invoices.transfer.scope.UNIDENTIFIED",
};

/**
 * Transfere uma fatura de âmbito/obra. A razão é obrigatória e o backend apaga
 * as despesas — o aviso e a razão são a paragem consciente, não há `useConfirm`
 * à parte. As notas de crédito ligadas seguem a fatura.
 *
 * Há um quarto destino que **não é** uma transferência: "outra rubrica desta
 * obra" (pedido do utilizador a 2026-09-21). Mover dentro da mesma obra é
 * repartir de novo — `split` com uma linha só, que substitui a repartição
 * inteira e serve tanto a fatura já associada como a que ainda não está
 * (`allocate` recusaria a primeira com `INVOICE_ALREADY_ALLOCATED`). Não passa
 * pelo `transfer` do backend, por isso não pede razão nem apaga as despesas
 * das NC ligadas. Só faz sentido numa fatura de obra — na quarentena e na
 * empresa a opção nem aparece.
 *
 * É o mesmo modal em todo o lado (pedido de 2026-11-11): a quarentena deixou de
 * o fixar em "obra" e passa a poder mandar a fatura para a empresa. O âmbito de
 * origem some da lista quando não é `PROJECT` — "por identificar → por
 * identificar" só daria `INVOICE_032` depois de submeter. Com destino numa obra
 * pode escolher-se já, opcionalmente, o lote e a rubrica: a fatura fica
 * associada logo a seguir à transferência, sem passar pelo picker.
 */
export const TransferInvoiceDrawer: FC<Props> = ({ open, invoice, onClose, onTransferred }) => {
  const { t } = useTranslation();
  const [options, setOptions] = useState<EnterpriseOption[]>([]);
  const [searching, setSearching] = useState(false);
  /** Destino "outra rubrica desta obra" — fora do schema, porque não é transferência. */
  const [toRubric, setToRubric] = useState(false);
  const [rubric, setRubric] = useState<BudgetItemSearchResult | null>(null);
  const [moving, setMoving] = useState(false);
  /** O lote do destino, só quando este tem mais do que um — atalho de UX (ver `onTransferred`). */
  const [targetLotId, setTargetLotId] = useState<string | null>(null);
  /** Rubrica opcional na obra de destino — associa a fatura logo depois de a transferir. */
  const [targetRubric, setTargetRubric] = useState<BudgetItemSearchResult | null>(null);

  const sourceScope = invoice?.scope ?? "PROJECT";
  const canMoveWithinProject = sourceScope === "PROJECT" && !!invoice?.enterpriseId;

  const {
    control,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isValid, isSubmitting },
  } = useForm<TransferInvoiceForm>({
    resolver: zodResolver(TransferInvoiceSchema),
    mode: "onChange",
    defaultValues: { targetScope: "PROJECT", targetEnterpriseId: null, reason: "" },
  });

  const targetScope = watch("targetScope");
  const targetEnterpriseId = watch("targetEnterpriseId");
  const targetLots = options.find((o) => o.id === targetEnterpriseId)?.lots ?? [];

  // O lote só faz sentido para o empreendimento escolhido — muda de obra, esquece o lote.
  useEffect(() => {
    setTargetLotId(null);
    setTargetRubric(null);
  }, [targetEnterpriseId]);

  // A rubrica tem de ser do lote escolhido — muda o lote, esquece a rubrica se não bater.
  useEffect(() => {
    setTargetRubric((current) =>
      current && targetLotId && current.budgetId !== targetLotId ? null : current
    );
  }, [targetLotId]);

  useEffect(() => {
    if (!open) return;
    setOptions([]);
    setToRubric(false);
    setRubric(null);
    setTargetLotId(null);
    setTargetRubric(null);
    reset({ targetScope: "PROJECT", targetEnterpriseId: null, reason: "" });
  }, [open, reset]);

  const moveToRubric = async () => {
    if (!invoice || !rubric) return;
    setMoving(true);
    try {
      // O total vai todo para a rubrica nova; sem total a linha fica provisória
      // (a zero), como no ecrã de classificação.
      await splitInvoice(invoice.id, [{ budgetItemId: rubric.id, amount: invoice.totalAmount }]);
      notificationService.success(t("invoices.transfer.rubricSuccess"));
      // A fatura não mudou de obra — os pais só precisam de recarregar.
      onTransferred({ invoice, suggestIncident: false });
      onClose();
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setMoving(false);
    }
  };

  const runSearch = async (q: string) => {
    if (q.trim().length < 2) {
      setOptions([]);
      return;
    }
    setSearching(true);
    try {
      setOptions(await searchEnterprises(q.trim()));
    } catch {
      setOptions([]);
    } finally {
      setSearching(false);
    }
  };

  const onSubmit = handleSubmit(async (values) => {
    if (!invoice) return;
    try {
      const result = await transferInvoice(invoice.id, {
        targetScope: values.targetScope,
        targetEnterpriseId:
          values.targetScope === "PROJECT" ? values.targetEnterpriseId : null,
        reason: values.reason.trim(),
      });
      const toProject = values.targetScope === "PROJECT";
      // Numa obra de vários lotes o backend recusa a rubrica sem lote
      // (`INVOICE_BUDGET_REQUIRED`): sem lote escolhido, usa-se o da rubrica.
      const lotId = toProject ? (targetLotId ?? targetRubric?.budgetId ?? null) : null;
      let allocated = false;
      try {
        if (lotId) await setInvoiceBudget(result.invoice.id, lotId);
        if (toProject && targetRubric) {
          await splitInvoice(result.invoice.id, [
            { budgetItemId: targetRubric.id, amount: result.invoice.totalAmount },
          ]);
          allocated = true;
        }
        notificationService.success(
          t(allocated ? "invoices.transfer.successWithRubric" : "invoices.transfer.success")
        );
      } catch (error) {
        // A transferência já está feita — só a associação falhou. Avisa e segue,
        // para o pai recarregar e a pessoa associar à mão.
        notificationService.warning(t("invoices.transfer.allocateFailed"));
        ErrorHandler.handle(error);
      }
      // Se `suggestIncident`, o pai (`InvoiceDetailDrawer` → página) abre o
      // `IncidentDrawer` já com esta fatura. Já associada, não há picker a abrir.
      onTransferred(result, toProject && !allocated ? lotId : null);
      onClose();
    } catch (error) {
      ErrorHandler.handle(error);
    }
  });

  const fieldError = (message?: string) =>
    message ? (
      <Text type="danger" style={{ fontSize: 12 }}>
        {t(message)}
      </Text>
    ) : null;

  return (
    <Drawer
      title={t("invoices.transfer.drawerTitle")}
      open={open}
      onClose={onClose}
      width="min(560px, 94vw)"
      destroyOnClose
      footer={
        <Space style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button onClick={onClose} disabled={isSubmitting || moving}>
            {t("common.cancel")}
          </Button>
          {toRubric ? (
            <Button type="primary" onClick={moveToRubric} loading={moving} disabled={!rubric}>
              {t("invoices.transfer.submitRubric")}
            </Button>
          ) : (
            <Button type="primary" onClick={onSubmit} loading={isSubmitting} disabled={!isValid}>
              {t("invoices.transfer.submit")}
            </Button>
          )}
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
        <Alert
          type="warning"
          showIcon
          message={t(
            toRubric
              ? "invoices.transfer.rubricWarning"
              : sourceScope === "PROJECT"
                ? "invoices.transfer.warning"
                : "invoices.transfer.warningOutsideProject"
          )}
        />

        <div>
            <label style={{ fontSize: 12, opacity: 0.7 }}>{t("invoices.transfer.fieldScope")}</label>
            <Controller
              name="targetScope"
              control={control}
              render={({ field }) => (
                <Radio.Group
                  value={toRubric ? "RUBRIC" : field.value}
                  onChange={(e) => {
                    const value = e.target.value as InvoiceScope | "RUBRIC";
                    if (value === "RUBRIC") {
                      setToRubric(true);
                    } else {
                      setToRubric(false);
                      field.onChange(value);
                    }
                  }}
                  style={{ display: "flex", flexDirection: "column", gap: 6, marginTop: 4 }}
                >
                  {canMoveWithinProject && (
                    <Radio value="RUBRIC">{t("invoices.transfer.scope.RUBRIC")}</Radio>
                  )}
                  {/* Vinda de fora de uma obra, "Outra obra" não faz sentido — é "Uma obra". */}
                  <Radio value="PROJECT">
                    {t(sourceScope === "PROJECT" ? SCOPE_LABEL.PROJECT : "invoices.transfer.scope.PROJECT_ANY")}
                  </Radio>
                  {sourceScope !== "COMPANY" && (
                    <Radio value="COMPANY">{t(SCOPE_LABEL.COMPANY)}</Radio>
                  )}
                  {sourceScope !== "UNIDENTIFIED" && (
                    <Radio value="UNIDENTIFIED">{t(SCOPE_LABEL.UNIDENTIFIED)}</Radio>
                  )}
                </Radio.Group>
              )}
            />
          </div>

        {toRubric && invoice?.enterpriseId && (
          <div>
            <label style={{ fontSize: 12, opacity: 0.7 }}>{t("invoices.transfer.fieldRubric")}</label>
            <div style={{ marginTop: 4 }}>
              <RubricSearchField
                enterpriseId={invoice.enterpriseId}
                selectedId={rubric?.id}
                onPick={setRubric}
                autoFocus
              />
            </div>
          </div>
        )}

        {!toRubric && targetScope === "PROJECT" && (
          <div>
            <label style={{ fontSize: 12, opacity: 0.7 }}>{t("invoices.transfer.fieldEnterprise")}</label>
            <Controller
              name="targetEnterpriseId"
              control={control}
              render={({ field }) => (
                <Select
                  showSearch
                  value={field.value ?? undefined}
                  onChange={(value) => field.onChange(value ?? null)}
                  onSearch={runSearch}
                  filterOption={false}
                  loading={searching}
                  placeholder={t("invoices.transfer.enterprisePlaceholder")}
                  notFoundContent={null}
                  style={{ width: "100%" }}
                  options={options.map((o) => ({ value: o.id, label: o.name }))}
                />
              )}
            />
            {fieldError(errors.targetEnterpriseId?.message)}
          </div>
        )}

        {!toRubric && targetScope === "PROJECT" && targetEnterpriseId && targetLots.length > 0 && (
          <div>
            <label style={{ fontSize: 12, opacity: 0.7 }}>{t("invoices.transfer.fieldLot")}</label>
            <Select
              allowClear
              value={targetLotId ?? undefined}
              onChange={(value) => setTargetLotId(value ?? null)}
              placeholder={t("invoices.transfer.lotPlaceholder")}
              style={{ width: "100%", marginTop: 4 }}
              options={targetLots.map((lot) => ({ value: lot.id, label: lot.name }))}
            />
          </div>
        )}

        {!toRubric && targetScope === "PROJECT" && targetEnterpriseId && (
          <div>
            <label style={{ fontSize: 12, opacity: 0.7 }}>
              {t("invoices.transfer.fieldTargetRubric")}
            </label>
            {targetRubric && (
              <div style={{ display: "flex", justifyContent: "space-between", gap: 8, margin: "4px 0", fontSize: 13 }}>
                <span style={{ minWidth: 0, overflow: "hidden", textOverflow: "ellipsis", whiteSpace: "nowrap" }}>
                  <Text strong>
                    {targetRubric.code ? `${targetRubric.code} · ` : ""}
                    {targetRubric.name}
                  </Text>
                </span>
                <Button size="small" type="link" onClick={() => setTargetRubric(null)}>
                  {t("invoices.transfer.clearRubric")}
                </Button>
              </div>
            )}
            <div style={{ marginTop: 4 }}>
              <RubricSearchField
                key={`${targetEnterpriseId}-${targetLotId ?? ""}`}
                enterpriseId={targetEnterpriseId}
                budgetId={targetLotId}
                selectedId={targetRubric?.id}
                onPick={setTargetRubric}
              />
            </div>
          </div>
        )}

        {!toRubric && (
          <div>
            <label style={{ fontSize: 12, opacity: 0.7 }}>{t("invoices.transfer.fieldReason")}</label>
            <Controller
              name="reason"
              control={control}
              render={({ field }) => (
                <Input.TextArea {...field} rows={3} maxLength={1000} showCount />
              )}
            />
            {fieldError(errors.reason?.message)}
          </div>
        )}
      </div>
    </Drawer>
  );
};

export default TransferInvoiceDrawer;
