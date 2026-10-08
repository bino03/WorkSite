import { useEffect } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button, DatePicker, Drawer, Space, Typography } from "antd";
import { useTranslation } from "react-i18next";
import dayjs from "dayjs";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { updateEmploymentDates } from "@/services/attendanceService";
import type { Employment } from "@/types/attendance";
import Label from "@/components/common/Label";
import { EmploymentDatesFormSchema } from "./employmentFormSchemas";
import type { EmploymentDatesForm } from "./employmentFormSchemas";

const { Text } = Typography;

interface Props {
  open: boolean;
  employment: Employment;
  onClose: () => void;
  onSaved: () => void;
}

function defaultsFor(employment: Employment): EmploymentDatesForm {
  return { hiredAt: employment.hiredAt, endedAt: employment.endedAt ?? "" };
}

/**
 * Corrigir a admissão e o fim de uma ficha. O backend recusa uma admissão que
 * deixe algum período a começar antes dela (`ATT_003`) — por isso corrigir a
 * admissão para mais tarde pode falhar, e a mensagem explica-o.
 */
export function EmploymentDatesDrawer({ open, employment, onClose, onSaved }: Props) {
  const { t } = useTranslation();

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors, isValid, isSubmitting },
  } = useForm<EmploymentDatesForm>({
    resolver: zodResolver(EmploymentDatesFormSchema),
    mode: "onChange",
    defaultValues: defaultsFor(employment),
  });

  useEffect(() => {
    if (open) reset(defaultsFor(employment));
  }, [open, employment, reset]);

  const onSubmit = handleSubmit(async (values) => {
    try {
      await updateEmploymentDates(employment.profileId, values.hiredAt, values.endedAt || null);
      notificationService.success("Datas atualizadas");
      onSaved();
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
      title="Datas de emprego"
      open={open}
      onClose={onClose}
      width={600}
      destroyOnClose
      footer={
        <Space style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button onClick={onClose} disabled={isSubmitting}>
            {t("common.cancel")}
          </Button>
          <Button type="primary" onClick={onSubmit} loading={isSubmitting} disabled={!isValid}>
            {t("common.save")}
          </Button>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
        <div>
          <Label required hasError={!!errors.hiredAt}>Admissão</Label>
          <Controller
            name="hiredAt"
            control={control}
            render={({ field }) => (
              <DatePicker
                style={{ width: "100%" }}
                format="DD/MM/YYYY"
                value={field.value ? dayjs(field.value) : null}
                onChange={(date) => field.onChange(date ? date.format("YYYY-MM-DD") : "")}
              />
            )}
          />
          {fieldError(errors.hiredAt?.message)}
          <p className="ind-card-meta" style={{ marginTop: 6 }}>
            Não pode ficar depois do início do período mais antigo — nesse caso acrescente primeiro
            um período com a data certa.
          </p>
        </div>

        <div>
          <Label hasError={!!errors.endedAt}>Fim</Label>
          <Controller
            name="endedAt"
            control={control}
            render={({ field }) => (
              <DatePicker
                style={{ width: "100%" }}
                format="DD/MM/YYYY"
                placeholder="Ainda ao serviço"
                value={field.value ? dayjs(field.value) : null}
                onChange={(date) => field.onChange(date ? date.format("YYYY-MM-DD") : "")}
              />
            )}
          />
          {fieldError(errors.endedAt?.message)}
        </div>
      </div>
    </Drawer>
  );
}
