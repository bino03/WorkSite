import { useEffect, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button, DatePicker, Drawer, Input, Segmented, Select, Space, Typography } from "antd";
import { useTranslation } from "react-i18next";
import dayjs from "dayjs";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { correctTimeEntry, registerTimeEntry } from "@/services/attendanceService";
import { searchEnterprises } from "@/services/enterpriseService";
import { listAssignableUsers } from "@/services/profileService";
import type { EnterpriseOption } from "@/services/enterpriseService";
import type { AssignableEmployee } from "@/services/profileService";
import type { TimeEntry } from "@/types/attendance";
import Label from "@/components/common/Label";
import { ReasonField } from "./ui/ReasonField";
import { TimeEntryFormSchema } from "./timeEntryFormSchema";
import type { TimeEntryForm } from "./timeEntryFormSchema";

const { Text } = Typography;

interface Props {
  open: boolean;
  /** Vazio quando se regista de um sítio sem funcionário escolhido (o painel "Hoje"): aí pede-se. */
  profileId: string;
  /** O dia aberto, para uma picagem nova já nascer nele. */
  day: string;
  /** null = registar uma picagem nova; caso contrário, corrigir esta. */
  entry: TimeEntry | null;
  onClose: () => void;
  onSaved: () => void;
}

function defaultsFor(entry: TimeEntry | null, day: string, profileId: string): TimeEntryForm {
  if (!entry) {
    return { profileId, day, time: "08:00", direction: "IN", enterpriseId: "", note: "", reason: "" };
  }
  const moment = dayjs(entry.happenedAt);
  return {
    profileId: entry.profileId,
    day: entry.localDate,
    time: moment.format("HH:mm"),
    direction: entry.direction,
    enterpriseId: entry.enterpriseId ?? "",
    note: entry.note ?? "",
    reason: "",
  };
}

/**
 * Registar uma picagem à mão, ou corrigir uma existente. Corrigir **exige
 * motivo** — é o que fica no histórico de revisões; registar não, porque não há
 * nada que justificar ainda.
 *
 * O instante vai para a API como ISO com offset: o backend guarda o instante e
 * deriva o dia em Lisboa (`localDate`), por isso é o `dayjs` local que manda.
 */
export function TimeEntryUpsertDrawer({ open, profileId, day, entry, onClose, onSaved }: Props) {
  const { t } = useTranslation();
  const isEdit = !!entry;
  const needsProfile = !profileId && !entry;
  const [enterprises, setEnterprises] = useState<EnterpriseOption[]>([]);
  const [employees, setEmployees] = useState<AssignableEmployee[]>([]);

  const {
    control,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isValid, isSubmitting },
  } = useForm<TimeEntryForm>({
    resolver: zodResolver(TimeEntryFormSchema),
    mode: "onChange",
    defaultValues: defaultsFor(entry, day, profileId),
  });

  useEffect(() => {
    if (open) reset(defaultsFor(entry, day, profileId));
  }, [open, entry, day, profileId, reset]);

  useEffect(() => {
    if (open) searchEnterprises("", 50).then(setEnterprises).catch(ErrorHandler.handle);
  }, [open]);

  useEffect(() => {
    if (open && needsProfile) listAssignableUsers().then(setEmployees).catch(ErrorHandler.handle);
  }, [open, needsProfile]);

  const reason = watch("reason");
  const missingReason = isEdit && !reason.trim();

  const onSubmit = handleSubmit(async (values) => {
    const happenedAt = dayjs(`${values.day}T${values.time}`).toISOString();
    const dto = {
      profileId: values.profileId,
      enterpriseId: values.enterpriseId,
      happenedAt,
      direction: values.direction,
      note: values.note.trim() || null,
      reason: values.reason.trim() || null,
    };
    try {
      if (entry) {
        await correctTimeEntry(entry.id, dto);
        notificationService.success("Picagem corrigida");
      } else {
        await registerTimeEntry(dto);
        notificationService.success("Picagem registada");
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
      title={isEdit ? "Corrigir picagem" : "Registar picagem"}
      open={open}
      onClose={onClose}
      width={600}
      destroyOnClose
      footer={
        <Space style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button onClick={onClose} disabled={isSubmitting}>
            {t("common.cancel")}
          </Button>
          <Button
            type="primary"
            onClick={onSubmit}
            loading={isSubmitting}
            disabled={!isValid || missingReason}
          >
            {isEdit ? t("common.save") : t("common.create")}
          </Button>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
        {needsProfile && (
          <div>
            <Label required hasError={!!errors.profileId}>Funcionário</Label>
            <Controller
              name="profileId"
              control={control}
              render={({ field }) => (
                <Select
                  {...field}
                  showSearch
                  optionFilterProp="label"
                  style={{ width: "100%" }}
                  placeholder="Escolher funcionário"
                  options={employees.map((employee) => ({ value: employee.id, label: employee.name }))}
                />
              )}
            />
            {fieldError(errors.profileId?.message)}
          </div>
        )}

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
          <div>
            <Label required hasError={!!errors.day}>Dia</Label>
            <Controller
              name="day"
              control={control}
              render={({ field }) => (
                <DatePicker
                  style={{ width: "100%" }}
                  format="DD/MM/YYYY"
                  allowClear={false}
                  value={field.value ? dayjs(field.value) : null}
                  onChange={(date) => field.onChange(date ? date.format("YYYY-MM-DD") : "")}
                />
              )}
            />
            {fieldError(errors.day?.message)}
          </div>

          <div>
            <Label required hasError={!!errors.time}>Hora</Label>
            <Controller
              name="time"
              control={control}
              render={({ field }) => <Input {...field} type="time" />}
            />
            {fieldError(errors.time?.message)}
          </div>
        </div>

        <div>
          <Label required>Sentido</Label>
          <Controller
            name="direction"
            control={control}
            render={({ field }) => (
              <Segmented
                value={field.value}
                onChange={field.onChange}
                options={[
                  { label: "Entrada", value: "IN" },
                  { label: "Saída", value: "OUT" },
                ]}
              />
            )}
          />
          <p className="ind-card-meta" style={{ marginTop: 6 }}>
            O dia alterna entrada → saída → entrada: uma picagem fora de sequência é recusada.
          </p>
        </div>

        <div>
          <Label required hasError={!!errors.enterpriseId}>Obra</Label>
          <Controller
            name="enterpriseId"
            control={control}
            render={({ field }) => (
              <Select
                {...field}
                showSearch
                optionFilterProp="label"
                style={{ width: "100%" }}
                placeholder="Escolher obra"
                options={enterprises.map((enterprise) => ({ value: enterprise.id, label: enterprise.name }))}
              />
            )}
          />
          {fieldError(errors.enterpriseId?.message)}
        </div>

        <div>
          <Label hasError={!!errors.note}>Nota</Label>
          <Controller
            name="note"
            control={control}
            render={({ field }) => <Input.TextArea {...field} rows={2} maxLength={500} />}
          />
          {fieldError(errors.note?.message)}
        </div>

        {isEdit && (
          <div>
            <Label required hasError={missingReason}>Motivo da correção</Label>
            <Controller
              name="reason"
              control={control}
              render={({ field }) => <ReasonField value={field.value} onChange={field.onChange} />}
            />
            <p className="ind-card-meta" style={{ marginTop: 6 }}>
              Fica no histórico de revisões desta picagem, com o teu nome e a data.
            </p>
          </div>
        )}
      </div>
    </Drawer>
  );
}
