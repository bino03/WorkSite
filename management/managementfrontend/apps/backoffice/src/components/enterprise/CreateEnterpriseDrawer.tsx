import { useState, useRef, useEffect } from "react";
import { Drawer, Button, Tabs, Tag, message } from "antd";
import { useTranslation } from "react-i18next";
import { FormProvider, useForm } from "react-hook-form";
import { zodResolver } from "@hookform/resolvers/zod";
import { createPortal } from "react-dom";
import { Building, MapPin, Calendar, DollarSign, Image as ImageIcon } from "lucide-react";

import { EnterpriseFormSchema, defaultValues, type EnterpriseFormValues } from "@/components/enterprise/create/enterpriseFormSchema";
import type { UploadFile } from "antd";
import type { GalleryItem } from "@/components/upload/MediaUploadsSection";

// Componentes das seções
import BasicInfoSection from "@/components/enterprise/create/BasicInfoSection";
import TimelineMetricsSection from "@/components/enterprise/create/TimelineMetricsSection";
import FinancialSection from "@/components/enterprise/create/FinancialSection";
import LocationSection from "@/components/enterprise/create/EnterpriseLocationSection";
import MediaSection from "@/components/enterprise/create/EnterpriseMediaSection";

// API
import api from "@/api";

interface CreateEnterpriseDrawerProps {
    open: boolean;
    onClose: () => void;
    onCreated?: (newId: string) => void;
}

export default function CreateEnterpriseDrawer({ open, onClose, onCreated }: CreateEnterpriseDrawerProps) {
  const { t } = useTranslation();
    const [bannerFileList, setBannerFileList] = useState<UploadFile[]>([]);
    const [galleryItems, setGalleryItems] = useState<GalleryItem[]>([]);
    const [tabKey, setTabKey] = useState("basic");
    const [elevated, setElevated] = useState(false);
    const [isSubmitting, setIsSubmitting] = useState(false);
    const scrollRef = useRef<HTMLDivElement | null>(null);

    const methods = useForm<EnterpriseFormValues>({
        resolver: zodResolver(EnterpriseFormSchema),
        mode: "onChange",
        defaultValues,
    });

    const { handleSubmit, formState: { isValid, errors }, reset } = methods;

    useEffect(() => {
        const el = scrollRef.current;
        if (!el) return;
        const onScroll = () => setElevated(el.scrollTop > 2);
        el.addEventListener("scroll", onScroll, { passive: true });
        return () => el.removeEventListener("scroll", onScroll);
    }, []);

    useEffect(() => {
        if (open) {
            reset(defaultValues);
        }
    }, [open, reset]);

    const resetAndClose = () => {
        reset(defaultValues);
        setBannerFileList([]);
        setGalleryItems([]);
        setTabKey("basic");
        onClose();
    };

    const StickyTabs = ({ elevated }: { elevated: boolean }) => (
        <div
            className="sticky top-0 z-10 px-6 pt-3 pb-2"
            style={{
                background: "var(--surface-1, #fff)",
                backdropFilter: "saturate(180%) blur(6px)",
                WebkitBackdropFilter: "saturate(180%) blur(6px)",
                borderBottom: "1px solid var(--border-color, rgba(0,0,0,0.06))",
                boxShadow: elevated ? "0 6px 12px rgba(0,0,0,0.06)" : "none",
                transition: "box-shadow .2s ease",
            }}
        >
            <Tabs
                activeKey={tabKey}
                onChange={setTabKey}
                animated
                items={[
                    { key: "basic", label: <span className="inline-flex items-center gap-2"><Building size={20} /> Informações Básicas</span> },
                    { key: "timeline", label: <span className="inline-flex items-center gap-2"><Calendar size={20} /> Cronograma & Métricas</span> },
                    { key: "financial", label: <span className="inline-flex items-center gap-2"><DollarSign size={20} /> Financeiro</span> },
                    { key: "location", label: <span className="inline-flex items-center gap-2"><MapPin size={20} /> Localização</span> },
                    { key: "media", label: <span className="inline-flex items-center gap-2"><ImageIcon size={20} /> Multimédia</span> },
                ]}
            />
        </div>
    );

   const onSubmit = async (data: EnterpriseFormValues) => {
    setIsSubmitting(true);
    try {
        console.log("Dados do formulário:", data);

        // Validação de erros do formulário
        if (Object.keys(errors).length > 0) {
            message.error(t('buildingCreate.formErrors'));
            setIsSubmitting(false);
            return;
        }

        if (!data.status) {
            message.warning('O status do projeto não está preenchido. Recomendamos que definas um estado antes de criar.');
            setTabKey("basic");
            setIsSubmitting(false);
            return;
        }

        // =============== TRANSFORMAÇÃO DE LOCALIZAÇÃO ===============
        let existingLocationId: string | undefined = undefined;
        let newLocation: Record<string, unknown> | undefined = undefined;

        if (data.useExistingLocation) {
            if (!data.existing_location_id) {
                message.warning(t('buildingCreate.locationError'));
                setTabKey("location");
                setIsSubmitting(false);
                return;
            }
            existingLocationId = data.existing_location_id;
        } else {
            const newLoc = data.new_location ?? {};
            const isBlankLocation = (
                !newLoc?.address_line1?.trim() &&
                !newLoc?.address_line2?.trim() &&
                !newLoc?.postal_code?.trim() &&
                !newLoc?.city?.trim() &&
                !newLoc?.country?.trim() &&
                !newLoc?.municipality?.trim() &&
                !newLoc?.parish?.trim() &&
                newLoc?.latitude == null &&
                newLoc?.longitude == null
            );

            if (!isBlankLocation) {
                newLocation = {
                    addressLine1: newLoc.address_line1?.trim() || null,
                    addressLine2: newLoc.address_line2?.trim() || null,
                    postalCode: newLoc.postal_code?.trim() || null,
                    city: newLoc.city?.trim() || null,
                    country: newLoc.country?.trim() || null,
                    municipality: newLoc.municipality?.trim() || null,
                    parish: newLoc.parish?.trim() || null,
                    latitude: typeof newLoc.latitude === "number" ? newLoc.latitude : null,
                    longitude: typeof newLoc.longitude === "number" ? newLoc.longitude : null,
                };
            }
        }

        // =============== CONSTRUIR ARRAY DE MEDIA ===============
        const mediaPayload: Record<string, unknown>[] = [];

        // Banner
        if (Array.isArray(bannerFileList) && bannerFileList.length > 0) {
            const bannerFile = bannerFileList[0].originFileObj as File;
            mediaPayload.push({
                type: "banner", // ← UPPERCASE
                mimeType: bannerFile.type, // ← camelCase
                fileSizeBytes: bannerFile.size, // ← camelCase
                sortOrder: 0, // ← camelCase
              //  visibility: "PUBLIC",
                // altText pode ser adicionado se tiveres
            });
        }

        // Galeria
        if (Array.isArray(galleryItems) && galleryItems.length > 0) {
            galleryItems.forEach((g, index) => {
                if (g.file) {
                    mediaPayload.push({
                        type: (g.type || "image").toLowerCase(), // ← UPPERCASE
                        altText: g.altText || null, // ← camelCase
                        sortOrder: g.sortOrder ?? index, // ← camelCase
                        mimeType: g.file.type, // ← camelCase
                        fileSizeBytes: g.file.size, // ← camelCase
                       // visibility: "public",
                    });
                }
            });
        }

        // =============== CONSTRUIR PAYLOAD CORRETO ===============
        const payload = {
            // Campos básicos EM CAMELCASE
            name: data.name,
            internalReference: data.internal_Reference || null,
            type: data.type || null,
            status: data.status || null,
            startDate: data.start_date || null,
            completionDate: data.completion_date || null,
            totalArea: data.total_area || null,
            landArea: data.land_area || null,
            totalUnits: data.total_units || null,
            totalInvestment: data.total_investment || null,
            currentValue: data.current_value || null,
            currency: data.currency || 'EUR',
            constructionCompany: data.construction_company || null,
            architect: data.architect || null,
            managerId: data.manager_id || null,
            ownerId: data.owner_id || null,
            createdBy: data.created_by || null,
            isActive: data.is_active,

            // Localização EM CAMELCASE
            ...(existingLocationId && { existingLocationId }),
            ...(newLocation && { newLocation }),

            // Media (apenas se houver items) - JÁ ESTÁ EM CAMELCASE
            ...(mediaPayload.length > 0 && { media: mediaPayload }),
        };

        console.log("Payload final para API:", JSON.stringify(payload, null, 2));

       // =============== ENVIAR COMO MULTIPART ===============
        const fd = new FormData();
        fd.append("enterprise", new Blob([JSON.stringify(payload)], { type: "application/json" }));

        // Adicionar banner se existir
        if (Array.isArray(bannerFileList) && bannerFileList.length > 0) {
            const bannerFile = bannerFileList[0].originFileObj as File;
            if (bannerFile) {
                fd.append("banner", bannerFile, bannerFile.name);
            }
        }

        // Adicionar gallery se existir
        if (Array.isArray(galleryItems) && galleryItems.length > 0) {
            const galleryTypes: string[] = [];
            const galleryAltTexts: string[] = [];
            const gallerySortOrders: number[] = [];

            galleryItems.forEach((g, index) => {
                if (g.file) {
                    fd.append("gallery", g.file, g.file.name);
                    galleryTypes.push(g.type || "image");
                    galleryAltTexts.push(g.altText || "");
                    gallerySortOrders.push(g.sortOrder ?? index);
                }
            });

            // Adicionar metadados da gallery
            if (galleryTypes.length > 0) {
                fd.append("galleryTypes", new Blob([JSON.stringify(galleryTypes)], { type: "application/json" }));
            }
            if (galleryAltTexts.length > 0) {
                fd.append("galleryAltTexts", new Blob([JSON.stringify(galleryAltTexts)], { type: "application/json" }));
            }
            if (gallerySortOrders.length > 0) {
                fd.append("gallerySortOrders", new Blob([JSON.stringify(gallerySortOrders)], { type: "application/json" }));
            }
        }

        const response = await api.post("/enterprises", fd, {
            headers: {
                "Content-Type": "multipart/form-data",
            },
        });
        
        message.success(t('enterprises.created'));
        resetAndClose();
        onCreated?.(response.data.id);
        
    } catch (e: unknown) {
        console.error("Erro detalhado:", e);
        const err = e as Record<string, unknown>;
        const resp = (err.response as Record<string, unknown>)?.data as Record<string, unknown>;
        message.error(t('enterprises.loadError'));
    } finally {
        setIsSubmitting(false);
    }
};
    if (!open) return null;

    return createPortal(
        <Drawer
            width={900}
            title={
                <div className="flex items-center gap-3">
                    <span className="text-xl font-bold" style={{ color: "var(--text-strong)" }}>
                        Criar Empreendimento
                    </span>
                    <Tag color="blue">Novo</Tag>
                </div>
            }
            open={open}
            bodyStyle={{ paddingBottom: 88 }}
            onClose={resetAndClose}
            destroyOnClose
            styles={{
                header: { borderBottom: "1px solid var(--border-color, rgba(0,0,0,0.08))" },
                body: { padding: 0 },
            }}
            footer={
                <div className="flex items-center justify-between gap-3 px-6 py-3">
                    <div className="flex items-center gap-3">
                        <Button onClick={resetAndClose}>Cancelar</Button>
                        <Button
                            type="primary"
                            size="large"
                            loading={isSubmitting}
                            disabled={!isValid}
                            onClick={handleSubmit(onSubmit)}
                        >
                            {isSubmitting ? "A criar..." : "Criar Empreendimento"}
                        </Button>
                    </div>
                </div>
            }
        >
            <FormProvider {...methods}>
                <div>
                    <StickyTabs elevated={elevated} />
                    <div
                        ref={scrollRef}
                        className="px-6 py-5 overflow-y-auto"
                        style={{ maxHeight: "calc(100vh - 160px)" }}
                    >
                        {tabKey === "basic" && <BasicInfoSection />}
                        {tabKey === "timeline" && <TimelineMetricsSection />}
                        {tabKey === "financial" && <FinancialSection />}
                        {tabKey === "location" && <LocationSection />}
                        {tabKey === "media" && (
                            <MediaSection
                                bannerFileList={bannerFileList}
                                setBannerFileList={setBannerFileList}
                                galleryItems={galleryItems}
                                setGalleryItems={setGalleryItems}
                            />
                        )}
                    </div>
                </div>
            </FormProvider>
        </Drawer>,
        document.body
    );
}