import { useEffect } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Alert, Button, Drawer, Input, InputNumber, Space, Switch, Typography } from "antd";
import { useTranslation } from "react-i18next";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { createWorkSchedule, updateWorkSchedule } from "@/services/attendanceService";
import { WEEKDAYS, WEEKDAY_LABEL } from "@/types/attendance";
import type { Weekday, WorkSchedule } from "@/types/attendance";
import { formatMinutes } from "@/utils/formatters";
import Label from "@/components/common/Label";
import { WorkScheduleFormSchema, toMinutes } from "./workScheduleFormSchema";
import type { WorkScheduleForm } from "./workScheduleFormSchema";

const { Text } = Typography;

interface Props {
  open: boolean;
  /** null = criar um horário novo. */
  schedule: WorkSchedule | null;
  onClose: () => void;
  onSaved: () => void;
}

const WORKING_WEEK: Weekday[] = ["MONDAY", "TUESDAY", "WEDNESDAY", "THURSDAY", "FRIDAY"];

function defaultsFor(schedule: WorkSchedule | null): WorkScheduleForm {
  return {
    name: schedule?.name ?? "",
    notes: schedule?.notes ?? "",
    days: WEEKDAYS.map((weekday) => {
      const day = schedule?.days.find((candidate) => candidate.weekday === weekday);
      if (day) {
        return { weekday, enabled: true, startTime: day.startTime, endTime: day.endTime, breakMinutes: day.breakMinutes };
      }
      // Um horário novo começa como 08–17 c/ 1h, de segunda a sexta: é o caso comum.
      return {
        weekday,
        enabled: !schedule && WORKING_WEEK.includes(weekday),
        startTime: "08:00",
        endTime: "17:00",
        breakMinutes: 60,
      };
    }),
  };
}

/**
 * Criar ou editar um horário. Tem as 7 linhas da semana; um dia desligado não é
 * dia de trabalho. **Um horário já atribuído não se edita** (`SCHED_007`): os
 * relatórios de meses passados dependem dele — cria-se outro e reatribui-se.
 */
export function WorkScheduleUpsertDrawer({ open, schedule, onClose, onSaved }: Props) {
  const { t } = useTranslation();
  const isEdit = !!schedule;

  const {
    control,
    handleSubmit,
    reset,
    watch,
    setValue,
    formState: { errors, isValid, isSubmitting },
  } = useForm<WorkScheduleForm>({
    resolver: zodResolver(WorkScheduleFormSchema),
    mode: "onChange",
    defaultValues: defaultsFor(schedule),
  });

  useEffect(() => {
    if (open) reset(defaultsFor(schedule));
  }, [open, schedule, reset]);

  const days = watch("days");
  const weeklyMinutes = days
    .filter((day) => day.enabled)
    .reduce((total, day) => {
      const span = toMinutes(day.endTime) - toMinutes(day.startTime);
      return span > 0 ? total + Math.max(0, span - day.breakMinutes) : total;
    }, 0);

  /** Copia o horário de segunda para os outros dias ligados — poupa 4 vezes a mesma hora. */
  const copyMondayToEnabled = () => {
    const monday = days[0];
    days.forEach((day, index) => {
      if (index === 0 || !day.enabled) return;
      setValue(`days.${index}.startTime`, monday.startTime, { shouldValidate: true });
      setValue(`days.${index}.endTime`, monday.endTime, { shouldValidate: true });
      setValue(`days.${index}.breakMinutes`, monday.breakMinutes, { shouldValidate: true });
    });
  };

  const onSubmit = handleSubmit(async (values) => {
    const dto = {
      name: values.name.trim(),
      notes: values.notes.trim() || null,
      days: values.days
        .filter((day) => day.enabled)
        .map((day) => ({
          weekday: day.weekday as Weekday,
          startTime: day.startTime,
          endTime: day.endTime,
          breakMinutes: day.breakMinutes,
        })),
    };
    try {
      if (schedule) {
        await updateWorkSchedule(schedule.id, dto);
        notificationService.success("Horário atualizado");
      } else {
        await createWorkSchedule(dto);
        notificationService.success("Horário criado");
      }
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
      title={isEdit ? "Editar horário" : "Novo horário"}
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
            {isEdit ? t("common.save") : t("common.create")}
          </Button>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
        {isEdit && (
          <Alert
            type="info"
            showIcon
            message="Se este horário já esteve atribuído a alguém, não pode ser alterado — os meses passados foram calculados com ele. Nesse caso crie um horário novo e atribua-o a partir de uma data."
          />
        )}

        <div>
          <Label required hasError={!!errors.name}>Nome</Label>
          <Controller
            name="name"
            control={control}
            render={({ field }) => <Input {...field} placeholder="ex.: 08–17 c/ 1h de almoço" maxLength={120} />}
          />
          {fieldError(errors.name?.message)}
        </div>

        <div>
          <Label hasError={!!errors.notes}>Notas</Label>
          <Controller
            name="notes"
            control={control}
            render={({ field }) => <Input.TextArea {...field} rows={2} maxLength={500} />}
          />
          {fieldError(errors.notes?.message)}
        </div>

        <div>
          <div style={{ display: "flex", justifyContent: "space-between", alignItems: "baseline" }}>
            <Label required>Dias de trabalho</Label>
            <Button type="text" size="small" onClick={copyMondayToEnabled}>
              Copiar segunda para os outros dias
            </Button>
          </div>

          <div style={{ display: "flex", flexDirection: "column", gap: 6 }}>
            {WEEKDAYS.map((weekday, index) => {
              const enabled = days[index]?.enabled;
              const dayErrors = errors.days?.[index];
              return (
                <div key={weekday}>
                  <div
                    style={{
                      display: "grid",
                      gridTemplateColumns: "44px 80px 1fr 1fr 110px",
                      gap: 8,
                      alignItems: "center",
                      opacity: enabled ? 1 : 0.55,
                    }}
                  >
                    <Controller
                      name={`days.${index}.enabled`}
                      control={control}
                      render={({ field }) => (
                        <Switch
                          size="small"
                          checked={field.value}
                          onChange={field.onChange}
                          aria-label={`Trabalha à ${WEEKDAY_LABEL[weekday]}`}
                        />
                      )}
                    />
                    <span>{WEEKDAY_LABEL[weekday]}</span>
                    <Controller
                      name={`days.${index}.startTime`}
                      control={control}
                      render={({ field }) => (
                        <Input {...field} type="time" disabled={!enabled} aria-label={`Entrada à ${WEEKDAY_LABEL[weekday]}`} />
                      )}
                    />
                    <Controller
                      name={`days.${index}.endTime`}
                      control={control}
                      render={({ field }) => (
                        <Input {...field} type="time" disabled={!enabled} aria-label={`Saída à ${WEEKDAY_LABEL[weekday]}`} />
                      )}
                    />
                    <Controller
                      name={`days.${index}.breakMinutes`}
                      control={control}
                      render={({ field }) => (
                        <InputNumber
                          value={field.value}
                          onChange={(value) => field.onChange(value ?? 0)}
                          min={0}
                          max={600}
                          step={15}
                          disabled={!enabled}
                          addonAfter="min"
                          aria-label={`Pausa à ${WEEKDAY_LABEL[weekday]}`}
                          style={{ width: "100%" }}
                        />
                      )}
                    />
                  </div>
                  {enabled &&
                    fieldError(
                      dayErrors?.startTime?.message ?? dayErrors?.endTime?.message ?? dayErrors?.breakMinutes?.message
                    )}
                </div>
              );
            })}
          </div>
          {fieldError(errors.days?.root?.message ?? errors.days?.message)}
          <p className="ind-card-meta" style={{ marginTop: 10 }}>
            Total semanal, já sem as pausas: <strong>{formatMinutes(weeklyMinutes)}</strong>
          </p>
        </div>
      </div>
    </Drawer>
  );
}
