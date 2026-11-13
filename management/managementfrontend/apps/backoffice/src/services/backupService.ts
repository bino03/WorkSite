import api from "@/api";
import { fileNameFromDisposition } from "@/utils/downloadBlob";

export interface BackupSelection {
  enterpriseId: string;
  budgetId?: string;
}

export interface BackupRequest {
  all: boolean;
  selections: BackupSelection[];
  includeCompany: boolean;
  includeUnidentified: boolean;
  includeWorkbooks: boolean;
  includeDocuments: boolean;
}

export interface BackupPreviewFolder {
  name: string;
  workbookName: string | null;
  documentCount: number;
  invoicesWithoutDocument: number;
  warnings: string[];
}

export interface BackupPreview {
  fileName: string;
  folders: BackupPreviewFolder[];
  totalDocuments: number;
  warnings: string[];
}

/**
 * `skipErrorNotification` nas duas chamadas: cancelar um pedido chega ao interceptor
 * global como falta de rede e mostrava "Sem ligação". O Drawer trata os erros.
 */
export async function previewBackup(request: BackupRequest, signal: AbortSignal): Promise<BackupPreview> {
  const { data } = await api.post<BackupPreview>("/backups/preview", request, {
    signal,
    skipErrorNotification: true,
  });
  return data;
}

/** O servidor escreve o zip em streaming, sem `Content-Length`: o progresso são os bytes recebidos. */
export async function downloadBackup(
  request: BackupRequest,
  fallbackFileName: string,
  signal: AbortSignal,
  onProgress: (receivedBytes: number) => void
): Promise<{ blob: Blob; fileName: string }> {
  const response = await api.post("/backups/download", request, {
    responseType: "blob",
    signal,
    skipErrorNotification: true,
    onDownloadProgress: (event) => onProgress(event.loaded),
  });
  return {
    blob: response.data,
    fileName: fileNameFromDisposition(response.headers["content-disposition"], fallbackFileName),
  };
}
