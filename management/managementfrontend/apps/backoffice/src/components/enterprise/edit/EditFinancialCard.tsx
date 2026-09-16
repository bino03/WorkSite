import type { FC } from "react";
import { Button, Space } from "antd";
import { useTranslation } from "react-i18next";
import { FormProvider, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { z } from "zod";

import FinancialSection from "@/components/enterprise/create/FinancialSection";
import { EnterpriseFormSchema } from "@/components/enterprise/create/enterpriseFormSchema";
import { updateEnterpriseFinance } from "@/services/enterpriseService";
import type { EnterpriseFullResponseDTO } from "@/services/enterpriseService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";

const FinanceSchema = EnterpriseFormSchema.pick({
  total_investment: true,
  current_value: true,
  currency: true,
});
// `z.input`, não `z.infer` (= `z.output`) — ver o comentário equivalente em
// `EditEnterpriseOverviewCard.tsx` (`currency` tem `.default()`).
type FinanceFormValues = z.input<typeof FinanceSchema>;

type Props = {
  data: EnterpriseFullResponseDTO;
  onSave: (newData: EnterpriseFullResponseDTO) => void;
  onCancel: () => void;
};

/**
 * A mesma `FinancialSection` (RHF+Zod) da criação. Construtora/arquiteto saíram daqui a
 * 2026-09-16 — passaram para o Overview, junto do resto dos dados descritivos do projeto.
 */
const EditFinancialCard: FC<Props> = ({ data, onSave, onCancel }) => {
  const { t } = useTranslation();

  const methods = useForm<FinanceFormValues>({
    resolver: zodResolver(FinanceSchema),
    mode: "onChange",
    defaultValues: {
      total_investment: data.totalInvestment ?? undefined,
      current_value: data.currentValue ?? undefined,
      currency: data.currency ?? "EUR",
    },
  });

  const { handleSubmit, formState: { isSubmitting, isValid } } = methods;

  const onSubmit = async (values: FinanceFormValues) => {
    try {
      const response = await updateEnterpriseFinance(data.id, {
        totalInvestment: values.total_investment ?? null,
        currentValue: values.current_value ?? null,
        currency: values.currency,
      });
      onSave({ ...data, ...response });
      notificationService.success(t('enterpriseEdit.financialUpdated'));
    } catch (error) {
      ErrorHandler.handle(error);
    }
  };

  return (
    <FormProvider {...methods}>
      <form onSubmit={handleSubmit(onSubmit)}>
        <FinancialSection />

        <Space style={{ display: "flex", justifyContent: "flex-end", marginTop: "13.6px" }}>
          <Button onClick={onCancel} disabled={isSubmitting}>
            {t('common.cancel')}
          </Button>
          <Button type="primary" htmlType="submit" loading={isSubmitting} disabled={!isValid}>
            {t('common.saveChanges')}
          </Button>
        </Space>
      </form>
    </FormProvider>
  );
};

export default EditFinancialCard;
