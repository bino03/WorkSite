// EnterpriseContextMenu.tsx
// Menu de contexto (botão direito) numa linha de empreendimento — mesmo padrão do
// `components/employees/EmployeeContextMenu.tsx`.
//
// Uso:
//   <Table onRow={(record) => ({ onContextMenu: (e) => handleContextMenu(e, record) })} />
//   <EnterpriseContextMenu ... />

import React, { useEffect, useRef } from 'react';
import {
  EyeOutlined,
  FileTextOutlined,
  FileDoneOutlined,
  DownloadOutlined,
  DeleteOutlined,
  HomeOutlined,
} from '@ant-design/icons';
import type { Enterprise } from '@/pages/enterprises/EnterprisesList';

interface EnterpriseContextMenuProps {
  visible: boolean;
  x: number;
  y: number;
  record: Enterprise | null;
  onClose: () => void;
  onViewDetails: (id: string) => void;
  onViewBudget: (id: string) => void;
  onViewInvoices: (id: string) => void;
  onExport: (id: string) => void;
  onDelete: (record: Enterprise) => void;
}

const EnterpriseContextMenu: React.FC<EnterpriseContextMenuProps> = ({
  visible, x, y, record, onClose, onViewDetails, onViewBudget, onViewInvoices, onExport, onDelete,
}) => {
  const menuRef = useRef<HTMLDivElement>(null);

  // Fechar ao clicar fora ou Escape
  useEffect(() => {
    if (!visible) return;
    const close = () => onClose();
    const esc = (e: KeyboardEvent) => { if (e.key === 'Escape') onClose(); };

    const timer = setTimeout(() => {
      document.addEventListener('click', close);
      document.addEventListener('contextmenu', close);
      document.addEventListener('keydown', esc);
    }, 0);

    return () => {
      clearTimeout(timer);
      document.removeEventListener('click', close);
      document.removeEventListener('contextmenu', close);
      document.removeEventListener('keydown', esc);
    };
  }, [visible, onClose]);

  // Ajustar posição se sair do ecrã
  useEffect(() => {
    if (visible && menuRef.current) {
      const rect = menuRef.current.getBoundingClientRect();
      if (rect.right > window.innerWidth) {
        menuRef.current.style.left = `${x - rect.width}px`;
      }
      if (rect.bottom > window.innerHeight) {
        menuRef.current.style.top = `${y - rect.height}px`;
      }
    }
  }, [visible, x, y]);

  if (!visible || !record) return null;

  type MenuItem = {
    icon: React.ReactNode;
    label: string;
    color: string;
    danger?: boolean;
    onClick: () => void;
  };

  const items: MenuItem[] = [
    {
      icon: <EyeOutlined />,
      label: 'Ver detalhes',
      color: '#1890ff',
      onClick: () => { onClose(); onViewDetails(record.id); },
    },
    {
      icon: <FileTextOutlined />,
      label: 'Ver orçamento',
      color: '#1890ff',
      onClick: () => { onClose(); onViewBudget(record.id); },
    },
    {
      icon: <FileDoneOutlined />,
      label: 'Ver faturas',
      color: '#1890ff',
      onClick: () => { onClose(); onViewInvoices(record.id); },
    },
    {
      icon: <DownloadOutlined />,
      label: 'Exportar',
      color: '#1890ff',
      onClick: () => { onClose(); onExport(record.id); },
    },
    {
      icon: <DeleteOutlined />,
      label: 'Eliminar',
      color: '#ff4d4f',
      danger: true,
      onClick: () => { onClose(); onDelete(record); },
    },
  ];

  return (
    <div
      ref={menuRef}
      style={{
        position: 'fixed', top: y, left: x, zIndex: 9999,
        minWidth: '210px', background: 'white', borderRadius: '10px',
        boxShadow: '0 8px 32px rgba(0,0,0,0.14), 0 2px 8px rgba(0,0,0,0.08)',
        border: '1px solid #e8e8e8', padding: '6px',
        animation: 'ctxFadeIn 0.12s ease-out',
      }}
      onClick={(e) => e.stopPropagation()}
    >
      {/* Nome do empreendimento */}
      <div style={{ padding: '8px 12px 6px', borderBottom: '1px solid #f0f0f0', marginBottom: '4px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <HomeOutlined style={{ color: '#8c8c8c', fontSize: '12px' }} />
          <div style={{
            fontSize: '12px', fontWeight: 600, color: '#262626',
            whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: '200px',
          }}>
            {record.name}
          </div>
        </div>
      </div>

      {/* Items */}
      {items.map((item, idx) => (
        <React.Fragment key={idx}>
          {item.danger && <div style={{ height: '1px', background: '#f0f0f0', margin: '4px 8px' }} />}
          <button
            onClick={item.onClick}
            style={{
              display: 'flex', alignItems: 'center', gap: '10px', width: '100%',
              padding: '8px 12px', border: 'none', background: 'transparent',
              borderRadius: '6px', cursor: 'pointer',
              fontSize: '13px', fontWeight: 500,
              color: item.danger ? '#ff4d4f' : '#404040',
              transition: 'all 0.15s ease', textAlign: 'left',
            }}
            onMouseEnter={(e) => {
              e.currentTarget.style.background = item.danger ? '#fff1f0' : '#f5f5f5';
              e.currentTarget.style.color = item.color;
            }}
            onMouseLeave={(e) => {
              e.currentTarget.style.background = 'transparent';
              e.currentTarget.style.color = item.danger ? '#ff4d4f' : '#404040';
            }}
          >
            <span style={{ fontSize: '14px', color: item.color, display: 'flex', alignItems: 'center' }}>
              {item.icon}
            </span>
            {item.label}
          </button>
        </React.Fragment>
      ))}

      <style>{`
        @keyframes ctxFadeIn {
          from { opacity: 0; transform: scale(0.95) translateY(-4px); }
          to { opacity: 1; transform: scale(1) translateY(0); }
        }
      `}</style>
    </div>
  );
};

export default EnterpriseContextMenu;
