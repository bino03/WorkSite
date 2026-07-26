import api from "@/api";
import type {
  ConstructionStageResponseDTO,
  ConstructionSubStageResponseDTO,
  ConstructionExpenseResponseDTO,
  CreateConstructionStageDTO,
  CreateConstructionSubStageDTO,
  CreateConstructionExpenseDTO,
} from "@/types/construction";

/* ========= Construction Stages ========= */

export const listStagesByEnterprise = async (enterpriseId: string): Promise<ConstructionStageResponseDTO[]> => {
  const response = await api.get(`/construction-stages/enterprise/${enterpriseId}`);
  return response.data;
};

export const getStageById = async (id: string): Promise<ConstructionStageResponseDTO> => {
  const response = await api.get(`/construction-stages/${id}`);
  return response.data;
};

export const createStage = async (dto: CreateConstructionStageDTO): Promise<ConstructionStageResponseDTO> => {
  const response = await api.post(`/construction-stages`, dto);
  return response.data;
};

export const updateStage = async (id: string, dto: CreateConstructionStageDTO): Promise<ConstructionStageResponseDTO> => {
  const response = await api.put(`/construction-stages/${id}`, dto);
  return response.data;
};

export const deleteStage = async (id: string): Promise<void> => {
  await api.delete(`/construction-stages/${id}`);
};

/* ========= Construction Sub-Stages ========= */

export const listSubStagesByStage = async (stageId: string): Promise<ConstructionSubStageResponseDTO[]> => {
  const response = await api.get(`/construction-sub-stages/stage/${stageId}`);
  return response.data;
};

export const getSubStageById = async (id: string): Promise<ConstructionSubStageResponseDTO> => {
  const response = await api.get(`/construction-sub-stages/${id}`);
  return response.data;
};

export const createSubStage = async (dto: CreateConstructionSubStageDTO): Promise<ConstructionSubStageResponseDTO> => {
  const response = await api.post(`/construction-sub-stages`, dto);
  return response.data;
};

export const updateSubStage = async (id: string, dto: CreateConstructionSubStageDTO): Promise<ConstructionSubStageResponseDTO> => {
  const response = await api.put(`/construction-sub-stages/${id}`, dto);
  return response.data;
};

export const deleteSubStage = async (id: string): Promise<void> => {
  await api.delete(`/construction-sub-stages/${id}`);
};

/* ========= Construction Expenses ========= */

export const listExpensesBySubStage = async (subStageId: string): Promise<ConstructionExpenseResponseDTO[]> => {
  const response = await api.get(`/construction-expenses/sub-stage/${subStageId}`);
  return response.data;
};

export const getExpenseById = async (id: string): Promise<ConstructionExpenseResponseDTO> => {
  const response = await api.get(`/construction-expenses/${id}`);
  return response.data;
};

export const createExpense = async (
  dto: CreateConstructionExpenseDTO,
  invoiceFile?: File
): Promise<ConstructionExpenseResponseDTO> => {
  const form = new FormData();
  form.append("expenseData", new Blob([JSON.stringify(dto)], { type: "application/json" }));
  if (invoiceFile) form.append("invoiceFile", invoiceFile);
  const response = await api.post(`/construction-expenses`, form, {
    headers: { "Content-Type": "multipart/form-data" },
  });
  return response.data;
};

export const updateExpense = async (
  id: string,
  dto: CreateConstructionExpenseDTO,
  invoiceFile?: File
): Promise<ConstructionExpenseResponseDTO> => {
  const form = new FormData();
  form.append("expenseData", new Blob([JSON.stringify(dto)], { type: "application/json" }));
  if (invoiceFile) form.append("invoiceFile", invoiceFile);
  const response = await api.put(`/construction-expenses/${id}`, form, {
    headers: { "Content-Type": "multipart/form-data" },
  });
  return response.data;
};

export const deleteExpense = async (id: string): Promise<void> => {
  await api.delete(`/construction-expenses/${id}`);
};
