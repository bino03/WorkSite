export interface ConstructionStageResponseDTO {
  id: string;
  name: string;
  description: string | null;
  enterpriseId: string;
  createdAt: string;
  updatedAt: string;
}

export interface ConstructionSubStageResponseDTO {
  id: string;
  name: string;
  description: string | null;
  stageId: string;
  createdAt: string;
  updatedAt: string;
}

export interface ConstructionExpenseResponseDTO {
  id: string;
  name: string;
  price: number;
  subStageId: string;
  invoiceUrl: string | null;
  originalFilename: string | null;
  mimeType: string | null;
  sizeBytes: number | null;
  createdAt: string;
  updatedAt: string;
}

export interface CreateConstructionStageDTO {
  name: string;
  description?: string;
  enterpriseId: string;
}

export interface CreateConstructionSubStageDTO {
  name: string;
  description?: string;
  stageId: string;
}

export interface CreateConstructionExpenseDTO {
  name: string;
  price: number;
  subStageId: string;
}
