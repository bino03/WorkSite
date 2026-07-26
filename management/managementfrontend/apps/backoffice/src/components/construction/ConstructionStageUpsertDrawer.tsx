import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Drawer, Input, Button, Space, message, Typography } from "antd";
import { Layers } from "lucide-react";
import {
  stageFormSchema,
  type StageFormValues,
} from "@/components/construction/constructionFormSchemas";
import {
  createStage,
  updateStage,
  getStageById,
} from "@/services/constructionService";
import type { ConstructionStageResponseDTO } from "@/types/construction";

const { TextArea } = Input;
const { Text } = Typography;

interface ConstructionStageUpsertDrawerProps {
  open: boolean;
  onClose: () => void;
  enterpriseId: string;
  stageId?: string | null;
  onSaved: (stage: ConstructionStageResponseDTO) => void;
}

const defaultValues: StageFormValues = { name: "", description: "" };

export default function ConstructionStageUpsertDrawer({
  open,
  onClose,
  enterpriseId,
  stageId,
  onSaved,
}: ConstructionStageUpsertDrawerProps) {
  const { t } = useTranslation();
  const isEditing = Boolean(stageId);

  const {
    control,
    handleSubmit,
    reset,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<StageFormValues>({
    resolver: zodResolver(stageFormSchema),
    mode: "onChange",
    defaultValues,
  });

  useEffect(() => {
    if (!open) return;
    if (stageId) {
      getStageById(stageId).then((stage) => {
        reset({ name: stage.name, description: stage.description ?? "" });
      });
    } else {
      reset(defaultValues);
      setTimeout(() => setFocus("name"), 0);
    }
  }, [open, stageId, reset, setFocus]);

  const onSubmit = async (values: StageFormValues) => {
    const dto = { name: values.name, description: values.description || undefined, enterpriseId };
    if (stageId) {
      const updated = await updateStage(stageId, dto);
      message.success(t("constructionStages.updated"));
      onSaved(updated);
      onClose();
    } else {
      const created = await createStage(dto);
      message.success(t("constructionStages.created"));
      onSaved(created);
      onClose();
    }
  };

  return (
    <Drawer
      width={600}
      title={
        <div style={{ display: "flex", alignItems: "center", gap: 8 }}>
          <Layers size={18} />
          <span>{isEditing ? t("constructionStages.editTitle") : t("constructionStages.createTitle")}</span>
        </div>
      }
      open={open}
      onClose={onClose}
      destroyOnClose
      footer={
        <Space style={{ width: "100%", justifyContent: "flex-end" }}>
          <Button onClick={onClose} disabled={isSubmitting}>
            {t("common.cancel")}
          </Button>
          <Button type="primary" loading={isSubmitting} onClick={handleSubmit(onSubmit)}>
            {isEditing ? t("common.save") : t("common.create")}
          </Button>
        </Space>
      }
    >
      <div style={{ display: "flex", flexDirection: "column", gap: 16 }}>
        <div>
          <Text strong>{t("constructionStages.fields.name")}</Text>
          <Controller
            name="name"
            control={control}
            render={({ field }) => (
              <Input {...field} size="large" placeholder={t("constructionStages.fields.namePlaceholder")} style={{ marginTop: 6 }} />
            )}
          />
          {errors.name && (
            <Text type="danger" style={{ fontSize: 12 }}>
              {t(errors.name.message as string)}
            </Text>
          )}
        </div>

        <div>
          <Text strong>{t("constructionStages.fields.description")}</Text>
          <Controller
            name="description"
            control={control}
            render={({ field }) => (
              <TextArea {...field} autoSize={{ minRows: 3 }} placeholder={t("constructionStages.fields.descriptionPlaceholder")} style={{ marginTop: 6 }} />
            )}
          />
        </div>
      </div>
    </Drawer>
  );
}
