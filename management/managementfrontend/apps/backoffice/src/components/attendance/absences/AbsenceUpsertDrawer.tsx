import { useEffect, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Alert, Button, DatePicker, Drawer, Input, Segmented, Select, Space, Typography } from "antd";
import { useTranslation } from "react-i18next";
import dayjs from "dayjs";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { createAbsence, getVacationBalance, updateAbsence } from "@/services/attendanceService";
import { listAssignableUsers } from "@/services/profileService";
import type { AssignableEmployee } from "@/services/profileService";
import { ABSENCE_TYPE_LABEL } from "@/types/attendance";
import type { Absence, AbsenceType, VacationBalance } from "@/types/attendance";
import Label from "@/components/common/Label";
import { AbsenceFormSchema } from "./absenceFormSchema";
import type { AbsenceForm } from "./absenceFormSchema";

const { Text } = Typography;

interface Props {
  open: boolean;
  /** null = marcar uma ausência nova. */
  absence: Absence | null;
  /** Dia por onde começar, quando se marca a partir de uma data já escolhida. */
  defaultDay?: string;
  onClose: () => void;
  onSaved: () => void;
}

const TYPE_OPTIONS = (Object.keys(ABSENCE_TYPE_LABEL) as AbsenceType[]).map((type) => ({
  value: type,
  label: ABSENCE_TYPE_LABEL[type],
}));

function defaultsFor(absence: Absence | null, defaultDay: string): AbsenceForm {
  return {
    profileId: absence?.profileId ?? "",
    type: absence?.type ?? "VACATION",
    startsOn: absence?.startsOn ?? defaultDay,
    endsOn: absence?.endsOn ?? defaultDay,
    halfDay: absence?.halfDay ?? "NONE",
    note: absence?.note ?? "",
  };
}

/**
 * Marcar ou corrigir uma ausência. Nasce sempre **pendente** — aprovar é um passo
 * à parte, para ficar registado quem decidiu e quando.
 *
 * Em férias mostra-se o saldo do funcionário escolhido, porque é a pergunta que
 * se faz a seguir ("tem dias para isto?") e não se deve ter de ir a outro ecrã.
 */
export function AbsenceUpsertDrawer({ open, absence, defaultDay, onClose, onSaved }: Props) {
  const { t } = useTranslation();
  const isEdit = !!absence;
  const today = dayjs().format("YYYY-MM-DD");
  const [employees, setEmployees] = useState<AssignableEmployee[]>([]);
  const [balance, setBalance] = useState<VacationBalance | null>(null);

  const {
    control,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isValid, isSubmitting },
  } = useForm<AbsenceForm>({
    resolver: zodResolver(AbsenceFormSchema),
    mode: "onChange",
    defaultValues: defaultsFor(absence, defaultDay ?? today),
  });

  useEffect(() => {
    if (open) reset(defaultsFor(absence, defaultDay ?? today));
  }, [open, absence, defaultDay, today, reset]);

  useEffect(() => {
    if (open) listAssignableUsers().then(setEmployees).catch(ErrorHandler.handle);
  }, [open]);

  const profileId = watch("profileId");
  const type = watch("type");
  const startsOn = watch("startsOn");
  const endsOn = watch("endsOn");
  const singleDay = !!startsOn && startsOn === endsOn;

  // O saldo é por funcionário e por ano — segue a data de início, não o ano de hoje.
  useEffect(() => {
    if (!open || !profileId || type !== "VACATION") {
      setBalance(null);
      return;
    }
    const year = startsOn ? Number(startsOn.slice(0, 4)) : undefined;
    getVacationBalance(profileId, year).then(setBalance).catch(() => setBalance(null));
  }, [open, profileId, type, startsOn]);

  const onSubmit = handleSubmit(async (values) => {
    const dto = {
      profileId: values.profileId,
      type: values.type as AbsenceType,
      startsOn: values.startsOn,
      endsOn: values.endsOn,
      halfDay: singleDay ? values.halfDay : ("NONE" as const),
      note: values.note.trim() || null,
    };
    try {
      if (absence) {
        await updateAbsence(absence.id, dto);
        notificationService.success("Ausência atualizada");
      } else {
        await createAbsence(dto);
        notificationService.success("Ausência marcada", "Fica pendente até ser aprovada.");
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
      title={isEdit ? "Editar ausência" : "Registar ausência"}
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
                disabled={isEdit}
                style={{ width: "100%" }}
                placeholder="Escolher funcionário"
                options={employees.map((employee) => ({ value: employee.id, label: employee.name }))}
              />
            )}
          />
          {fieldError(errors.profileId?.message)}
          {isEdit && (
            <p className="ind-card-meta" style={{ marginTop: 6 }}>
              A ausência não muda de pessoa — para isso, elimine esta e marque outra.
            </p>
          )}
        </div>

        <div>
          <Label required>Tipo</Label>
          <Controller
            name="type"
            control={control}
            render={({ field }) => <Select {...field} style={{ width: "100%" }} options={TYPE_OPTIONS} />}
          />
        </div>

        {type === "VACATION" && balance && (
          <Alert
            type={balance.available > 0 ? "info" : "warning"}
            showIcon
            message={`Saldo de ${balance.year}: ${balance.available} dia(s) disponíveis`}
            description={`${balance.entitled} a que tem direito · ${balance.taken} gozados · ${balance.pending} por aprovar. Contados em dias úteis do horário, já sem feriados.`}
          />
        )}

        <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
          <div>
            <Label required hasError={!!errors.startsOn}>De</Label>
            <Controller
              name="startsOn"
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
            {fieldError(errors.startsOn?.message)}
          </div>

          <div>
            <Label required hasError={!!errors.endsOn}>Até</Label>
            <Controller
              name="endsOn"
              control={control}
              render={({ field }) => (
                <DatePicker
                  style={{ width: "100%" }}
                  format="DD/MM/YYYY"
                  allowClear={false}
                  value={field.value ? dayjs(field.value) : null}
                  disabledDate={(date) => !!startsOn && date.isBefore(dayjs(startsOn), "day")}
                  onChange={(date) => field.onChange(date ? date.format("YYYY-MM-DD") : "")}
                />
              )}
            />
            {fieldError(errors.endsOn?.message)}
          </div>
        </div>

        {singleDay && (
          <div>
            <Label>Meio dia</Label>
            <Controller
              name="halfDay"
              control={control}
              render={({ field }) => (
                <Segmented
                  value={field.value}
                  onChange={field.onChange}
                  options={[
                    { label: "Dia inteiro", value: "NONE" },
                    { label: "Manhã", value: "MORNING" },
                    { label: "Tarde", value: "AFTERNOON" },
                  ]}
                />
              )}
            />
          </div>
        )}

        <div>
          <Label hasError={!!errors.note}>Nota</Label>
          <Controller
            name="note"
            control={control}
            render={({ field }) => <Input.TextArea {...field} rows={2} maxLength={500} />}
          />
          {fieldError(errors.note?.message)}
        </div>

        {!isEdit && (
          <p className="ind-card-meta" style={{ margin: 0 }}>
            O justificativo anexa-se depois, no detalhe da ausência.
          </p>
        )}
      </div>
    </Drawer>
  );
}
