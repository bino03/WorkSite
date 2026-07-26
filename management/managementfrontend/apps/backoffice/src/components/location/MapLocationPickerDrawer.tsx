// src/components/common/MapLocationPickerDrawer.tsx
import React, { useEffect, useRef, useState } from "react";
import { useTranslation } from "react-i18next";
import {
  Drawer,
  Button,
  Typography,
  Row,
  Col,
  Input,
  InputNumber,
  message,
  Spin,
} from "antd";
import {
  EnvironmentOutlined,
  UndoOutlined,
  AimOutlined,
} from "@ant-design/icons";

const { Text } = Typography;

// ─── Tipos exportados ────────────────────────────────────────────────────────
export interface LocationPickData {
  addressLine1: string;
  addressLine2: string;
  city: string;
  postalCode: string;
  parish: string;
  municipality: string;
  country: string;
  latitude: number | null;
  longitude: number | null;
}

interface MapLocationPickerDrawerProps {
  open: boolean;
  onClose: () => void;
  onConfirm: (location: LocationPickData) => void;
  /** Centro inicial do mapa — por defeito Lisboa */
  defaultCenter?: { lat: number; lng: number };
  /** Zoom inicial do mapa */
  defaultZoom?: number;
}

// ─── Localização vazia ───────────────────────────────────────────────────────
const EMPTY_LOCATION: LocationPickData = {
  addressLine1: "",
  addressLine2: "",
  city: "",
  postalCode: "",
  parish: "",
  municipality: "",
  country: "",
  latitude: null,
  longitude: null,
};

// ─── Parsing dos address_components do Google ────────────────────────────────
function parseGeocoderResult(
  result: any,
  lat: number,
  lng: number
): LocationPickData {
  const comps: any[] = result.address_components || [];

  const get = (type: string, useLong = true) => {
    const c = comps.find((comp: any) => comp.types.includes(type));
    return c ? (useLong ? c.long_name : c.short_name) : "";
  };

  const route = get("route");
  const streetNumber = get("street_number");
  const addressLine1 = route
    ? streetNumber
      ? `${route}, ${streetNumber}`
      : route
    : result.formatted_address?.split(",")[0] ?? "";

  // Em Portugal:
  //   locality                      → Cidade
  //   administrative_area_level_3   → Freguesia
  //   administrative_area_level_2   → Município
  //   administrative_area_level_1   → Distrito
  // level_4 existe em alguns países mas em Portugal a freguesia é level_3.
  const parish =
    get("administrative_area_level_4") ||
    get("administrative_area_level_3");

  return {
    addressLine1,
    addressLine2: "",
    city: get("locality") || get("administrative_area_level_2"),
    postalCode: get("postal_code"),
    parish,
    municipality: get("administrative_area_level_2"),
    country: get("country"),
    latitude: lat,
    longitude: lng,
  };
}

// ─── Carregar script do Google Maps (reutiliza se já existir) ────────────────
function loadGoogleMapsScript(): Promise<void> {
  return new Promise((resolve, reject) => {
    if (window.google?.maps) {
      resolve();
      return;
    }

    // Script já no DOM mas ainda a carregar — espera pelo callback
    const existing = document.querySelector(
      'script[data-google-maps="true"]'
    ) as HTMLScriptElement | null;
    if (existing) {
      const prev = (window as any).__initMapPicker;
      (window as any).__initMapPicker = () => {
        if (prev) prev();
        resolve();
      };
      return;
    }

    const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;
    if (!apiKey) {
      reject(new Error("VITE_GOOGLE_MAPS_API_KEY não configurada"));
      return;
    }

    (window as any).__initMapPicker = () => resolve();

    const script = document.createElement("script");
    script.src = `https://maps.googleapis.com/maps/api/js?key=${apiKey}&callback=__initMapPicker`;
    script.async = true;
    script.defer = true;
    script.setAttribute("data-google-maps", "true");
    script.onerror = () => reject(new Error("Erro ao carregar Google Maps API"));
    document.head.appendChild(script);
  });
}

// ─── Componente ───────────────────────────────────────────────────────────────
const MapLocationPickerDrawer: React.FC<MapLocationPickerDrawerProps> = ({
  open,
  onClose,
  onConfirm,
  defaultCenter = { lat: 41.2734, lng: -7.5863 },
  defaultZoom = 10,
}) => {
  const { t } = useTranslation();
  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<any>(null);
  const markerRef = useRef<any>(null);
  const geocoderRef = useRef<any>(null);
  const clickListenerRef = useRef<any>(null);

  const [mapsLoaded, setMapsLoaded] = useState(false);
  const [loadError, setLoadError] = useState<string | null>(null);
  const [location, setLocation] = useState<LocationPickData>(EMPTY_LOCATION);
  const [isGeocoding, setIsGeocoding] = useState(false);
  const hasSelection = location.latitude !== null;

  // ── Carregar API quando o drawer abre ────────────────────────────────────
  useEffect(() => {
    if (!open) return;

    setLocation(EMPTY_LOCATION);
    setLoadError(null);
    setIsGeocoding(false);

    // Remover marker anterior
    if (markerRef.current) {
      markerRef.current.setMap(null);
      markerRef.current = null;
    }

    loadGoogleMapsScript()
      .then(() => setMapsLoaded(true))
      .catch((err) => setLoadError(err.message));
  }, [open]);

  // ── Função para dar reverse geocode num ponto ───────────────────────────
  const doReverseGeocode = (lat: number, lng: number) => {
    console.log("🗺️ [MapPicker] geocoderRef.current existe?", !!geocoderRef.current);
    if (!geocoderRef.current) {
      console.error("🗺️ [MapPicker] Geocoder não foi inicializado!");
      setLocation({ ...EMPTY_LOCATION, latitude: lat, longitude: lng });
      return;
    }

    setIsGeocoding(true);
    geocoderRef.current.geocode(
      { location: { lat, lng } },
      (results: any[], status: string) => {
        console.log("🗺️ [MapPicker] Status do geocoder:", status);
        console.log("🗺️ [MapPicker] Resultados:", JSON.stringify(results, null, 2));

        setIsGeocoding(false);
        if (status === "OK" && results?.length) {
          const parsed = parseGeocoderResult(results[0], lat, lng);
          console.log("🗺️ [MapPicker] Dados parsed:", parsed);
          setLocation(parsed);
        } else {
          console.warn("🗺️ [MapPicker] Geocoding falhou — status:", status);
          setLocation({ ...EMPTY_LOCATION, latitude: lat, longitude: lng });
          message.warning(t('locationPicker.geocodingPartial'));
        }
      }
    );
  };

  // ── Inicializar / recentrar mapa ─────────────────────────────────────────
  useEffect(() => {
    if (!open || !mapsLoaded || !mapRef.current) return;

    if (mapInstanceRef.current) {
      mapInstanceRef.current.setCenter(defaultCenter);
      mapInstanceRef.current.setZoom(defaultZoom);
      return;
    }

    const map = new window.google.maps.Map(mapRef.current, {
      center: defaultCenter,
      zoom: defaultZoom,
      mapTypeControl: true,
      streetViewControl: false,
      fullscreenControl: false,
      gestureHandling: "greedy",
      zoomControl: true,
    });

    geocoderRef.current = new window.google.maps.Geocoder();

    // Click no mapa
    clickListenerRef.current = map.addListener("click", (event: any) => {
      if (!event.latLng) return;
      const lat = event.latLng.lat();
      const lng = event.latLng.lng();

      if (markerRef.current) {
        markerRef.current.setPosition({ lat, lng });
      } else {
        markerRef.current = new window.google.maps.Marker({
          position: { lat, lng },
          map,
          title: "Localização selecionada",
          animation: window.google.maps.Animation.DROP,
        });
      }

      map.panTo({ lat, lng });
      doReverseGeocode(lat, lng);
    });

    mapInstanceRef.current = map;
  }, [open, mapsLoaded, defaultCenter, defaultZoom]);

  // ── Geolocalização do utilizador ──────────────────────────────────────────
  const handleGeolocate = () => {
    if (!navigator.geolocation) {
      message.warning(t('locationPicker.geoNotSupported'));
      return;
    }

    navigator.geolocation.getCurrentPosition(
      (pos) => {
        const lat = pos.coords.latitude;
        const lng = pos.coords.longitude;
        const map = mapInstanceRef.current;
        if (!map) return;

        if (markerRef.current) {
          markerRef.current.setPosition({ lat, lng });
        } else {
          markerRef.current = new window.google.maps.Marker({
            position: { lat, lng },
            map,
            title: "A sua localização",
            animation: window.google.maps.Animation.DROP,
          });
        }

        map.panTo({ lat, lng });
        map.setZoom(16);
        doReverseGeocode(lat, lng);
      },
      () => {
        message.error(t('locationPicker.geoFailed'));
      }
    );
  };

  // ── Limpar seleção ────────────────────────────────────────────────────────
  const handleReset = () => {
    if (markerRef.current) {
      markerRef.current.setMap(null);
      markerRef.current = null;
    }
    setLocation(EMPTY_LOCATION);
  };

  // ── Confirmar ─────────────────────────────────────────────────────────────
  const handleConfirm = () => {
    if (!hasSelection) {
      message.warning(t('locationPicker.selectFirst'));
      return;
    }
    onConfirm(location);
  };

  // ─── RENDER ───────────────────────────────────────────────────────────────
  return (
    <Drawer
      open={open}
      onClose={onClose}
      width={680}
      title={
        <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
          <div
            style={{
              width: 36,
              height: 36,
              borderRadius: 10,
              backgroundColor: "#1890ff",
              display: "flex",
              alignItems: "center",
              justifyContent: "center",
            }}
          >
            <EnvironmentOutlined style={{ color: "#fff", fontSize: 18 }} />
          </div>
          <div>
            <div style={{ fontWeight: 600, fontSize: 16, color: "#262626" }}>
              {t('locationPicker.title')}
            </div>
            <div style={{ fontSize: 12, color: "#8c8c8c", marginTop: 1 }}>
              {t('locationPicker.subtitle')}
            </div>
          </div>
        </div>
      }
      footer={
        <div style={{ display: "flex", justifyContent: "flex-end", gap: 12 }}>
          <Button size="large" onClick={onClose} style={{ borderRadius: 8 }}>
            {t('common.cancel')}
          </Button>
          <Button
            type="primary"
            size="large"
            onClick={handleConfirm}
            disabled={!hasSelection || isGeocoding}
            style={{
              borderRadius: 8,
              minWidth: 180,
              backgroundColor: "#1890ff",
              border: "none",
            }}
          >
            {t('locationPicker.confirm')}
          </Button>
        </div>
      }
      footerStyle={{
        borderTop: "1px solid #e8e8e8",
        padding: "16px 24px",
        backgroundColor: "#fafafa",
      }}
    >
      {/* ── Erro de carregamento ── */}
      {loadError && (
        <div style={{ padding: 40, textAlign: "center", color: "#ff4d4f" }}>
          <EnvironmentOutlined style={{ fontSize: 48, marginBottom: 12 }} />
          <p style={{ fontWeight: 600 }}>{t('locationPicker.loadError')}</p>
          <p style={{ fontSize: 13, color: "#8c8c8c" }}>{loadError}</p>
        </div>
      )}

      {!loadError && (
        <>
          {/* ── Botões auxiliares ── */}
          <div style={{ display: "flex", gap: 8, marginBottom: 12 }}>
            <Button
              icon={<AimOutlined />}
              size="small"
              onClick={handleGeolocate}
              disabled={!mapsLoaded}
              style={{ borderRadius: 6, borderColor: "#1890ff", color: "#1890ff" }}
            >
              {t('locationPicker.myLocation')}
            </Button>
            {hasSelection && (
              <Button
                icon={<UndoOutlined />}
                size="small"
                onClick={handleReset}
                danger
                ghost
                style={{ borderRadius: 6 }}
              >
                {t('locationPicker.clear')}
              </Button>
            )}
          </div>

          {/* ── Mapa ── */}
          <div
            style={{
              width: "100%",
              height: 340,
              borderRadius: 12,
              overflow: "hidden",
              border: "1px solid #e8e8e8",
              marginBottom: 20,
              position: "relative",
            }}
          >
            <div ref={mapRef} style={{ width: "100%", height: "100%" }} />

            {/* Loading */}
            {!mapsLoaded && (
              <div
                style={{
                  position: "absolute",
                  inset: 0,
                  display: "flex",
                  alignItems: "center",
                  justifyContent: "center",
                  backgroundColor: "#f5f5f5",
                  zIndex: 2,
                }}
              >
                <Spin size="large" tip={t('locations.mapsLoading')} />
              </div>
            )}

            {/* Overlay geocoding */}
            {isGeocoding && (
              <div
                style={{
                  position: "absolute",
                  bottom: 12,
                  left: "50%",
                  transform: "translateX(-50%)",
                  backgroundColor: "rgba(0,0,0,0.6)",
                  color: "#fff",
                  padding: "6px 16px",
                  borderRadius: 20,
                  fontSize: 13,
                  display: "flex",
                  alignItems: "center",
                  gap: 8,
                  zIndex: 10,
                }}
              >
                <Spin size="small" style={{ color: "#fff" }} /> {t('locationPicker.geocoding')}
              </div>
            )}
          </div>

          {/* ── Campos preenchidos (editáveis) ── */}
          {hasSelection && !isGeocoding && (
            <div
              style={{
                padding: 20,
                background: "linear-gradient(135deg, #f0f9ff 0%, #e6f7ff 100%)",
                borderRadius: 12,
                border: "1px solid #bae7ff",
              }}
            >
              <div
                style={{
                  display: "flex",
                  justifyContent: "space-between",
                  alignItems: "center",
                  marginBottom: 16,
                }}
              >
                <Text strong style={{ fontSize: 15, color: "#262626" }}>
                  {t('locationPicker.locationData')}
                </Text>
                <Text style={{ fontSize: 12, color: "#1890ff" }}>
                  {t('locationPicker.editNote')}
                </Text>
              </div>

              <Row gutter={[12, 12]}>
                <Col span={24}>
                  <Text type="secondary" style={{ fontSize: 12, display: "block", marginBottom: 4 }}>
                    {t('locationPicker.addressLine1')}
                  </Text>
                  <Input
                    value={location.addressLine1}
                    onChange={(e) => setLocation({ ...location, addressLine1: e.target.value })}
                    placeholder={t('locationPicker.addressPlaceholder')}
                    style={{ borderRadius: 8 }}
                  />
                </Col>

                <Col span={24}>
                  <Text type="secondary" style={{ fontSize: 12, display: "block", marginBottom: 4 }}>
                    {t('locationPicker.addressLine2Optional')}
                  </Text>
                  <Input
                    value={location.addressLine2}
                    onChange={(e) => setLocation({ ...location, addressLine2: e.target.value })}
                    placeholder={t('locationPicker.addressLine2Placeholder')}
                    style={{ borderRadius: 8 }}
                  />
                </Col>

                <Col span={12}>
                  <Text type="secondary" style={{ fontSize: 12, display: "block", marginBottom: 4 }}>
                    {t('common.city')}
                  </Text>
                  <Input
                    value={location.city}
                    onChange={(e) => setLocation({ ...location, city: e.target.value })}
                    style={{ borderRadius: 8 }}
                  />
                </Col>
                <Col span={12}>
                  <Text type="secondary" style={{ fontSize: 12, display: "block", marginBottom: 4 }}>
                    {t('common.municipality')}
                  </Text>
                  <Input
                    value={location.municipality}
                    onChange={(e) => setLocation({ ...location, municipality: e.target.value })}
                    style={{ borderRadius: 8 }}
                  />
                </Col>

                <Col span={8}>
                  <Text type="secondary" style={{ fontSize: 12, display: "block", marginBottom: 4 }}>
                    {t('common.postalCode')}
                  </Text>
                  <Input
                    value={location.postalCode}
                    onChange={(e) => setLocation({ ...location, postalCode: e.target.value })}
                    placeholder={t('locationPicker.postalCodePlaceholder')}
                    style={{ borderRadius: 8 }}
                  />
                </Col>
                <Col span={8}>
                  <Text type="secondary" style={{ fontSize: 12, display: "block", marginBottom: 4 }}>
                    {t('common.parish')}
                  </Text>
                  <Input
                    value={location.parish}
                    onChange={(e) => setLocation({ ...location, parish: e.target.value })}
                    style={{ borderRadius: 8 }}
                  />
                </Col>
                <Col span={8}>
                  <Text type="secondary" style={{ fontSize: 12, display: "block", marginBottom: 4 }}>
                    {t('common.country')}
                  </Text>
                  <Input
                    value={location.country}
                    onChange={(e) => setLocation({ ...location, country: e.target.value })}
                    style={{ borderRadius: 8 }}
                  />
                </Col>

                <Col span={12}>
                  <Text type="secondary" style={{ fontSize: 12, display: "block", marginBottom: 4 }}>
                    {t('common.latitude')}
                  </Text>
                  <InputNumber
                    value={location.latitude}
                    style={{ width: "100%", borderRadius: 8 }}
                    disabled
                    precision={6}
                  />
                </Col>
                <Col span={12}>
                  <Text type="secondary" style={{ fontSize: 12, display: "block", marginBottom: 4 }}>
                    {t('common.longitude')}
                  </Text>
                  <InputNumber
                    value={location.longitude}
                    style={{ width: "100%", borderRadius: 8 }}
                    disabled
                    precision={6}
                  />
                </Col>
              </Row>
            </div>
          )}

          {/* Estado vazio */}
          {!hasSelection && !isGeocoding && mapsLoaded && (
            <div
              style={{
                padding: "32px 20px",
                textAlign: "center",
                background: "#fafafa",
                borderRadius: 12,
                border: "1px dashed #d9d9d9",
              }}
            >
              <EnvironmentOutlined style={{ fontSize: 32, color: "#d9d9d9", marginBottom: 8 }} />
              <p style={{ color: "#8c8c8c", fontSize: 14, margin: "8px 0 0" }}>
                {t('locationPicker.noSelection')}
              </p>
              <p style={{ color: "#bfbfbf", fontSize: 12, margin: "4px 0 0" }}>
                {t('locationPicker.noSelectionHint')}
              </p>
            </div>
          )}
        </>
      )}
    </Drawer>
  );
};

export default MapLocationPickerDrawer;