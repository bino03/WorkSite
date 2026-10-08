import { useEffect } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Alert, Button, DatePicker, Drawer, InputNumber, Select, Space, Typography } from "antd";
import { useTranslation } from "react-i18next";
import dayjs from "dayjs";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { addEmploymentTerm } from "@/services/attendanceService";
import type { Employment, WorkSchedule } from "@/types/attendance";
import Label from "@/components/common/Label";
import { EmploymentTermFormSchema } from "./employmentFormSchemas";
import type { EmploymentTermForm } from "./employmentFormSchemas";

const { Text } = Typography;

interface Props {
  open: boolean;
  employment: Employment;
  schedules: WorkSchedule[];
  onClose: () => void;
  onSaved: () => void;
}

/**
 * Acrescentar um período de condições. **Não há editar**: o período em vigor
 * fecha-se no dia anterior e abre-se outro, para os meses já passados continuarem
 * a ser calculados com o que estava em vigor na altura.
 *
 * O backend exige `validFrom` depois do início do período atual (`ATT_004`) e não
 * antes da admissão (`ATT_003`); as duas datas são travadas aqui no `disabledDate`,
 * para o utilizador não descobrir o limite só ao submeter.
 */
/** O primeiro dia aceitável: o dia seguinte ao início do período em vigor. */
function earliestFor(employment: Employment) {
  const current = employment.currentTerm;
  return current ? dayjs(current.validFrom).add(1, "day") : dayjs(employment.hiredAt);
}

function defaultsFor(employment: Employment): EmploymentTermForm {
  const current = employment.currentTerm;
  return {
    workScheduleId: current?.workScheduleId ?? "",
    vacationDaysPerYear: current?.vacationDaysPerYear ?? 22,
    validFrom: earliestFor(employment).format("YYYY-MM-DD"),
  };
}

export function EmploymentTermDrawer({ open, employment, schedules, onClose, onSaved }: Props) {
  const { t } = useTranslation();
  const current = employment.currentTerm;
  const earliest = earliestFor(employment);

  const {
    control,
    handleSubmit,
    reset,
    formState: { errors, isValid, isSubmitting },
  } = useForm<EmploymentTermForm>({
    resolver: zodResolver(EmploymentTermFormSchema),
    mode: "onChange",
    defaultValues: defaultsFor(employment),
  });

  useEffect(() => {
    if (open) reset(defaultsFor(employment));
  }, [open, employment, reset]);

  const onSubmit = handleSubmit(async (values) => {
    try {
      await addEmploymentTerm(employment.profileId, values);
      notificationService.success("Período acrescentado");
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
      title="Novo período de condições"
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
        <Alert
          type="info"
          showIcon
          message="O período em vigor fecha-se no dia anterior a esta data. O que já foi calculado com ele não muda."
        />

        <div>
          <Label required hasError={!!errors.validFrom}>Em vigor desde</Label>
          <Controller
            name="validFrom"
            control={control}
            render={({ field }) => (
              <DatePicker
                style={{ width: "100%" }}
                format="DD/MM/YYYY"
                value={field.value ? dayjs(field.value) : null}
                disabledDate={(date) => date.isBefore(earliest, "day")}
                onChange={(date) => field.onChange(date ? date.format("YYYY-MM-DD") : "")}
              />
            )}
          />
          {fieldError(errors.validFrom?.message)}
          {current && (
            <p className="ind-card-meta" style={{ marginTop: 6 }}>
              O período atual começou a {dayjs(current.validFrom).format("DD/MM/YYYY")} — o novo tem de
              começar depois disso.
            </p>
          )}
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
        </div>
      </div>
    </Drawer>
  );
}
