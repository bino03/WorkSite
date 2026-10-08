import { useState } from "react";
import { Modal } from "antd";
import { AppstoreOutlined } from "@ant-design/icons";
import { useLocation, useNavigate } from "react-router-dom";
import { useAuth } from "@/hooks/useAuth";
import { rememberSpace, spaceOfPath, visibleSpaces } from "./spaces";
import type { SpaceDefinition } from "./spaces";

/**
 * O lançador de espaços, à maneira do Win+Tab: um botão de grelha à esquerda do
 * wordmark abre um painel por cima de tudo com os espaços em mosaicos. Escolher um
 * troca a shell inteira. Quem só tem um espaço (EMPLOYEE) não vê o botão — um
 * lançador com uma opção é ruído.
 */
export function SpaceLauncher() {
  const { isAdmin } = useAuth();
  const navigate = useNavigate();
  const { pathname } = useLocation();
  const [open, setOpen] = useState(false);

  const spaces = visibleSpaces(isAdmin());
  const current = spaceOfPath(pathname);

  if (spaces.length < 2) {
    return null;
  }

  const pick = (space: SpaceDefinition) => {
    setOpen(false);
    rememberSpace(space.id);
    if (space.id !== current) {
      navigate(space.basePath);
    }
  };

  return (
    <>
      <button
        type="button"
        className="space-launcher-trigger"
        aria-label="Mudar de espaço"
        aria-haspopup="dialog"
        onClick={() => setOpen(true)}
      >
        <AppstoreOutlined />
      </button>

      <Modal
        open={open}
        onCancel={() => setOpen(false)}
        footer={null}
        closable={false}
        centered
        width={700}
        title={null}
        styles={{
          mask: { background: "rgba(18, 22, 26, 0.72)" },
          content: { background: "transparent", boxShadow: "none", padding: 0 },
        }}
      >
        <div className="space-launcher-panel">
          <h2 className="space-launcher-heading">Escolher espaço</h2>
          <div className="space-launcher-tiles">
            {spaces.map((space) => (
              <button
                key={space.id}
                type="button"
                className={`space-tile space-tile-${space.id}${space.id === current ? " is-current" : ""}`}
                aria-current={space.id === current ? "page" : undefined}
                onClick={() => pick(space)}
              >
                <span className="space-tile-preview" aria-hidden="true">
                  <span className="space-tile-preview-header" />
                  <span className="space-tile-preview-body">
                    <span />
                    <span />
                    <span />
                  </span>
                </span>
                <span className="space-tile-title">
                  <span>{space.name}</span>
                  {space.id === current && <span className="space-tile-here">Aqui</span>}
                </span>
                <span className="space-tile-description">{space.description}</span>
              </button>
            ))}
          </div>
        </div>
      </Modal>
    </>
  );
}
