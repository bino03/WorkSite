import { useEffect } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Alert, Button, DatePicker, Drawer, InputNumber, Select, Space, Typography } from "antd";
import { useTranslation } from "react-i18next";
import dayjs from "dayjs";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { createEmployment } from "@/services/attendanceService";
import type { WorkSchedule } from "@/types/attendance";
import Label from "@/components/common/Label";
import { EmploymentCreateFormSchema } from "./employmentFormSchemas";
import type { EmploymentCreateForm } from "./employmentFormSchemas";

const { Text } = Typography;

interface Props {
  open: boolean;
  profileId: string;
  /** Os horários vivos, carregados pelo cartão — aqui só se escolhe. */
  schedules: WorkSchedule[];
  onClose: () => void;
  onSaved: () => void;
}

const EMPTY: EmploymentCreateForm = {
  hiredAt: dayjs().format("YYYY-MM-DD"),
  endedAt: "",
  workScheduleId: "",
  vacationDaysPerYear: 22,
};

/**
 * Criar a ficha de emprego de um funcionário: a admissão e o **primeiro período**
 * de condições (horário + dias de férias), que o backend exige em conjunto. Daí
 * em diante as condições mudam acrescentando períodos, nunca editando o atual.
 */
export function EmploymentCreateDrawer({ open, profileId, schedules, onClose, onSaved }: Props) {
  const { t } = useTranslation();

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors, isValid, isSubmitting },
  } = useForm<EmploymentCreateForm>({
    resolver: zodResolver(EmploymentCreateFormSchema),
    mode: "onChange",
    defaultValues: EMPTY,
  });

  useEffect(() => {
    if (open) reset(EMPTY);
  }, [open, reset]);

  const onSubmit = handleSubmit(async (values) => {
    try {
      await createEmployment({
        profileId,
        hiredAt: values.hiredAt,
        endedAt: values.endedAt || null,
        workScheduleId: values.workScheduleId,
        vacationDaysPerYear: values.vacationDaysPerYear,
      });
      notificationService.success("Ficha de emprego criada");
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
      title="Criar ficha de emprego"
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
            {t("common.create")}
          </Button>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
        <p className="ind-card-meta" style={{ margin: 0 }}>
          É o que faz as horas, as faltas e o saldo de férias deste funcionário passarem a ser
          contados.
        </p>

        {schedules.length === 0 && (
          <Alert
            type="warning"
            showIcon
            message="Não há horários criados"
            description="Crie um horário em Configuração → Horários antes de abrir a ficha: sem horário não há horas esperadas nem saldo de férias."
          />
        )}

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
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

        <div>
          <Label required hasError={!!errors.workScheduleId}>Horário</Label>
          <Controller
            name="workScheduleId"
            control={control}
            render={({ field }) => (
              <Select
                {...field}
                style={{ width: "100%" }}
                placeholder="Escolher horário"
                options={schedules.map((schedule) => ({ value: schedule.id, label: schedule.name }))}
              />
            )}
          />
          {fieldError(errors.workScheduleId?.message)}
        </div>

        <div>
          <Label required hasError={!!errors.vacationDaysPerYear}>Dias de férias por ano</Label>
          <Controller
            name="vacationDaysPerYear"
            control={control}
            render={({ field }) => (
              <InputNumber
                value={field.value}
                onChange={(value) => field.onChange(value ?? 0)}
                min={0}
                max={365}
                style={{ width: "100%" }}
              />
            )}
          />
          {fieldError(errors.vacationDaysPerYear?.message)}
          <p className="ind-card-meta" style={{ marginTop: 6 }}>
            Contados em <strong>dias úteis</strong> do horário, já sem feriados.
          </p>
        </div>
      </div>
    </Drawer>
  );
}
