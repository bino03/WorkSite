import api from "@/api";
import type {
  TaskResponse,
  TaskUpsertRequest,
  TaskStatusUpdateRequest,
  TaskListFilters,
  TaskListResponse,
} from "@/types/task";

export const taskService = {
  list: async (filters: TaskListFilters): Promise<TaskListResponse> => {
    const params = new URLSearchParams();
    if (filters.q) params.set("q", filters.q);
    if (filters.status) params.set("status", filters.status);
    if (filters.enterpriseId) params.set("enterpriseId", filters.enterpriseId);
    if (filters.assigneeId) params.set("assigneeId", filters.assigneeId);
    params.set("page", String(filters.page));
    params.set("size", String(filters.size));

    const { data } = await api.get(`/tasks?${params.toString()}`);
    return data;
  },

  getById: async (id: string): Promise<TaskResponse> => {
    const { data } = await api.get(`/tasks/${id}`);
    return data;
  },

  create: async (payload: TaskUpsertRequest): Promise<TaskResponse> => {
    const { data } = await api.post(`/tasks`, payload);
    return data;
  },

  update: async (id: string, payload: TaskUpsertRequest): Promise<TaskResponse> => {
    const { data } = await api.put(`/tasks/${id}`, payload);
    return data;
  },

  updateStatus: async (id: string, payload: TaskStatusUpdateRequest): Promise<TaskResponse> => {
    const { data } = await api.patch(`/tasks/${id}/status`, payload);
    return data;
  },

  remove: async (id: string): Promise<void> => {
    await api.delete(`/tasks/${id}`);
  },
};
