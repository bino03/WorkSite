export type TaskStatus = "PENDING" | "IN_PROGRESS" | "DONE";

export interface TaskAssigneeSummary {
  id: string;
  name: string;
}

export interface TaskResponse {
  id: string;
  name: string;
  description: string | null;
  dueDate: string;
  status: TaskStatus;
  createdBy: TaskAssigneeSummary | null;
  assignees: TaskAssigneeSummary[];
  createdAt: string;
  updatedAt: string;
}

export interface TaskUpsertRequest {
  name: string;
  description?: string;
  dueDate: string;
  assigneeIds: string[];
}

export interface TaskStatusUpdateRequest {
  status: TaskStatus;
}

export interface TaskListFilters {
  q?: string;
  status?: TaskStatus | null;
  assigneeId?: string | null;
  page: number;
  size: number;
}

export interface SpringPageMeta {
  number: number;
  size: number;
  totalElements: number;
  totalPages: number;
}

export interface TaskListResponse {
  content: TaskResponse[];
  page: SpringPageMeta;
}
