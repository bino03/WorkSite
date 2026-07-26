// EmployeeContextMenu.tsx
// Menu de contexto (botão direito) numa linha de funcionário
//
// Uso:
//   <Table onRow={(record) => ({ onContextMenu: (e) => handleContextMenu(e, record) })} />
//   <EmployeeContextMenu ... />

import React, { useEffect, useRef } from 'react';
import { useTranslation } from 'react-i18next';
import { message, Modal } from 'antd';
import {
  EyeOutlined,
  LockOutlined,
  UnlockOutlined,
  DeleteOutlined,
  UserOutlined,
} from '@ant-design/icons';
import { patchAccountStatus, markAccountDeleted } from '@/services/profileService';
import type { Employee, AccountStatus } from '@/services/profileService';

interface EmployeeContextMenuProps {
  visible: boolean;
  x: number;
  y: number;
  record: Employee | null;
  onClose: () => void;
  onView?: (id: string) => void;
  onStatusChanged?: (id: string, newStatus: AccountStatus) => void;
  onDeleted?: (id: string) => void;
}

const EmployeeContextMenu: React.FC<EmployeeContextMenuProps> = ({
  visible, x, y, record, onClose, onView, onStatusChanged, onDeleted,
}) => {
  const { t } = useTranslation();
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

  const isDeleted = record.status === 'deleted';
  const nextStatus: AccountStatus | null =
    record.status === 'unlocked' ? 'blocked'
    : (record.status === 'blocked' || record.status === 'locked') ? 'unlocked'
    : null;

  // ── Toggle Bloquear/Desbloquear ──
  const handleToggleStatus = async () => {
    onClose();
    if (!nextStatus) return;
    const isBlocking = nextStatus === 'blocked';
    const hide = message.loading(isBlocking ? t('employeeContext.blocking') : t('employeeContext.unblocking'), 0);
    try {
      await patchAccountStatus({ profileId: record.id, status: nextStatus });
      message.success(isBlocking ? t('employeeContext.blocked') : t('employeeContext.unblocked'));
      onStatusChanged?.(record.id, nextStatus);
    } catch {
      message.error(t('employeeContext.blockError'));
    } finally {
      hide();
    }
  };

  // ── Eliminar ──
  const handleDelete = () => {
    onClose();
    Modal.confirm({
      title: t('employeeContext.deleteTitle'),
      content: t('employeeContext.deleteConfirm', { name: record.name }),
      okText: t('employeeContext.deleteOk'),
      okType: 'danger',
      cancelText: t('employeeContext.deleteCancel'),
      onOk: async () => {
        const hide = message.loading(t('employeeContext.deleting'), 0);
        try {
          await markAccountDeleted(record.id);
          message.success(t('employeeContext.deleteSuccess'));
          onDeleted?.(record.id);
        } catch {
          message.error(t('employeeContext.deleteError'));
        } finally {
          hide();
        }
      },
    });
  };

  type MenuItem = {
    icon: React.ReactNode;
    label: string;
    color: string;
    disabled: boolean;
    danger?: boolean;
    onClick: () => void;
  };

  // ── Items do menu ──
  const items: MenuItem[] = [
    {
      icon: <EyeOutlined />,
      label: t('employeeContext.viewProfile'),
      color: '#1890ff',
      disabled: isDeleted,
      onClick: () => { onClose(); onView?.(record.id); },
    },
    ...(nextStatus && !isDeleted ? [{
      icon: nextStatus === 'blocked' ? <LockOutlined /> : <UnlockOutlined />,
      label: nextStatus === 'blocked' ? t('employeeContext.blockProfile') : t('employeeContext.unblockProfile'),
      color: nextStatus === 'blocked' ? '#fa8c16' : '#52c41a',
      disabled: false,
      onClick: handleToggleStatus,
    }] : []),
    ...(!isDeleted ? [{
      icon: <DeleteOutlined />,
      label: t('employeeContext.deleteProfile'),
      color: '#ff4d4f',
      disabled: false,
      danger: true as const,
      onClick: handleDelete,
    }] : []),
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
      {/* Nome do funcionário */}
      <div style={{ padding: '8px 12px 6px', borderBottom: '1px solid #f0f0f0', marginBottom: '4px' }}>
        <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
          <UserOutlined style={{ color: '#8c8c8c', fontSize: '12px' }} />
          <div style={{
            fontSize: '12px', fontWeight: 600, color: '#262626',
            whiteSpace: 'nowrap', overflow: 'hidden', textOverflow: 'ellipsis', maxWidth: '200px',
          }}>
            {record.name}
          </div>
        </div>
        <div style={{ fontSize: '10px', color: '#8c8c8c', marginTop: '2px', paddingLeft: '20px' }}>
          {record.email}
        </div>
      </div>

      {/* Items */}
      {items.map((item, idx) => (
        <React.Fragment key={idx}>
          {item.danger && <div style={{ height: '1px', background: '#f0f0f0', margin: '4px 8px' }} />}
          <button
            onClick={item.disabled ? undefined : item.onClick}
            disabled={item.disabled}
            style={{
              display: 'flex', alignItems: 'center', gap: '10px', width: '100%',
              padding: '8px 12px', border: 'none', background: 'transparent',
              borderRadius: '6px', cursor: item.disabled ? 'not-allowed' : 'pointer',
              fontSize: '13px', fontWeight: 500,
              color: item.disabled ? '#bfbfbf' : item.danger ? '#ff4d4f' : '#404040',
              transition: 'all 0.15s ease', textAlign: 'left', opacity: item.disabled ? 0.5 : 1,
            }}
            onMouseEnter={(e) => {
              if (item.disabled) return;
              e.currentTarget.style.background = item.danger ? '#fff1f0' : '#f5f5f5';
              e.currentTarget.style.color = item.color;
            }}
            onMouseLeave={(e) => {
              if (item.disabled) return;
              e.currentTarget.style.background = 'transparent';
              e.currentTarget.style.color = item.danger ? '#ff4d4f' : '#404040';
            }}
          >
            <span style={{ fontSize: '14px', color: item.disabled ? '#bfbfbf' : item.color, display: 'flex', alignItems: 'center' }}>
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

export default EmployeeContextMenu;
