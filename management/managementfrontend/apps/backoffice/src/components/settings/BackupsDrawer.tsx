import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import type { CSSProperties, FC, Key } from "react";
import { Alert, Button, Checkbox, Drawer, Empty, Space, Spin, Switch, Tag, Tree } from "antd";
import type { TreeDataNode } from "antd";
import { CloudDownloadOutlined } from "@ant-design/icons";
import axios from "axios";

import {
  downloadBackup,
  previewBackup,
  type BackupPreview,
  type BackupRequest,
  type BackupSelection,
} from "@/services/backupService";
import { listEnterprisesForBackup, type BackupEnterpriseOption } from "@/services/enterpriseService";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { downloadBlob } from "@/utils/downloadBlob";

interface Props {
  open: boolean;
  onClose: () => void;
}

const PREVIEW_DEBOUNCE_MS = 400;
const BYTES_IN_MB = 1024 * 1024;

const enterpriseKey = (enterpriseId: string) => `e:${enterpriseId}`;
const lotKey = (enterpriseId: string, lotId: string) => `l:${enterpriseId}:${lotId}`;
const COMPANY_KEY = "company";
const UNIDENTIFIED_KEY = "unidentified";
const WITHOUT_ENTERPRISE_NODES: TreeDataNode[] = [
  { key: COMPANY_KEY, title: "Despesas da empresa" },
  { key: UNIDENTIFIED_KEY, title: "Faturas por identificar" },
];

function selectionOf(key: string): BackupSelection {
  const [kind, enterpriseId, budgetId] = key.split(":");
  return kind === "e" ? { enterpriseId } : { enterpriseId, budgetId };
}

/**
 * Backups: descarregar o Excel e os ficheiros de obras e lotes, num só zip.
 *
 * Fica montada com `open=false`, por isso só pede dados ao abrir. Fechar não cancela:
 * a transferência continua e o ficheiro grava-se na mesma. Para parar é "Cancelar".
 */
export const BackupsDrawer: FC<Props> = ({ open, onClose }) => {
  const [enterprises, setEnterprises] = useState<BackupEnterpriseOption[]>([]);
  const [loading, setLoading] = useState(false);
  const [everything, setEverything] = useState(false);
  const [checkedKeys, setCheckedKeys] = useState<string[]>([]);
  const [includeWorkbooks, setIncludeWorkbooks] = useState(true);
  const [includeDocuments, setIncludeDocuments] = useState(true);
  const [preview, setPreview] = useState<BackupPreview | null>(null);
  const [previewLoading, setPreviewLoading] = useState(false);
  const [downloading, setDownloading] = useState(false);
  const [receivedBytes, setReceivedBytes] = useState(0);
  const downloadController = useRef<AbortController | null>(null);

  const load = useCallback(async () => {
    setLoading(true);
    try {
      setEnterprises(await listEnterprisesForBackup());
    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setLoading(false);
    }
  }, []);

  useEffect(() => {
    if (open) load();
  }, [open, load]);

  const hasSelection = everything || checkedKeys.length > 0;
  const hasContentType = includeWorkbooks || includeDocuments;
  const canDownload = hasSelection && hasContentType && (preview?.folders.length ?? 0) > 0 && !previewLoading;

  const request = useMemo<BackupRequest>(
    () => ({
      all: everything,
      selections: everything
        ? []
        : checkedKeys.filter((key) => key !== COMPANY_KEY && key !== UNIDENTIFIED_KEY).map(selectionOf),
      includeCompany: checkedKeys.includes(COMPANY_KEY),
      includeUnidentified: checkedKeys.includes(UNIDENTIFIED_KEY),
      includeWorkbooks,
      includeDocuments,
    }),
    [everything, checkedKeys, includeWorkbooks, includeDocuments]
  );

  useEffect(() => {
    if (!open || !hasSelection || !hasContentType) {
      setPreview(null);
      setPreviewLoading(false);
      return;
    }

    const controller = new AbortController();
    setPreviewLoading(true);
    const timer = setTimeout(async () => {
      try {
        const result = await previewBackup(request, controller.signal);
        setPreview(result);
        setPreviewLoading(false);
      } catch (error) {
        if (axios.isCancel(error)) return;
        setPreview(null);
        setPreviewLoading(false);
        ErrorHandler.handle(error);
      }
    }, PREVIEW_DEBOUNCE_MS);

    return () => {
      clearTimeout(timer);
      controller.abort();
    };
  }, [open, request, hasSelection, hasContentType]);

  const treeData = useMemo<TreeDataNode[]>(
    () => [
      ...enterprises.map((enterprise) => ({
        key: enterpriseKey(enterprise.id),
        title: (
          <span>
            {enterprise.name} {enterprise.isTest && <Tag>TESTE</Tag>}
          </span>
        ),
        // Com um só lote, escolher o lote ou a obra dava o mesmo: não se oferece
        children:
          enterprise.lots.length > 1
            ? enterprise.lots.map((lot) => ({
                key: lotKey(enterprise.id, lot.id),
                title: lot.name,
                // A API recusa a obra e um lote dela ao mesmo tempo
                disableCheckbox: checkedKeys.includes(enterpriseKey(enterprise.id)),
              }))
            : undefined,
      })),
      ...WITHOUT_ENTERPRISE_NODES,
    ],
    [enterprises, checkedKeys]
  );

  const handleCheck = (checked: Key[] | { checked: Key[]; halfChecked: Key[] }) => {
    const keys = (Array.isArray(checked) ? checked : checked.checked).map(String);
    const wholeEnterprises = keys.filter((key) => key.startsWith("e:")).map((key) => key.slice(2));
    setCheckedKeys(
      keys.filter((key) => !key.startsWith("l:") || !wholeEnterprises.includes(selectionOf(key).enterpriseId))
    );
  };

  const handleDownload = async () => {
    const controller = new AbortController();
    downloadController.current = controller;
    setDownloading(true);
    setReceivedBytes(0);
    try {
      const { blob, fileName } = await downloadBackup(
        request,
        preview?.fileName ?? "Backup Vilatro.zip",
        controller.signal,
        setReceivedBytes
      );
      // Só se grava no fim: um download interrompido nunca deixa um zip cortado
      downloadBlob(blob, fileName);
      notificationService.success("Backups", `${fileName} descarregado.`);
    } catch (error) {
      if (!axios.isCancel(error)) ErrorHandler.handle(error);
    } finally {
      downloadController.current = null;
      setDownloading(false);
    }
  };

  const summary = () => {
    if (!hasSelection) return <p style={HINT}>Escolha pelo menos um empreendimento.</p>;
    if (!hasContentType) return <p style={HINT}>Escolha o Excel, os ficheiros, ou os dois.</p>;
    if (!preview) return <Spin spinning={previewLoading} />;

    return (
      <Spin spinning={previewLoading}>
        <div style={SUMMARY}>
          {preview.folders.length > 0 && (
            <>
              <strong style={{ overflowWrap: "anywhere" }}>{preview.fileName}</strong>
              <span style={HINT}>
                {preview.folders.length} {preview.folders.length === 1 ? "pasta" : "pastas"} ·{" "}
                {preview.totalDocuments} {preview.totalDocuments === 1 ? "ficheiro" : "ficheiros"}
              </span>
            </>
          )}
          <ul style={LIST}>
            {preview.folders.map((folder) => (
              <li key={folder.name} style={FOLDER}>
                <span style={{ overflowWrap: "anywhere" }}>{folder.name}</span>
                <span style={HINT}>
                  {folder.workbookName ? "Excel · " : ""}
                  {folder.documentCount} {folder.documentCount === 1 ? "ficheiro" : "ficheiros"}
                  {folder.invoicesWithoutDocument > 0 && ` · ${folder.invoicesWithoutDocument} sem documento`}
                </span>
                {folder.warnings.map((warning) => (
                  <span key={warning} style={WARNING}>
                    {warning}
                  </span>
                ))}
              </li>
            ))}
          </ul>
          {preview.warnings.length > 0 && (
            <Alert
              type="warning"
              showIcon
              message="Fica de fora"
              description={preview.warnings.map((warning) => (
                <div key={warning}>{warning}</div>
              ))}
            />
          )}
        </div>
      </Spin>
    );
  };

  return (
    <Drawer
      open={open}
      onClose={onClose}
      width={640}
      title={
        <div>
          <h6 style={{ color: "var(--ind-accent-700)", margin: 0 }}>Definições</h6>
          <h2 style={{ margin: 0 }}>Backups</h2>
        </div>
      }
      footer={
        <Space style={{ display: "flex", justifyContent: "flex-end" }} wrap>
          {downloading && (
            <span style={HINT}>{(receivedBytes / BYTES_IN_MB).toFixed(1)} MB recebidos</span>
          )}
          <Button onClick={onClose}>Fechar</Button>
          {downloading ? (
            <Button danger onClick={() => downloadController.current?.abort()}>
              Cancelar
            </Button>
          ) : (
            <Button
              type="primary"
              icon={<CloudDownloadOutlined />}
              disabled={!canDownload}
              onClick={handleDownload}
            >
              Descarregar
            </Button>
          )}
        </Space>
      }
    >
      <Spin spinning={loading}>
        <div style={{ display: "flex", flexDirection: "column", gap: "13.6px" }}>
          <p style={HINT}>
            Um zip com o Excel e os ficheiros das faturas, com a data de hoje no nome das pastas. Não
            leva utilizadores, assiduidade, tarefas nem fotos das obras.
          </p>

          <span style={{ display: "inline-flex", alignItems: "center", gap: 8 }}>
            <Switch checked={everything} onChange={setEverything} disabled={downloading} /> Tudo
            <span style={HINT}>obras, despesas da empresa e faturas por identificar</span>
          </span>

          {enterprises.length === 0 && !loading && (
            <Empty image={Empty.PRESENTED_IMAGE_SIMPLE} description="Ainda não há empreendimentos." />
          )}
          <div style={TREE_BOX}>
            <Tree
              checkable
              checkStrictly
              selectable={false}
              disabled={everything || downloading}
              treeData={treeData}
              checkedKeys={{ checked: checkedKeys, halfChecked: [] }}
              onCheck={handleCheck}
            />
          </div>

          <Space size="large">
            <Checkbox
              checked={includeWorkbooks}
              disabled={downloading}
              onChange={(event) => setIncludeWorkbooks(event.target.checked)}
            >
              Excel
            </Checkbox>
            <Checkbox
              checked={includeDocuments}
              disabled={downloading}
              onChange={(event) => setIncludeDocuments(event.target.checked)}
            >
              Ficheiros
            </Checkbox>
          </Space>

          {summary()}
        </div>
      </Spin>
    </Drawer>
  );
};

const HINT: CSSProperties = { fontSize: 13, opacity: 0.75, margin: 0, lineHeight: 1.5 };

const WARNING: CSSProperties = { fontSize: 12, color: "var(--color-warning)" };

const LIST: CSSProperties = { listStyle: "none", margin: 0, padding: 0 };

const SUMMARY: CSSProperties = { display: "flex", flexDirection: "column", gap: 6 };

const FOLDER: CSSProperties = {
  display: "flex",
  flexDirection: "column",
  gap: 2,
  padding: "6.8px 0",
  borderTop: "1px solid var(--ind-color-divider)",
};

const TREE_BOX: CSSProperties = {
  maxHeight: 320,
  overflowY: "auto",
  border: "1px solid var(--ind-color-divider)",
  padding: "6.8px",
};
