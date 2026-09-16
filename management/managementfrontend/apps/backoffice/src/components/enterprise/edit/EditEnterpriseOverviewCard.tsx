import type { FC } from "react";
import { Button, Space } from "antd";
import { useTranslation } from "react-i18next";
import { FormProvider, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import type { z } from "zod";

import BasicInfoSection from "@/components/enterprise/create/BasicInfoSection";
import { EnterpriseFormSchema } from "@/components/enterprise/create/enterpriseFormSchema";
import { updateEnterpriseOverview } from "@/services/enterpriseService";
import type { EnterpriseFullResponseDTO } from "@/services/enterpriseService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";

const OverviewSchema = EnterpriseFormSchema.pick({
  name: true,
  internal_Reference: true,
  type: true,
  status: true,
  slug: true,
  is_test: true,
  construction_company: true,
  architect: true,
});
// `z.input`, não `z.infer` (= `z.output`): campos com `.default()` (`is_test`) ficam opcionais
// do lado de entrada, que é o que o `zodResolver` espera receber — casar com `z.output` aqui é o
// mesmo desalinhamento de tipos que já existe no `CreateEnterpriseDrawer.tsx` (26 erros base).
type OverviewFormValues = z.input<typeof OverviewSchema>;

type Props = {
  data: EnterpriseFullResponseDTO;
  onSave: (newData: EnterpriseFullResponseDTO) => void;
  onCancel: () => void;
};

/** A mesma `BasicInfoSection` (RHF+Zod) da criação — o único empreendimento é o mesmo formulário. */
const EditEnterpriseOverviewCard: FC<Props> = ({ data, onSave, onCancel }) => {
  const { t } = useTranslation();

  const methods = useForm<OverviewFormValues>({
    resolver: zodResolver(OverviewSchema),
    mode: "onChange",
    defaultValues: {
      name: data.name ?? "",
      internal_Reference: data.internalReference ?? "",
      type: data.type,
      status: data.status,
      slug: data.slug ?? "",
      is_test: data.isTest ?? false,
      construction_company: data.constructionCompany ?? "",
      architect: data.architect ?? "",
    },
  });

  const { handleSubmit, formState: { isSubmitting, isValid } } = methods;

  const onSubmit = async (values: OverviewFormValues) => {
    try {
      const response = await updateEnterpriseOverview(data.id, {
        name: values.name,
        internalReference: values.internal_Reference || null,
        type: values.type as string,
        status: values.status as string,
        slug: values.slug?.trim() || null,
        isTest: values.is_test,
        constructionCompany: values.construction_company || null,
        architect: values.architect || null,
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
        <BasicInfoSection showActiveToggle={false} />

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

export default EditEnterpriseOverviewCard;
