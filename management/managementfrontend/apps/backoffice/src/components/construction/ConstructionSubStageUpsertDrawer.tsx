import { useEffect } from "react";
import { useTranslation } from "react-i18next";
import { useForm, Controller } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { Drawer, Input, Button, Space, message, Typography } from "antd";
import { Layers } from "lucide-react";
import {
  subStageFormSchema,
  type SubStageFormValues,
} from "@/components/construction/constructionFormSchemas";
import {
  createSubStage,
  updateSubStage,
  getSubStageById,
} from "@/services/constructionService";
import type { ConstructionSubStageResponseDTO } from "@/types/construction";

const { TextArea } = Input;
const { Text } = Typography;

interface ConstructionSubStageUpsertDrawerProps {
  open: boolean;
  onClose: () => void;
  stageId: string;
  subStageId?: string | null;
  onSaved: (subStage: ConstructionSubStageResponseDTO) => void;
}

const defaultValues: SubStageFormValues = { name: "", description: "" };

export default function ConstructionSubStageUpsertDrawer({
  open,
  onClose,
  stageId,
  subStageId,
  onSaved,
}: ConstructionSubStageUpsertDrawerProps) {
  const { t } = useTranslation();
  const isEditing = Boolean(subStageId);

  const {
    control,
    handleSubmit,
    reset,
    setFocus,
    formState: { errors, isSubmitting },
  } = useForm<SubStageFormValues>({
    resolver: zodResolver(subStageFormSchema),
    mode: "onChange",
    defaultValues,
  });

  useEffect(() => {
    if (!open) return;
    if (subStageId) {
      getSubStageById(subStageId).then((subStage) => {
        reset({ name: subStage.name, description: subStage.description ?? "" });
      });
    } else {
      reset(defaultValues);
      setTimeout(() => setFocus("name"), 0);
    }
  }, [open, subStageId, reset, setFocus]);

  const onSubmit = async (values: SubStageFormValues) => {
    const dto = { name: values.name, description: values.description || undefined, stageId };
    if (subStageId) {
      const updated = await updateSubStage(subStageId, dto);
      message.success(t("constructionSubStages.updated"));
      onSaved(updated);
      onClose();
    } else {
      const created = await createSubStage(dto);
      message.success(t("constructionSubStages.created"));
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
          <span>{isEditing ? t("constructionSubStages.editTitle") : t("constructionSubStages.createTitle")}</span>
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
          <Text strong>{t("constructionSubStages.fields.name")}</Text>
          <Controller
            name="name"
            control={control}
            render={({ field }) => (
              <Input {...field} size="large" placeholder={t("constructionSubStages.fields.namePlaceholder")} style={{ marginTop: 6 }} />
            )}
          />
          {errors.name && (
            <Text type="danger" style={{ fontSize: 12 }}>
              {t(errors.name.message as string)}
            </Text>
          )}
        </div>

        <div>
          <Text strong>{t("constructionSubStages.fields.description")}</Text>
          <Controller
            name="description"
            control={control}
            render={({ field }) => (
              <TextArea {...field} autoSize={{ minRows: 3 }} placeholder={t("constructionSubStages.fields.descriptionPlaceholder")} style={{ marginTop: 6 }} />
            )}
          />
        </div>
      </div>
    </Drawer>
  );
}
