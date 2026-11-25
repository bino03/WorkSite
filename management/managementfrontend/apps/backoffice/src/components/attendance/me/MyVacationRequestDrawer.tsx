import { useEffect, useState } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Alert, Button, DatePicker, Drawer, Input, Segmented, Space, Typography } from "antd";
import { useTranslation } from "react-i18next";
import dayjs from "dayjs";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { previewVacationRequest, requestVacation } from "@/services/myAttendanceService";
import type { VacationRequestPreview } from "@/types/attendance";
import Label from "@/components/common/Label";
import { MyVacationRequestSchema } from "./myVacationRequestSchema";
import type { MyVacationRequestForm } from "./myVacationRequestSchema";

const { Text } = Typography;

interface Props {
  open: boolean;
  onClose: () => void;
  onSaved: () => void;
}

const formatDays = (days: number) => (Number.isInteger(days) ? String(days) : days.toFixed(1).replace(".", ","));

function defaults(): MyVacationRequestForm {
  const tomorrow = dayjs().add(1, "day").format("YYYY-MM-DD");
  return { startsOn: tomorrow, endsOn: tomorrow, halfDay: "NONE", note: "" };
}

/**
 * O funcionário pede férias para si (fase 5b). O pedido fica **pendente** até o escritório
 * decidir, e o funcionário é avisado no sino quando for aprovado ou recusado.
 *
 * O saldo **avisa, não bloqueia** (decidido a 2026-11-23): mostra quantos dias úteis o
 * pedido gasta, já sem fins de semana nem feriados, contra o disponível do ano.
 */
export function MyVacationRequestDrawer({ open, onClose, onSaved }: Props) {
  const { t } = useTranslation();
  const [preview, setPreview] = useState<VacationRequestPreview | null>(null);

  const {
    control,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isValid, isSubmitting },
  } = useForm<MyVacationRequestForm>({
    resolver: zodResolver(MyVacationRequestSchema),
    mode: "onChange",
    defaultValues: defaults(),
  });

  useEffect(() => {
    if (open) reset(defaults());
  }, [open, reset]);

  const startsOn = watch("startsOn");
  const endsOn = watch("endsOn");
  const halfDay = watch("halfDay");
  const singleDay = !!startsOn && startsOn === endsOn;
  const effectiveHalfDay = singleDay ? halfDay : "NONE";

  useEffect(() => {
    if (!open || !startsOn || !endsOn || endsOn < startsOn) {
      setPreview(null);
      return;
    }
    let cancelled = false;
    previewVacationRequest(startsOn, endsOn, effectiveHalfDay)
      .then((next) => !cancelled && setPreview(next))
      .catch(() => !cancelled && setPreview(null));
    return () => {
      cancelled = true;
    };
  }, [open, startsOn, endsOn, effectiveHalfDay]);

  const onSubmit = handleSubmit(async (values) => {
    try {
      await requestVacation({
        startsOn: values.startsOn,
        endsOn: values.endsOn,
        halfDay: effectiveHalfDay,
        note: values.note.trim() || null,
      });
      notificationService.success("Pedido enviado", "Fica pendente até o escritório decidir.");
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

  const exceeds = !!preview && preview.days > preview.available;

  return (
    <Drawer
      title="Pedir férias"
      open={open}
      onClose={onClose}
      width={520}
      destroyOnClose
      footer={
        <Space style={{ display: "flex", justifyContent: "flex-end" }}>
          <Button onClick={onClose} disabled={isSubmitting}>
            {t("common.cancel")}
          </Button>
          <Button type="primary" onClick={onSubmit} loading={isSubmitting} disabled={!isValid}>
            Enviar pedido
          </Button>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
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
                  inputReadOnly
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
                  inputReadOnly
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

        {preview && (
          <Alert
            type={exceeds ? "warning" : "info"}
            showIcon
            message={`Este pedido gasta ${formatDays(preview.days)} dia(s) úteis de ${preview.year}`}
            description={
              exceeds
                ? `Tem ${formatDays(preview.available)} disponíveis — o pedido passa o saldo em ${formatDays(
                    preview.days - preview.available
                  )}. Pode enviá-lo na mesma; o escritório decide.`
                : `Tem ${formatDays(preview.available)} disponíveis. Fins de semana e feriados não contam.`
            }
          />
        )}

        <div>
          <Label hasError={!!errors.note}>Nota para o escritório</Label>
          <Controller
            name="note"
            control={control}
            render={({ field }) => <Input.TextArea {...field} rows={2} maxLength={500} />}
          />
          {fieldError(errors.note?.message)}
        </div>
      </div>
    </Drawer>
  );
}
