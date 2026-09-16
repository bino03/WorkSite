import type { FC } from "react";
import { Button, Space } from "antd";
import { useTranslation } from "react-i18next";
import { FormProvider, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { z } from "zod";

import TimelineMetricsSection from "@/components/enterprise/create/TimelineMetricsSection";
import { EnterpriseFormSchema } from "@/components/enterprise/create/enterpriseFormSchema";
import { updateEnterpriseDatesAreas } from "@/services/enterpriseService";
import type { EnterpriseFullResponseDTO } from "@/services/enterpriseService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";

const DatesAndAreasSchema = EnterpriseFormSchema.pick({
  start_date: true,
  completion_date: true,
  total_area: true,
  land_area: true,
  total_units: true,
});
type DatesAndAreasFormValues = z.infer<typeof DatesAndAreasSchema>;

type Props = {
  data: EnterpriseFullResponseDTO;
  onSave: (newData: EnterpriseFullResponseDTO) => void;
  onCancel: () => void;
};

/**
 * A mesma `TimelineMetricsSection` (RHF+Zod) da criação. `total_units` fica desativado — o
 * `PATCH .../dates-areas` não o aceita, é derivado (ver `TimelineMetricsSection`).
 */
const EditDatesAndAreasCard: FC<Props> = ({ data, onSave, onCancel }) => {
  const { t } = useTranslation();

  const methods = useForm<DatesAndAreasFormValues>({
    resolver: zodResolver(DatesAndAreasSchema),
    mode: "onChange",
    defaultValues: {
      start_date: data.startDate ?? "",
      completion_date: data.completionDate ?? "",
      total_area: data.totalArea ?? undefined,
      land_area: data.landArea ?? undefined,
      total_units: data.totalUnits ?? undefined,
    },
  });

  const { handleSubmit, formState: { isSubmitting, isValid } } = methods;

  const onSubmit = async (values: DatesAndAreasFormValues) => {
    try {
      const response = await updateEnterpriseDatesAreas(data.id, {
        startDate: values.start_date || null,
        completionDate: values.completion_date || null,
        totalArea: values.total_area ?? null,
        landArea: values.land_area ?? null,
      });
      onSave({ ...data, ...response });
      notificationService.success(t('enterprises.updated'));
    } catch (error) {
      ErrorHandler.handle(error);
    }
  };

  return (
    <FormProvider {...methods}>
      <form onSubmit={handleSubmit(onSubmit)}>
        <TimelineMetricsSection totalUnitsReadOnly />

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

export default EditDatesAndAreasCard;
