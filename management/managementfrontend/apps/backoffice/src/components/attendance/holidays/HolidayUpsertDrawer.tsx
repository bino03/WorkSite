import { useEffect } from "react";
import { Controller, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Button, DatePicker, Drawer, Input, Segmented, Space, Typography } from "antd";
import { useTranslation } from "react-i18next";
import dayjs from "dayjs";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { createHoliday, updateHoliday } from "@/services/attendanceService";
import type { Holiday, HolidayScope } from "@/types/attendance";
import Label from "@/components/common/Label";
import { HolidayFormSchema } from "./holidayFormSchema";
import type { HolidayForm } from "./holidayFormSchema";

const { Text } = Typography;

interface Props {
  open: boolean;
  /** null = criar um feriado novo. */
  holiday: Holiday | null;
  /** Ano aberto na página: um feriado novo começa nele, não no ano de hoje. */
  defaultYear: number;
  onClose: () => void;
  onSaved: () => void;
}

function defaultsFor(holiday: Holiday | null, defaultYear: number): HolidayForm {
  return {
    date: holiday?.date ?? `${defaultYear}-01-01`,
    name: holiday?.name ?? "",
    scope: holiday?.scope ?? "NATIONAL",
    municipality: holiday?.municipality ?? "",
  };
}

/**
 * Criar ou editar um feriado. Um feriado nacional não leva concelho e um
 * municipal tem de o levar (`ABS_003`/`ABS_004`) — o campo aparece e desaparece
 * com o âmbito, e o que lá estiver não é enviado em modo nacional.
 */
export function HolidayUpsertDrawer({ open, holiday, defaultYear, onClose, onSaved }: Props) {
  const { t } = useTranslation();
  const isEdit = !!holiday;

  const {
    control,
    handleSubmit,
    reset,
    watch,
    formState: { errors, isValid, isSubmitting },
  } = useForm<HolidayForm>({
    resolver: zodResolver(HolidayFormSchema),
    mode: "onChange",
    defaultValues: defaultsFor(holiday, defaultYear),
  });

  useEffect(() => {
    if (open) reset(defaultsFor(holiday, defaultYear));
  }, [open, holiday, defaultYear, reset]);

  const scope = watch("scope");

  const onSubmit = handleSubmit(async (values) => {
    const dto = {
      date: values.date,
      name: values.name.trim(),
      scope: values.scope as HolidayScope,
      municipality: values.scope === "MUNICIPAL" ? values.municipality.trim() : null,
    };
    try {
      if (holiday) {
        await updateHoliday(holiday.id, dto);
        notificationService.success("Feriado atualizado");
      } else {
        await createHoliday(dto);
        notificationService.success("Feriado criado");
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
      title={isEdit ? "Editar feriado" : "Novo feriado"}
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
          <Label required hasError={!!errors.date}>Data</Label>
          <Controller
            name="date"
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
          {fieldError(errors.date?.message)}
        </div>

        <div>
          <Label required hasError={!!errors.name}>Nome</Label>
          <Controller
            name="name"
            control={control}
            render={({ field }) => <Input {...field} placeholder="ex.: Dia de Portugal" maxLength={120} />}
          />
          {fieldError(errors.name?.message)}
        </div>

        <div>
          <Label required>Âmbito</Label>
          <Controller
            name="scope"
            control={control}
            render={({ field }) => (
              <Segmented
                value={field.value}
                onChange={field.onChange}
                options={[
                  { label: "Nacional", value: "NATIONAL" },
                  { label: "Municipal", value: "MUNICIPAL" },
                ]}
              />
            )}
          />
        </div>

        {scope === "MUNICIPAL" && (
          <div>
            <Label required hasError={!!errors.municipality}>Concelho</Label>
            <Controller
              name="municipality"
              control={control}
              render={({ field }) => <Input {...field} placeholder="ex.: Vila Real" maxLength={120} />}
            />
            {fieldError(errors.municipality?.message)}
            <p className="ind-card-meta" style={{ marginTop: 6 }}>
              O concelho fica registado, mas o feriado conta para toda a equipa — o cálculo do mês
              ainda não distingue por obra.
            </p>
          </div>
        )}
      </div>
    </Drawer>
  );
}
