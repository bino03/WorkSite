import { z } from "zod";

// Defina os enums com base no que existe no banco
export const EnterpriseTypeEnum = z.enum(["residential", "commercial", "industrial", "mixed_use"]);
export const EnterpriseStatusEnum = z.enum(["planning", "under_construction", "completed", "on_hold", "cancelled"]);
export const VisibilityEnum = z.enum(["PUBLIC", "PRIVATE"]);

// Schema para nova localização
const NewLocationSchema = z.object({
  address_line1: z.string().optional(),
  address_line2: z.string().optional(),
  postal_code: z.string().optional(),
  city: z.string().optional(),
  state: z.string().optional(),
  country: z.string().optional(),
  municipality: z.string().optional(),
  parish: z.string().optional(),
  latitude: z.number().optional(),
  longitude: z.number().optional(),
  google_place_id: z.string().optional(),
  notes: z.string().optional(),
  name: z.string().optional(),
});

// Schema para media items
const MediaItemSchema = z.object({
  type: EnterpriseTypeEnum,
  storage_key: z.string().optional(),
  alt_text: z.string().optional(),
  sort_order: z.number().default(0),
  mime_type: z.string().optional(),
  file_size_bytes: z.number().optional(),
  width: z.number().optional(),
  height: z.number().optional(),
  duration_ms: z.number().optional(),
  checksum_sha256: z.string().optional(),
  visibility: VisibilityEnum.default("PRIVATE"),
  uploaded_by: z.string().uuid().optional(),
});


export const EnterpriseFormSchema = z.object({
  // BasicInfoSection
  name: z.string().min(1, "O nome é obrigatório"),
  internal_Reference: z.string().optional(),
  type: EnterpriseTypeEnum.optional(),
  status: EnterpriseStatusEnum.optional(),
  is_active: z.boolean().default(true),

  // TimelineMetricsSection
  start_date: z.string().optional(),
  completion_date: z.string().optional(),
  total_area: z.number().min(0).optional(),
  land_area: z.number().min(0).optional(),
  total_units: z.number().int().min(0).optional(),

  // FinancialSection
  total_investment: z.number().min(0).optional(),
  current_value: z.number().min(0).optional(),
  currency: z.string().default("EUR"),

  // Professional contacts (opcionais)
  construction_company: z.string().optional(),
  architect: z.string().optional(),
  manager_id: z.string().uuid().optional(),
  owner_id: z.string().uuid().optional(),
  created_by: z.string().uuid().optional(),

  // Location - estrutura correta
  useExistingLocation: z.boolean().default(false),
  existing_location_id: z.string().uuid().optional(),
  new_location: NewLocationSchema.optional(),

  // Media - será preenchido automaticamente dos uploads
  media: z.array(MediaItemSchema).optional(),
});
export type EnterpriseFormValues = z.infer<typeof EnterpriseFormSchema>;




export const defaultValues: EnterpriseFormValues = {
  name: "",
  internal_Reference: "",
  type: undefined,
  status: undefined,
  is_active: true,
  start_date: "",
  completion_date: "",
  total_area: undefined,
  land_area: undefined,
  total_units: undefined,
  total_investment: undefined,
  current_value: undefined,
  currency: "EUR",
  construction_company: "",
  architect: "",
  manager_id: undefined,
  owner_id: undefined,
  created_by: undefined,
  useExistingLocation: false,
  existing_location_id: undefined,
  new_location: {
    address_line1: "",
    address_line2: "",
    postal_code: "",
    city: "",
    state: "",
    country: "",
    municipality: "",
    parish: "",
    latitude: undefined,
    longitude: undefined,
    google_place_id: "",
    notes: "",
    name: "",
  },
  media: [],
};