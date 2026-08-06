// src/components/enterprises/EnterpriseViewDrawer.tsx
import React, { useEffect, useRef, useState } from "react";
import { useNavigate } from "react-router-dom";
import { Drawer, Button, Skeleton, Empty, message } from "antd";
import { EditOutlined, CloseOutlined } from "@ant-design/icons";
import dayjs from "dayjs";

import type {
  EnterpriseFullResponseDTO,
  LocationResponseDTO,
  MediaResponseDTO,
} from "@/services/enterpriseService";
import { getEnterpriseById } from "@/services/enterpriseService";
import EditEnterpriseOverviewCard from "@/components/enterprise/edit/EditEnterpriseOverviewCard";
import EditDatesAndAreasCard from "@/components/enterprise/edit/EditDatesAndAreasCard";
import EditFinancialCard from "@/components/enterprise/edit/EditFinancialCard";
import EditEnterpriseLocationCard from "@/components/enterprise/edit/EditEnterpriseLocationCard";
import EditEnterpriseGalleryCard from "@/components/enterprise/edit/EditEnterpriseGalleryCard";
import AuthenticatedImage from "@/components/image/AuthenticatedImage";
import BlueprintCard from "@/components/common/BlueprintCard";

/* ========= CONSTANTES ========= */

const TYPE_LABELS: Record<string, string> = {
  residential: "Residencial",
  commercial: "Comercial",
  industrial: "Industrial",
  mixed_use: "Uso misto",
  land: "Terreno",
};
const TYPE_CLASS: Record<string, string> = {
  residential: "ind-tag-accent",
  commercial: "ind-tag-neutral",
  industrial: "ind-tag-outline",
  mixed_use: "ind-tag-accent-2",
  land: "ind-tag-neutral",
};
const STATUS_LABELS: Record<string, string> = {
  planning: "Planeamento",
  under_construction: "Em construção",
  active: "Ativo",
  completed: "Concluído",
  archived: "Arquivado",
  deleted: "Eliminado",
};
const STATUS_CLASS: Record<string, string> = {
  planning: "ind-tag-outline",
  under_construction: "ind-tag-accent",
  active: "ind-tag-accent",
  completed: "ind-tag-neutral",
  archived: "ind-tag-neutral",
  deleted: "ind-tag-neutral",
};

const VIEW_STEPS = ["Visão Geral", "Datas e Áreas", "Financeiro", "Localização", "Multimédia", "Auditoria"];

/* ========= HELPERS ========= */

const formatMoney = (amount?: number | null, currency?: string | null) => {
  if (amount == null) return "—";
  try {
    return new Intl.NumberFormat("pt-PT", {
      style: "currency",
      currency: currency || "EUR",
      maximumFractionDigits: 2,
    }).format(amount);
  } catch {
    return `${amount} ${currency || ""}`.trim();
  }
};

const formatArea = (area?: number | null) => {
  if (area == null) return "—";
  return `${new Intl.NumberFormat("pt-PT", { maximumFractionDigits: 2 }).format(area)} m²`;
};

const formatDate = (dateStr?: string | null) => (dateStr ? dayjs(dateStr).format("DD/MM/YYYY") : "—");
const formatDateTime = (dateStr?: string | null) => (dateStr ? dayjs(dateStr).format("DD/MM/YYYY HH:mm") : "—");

const resolveMediaUrl = (m: MediaResponseDTO & { url?: string | null }): string => {
  if (m.url) return m.url;
  if (m.downloadUrl) {
    if (m.downloadUrl.startsWith("http://") || m.downloadUrl.startsWith("https://")) return m.downloadUrl;
    if (m.downloadUrl.startsWith("/")) {
      const apiUrl = import.meta.env.VITE_API_URL || "http://localhost:8080";
      return `${apiUrl}${m.downloadUrl}`;
    }
    return m.downloadUrl;
  }
  if (m.bucket && m.storageKey && import.meta.env.VITE_SUPABASE_URL) {
    const base = String(import.meta.env.VITE_SUPABASE_URL).replace(/\/+$/, "");
    return `${base}/storage/v1/object/public/${m.bucket}/${m.storageKey}`;
  }
  return "/src/assets/images/property/default.jpg";
};

function loadGoogleMapsScript(): Promise<void> {
  return new Promise((resolve, reject) => {
    if ((window as any).google?.maps) { resolve(); return; }
    const existing = document.querySelector('script[data-google-maps="true"]');
    if (existing) {
      const prev = (window as any).__initMapView;
      (window as any).__initMapView = () => { if (prev) prev(); resolve(); };
      return;
    }
    const apiKey = import.meta.env.VITE_GOOGLE_MAPS_API_KEY;
    if (!apiKey) { reject(new Error("VITE_GOOGLE_MAPS_API_KEY não configurada")); return; }
    (window as any).__initMapView = () => resolve();
    const script = document.createElement("script");
    script.src = `https://maps.googleapis.com/maps/api/js?key=${apiKey}&callback=__initMapView`;
    script.async = true;
    script.defer = true;
    script.setAttribute("data-google-maps", "true");
    script.onerror = () => reject(new Error("Erro ao carregar Google Maps API"));
    document.head.appendChild(script);
  });
}

/* ========= CARDS DE VISUALIZAÇÃO (blueprint: kicker + 2-col label/value grid) ========= */

function Fact({ label, children }: { label: string; children: React.ReactNode }) {
  return (
    <div>
      <div style={{ fontSize: 11, opacity: 0.55 }}>{label}</div>
      <div style={{ fontSize: 14 }}>{children}</div>
    </div>
  );
}

const OverviewCard: React.FC<{ data: EnterpriseFullResponseDTO }> = ({ data }) => (
  <BlueprintCard kicker="Visão geral" style={{ padding: "13.6px", gap: "10.2px" }}>
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
      <Fact label="Construtora">{data.constructionCompany || "—"}</Fact>
      <Fact label="Arquiteto">{data.architect || "—"}</Fact>
      <Fact label="Cidade">{data.location?.city || "—"}</Fact>
      <Fact label="Estado">{STATUS_LABELS[data.status] || data.status}</Fact>
    </div>
    {data.description && (
      <div>
        <div style={{ fontSize: 11, opacity: 0.55, marginBottom: 4 }}>Descrição</div>
        <div style={{ fontSize: 14 }}>{data.description}</div>
      </div>
    )}
  </BlueprintCard>
);

const DatesAndAreasCard: React.FC<{ data: EnterpriseFullResponseDTO }> = ({ data }) => (
  <BlueprintCard kicker="Datas e áreas" style={{ padding: "13.6px", gap: "10.2px" }}>
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
      <Fact label="Início">{formatDate(data.startDate)}</Fact>
      <Fact label="Conclusão prevista">{formatDate(data.completionDate)}</Fact>
      <Fact label="Área total">{formatArea(data.totalArea)}</Fact>
      <Fact label="Área do terreno">{formatArea(data.landArea)}</Fact>
      <Fact label="Unidades">{data.totalUnits ?? "—"}</Fact>
    </div>
  </BlueprintCard>
);

const FinancialCard: React.FC<{ data: EnterpriseFullResponseDTO }> = ({ data }) => (
  <BlueprintCard kicker="Financeiro" style={{ padding: "13.6px", gap: "10.2px" }}>
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
      <Fact label="Investimento total">{formatMoney(data.totalInvestment, data.currency)}</Fact>
      <Fact label="Valor atual">{formatMoney(data.currentValue, data.currency)}</Fact>
    </div>
  </BlueprintCard>
);

const LocationCard: React.FC<{ location?: LocationResponseDTO | null }> = ({ location }) => {
  const hasLocation = location && (
    location.addressLine1 || location.city || location.country ||
    location.postalCode || location.parish || location.municipality
  );

  const [showMap, setShowMap] = useState(false);
  const [mapsLoaded, setMapsLoaded] = useState(false);
  const [mapError, setMapError] = useState<string | null>(null);
  const mapRef = useRef<HTMLDivElement>(null);
  const mapInstanceRef = useRef<any>(null);
  const markerRef = useRef<any>(null);

  const lat = location?.latitude;
  const lng = location?.longitude;
  const hasCoords = lat != null && lng != null;

  useEffect(() => {
    if (!showMap || !hasCoords) return;
    loadGoogleMapsScript().then(() => setMapsLoaded(true)).catch((err) => setMapError(err.message));
  }, [showMap, hasCoords]);

  useEffect(() => {
    if (!showMap || !mapsLoaded || !mapRef.current || !hasCoords) return;
    if (mapInstanceRef.current) { mapInstanceRef.current.setCenter({ lat, lng }); return; }
    const map = new (window as any).google.maps.Map(mapRef.current, {
      center: { lat, lng }, zoom: 15, mapTypeControl: true, streetViewControl: false,
      fullscreenControl: false, gestureHandling: "greedy", zoomControl: true,
    });
    markerRef.current = new (window as any).google.maps.Marker({
      position: { lat, lng }, map, animation: (window as any).google.maps.Animation.DROP,
    });
    mapInstanceRef.current = map;
  }, [showMap, mapsLoaded, hasCoords, lat, lng]);

  useEffect(() => {
    if (!showMap && mapInstanceRef.current) { mapInstanceRef.current = null; markerRef.current = null; }
  }, [showMap]);

  if (!hasLocation) {
    return (
      <BlueprintCard kicker="Localização" style={{ padding: "13.6px" }}>
        <Empty description="Sem localização definida" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      </BlueprintCard>
    );
  }

  const addressLine = [location?.addressLine1, location?.addressLine2].filter(Boolean).join(" — ");
  const metaLine = [location?.parish, location?.municipality, location?.city, location?.country].filter(Boolean).join(" · ");

  return (
    <BlueprintCard kicker="Localização" style={{ padding: "13.6px", gap: "10.2px" }}>
      <div style={{ fontSize: 14 }}>
        {addressLine || "—"}
        {metaLine && (
          <div style={{ fontSize: 12, opacity: 0.65, marginTop: 2 }}>
            {metaLine}{location?.postalCode ? ` · ${location.postalCode}` : ""}
          </div>
        )}
      </div>

      {hasCoords && (
        <Button size="small" onClick={() => setShowMap((v) => !v)} style={{ width: "fit-content" }}>
          {showMap ? "Fechar Mapa" : "Ver no Mapa"}
        </Button>
      )}

      {showMap && hasCoords ? (
        mapError ? (
          <div style={{ padding: 20, textAlign: "center", fontSize: 13, opacity: 0.7 }}>
            Erro ao carregar mapa: {mapError}
          </div>
        ) : (
          <div style={{ position: "relative", border: "1px solid var(--ind-color-divider)" }}>
            <div ref={mapRef} style={{ width: "100%", height: 220 }} />
            {!mapsLoaded && (
              <div style={{ position: "absolute", inset: 0, display: "flex", alignItems: "center", justifyContent: "center", background: "var(--ind-color-surface)" }}>
                <span style={{ fontSize: 13, opacity: 0.7 }}>A carregar mapa…</span>
              </div>
            )}
          </div>
        )
      ) : (
        <div className="ind-hatch" style={{ height: 140, display: "flex", alignItems: "center", justifyContent: "center", fontSize: 11, fontFamily: "monospace", color: "var(--ind-neutral-700)" }}>
          mapa
        </div>
      )}

      {location?.notes && <div style={{ fontSize: 13, opacity: 0.8 }}>{location.notes}</div>}
    </BlueprintCard>
  );
};

const MediaCard: React.FC<{ data: EnterpriseFullResponseDTO }> = ({ data }) => {
  const media = data.media || [];
  const banner = media.find((m) => m.type === "banner");
  const galleryImages = media.filter((m) => m.type === "image");

  return (
    <BlueprintCard kicker="Multimédia" style={{ padding: "13.6px", gap: "10.2px" }}>
      <div style={{ fontSize: 13, opacity: 0.7 }}>{media.length} ficheiro{media.length !== 1 ? "s" : ""}</div>

      {banner && (
        <AuthenticatedImage
          src={resolveMediaUrl(banner)}
          alt={banner.altText || "Banner"}
          style={{ width: "100%", maxHeight: 200, objectFit: "cover", border: "1px solid var(--ind-color-divider)" }}
          fallback="/src/assets/images/property/default.jpg"
        />
      )}

      {galleryImages.length > 0 ? (
        <div style={{ display: "grid", gridTemplateColumns: "repeat(4, 1fr)", gap: 8 }}>
          {galleryImages.map((img, index) => (
            <AuthenticatedImage
              key={img.id}
              src={resolveMediaUrl(img)}
              alt={img.altText || `Imagem ${index + 1}`}
              style={{ width: "100%", aspectRatio: "1", objectFit: "cover", border: "1px solid var(--ind-color-divider)" }}
              fallback="/src/assets/images/property/default.jpg"
            />
          ))}
        </div>
      ) : (
        <Empty description="Sem imagens na galeria" image={Empty.PRESENTED_IMAGE_SIMPLE} />
      )}
    </BlueprintCard>
  );
};

const AuditCard: React.FC<{ data: EnterpriseFullResponseDTO }> = ({ data }) => (
  <BlueprintCard kicker="Auditoria" style={{ padding: "13.6px", gap: "10.2px" }}>
    <div style={{ display: "grid", gridTemplateColumns: "1fr 1fr", gap: "10.2px" }}>
      <Fact label="Criado em">{formatDateTime(data.createdAt)}</Fact>
      <Fact label="Criado por">{data.createdbyName || "—"}</Fact>
      <Fact label="Última atualização">{formatDateTime(data.updatedAt)}</Fact>
      <Fact label="Atualizado por">{data.updatedBy || "—"}</Fact>
    </div>
  </BlueprintCard>
);

/* ========= COMPONENTE PRINCIPAL ========= */

interface EnterpriseViewDrawerProps {
  open: boolean;
  enterpriseId?: string;
  onClose: () => void;
  onUpdated?: (id: string, updated: any) => void;
}

const EnterpriseViewDrawer: React.FC<EnterpriseViewDrawerProps> = ({
  open,
  enterpriseId,
  onClose,
  onUpdated,
}) => {
  const navigate = useNavigate();
  const [data, setData] = useState<EnterpriseFullResponseDTO | null>(null);
  const [loading, setLoading] = useState(false);
  const [current, setCurrent] = useState(0);
  const [editing, setEditing] = useState(false);

  useEffect(() => {
    if (open) document.body.style.overflow = "hidden"; else document.body.style.overflow = "";
    return () => { document.body.style.overflow = ""; };
  }, [open]);

  useEffect(() => {
    if (open && enterpriseId) fetchData();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [open, enterpriseId]);

  const fetchData = async () => {
    if (!enterpriseId) return;
    setLoading(true);
    try {
      const result = await getEnterpriseById(enterpriseId);
      setData(result);
    } catch (error) {
      console.error("Erro ao carregar empreendimento:", error);
      message.error("Não foi possível carregar o empreendimento");
    } finally {
      setLoading(false);
    }
  };

  const handleStepChange = (i: number) => { setCurrent(i); setEditing(false); };

  const handleSave = (updatedData: EnterpriseFullResponseDTO) => {
    setData(updatedData);
    setEditing(false);
    message.success("Empreendimento atualizado");
    if (onUpdated && enterpriseId) onUpdated(enterpriseId, updatedData);
  };

  const handleClose = () => { setCurrent(0); setEditing(false); onClose(); };

  return (
    <Drawer
      open={open}
      onClose={handleClose}
      destroyOnClose
      closable={false}
      width="min(760px, 94vw)"
      styles={{ body: { padding: 0 }, content: { background: "var(--ind-color-bg)" } }}
    >
      {loading ? (
        <div style={{ padding: 20 }}><Skeleton active paragraph={{ rows: 12 }} /></div>
      ) : data ? (
        <div style={{ display: "flex", flexDirection: "column", height: "100%" }}>
          <div style={{ background: "var(--ind-accent-100)", padding: "20.4px", display: "flex", justifyContent: "space-between", alignItems: "flex-start", borderBottom: "1px solid var(--ind-color-divider)" }}>
            <div>
              <span className={`ind-tag ${TYPE_CLASS[data.type] || "ind-tag-neutral"}`}>{TYPE_LABELS[data.type] || data.type}</span>
              <h2 style={{ margin: "4px 0 2px", color: "var(--ind-accent-900)" }}>{data.name}</h2>
              {data.internalReference && <div style={{ fontSize: 12, color: "var(--ind-accent-700)" }}>{data.internalReference}</div>}
            </div>
            <div style={{ display: "flex", alignItems: "center", gap: 10 }}>
              <span className={`ind-tag ${STATUS_CLASS[data.status] || "ind-tag-neutral"}`}>{STATUS_LABELS[data.status] || data.status}</span>
              {!editing && current < 5 && (
                <Button size="small" icon={<EditOutlined />} onClick={() => setEditing(true)}>Editar</Button>
              )}
              {editing && <Button size="small" onClick={() => setEditing(false)}>Cancelar</Button>}
              <Button size="small" icon={<CloseOutlined />} onClick={handleClose} />
            </div>
          </div>

          <div style={{ display: "flex", flex: 1, minHeight: 0 }}>
            <div style={{ width: 190, flexShrink: 0, borderRight: "1px solid var(--ind-color-divider)", padding: "13.6px 0" }}>
              {VIEW_STEPS.map((label, i) => {
                const active = i === current;
                return (
                  <div
                    key={label}
                    onClick={() => handleStepChange(i)}
                    style={{
                      padding: "10px 13.6px",
                      cursor: "pointer",
                      fontSize: 13,
                      borderLeft: active ? "2px solid var(--ind-color-accent)" : "2px solid transparent",
                      background: active ? "var(--ind-accent-100)" : "transparent",
                      color: active ? "var(--ind-accent-800)" : "inherit",
                      fontWeight: active ? 600 : 400,
                    }}
                  >
                    {label}
                  </div>
                );
              })}
            </div>

            <div style={{ flex: 1, overflowY: "auto", padding: "20.4px", display: "flex", flexDirection: "column", gap: "13.6px" }}>
              {editing ? (
                <>
                  {current === 0 && <EditEnterpriseOverviewCard data={data} onSave={handleSave} onCancel={() => setEditing(false)} />}
                  {current === 1 && <EditDatesAndAreasCard data={data} onSave={handleSave} onCancel={() => setEditing(false)} />}
                  {current === 2 && <EditFinancialCard data={data} onSave={handleSave} onCancel={() => setEditing(false)} />}
                  {current === 3 && <EditEnterpriseLocationCard data={data} onSave={handleSave} onCancel={() => setEditing(false)} />}
                  {current === 4 && <EditEnterpriseGalleryCard data={data} onSave={handleSave} onCancel={() => setEditing(false)} />}
                </>
              ) : (
                <>
                  {current === 0 && <OverviewCard data={data} />}
                  {current === 1 && <DatesAndAreasCard data={data} />}
                  {current === 2 && <FinancialCard data={data} />}
                  {current === 3 && <LocationCard location={data.location} />}
                  {current === 4 && <MediaCard data={data} />}
                  {current === 5 && <AuditCard data={data} />}
                </>
              )}
            </div>
          </div>

          <div style={{ borderTop: "1px solid var(--ind-color-divider)", padding: "10.2px 20.4px", display: "flex", justifyContent: "space-between", alignItems: "center" }}>
            <a
              href="#"
              onClick={(e) => { e.preventDefault(); if (enterpriseId) navigate(`/backoffice/empreendimentos/${enterpriseId}/budget`); }}
              style={{ fontSize: 13 }}
            >
              Orçamento de obra →
            </a>
            <div style={{ display: "flex", gap: "6.8px" }}>
              <Button disabled={current === 0} onClick={() => { setEditing(false); setCurrent((c) => Math.max(0, c - 1)); }}>Anterior</Button>
              <Button disabled={current === VIEW_STEPS.length - 1} onClick={() => { setEditing(false); setCurrent((c) => Math.min(VIEW_STEPS.length - 1, c + 1)); }}>Seguinte</Button>
            </div>
          </div>
        </div>
      ) : (
        <Empty description="Sem dados" />
      )}
    </Drawer>
  );
};

export default EnterpriseViewDrawer;
