import { useState, useRef, useEffect } from "react";
import { useTranslation } from "react-i18next";
import { Controller, useFormContext } from "react-hook-form";
import { Input, Select, Button, Skeleton, Row, Col } from "antd";
import { EnvironmentOutlined, GlobalOutlined, UndoOutlined } from "@ant-design/icons";

import BlueprintCard from "@/components/common/BlueprintCard";
import { searchLocations } from "@/services/locationService";
import MapLocationPickerDrawer, {
  type LocationPickData,
} from "@/components/location/MapLocationPickerDrawer";

const FIELD_SIZE = "large" as const;

type LocOption = { value: string; label: string };

function clearNewLocationFields(setValue: ReturnType<typeof useFormContext>["setValue"]) {
  setValue("new_location.address_line1", "");
  setValue("new_location.address_line2", "");
  setValue("new_location.postal_code", "");
  setValue("new_location.city", "");
  setValue("new_location.country", "");
  setValue("new_location.municipality", "");
  setValue("new_location.parish", "");
  setValue("new_location.state", "");
  setValue("new_location.google_place_id", "");
  setValue("new_location.notes", "");
  setValue("new_location.name", "");
  setValue("new_location.latitude", undefined);
  setValue("new_location.longitude", undefined);
}

export default function EnterpriseLocationSection() {
  const { t } = useTranslation();
  const { control, watch, setValue } = useFormContext();
  const useExistingLocation = watch("useExistingLocation");
  const existing_location_id = watch("existing_location_id");

  // Modo local
  const [addMode, setAddMode] = useState<"new" | "existing">(
    useExistingLocation ? "existing" : "new",
  );

  const [locOptions, setLocOptions] = useState<LocOption[]>([]);
  const [locLoading, setLocLoading] = useState(false);
  const [initialLoading, setInitialLoading] = useState(false);
  const searchDebounceRef = useRef<number | null>(null);
  const [selectedOption, setSelectedOption] = useState<LocOption | null>(null);

  // Mapa
  const [mapOpen, setMapOpen] = useState(false);
  const [mapPickedLocation, setMapPickedLocation] = useState<LocationPickData | null>(null);

  function formatLocLabel(l: {
    country?: string | null;
    city?: string | null;
    addressLine1?: string | null;
    postalCode?: string | null;
    municipality?: string | null;
  }) {
    const left = `${l.country ?? "—"}, ${l.city ?? "—"}`;
    const right = [l.addressLine1, l.postalCode].filter(Boolean).join(" ; ");
    return right ? `${left}  -  ${right}` : left;
  }

  // Sync quando existing_location_id muda externamente
  useEffect(() => {
    if (existing_location_id && locOptions.length > 0) {
      const option = locOptions.find((opt) => opt.value === existing_location_id);
      if (option) setSelectedOption(option);
    } else if (!existing_location_id) {
      setSelectedOption(null);
    }
  }, [existing_location_id, locOptions]);

  // Carregar localização inicial se já existir existing_location_id
  useEffect(() => {
    const fetchInitialLocation = async () => {
      if (existing_location_id && useExistingLocation && !selectedOption) {
        try {
          setInitialLoading(true);
          const items = await searchLocations("");
          const newOptions = items.map((l) => ({
            value: l.id,
            label: formatLocLabel(l),
          }));
          setLocOptions(newOptions);

          const option = newOptions.find((opt) => opt.value === existing_location_id);
          if (option) setSelectedOption(option);
        } catch (error) {
          console.error("Erro ao buscar localização:", error);
        } finally {
          setInitialLoading(false);
        }
      }
    };

    fetchInitialLocation();
  }, [existing_location_id, useExistingLocation]);

  const onSearchLocation = (text: string) => {
    if (searchDebounceRef.current) window.clearTimeout(searchDebounceRef.current);
    searchDebounceRef.current = window.setTimeout(async () => {
      if (!text || text.trim().length < 2) {
        setLocOptions([]);
        return;
      }
      try {
        setLocLoading(true);
        const items = await searchLocations(text.trim());
        const newOptions = items.map((l) => ({
          value: l.id,
          label: formatLocLabel(l),
        }));
        setLocOptions(newOptions);
      } finally {
        setLocLoading(false);
      }
    }, 300);
  };

  const handleModeChange = (mode: "new" | "existing") => {
    setAddMode(mode);
    if (mode === "existing") {
      setValue("useExistingLocation", true);
      clearNewLocationFields(setValue);
      setMapPickedLocation(null);
    } else {
      setValue("useExistingLocation", false);
      setValue("existing_location_id", undefined);
      setSelectedOption(null);
    }
  };

  const handleMapConfirm = (picked: LocationPickData) => {
    setMapPickedLocation(picked);
    setValue("new_location.address_line1", picked.addressLine1 || "");
    setValue("new_location.address_line2", picked.addressLine2 || "");
    setValue("new_location.city", picked.city || "");
    setValue("new_location.municipality", picked.municipality || "");
    setValue("new_location.parish", picked.parish || "");
    setValue("new_location.postal_code", picked.postalCode || "");
    setValue("new_location.country", picked.country || "");
    setValue("new_location.latitude", picked.latitude ?? undefined);
    setValue("new_location.longitude", picked.longitude ?? undefined);
    setMapOpen(false);
  };

  const handleMapClear = () => {
    setMapPickedLocation(null);
    clearNewLocationFields(setValue);
  };

  if (initialLoading) {
    return (
      <BlueprintCard kicker="Localização" style={{ padding: "13.6px", gap: "10.2px" }}>
        <div className="space-y-4">
          <Skeleton.Button active size="large" block />
          <Skeleton.Input active size="large" block />
        </div>
      </BlueprintCard>
    );
  }

  return (
    <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
      {/* Seleção de modo */}
      <Row gutter={12}>
        <Col span={12}>
          <BlueprintCard
            style={{
              padding: "13.6px",
              gap: "6.8px",
              cursor: "pointer",
              borderColor: addMode === "new" ? "var(--ind-color-accent)" : undefined,
            }}
            onClick={() => handleModeChange("new")}
          >
            <span
              className="ind-card-title"
              style={{ color: addMode === "new" ? "var(--ind-accent-800)" : undefined }}
            >
              Nova Localização
            </span>
            <p className="ind-card-body">Introduzir um endereço novo.</p>
          </BlueprintCard>
        </Col>
        <Col span={12}>
          <BlueprintCard
            style={{
              padding: "13.6px",
              gap: "6.8px",
              cursor: "pointer",
              borderColor: addMode === "existing" ? "var(--ind-color-accent)" : undefined,
            }}
            onClick={() => handleModeChange("existing")}
          >
            <span
              className="ind-card-title"
              style={{ color: addMode === "existing" ? "var(--ind-accent-800)" : undefined }}
            >
              Localização Existente
            </span>
            <p className="ind-card-body">Associar a uma localização já registada.</p>
          </BlueprintCard>
        </Col>
      </Row>

      {/* ── Modo: Localização Existente ── */}
      {addMode === "existing" && (
        <BlueprintCard kicker={t('enterpriseCreate.location.searchTitle')} style={{ padding: "13.6px", gap: "10.2px" }}>
          <div className="field">
            <label>{t('enterpriseCreate.location.searchLabel')}</label>
          <Controller
            control={control}
            name="existing_location_id"
            render={({ field }) => (
              <Select
                value={locOptions.find((opt) => opt.value === field.value) || null}
                onChange={(value, option) => {
                  field.onChange(value || undefined);
                  setSelectedOption(option as LocOption);
                }}
                allowClear
                size={FIELD_SIZE}
                showSearch
                placeholder={t('enterpriseCreate.location.searchPlaceholder')}
                filterOption={false}
                loading={locLoading}
                onSearch={onSearchLocation}
                notFoundContent={locLoading ? t('common.searching') : t('common.noResults')}
                dropdownMatchSelectWidth={600}
                className="w-full"
                options={locOptions}
                labelInValue={false}
                onClear={() => {
                  field.onChange(undefined);
                  setSelectedOption(null);
                }}
              />
            )}
          />
          </div>
        </BlueprintCard>
      )}

      {/* ── Modo: Nova Localização ── */}
      {addMode === "new" && (
        <BlueprintCard kicker={t('enterpriseCreate.location.createNewTitle')} style={{ padding: "13.6px", gap: "10.2px" }}>
          {/* Botão do mapa */}
          <div style={{ marginBottom: "20px" }}>
            <Button
              size={FIELD_SIZE}
              type={mapPickedLocation ? "default" : "primary"}
              icon={<GlobalOutlined />}
              onClick={() => setMapOpen(true)}
              style={{ borderRadius: "8px" }}
            >
              {mapPickedLocation ? t('enterpriseCreate.location.changeOnMap') : t('enterpriseCreate.location.selectOnMap')}
            </Button>

            {mapPickedLocation && (
              <Button
                size={FIELD_SIZE}
                style={{ marginLeft: "8px", borderRadius: "8px" }}
                icon={<UndoOutlined />}
                onClick={handleMapClear}
                danger
              >
                {t('locationPicker.clear')}
              </Button>
            )}
          </div>

          {/* Preview do mapa */}
          {mapPickedLocation && (
            <div
              style={{
                padding: "16px 20px",
                marginBottom: "20px",
                borderRadius: "12px",
                background: "linear-gradient(135deg, #f0fdf4 0%, #dcfce7 100%)",
                border: "1px solid #86efac",
              }}
            >
              <div
                style={{
                  display: "flex",
                  alignItems: "center",
                  gap: 8,
                  marginBottom: 12,
                }}
              >
                <EnvironmentOutlined style={{ color: "#16a34a", fontSize: 16 }} />
                <span style={{ fontWeight: 600, color: "#15803d", fontSize: 14 }}>
                  {t('enterpriseCreate.location.selectedOnMap')}
                </span>
              </div>
              <div
                style={{
                  display: "grid",
                  gridTemplateColumns: "1fr 1fr",
                  gap: "8px 20px",
                  fontSize: 13,
                  color: "#374151",
                }}
              >
                {mapPickedLocation.addressLine1 && (
                  <span style={{ gridColumn: "1 / -1" }}>
                    <span style={{ color: "#6b7280", fontWeight: 500 }}>{t('common.address')}: </span>
                    {mapPickedLocation.addressLine1}
                    {mapPickedLocation.addressLine2
                      ? ` — ${mapPickedLocation.addressLine2}`
                      : ""}
                  </span>
                )}
                {mapPickedLocation.parish && (
                  <span>
                    <span style={{ color: "#6b7280", fontWeight: 500 }}>{t('common.parish')}: </span>
                    {mapPickedLocation.parish}
                  </span>
                )}
                {mapPickedLocation.municipality && (
                  <span>
                    <span style={{ color: "#6b7280", fontWeight: 500 }}>{t('common.municipality')}: </span>
                    {mapPickedLocation.municipality}
                  </span>
                )}
                {mapPickedLocation.city && (
                  <span>
                    <span style={{ color: "#6b7280", fontWeight: 500 }}>{t('common.city')}: </span>
                    {mapPickedLocation.city}
                  </span>
                )}
                {mapPickedLocation.postalCode && (
                  <span>
                    <span style={{ color: "#6b7280", fontWeight: 500 }}>{t('common.postalCode')}: </span>
                    {mapPickedLocation.postalCode}
                  </span>
                )}
                {mapPickedLocation.country && (
                  <span>
                    <span style={{ color: "#6b7280", fontWeight: 500 }}>{t('common.country')}: </span>
                    {mapPickedLocation.country}
                  </span>
                )}
                {mapPickedLocation.latitude && mapPickedLocation.longitude && (
                  <span
                    style={{
                      gridColumn: "1 / -1",
                      color: "#6b7280",
                      fontFamily: "monospace",
                      fontSize: 12,
                      marginTop: 4,
                    }}
                  >
                    {mapPickedLocation.latitude.toFixed(6)},{" "}
                    {mapPickedLocation.longitude.toFixed(6)}
                  </span>
                )}
              </div>
            </div>
          )}

          {/* Campos de localização */}
          <Row gutter={16}>
            <Col span={12} className="field">
              <label>{t('common.country')}</label>
              <Controller
                control={control}
                name="new_location.country"
                render={({ field }) => (
                  <Input {...field} value={field.value ?? ""} size={FIELD_SIZE} placeholder={t('common.country')} allowClear />
                )}
              />
            </Col>
            <Col span={12} className="field">
              <label>{t('common.city')}</label>
              <Controller
                control={control}
                name="new_location.city"
                render={({ field }) => (
                  <Input {...field} value={field.value ?? ""} size={FIELD_SIZE} placeholder="Ex.: Lisboa" allowClear />
                )}
              />
            </Col>
            <Col span={12} className="field">
              <label>{t('common.municipality')}</label>
              <Controller
                control={control}
                name="new_location.municipality"
                render={({ field }) => (
                  <Input {...field} value={field.value ?? ""} size={FIELD_SIZE} placeholder="Ex.: Vila Real" allowClear />
                )}
              />
            </Col>
            <Col span={12} className="field">
              <label>{t('common.parish')}</label>
              <Controller
                control={control}
                name="new_location.parish"
                render={({ field }) => (
                  <Input {...field} value={field.value ?? ""} size={FIELD_SIZE} placeholder="Ex.: Arroios" allowClear />
                )}
              />
            </Col>
            <Col span={16} className="field">
              <label>{t('buildingCreate.location.addressLine1')}</label>
              <Controller
                control={control}
                name="new_location.address_line1"
                render={({ field }) => (
                  <Input {...field} value={field.value ?? ""} size={FIELD_SIZE} placeholder="Rua / Avenida" allowClear />
                )}
              />
            </Col>
            <Col span={8} className="field">
              <label>{t('buildingCreate.location.addressLine2')}</label>
              <Controller
                control={control}
                name="new_location.address_line2"
                render={({ field }) => (
                  <Input {...field} value={field.value ?? ""} size={FIELD_SIZE} placeholder="Andar / Fração" allowClear />
                )}
              />
            </Col>
            <Col span={12} className="field">
              <label>{t('common.postalCode')}</label>
              <Controller
                control={control}
                name="new_location.postal_code"
                render={({ field }) => (
                  <Input {...field} value={field.value ?? ""} size={FIELD_SIZE} placeholder="0000-000" allowClear />
                )}
              />
            </Col>
            <Col span={12} className="field">
              <label>{t('buildingCreate.location.state')}</label>
              <Controller
                control={control}
                name="new_location.state"
                render={({ field }) => (
                  <Input {...field} value={field.value ?? ""} size={FIELD_SIZE} placeholder={t('buildingCreate.location.state')} allowClear />
                )}
              />
            </Col>
            <Col span={12} className="field">
              <label>{t('buildingCreate.location.googlePlaceId')}</label>
              <Controller
                control={control}
                name="new_location.google_place_id"
                render={({ field }) => (
                  <Input {...field} value={field.value ?? ""} size={FIELD_SIZE} placeholder="ID do Google Places" allowClear />
                )}
              />
            </Col>
            <Col span={12} className="field">
              <label>{t('buildingCreate.location.locationName')}</label>
              <Controller
                control={control}
                name="new_location.name"
                render={({ field }) => (
                  <Input {...field} value={field.value ?? ""} size={FIELD_SIZE} placeholder={t('buildingCreate.location.locationNamePlaceholder')} allowClear />
                )}
              />
            </Col>
            <Col span={24} className="field">
              <label>{t('common.notes')}</label>
              <Controller
                control={control}
                name="new_location.notes"
                render={({ field }) => (
                  <Input.TextArea {...field} value={field.value ?? ""} rows={3} placeholder={t('buildingCreate.location.notesPlaceholder')} allowClear />
                )}
              />
            </Col>
          </Row>
        </BlueprintCard>
      )}

      {/* Drawer do mapa */}
      <MapLocationPickerDrawer
        open={mapOpen}
        onClose={() => setMapOpen(false)}
        onConfirm={handleMapConfirm}
        defaultCenter={{ lat: 41.2734, lng: -7.5863 }}
      />
    </div>
  );
}
