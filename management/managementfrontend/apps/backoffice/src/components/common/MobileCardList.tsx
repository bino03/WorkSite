import type { MouseEvent, ReactNode } from "react";
import { Empty, Pagination, Spin } from "antd";
import { MoreOutlined } from "@ant-design/icons";
import BlueprintCard from "./BlueprintCard";

interface MobileCardListProps<T> {
  items: T[];
  rowKey: (item: T) => string;
  /** O conteúdo do cartão; o "⋯" e o toque são tratados aqui. */
  renderCard: (item: T) => ReactNode;
  /** Tocar no cartão (o equivalente ao clique na linha da tabela). */
  onOpen?: (item: T) => void;
  /** O "⋯": no telemóvel não há clique-direito, abre o mesmo menu de contexto da tabela. */
  onMore?: (item: T, event: MouseEvent<HTMLButtonElement>) => void;
  loading?: boolean;
  emptyText?: ReactNode;
  pagination?: {
    current: number;
    pageSize: number;
    total: number;
    onChange: (page: number, pageSize: number) => void;
  };
}

/**
 * A lista de cartões que substitui uma `Table` abaixo de 768px (`useIsMobile`).
 * Cada tabela passa as mesmas colunas-chave em `renderCard` e o mesmo `onClick`/menu.
 */
export default function MobileCardList<T>({
  items,
  rowKey,
  renderCard,
  onOpen,
  onMore,
  loading = false,
  emptyText,
  pagination,
}: MobileCardListProps<T>) {
  return (
    <Spin spinning={loading}>
      {items.length === 0 && !loading ? (
        <Empty description={emptyText} image={Empty.PRESENTED_IMAGE_SIMPLE} />
      ) : (
        <div style={{ display: "flex", flexDirection: "column", gap: 10 }}>
          {items.map((item) => (
            <BlueprintCard
              key={rowKey(item)}
              onClick={onOpen ? () => onOpen(item) : undefined}
              style={{ padding: 12, cursor: onOpen ? "pointer" : undefined, position: "relative" }}
            >
              <div style={{ display: "flex", alignItems: "flex-start", gap: 8 }}>
                <div style={{ flex: 1, minWidth: 0 }}>{renderCard(item)}</div>
                {onMore && (
                  <button
                    type="button"
                    aria-label="Mais ações"
                    onClick={(e) => {
                      e.stopPropagation();
                      onMore(item, e);
                    }}
                    style={{
                      width: 44, height: 44, margin: "-8px -8px 0 0", flexShrink: 0,
                      display: "inline-flex", alignItems: "center", justifyContent: "center",
                      background: "none", border: "none", cursor: "pointer", color: "inherit", fontSize: 18,
                    }}
                  >
                    <MoreOutlined />
                  </button>
                )}
              </div>
            </BlueprintCard>
          ))}
        </div>
      )}
      {pagination && pagination.total > pagination.pageSize && (
        <div style={{ display: "flex", justifyContent: "center", marginTop: 14 }}>
          <Pagination
            simple
            current={pagination.current}
            pageSize={pagination.pageSize}
            total={pagination.total}
            showSizeChanger={false}
            onChange={pagination.onChange}
          />
        </div>
      )}
    </Spin>
  );
}
