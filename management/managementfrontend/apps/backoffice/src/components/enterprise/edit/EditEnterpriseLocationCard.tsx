import { useRef, useState } from "react";
import type { FC } from "react";
import { useTranslation } from "react-i18next";
import { Button, Col, Input, Row, Select, Space } from "antd";
import { EnvironmentOutlined, GlobalOutlined, UndoOutlined } from "@ant-design/icons";

import BlueprintCard from "@/components/common/BlueprintCard";
import MapLocationPickerDrawer, {
  type LocationPickData,
} from "@/components/location/MapLocationPickerDrawer";
import { searchLocations } from "@/services/locationService";
import type { LocationLite } from "@/services/locationService";
import {
  upsertEnterpriseLocation,
  removeEnterpriseLocation,
} from "@/services/enterpriseService";
import type { EnterpriseFullResponseDTO } from "@/services/enterpriseService";
import { useConfirm } from "@/context/ConfirmDialogContext";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";

const FIELD_SIZE = "large" as const;

type LocOption = { value: string; label: string };
type Mode = "new" | "existing";

type NewLocationDraft = {
  addressLine1: string;
  addressLine2: string;
  postalCode: string;
  city: string;
  country: string;
  municipality: string;
  parish: string;
  state: string;
  googlePlaceId: string;
  notes: string;
  latitude?: number;
  longitude?: number;
};

const blankDraft: NewLocationDraft = {
  addressLine1: "",
  addressLine2: "",
  postalCode: "",
  city: "",
  country: "",
  municipality: "",
  parish: "",
  state: "",
  googlePlaceId: "",
  notes: "",
};

function formatLocLabel(l: LocationLite) {
  const left = `${l.country ?? "—"}, ${l.city ?? "—"}`;
  const right = [l.addressLine1, l.postalCode].filter(Boolean).join(" ; ");
  return right ? `${left}  -  ${right}` : left;
}

type Props = {
  data: EnterpriseFullResponseDTO;
  onSave: (newData: EnterpriseFullResponseDTO) => void;
  onCancel: () => void;
};

/**
 * A mesma escolha "nova/existente" + `MapLocationPickerDrawer` da criação, só que com estado
 * local em vez de RHF — o `PATCH .../location/upsert` é um contrato à parte, não faz parte do
 * `EnterpriseFormSchema`. A vista de leitura (endereço, mapa) é a `LocationCard` do
 * `EnterpriseViewDrawer` — este componente só aparece já em modo de edição, sem a duplicar.
 */
const EditEnterpriseLocationCard: FC<Props> = ({ data, onSave, onCancel }) => {
  const { t } = useTranslation();
  const confirm = useConfirm();

  const hasLocation = !!data.location;
  const [mode, setMode] = useState<Mode>("new");
  const [draft, setDraft] = useState<NewLocationDraft>(
    data.location
      ? {
          addressLine1: data.location.addressLine1 ?? "",
          addressLine2: data.location.addressLine2 ?? "",
          postalCode: data.location.postalCode ?? "",
          city: data.location.city ?? "",
          country: data.location.country ?? "",
          municipality: data.location.municipality ?? "",
          parish: data.location.parish ?? "",
          state: "",
          googlePlaceId: data.location.googlePlaceId ?? "",
          notes: data.location.notes ?? "",
          latitude: data.location.latitude ?? undefined,
          longitude: data.location.longitude ?? undefined,
        }
      : blankDraft,
  );
  const [existingId, setExistingId] = useState<string | undefined>(undefined);

  const [locOptions, setLocOptions] = useState<LocOption[]>([]);
  const [locLoading, setLocLoading] = useState(false);
  const searchDebounceRef = useRef<number | null>(null);

  const [mapOpen, setMapOpen] = useState(false);
  const [mapPicked, setMapPicked] = useState<LocationPickData | null>(null);

  const [saving, setSaving] = useState(false);
  const [removing, setRemoving] = useState(false);

  const onSearchLocation = (text: string) => {
    if (searchDebounceRef.current) window.clearTimeout(searchDebounceRef.current);
    searchDebounceRef.current = window.setTimeout(async () => {
      if (!text || text.trim().length < 2) {
        setLocOptions([]);
        return;
      }
      setLocLoading(true);
      try {
        const items = await searchLocations(text.trim());
        setLocOptions(items.map((l) => ({ value: l.id, label: formatLocLabel(l) })));
      } catch (error) {
        ErrorHandler.handle(error);
      } finally {
        setLocLoading(false);
      }
    }, 300);
  };

  const handleMapConfirm = (picked: LocationPickData) => {
    setMapPicked(picked);
    setDraft((prev) => ({
      ...prev,
      addressLine1: picked.addressLine1 || "",
      addressLine2: picked.addressLine2 || "",
      city: picked.city || "",
      municipality: picked.municipality || "",
      parish: picked.parish || "",
      postalCode: picked.postalCode || "",
      country: picked.country || "",
      latitude: picked.latitude ?? undefined,
      longitude: picked.longitude ?? undefined,
    }));
    setMapOpen(false);
  };

  const handleMapClear = () => {
    setMapPicked(null);
    setDraft(blankDraft);
  };

  const handleSave = async () => {
    if (mode === "existing" && !existingId) {
      notificationService.warning(t('enterpriseEdit.selectLocation'));
      return;
    }
    setSaving(true);
    try {
      const response = await upsertEnterpriseLocation(
        data.id,
        mode === "existing"
          ? { id: existingId }
          : {
              addressLine1: draft.addressLine1 || null,
              addressLine2: draft.addressLine2 || null,
              postalCode: draft.postalCode || null,
              city: draft.city || null,
              state: draft.state || null,
              country: draft.country || null,
              municipality: draft.municipality || null,
              parish: draft.parish || null,
              latitude: draft.latitude ?? null,
              longitude: draft.longitude ?? null,
              googlePlaceId: draft.googlePlaceId || null,
              notes: draft.notes || null,
            },
      );
      onSave({ ...data, location: response.location });
      notificationService.success(t('locations.updated'));
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSaving(false);
    }
  };

  const handleRemove = () => {
    confirm({
      title: t('buildingEdit.location.removeLocation'),
      message: t('buildingEdit.location.removeConfirm'),
      actionLabel: t('buildingEdit.location.removeYes'),
      onConfirm: async () => {
        setRemoving(true);
        try {
          await removeEnterpriseLocation(data.id);
          onSave({ ...data, location: null });
          notificationService.success(t('enterpriseEdit.locationRemoved'));
        } catch (error) {
          ErrorHandler.handle(error);
        } finally {
          setRemoving(false);
        }
      },
    });
  };

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
      <Row gutter={12}>
        <Col span={12}>
          <BlueprintCard
            style={{
              padding: "13.6px", gap: "6.8px", cursor: "pointer",
              borderColor: mode === "new" ? "var(--ind-color-accent)" : undefined,
            }}
            onClick={() => setMode("new")}
          >
            <span className="ind-card-title" style={{ color: mode === "new" ? "var(--ind-accent-800)" : undefined }}>
              {t('enterpriseLocationEdit.createNew')}
            </span>
            <p className="ind-card-body">{t('enterpriseLocationEdit.insertManually')}</p>
          </BlueprintCard>
        </Col>
        <Col span={12}>
          <BlueprintCard
            style={{
              padding: "13.6px", gap: "6.8px", cursor: "pointer",
              borderColor: mode === "existing" ? "var(--ind-color-accent)" : undefined,
            }}
            onClick={() => setMode("existing")}
          >
            <span className="ind-card-title" style={{ color: mode === "existing" ? "var(--ind-accent-800)" : undefined }}>
              {t('enterpriseLocationEdit.useExisting')}
            </span>
            <p className="ind-card-body">{t('enterpriseLocationEdit.selectLocation')}</p>
          </BlueprintCard>
        </Col>
      </Row>

      {mode === "existing" ? (
        <BlueprintCard kicker={t('buildingEdit.location.selectExistingTitle')} style={{ padding: "13.6px", gap: "10.2px" }}>
          <div className="field">
            <label>{t('buildingEdit.location.searchPlaceholder')}</label>
            <Select
              value={existingId ? locOptions.find((o) => o.value === existingId) : null}
              onChange={(_, option) => setExistingId((option as LocOption | undefined)?.value)}
              allowClear
              size={FIELD_SIZE}
              showSearch
              placeholder={t('buildingEdit.location.searchPlaceholder')}
              filterOption={false}
              loading={locLoading}
              onSearch={onSearchLocation}
              notFoundContent={locLoading ? t('common.searching') : t('common.noResults')}
              options={locOptions}
              className="w-full"
              onClear={() => setExistingId(undefined)}
            />
          </div>
        </BlueprintCard>
      ) : (
        <BlueprintCard kicker={t('enterpriseCreate.location.createNewTitle')} style={{ padding: "13.6px", gap: "10.2px" }}>
          <div style={{ marginBottom: "20px" }}>
            <Button
              size={FIELD_SIZE}
              type={mapPicked ? "default" : "primary"}
              icon={<GlobalOutlined />}
              onClick={() => setMapOpen(true)}
            >
              {mapPicked ? t('enterpriseCreate.location.changeOnMap') : t('enterpriseCreate.location.selectOnMap')}
            </Button>
            {mapPicked && (
              <Button
                size={FIELD_SIZE}
                style={{ marginLeft: 8 }}
                icon={<UndoOutlined />}
                onClick={handleMapClear}
                danger
              >
                {t('locationPicker.clear')}
              </Button>
            )}
          </div>

          <Row gutter={16}>
            <Col span={12} className="field">
              <label>{t('common.country')}</label>
              <Input value={draft.country} onChange={(e) => setDraft({ ...draft, country: e.target.value })} size={FIELD_SIZE} allowClear />
            </Col>
            <Col span={12} className="field">
              <label>{t('common.city')}</label>
              <Input value={draft.city} onChange={(e) => setDraft({ ...draft, city: e.target.value })} size={FIELD_SIZE} allowClear />
            </Col>
            <Col span={12} className="field">
              <label>{t('common.municipality')}</label>
              <Input value={draft.municipality} onChange={(e) => setDraft({ ...draft, municipality: e.target.value })} size={FIELD_SIZE} allowClear />
            </Col>
            <Col span={12} className="field">
              <label>{t('common.parish')}</label>
              <Input value={draft.parish} onChange={(e) => setDraft({ ...draft, parish: e.target.value })} size={FIELD_SIZE} allowClear />
            </Col>
            <Col span={16} className="field">
              <label>{t('buildingCreate.location.addressLine1')}</label>
              <Input value={draft.addressLine1} onChange={(e) => setDraft({ ...draft, addressLine1: e.target.value })} size={FIELD_SIZE} allowClear />
            </Col>
            <Col span={8} className="field">
              <label>{t('buildingCreate.location.addressLine2')}</label>
              <Input value={draft.addressLine2} onChange={(e) => setDraft({ ...draft, addressLine2: e.target.value })} size={FIELD_SIZE} allowClear />
            </Col>
            <Col span={12} className="field">
              <label>{t('common.postalCode')}</label>
              <Input value={draft.postalCode} onChange={(e) => setDraft({ ...draft, postalCode: e.target.value })} size={FIELD_SIZE} allowClear />
            </Col>
            <Col span={12} className="field">
              <label>{t('buildingCreate.location.googlePlaceId')}</label>
              <Input value={draft.googlePlaceId} onChange={(e) => setDraft({ ...draft, googlePlaceId: e.target.value })} size={FIELD_SIZE} allowClear />
            </Col>
            <Col span={24} className="field">
              <label>{t('common.notes')}</label>
              <Input.TextArea value={draft.notes} onChange={(e) => setDraft({ ...draft, notes: e.target.value })} rows={3} allowClear />
            </Col>
          </Row>
        </BlueprintCard>
      )}

      <MapLocationPickerDrawer
        open={mapOpen}
        onClose={() => setMapOpen(false)}
        onConfirm={handleMapConfirm}
        defaultCenter={{ lat: 41.2734, lng: -7.5863 }}
      />

      <Space style={{ display: "flex", justifyContent: "space-between", marginTop: "3.4px" }}>
        {hasLocation ? (
          <Button danger icon={<EnvironmentOutlined />} onClick={handleRemove} loading={removing} disabled={saving}>
            {t('buildingEdit.location.removeLocation')}
          </Button>
        ) : (
          <span />
        )}
        <Space>
          <Button onClick={onCancel} disabled={saving || removing}>
            {t('common.cancel')}
          </Button>
          <Button type="primary" onClick={handleSave} loading={saving} disabled={removing}>
            {t('buildingEdit.location.saveButton')}
          </Button>
        </Space>
      </Space>
    </div>
  );
};

export default EditEnterpriseLocationCard;
