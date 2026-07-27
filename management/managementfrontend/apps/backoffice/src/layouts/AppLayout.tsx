import { Outlet, NavLink } from "react-router-dom";
import { Settings, ChevronDown } from "lucide-react";
import MyProfileModal from "@/components/profile/MyProfileModal";
import { useState, useRef, useEffect } from "react";
import api from "@/api";
import { Avatar, Modal } from "antd";
import { UserOutlined, ExclamationCircleOutlined, GlobalOutlined, BuildOutlined, TeamOutlined, CheckSquareOutlined } from "@ant-design/icons";
import { useTranslation } from "react-i18next";
import { Switch } from "antd";
import { useAuthContext } from "@/context/AuthContext";

export default function AppLayout() {
  const { user, logout } = useAuthContext();
  const { t, i18n } = useTranslation();
  const [isProfileModalVisible, setIsProfileModalVisible] = useState(false);
  const [isSettingsDropdownOpen, setIsSettingsDropdownOpen] = useState(false);
  const settingsDropdownRef = useRef<HTMLDivElement>(null);

  const userPhoto = user?.photoUrl ?? null;
  const userName = user?.name ?? t('common.user');
  const userRole = user?.role ?? null;

  // Load avatar — Supabase signed URLs are used directly; relative paths go through the authenticated Axios instance
  const [avatarBlobUrl, setAvatarBlobUrl] = useState<string | null>(null);
  useEffect(() => {
    if (!userPhoto) { setAvatarBlobUrl(null); return; }

    if (userPhoto.startsWith('http://') || userPhoto.startsWith('https://')) {
      setAvatarBlobUrl(userPhoto);
      return;
    }

    let objectUrl: string;
    api.get(userPhoto, { responseType: 'blob' })
      .then(res => {
        objectUrl = URL.createObjectURL(res.data);
        setAvatarBlobUrl(objectUrl);
      })
      .catch(() => setAvatarBlobUrl(null));
    return () => { if (objectUrl) URL.revokeObjectURL(objectUrl); };
  }, [userPhoto]);

  const [isLogoutModalOpen, setIsLogoutModalOpen] = useState(false);

  useEffect(() => {
    function handleClickOutside(event: MouseEvent) {
      if (
        settingsDropdownRef.current &&
        !settingsDropdownRef.current.contains(event.target as Node)
      ) {
        setIsSettingsDropdownOpen(false);
      }
    }
    document.addEventListener("mousedown", handleClickOutside);
    return () => document.removeEventListener("mousedown", handleClickOutside);
  }, []);

  const handleLogoutClick = () => {
    setIsSettingsDropdownOpen(false);
    setIsLogoutModalOpen(true);
  };

  const handleConfirmLogout = async () => {
    setIsLogoutModalOpen(false);
    await logout();
  };

  const linkClass = ({ isActive }: { isActive: boolean }) =>
    [
      "inline-flex items-center px-4 py-2 rounded-lg text-[15px] font-medium transition-opacity duration-150 text-white",
      isActive ? "opacity-100" : "opacity-60 hover:opacity-100",
    ].join(" ");

  const handleProfileClick = () => {
    setIsProfileModalVisible(true);
  };

  const handleCloseProfileModal = () => {
    setIsProfileModalVisible(false);
  };

  const getRoleText = () => {
    return userRole === "ADMIN" ? t('profile.roles.admin') : t('profile.roles.employee');
  };

  const getBasePath = () => {
    return "/backoffice";
  };

  return (
    <div className="min-h-screen" style={{ backgroundColor: "#edecea" }}>
      {/* topbar fixa */}
      <header
        className="sticky top-0 z-40"
        style={{
          backgroundColor: "#30302e",
          borderBottom: "1px solid #3d3d3a",
        }}
      >
        <div className="container-page h-16 flex items-center gap-6">
          <div className="text-lg tracking-tight select-none" style={{ fontFamily: "Georgia, serif", fontWeight: 500, color: "#141413" }}>
            Worksite
          </div>

          <nav className="flex items-center gap-1">
            <NavLink to={`${getBasePath()}`} end className={linkClass}>{t('nav.home')}</NavLink>

            <NavLink to={`${getBasePath()}/empreendimentos`} className={linkClass}>
              <BuildOutlined className="mr-2" />{t('nav.enterprises')}
            </NavLink>

            <NavLink to={`${getBasePath()}/tasks`} className={linkClass}>
              <CheckSquareOutlined className="mr-2" />{userRole === "EMPLOYEE" ? "Minhas Tarefas" : "Tarefas"}
            </NavLink>

            {userRole === "ADMIN" && (
              <NavLink to={`${getBasePath()}/funcionarios`} className={linkClass}>
                <TeamOutlined className="mr-2" />{t('nav.manageAccounts')}
              </NavLink>
            )}
          </nav>

          <div className="ml-auto flex items-center gap-3">
            {/* Card de perfil do utilizador */}
            <div
              onClick={handleProfileClick}
              style={{
                display: 'flex',
                alignItems: 'center',
                gap: '12px',
                padding: '7px 18px 7px 7px',
                borderRadius: '40px',
                cursor: 'pointer',
                background: 'rgba(255,255,255,0.07)',
                border: '1px solid rgba(255,255,255,0.11)',
                transition: 'background 0.18s ease, border-color 0.18s ease',
                userSelect: 'none',
              }}
              onMouseEnter={e => {
                (e.currentTarget as HTMLDivElement).style.background = 'rgba(255,255,255,0.13)';
                (e.currentTarget as HTMLDivElement).style.borderColor = 'rgba(255,255,255,0.2)';
              }}
              onMouseLeave={e => {
                (e.currentTarget as HTMLDivElement).style.background = 'rgba(255,255,255,0.07)';
                (e.currentTarget as HTMLDivElement).style.borderColor = 'rgba(255,255,255,0.11)';
              }}
            >
              {/* Avatar com anel terracotta + ponto de estado */}
              <div style={{ position: 'relative', flexShrink: 0 }}>
                <Avatar
                  size={36}
                  src={avatarBlobUrl}
                  style={{
                    backgroundColor: userPhoto ? 'transparent' : '#c96442',
                    boxShadow: '0 0 0 2px #c96442, 0 0 0 4px rgba(201,100,66,0.22)',
                    flexShrink: 0,
                  }}
                  icon={!avatarBlobUrl && <UserOutlined />}
                />
                <span
                  style={{
                    position: 'absolute',
                    bottom: 0,
                    right: 0,
                    width: 9,
                    height: 9,
                    borderRadius: '50%',
                    background: '#4ade80',
                    border: '2px solid #30302e',
                    display: 'block',
                  }}
                />
              </div>

              <div style={{ minWidth: 0 }}>
                <div
                  style={{
                    fontSize: 14,
                    fontWeight: 500,
                    color: '#f5f4ed',
                    lineHeight: 1.25,
                    overflow: 'hidden',
                    textOverflow: 'ellipsis',
                    whiteSpace: 'nowrap',
                    maxWidth: 150,
                  }}
                >
                  {userName}
                </div>
                <div
                  style={{
                    fontSize: 11,
                    fontWeight: 600,
                    letterSpacing: '0.55px',
                    textTransform: 'uppercase',
                    color: userRole === 'ADMIN' ? '#d97757' : '#b0aea5',
                    lineHeight: 1.3,
                  }}
                >
                  {getRoleText()}
                </div>
              </div>
            </div>

            {/* Botão com dropdown das Settings */}
            <div
              className="relative"
              ref={settingsDropdownRef}
              onMouseEnter={() => setIsSettingsDropdownOpen(true)}
              onMouseLeave={() => setIsSettingsDropdownOpen(false)}
            >
              <button
                className="inline-flex items-center gap-1 p-2 rounded-lg transition-colors"
                style={{
                  border: "1px solid #e8e6dc",
                  backgroundColor: "#f5f4ed",
                  color: "#5e5d59",
                }}
                onMouseEnter={e => (e.currentTarget.style.backgroundColor = "#f0eee6")}
                onMouseLeave={e => (e.currentTarget.style.backgroundColor = "#f5f4ed")}
              >
                <Settings className="w-5 h-5" />
                <ChevronDown className={`w-4 h-4 transition-transform ${isSettingsDropdownOpen ? 'rotate-180' : ''}`} />
              </button>

              {isSettingsDropdownOpen && (
                <div className="absolute right-0 top-full pt-2 w-48 z-50">
                  <div
                    className="rounded-lg overflow-hidden"
                    style={{
                      backgroundColor: "#faf9f5",
                      border: "1px solid #f0eee6",
                      boxShadow: "rgba(0,0,0,0.08) 0px 8px 24px",
                    }}
                  >
                    <button
                      onClick={() => {
                        handleProfileClick();
                        setIsSettingsDropdownOpen(false);
                      }}
                      className="w-full flex items-center gap-2 px-4 py-3 text-left text-sm transition-colors"
                      style={{ color: "#5e5d59" }}
                      onMouseEnter={e => (e.currentTarget.style.backgroundColor = "#f0eee6")}
                      onMouseLeave={e => (e.currentTarget.style.backgroundColor = "")}
                    >
                      <span>{t('profile.myAccount')}</span>
                    </button>

                    <div style={{ borderTop: "1px solid #f0eee6" }} />

                    {/* Switch de idioma */}
                    <div
                      className="flex items-center justify-between px-4 py-3"
                      style={{ fontSize: 14 }}
                    >
                      <div className="flex items-center gap-2" style={{ color: "#5e5d59" }}>
                        <GlobalOutlined style={{ fontSize: 14 }} />
                        <span>{t('profile.language')}</span>
                      </div>
                      <Switch
                        size="small"
                        checked={i18n.language === 'en'}
                        onChange={(checked) => {
                          const lang = checked ? 'en' : 'pt';
                          i18n.changeLanguage(lang);
                          localStorage.setItem('language', lang);
                        }}
                        checkedChildren="EN"
                        unCheckedChildren="PT"
                      />
                    </div>

                    <div style={{ borderTop: "1px solid #f0eee6" }} />

                    <button
                      onClick={() => {
                        handleLogoutClick();
                        setIsSettingsDropdownOpen(false);
                      }}
                      className="w-full flex items-center gap-2 px-4 py-3 text-left text-sm transition-colors"
                      style={{ color: "#b53333" }}
                      onMouseEnter={e => (e.currentTarget.style.backgroundColor = "#fdf0f0")}
                      onMouseLeave={e => (e.currentTarget.style.backgroundColor = "")}
                    >
                      <span>{t('profile.logout')}</span>
                    </button>
                  </div>
                </div>
              )}
            </div>
          </div>
        </div>
      </header>

      {/* conteúdo das páginas */}
      <main className="container-page py-8">
        <Outlet />
      </main>

      {/* Modal de perfil */}
      {isProfileModalVisible && (
        <MyProfileModal onClose={handleCloseProfileModal} />
      )}

      {/* Modal de logout */}
      <Modal
        open={isLogoutModalOpen}
        onCancel={() => setIsLogoutModalOpen(false)}
        onOk={handleConfirmLogout}
        title={
          <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
            <ExclamationCircleOutlined style={{ color: '#ff4d4f', fontSize: '20px' }} />
            <span>{t('logout.title')}</span>
          </div>
        }
        okText={t('logout.yes')}
        cancelText={t('logout.cancel')}
        okButtonProps={{
          danger: true,
          size: 'large'
        }}
        cancelButtonProps={{
          size: 'large'
        }}
        centered
        width={400}
      >
        <p style={{ fontSize: '18px', marginTop: '16px' }}>
          {t('logout.confirm')}
        </p>
        <p style={{ fontSize: '16px', color: '#8c8c8c', marginTop: '8px' }}>
          {t('logout.redirect')}
        </p>
      </Modal>
    </div>
  );
}
