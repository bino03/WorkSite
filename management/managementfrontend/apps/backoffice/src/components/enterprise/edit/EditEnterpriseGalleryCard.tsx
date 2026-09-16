// src/components/enterprise/edit/EditEnterpriseGalleryCard.tsx
/* eslint-disable @typescript-eslint/no-explicit-any */
import React, { useState, useRef, useEffect } from "react";
import { useTranslation } from "react-i18next";
import {
  Row,
  Col,
  Button,
  Space,
  Image,
  List,
  Typography,
  Modal,
  Input,
  Form,
} from "antd";
import {
  FileImageOutlined,
  DeleteOutlined,
  PlusOutlined,
  EditOutlined,
  SaveOutlined,
  CloseOutlined,
} from "@ant-design/icons";
import api from "@/api";
import { ErrorHandler } from "@/errors/errorHandler";
import { notificationService } from "@/services/general/notificationService";
import { useConfirm } from "@/context/ConfirmDialogContext";
import BlueprintCard from "@/components/common/BlueprintCard";

const { Text } = Typography;

type Props = {
  data: any;
  onSave: (newData: any) => void;
  onCancel: () => void;
};

// Função para resolver a URL do banner do empreendimento
const resolveHeroUrl = (data: any) => {
  if (!data) return "/src/assets/images/enterprise/default.jpg";

  // Primeiro tenta usar o banner das media
  const bannerMedia = data.media?.find((m: any) => m?.type === "banner");
  if (bannerMedia) {
    return resolveMediaUrl(bannerMedia) || "/src/assets/images/enterprise/default.jpg";
  }

  // Fallback para imagem padrão
  return "/src/assets/images/enterprise/default.jpg";
};

// Função para resolver a URL de uma media
const resolveMediaUrl = (m: any): string | undefined => {
  if (m.url) return m.url;
  if (m.downloadUrl) {
    // Se downloadUrl começar com /, adiciona o base URL da API
    if (m.downloadUrl.startsWith("/")) {
      return `${import.meta.env.VITE_API_URL || "http://localhost:8080"}${m.downloadUrl}`;
    }
    return m.downloadUrl;
  }
  if (m.bucket && m.storageKey && import.meta.env.VITE_SUPABASE_URL) {
    const base = String(import.meta.env.VITE_SUPABASE_URL).replace(/\/+$/, "");
    return `${base}/storage/v1/object/public/${m.bucket}/${m.storageKey}`;
  }
  return undefined;
};

const EditEnterpriseGalleryCard: React.FC<Props> = ({ data, onSave, onCancel }) => {
  const { t } = useTranslation();
  const confirm = useConfirm();
  // Estados para o banner
  const [editingBanner, setEditingBanner] = useState(false);
  const [selectedBannerFile, setSelectedBannerFile] = useState<File | null>(null);
  const [bannerPreviewUrl, setBannerPreviewUrl] = useState<string | null>(null);
  const [uploadingBanner, setUploadingBanner] = useState(false);

  // Estados para a galeria
  const [localGallery, setLocalGallery] = useState<any[]>([]);
  const [savingGallery, setSavingGallery] = useState(false);
  const [removedOriginalPhotos, setRemovedOriginalPhotos] = useState<string[]>([]);

  // Estados para edição de nome
  const [editingPhotoName, setEditingPhotoName] = useState<{ id: string; currentName: string; isNew: boolean } | null>(null);
  const [photoNameInput, setPhotoNameInput] = useState("");
  const [savingPhotoName, setSavingPhotoName] = useState(false);

  const fileInputRef = useRef<HTMLInputElement>(null);
  const galleryFileInputRef = useRef<HTMLInputElement>(null);

  const hero = resolveHeroUrl(data);
  const fallback = "/src/assets/images/enterprise/default.jpg";

  const currentBannerUrl = bannerPreviewUrl || hero;

  // Calcular se há alterações pendentes
  const hasChanges = localGallery.some((item: any) => item.isNew) || removedOriginalPhotos.length > 0;

  // Inicializar galeria com dados originais (APENAS IMAGENS)
  useEffect(() => {
    const galleryItems = (data.media || []).filter((m: any) => m?.type === "image");
    const galleryResolved = galleryItems
      .map((m: any) => ({
        ...m,
        _url: resolveMediaUrl(m),
        isOriginal: true,
        displayName: m.altText || `Imagem ${m.sortOrder + 1}`,
        originalName: m.altText // Guardar o nome original para comparação
      }))
      .filter((m: any) => !!m._url);

    setLocalGallery(galleryResolved);
    setRemovedOriginalPhotos([]);
  }, [data]);

  // Handler para selecionar ficheiro do banner
  const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (!file) return;

    if (!file.type.startsWith('image/')) {
      notificationService.error(t('enterpriseEdit.selectImageFile'));
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      notificationService.error(t('enterpriseEdit.imageSizeLimit'));
      return;
    }

    setSelectedBannerFile(file);

    const previewUrl = URL.createObjectURL(file);
    setBannerPreviewUrl(previewUrl);
    setEditingBanner(true);

    if (fileInputRef.current) {
      fileInputRef.current.value = '';
    }
  };

  // Handler para selecionar ficheiros da galeria
  const handleGalleryFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
    const files = Array.from(event.target.files || []);
    if (files.length === 0) return;

    // Validar ficheiros
    for (const file of files) {
      if (!file.type.startsWith('image/')) {
        notificationService.error(t('enterpriseEdit.onlyImages'));
        return;
      }
      if (file.size > 10 * 1024 * 1024) {
        notificationService.error(t('enterpriseEdit.imageTooLarge', { name: file.name }));
        return;
      }
    }

    // Adicionar às fotos locais
    const newItems = files.map((file) => ({
      id: `temp-${Date.now()}-${Math.random()}`,
      file,
      _url: URL.createObjectURL(file),
      type: 'image',
      isNew: true,
      displayName: file.name, // Nome original do ficheiro por padrão
      originalName: file.name, // Guardar o nome original
      hasCustomName: false // Flag para indicar se o nome foi editado
    }));

    setLocalGallery(prev => [...prev, ...newItems]);
    notificationService.info(t('enterpriseEdit.photosAddedLocal', { count: files.length }));

    // Reset do input
    if (galleryFileInputRef.current) {
      galleryFileInputRef.current.value = '';
    }
  };

  // Handler para iniciar a seleção de ficheiro do banner
  const handleChangeBanner = () => {
    fileInputRef.current?.click();
  };

  // Handler para iniciar a seleção de ficheiros da galeria
  const handleAddPhotos = () => {
    galleryFileInputRef.current?.click();
  };

  // Handler para eliminar uma foto da galeria
  const handleDeletePhoto = (photoId: string) => {
    const photoToDelete = localGallery.find(item => item.id === photoId);

    if (photoToDelete) {
      if (photoToDelete.isNew) {
        URL.revokeObjectURL(photoToDelete._url);
      } else if (photoToDelete.isOriginal) {
        setRemovedOriginalPhotos(prev => [...prev, photoId]);
      }
    }

    setLocalGallery(prev => prev.filter(item => item.id !== photoId));
    notificationService.info(t('enterpriseEdit.photoRemovedLocal'));
  };

  const confirmDeletePhoto = (photoId: string) => {
    confirm({
      title: t('enterpriseEdit.deletePhoto'),
      message: t('common.cannotBeUndone'),
      onConfirm: () => handleDeletePhoto(photoId),
    });
  };

  // Handler para editar nome da foto
  const handleEditPhotoName = (photo: any) => {
    setEditingPhotoName({
      id: photo.id,
      currentName: photo.displayName || photo.originalName || 'Sem nome',
      isNew: photo.isNew
    });
    setPhotoNameInput(photo.displayName || photo.originalName || '');
  };

  // Handler para guardar nome da foto
  const handleSavePhotoName = async () => {
    if (!editingPhotoName || !photoNameInput.trim()) return;

    setSavingPhotoName(true);
    try {
      // Atualizar o estado local
      const updatedGallery = localGallery.map(item =>
        item.id === editingPhotoName.id
          ? {
            ...item,
            displayName: photoNameInput.trim(),
            hasCustomName: photoNameInput.trim() !== item.originalName
          }
          : item
      );

      setLocalGallery(updatedGallery);

      // Se a foto já existe no backend (não é nova), atualizar via API
      if (!editingPhotoName.isNew) {
        await api.patch(`/enterprises/${data.id}/media/${editingPhotoName.id}`, {
          altText: photoNameInput.trim()
        });

        // Recarregar os dados para garantir que o EnterpriseViewDrawer tem os dados atualizados
        const response = await api.get(`/enterprises/${data.id}`);
        onSave(response.data);

        notificationService.success(t('enterpriseEdit.photoNameUpdated'));
      } else {
        notificationService.success(t('enterpriseEdit.photoNameLocal'));
      }

      setEditingPhotoName(null);
      setPhotoNameInput("");

    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSavingPhotoName(false);
    }
  };

  // Handler para cancelar edição de nome
  const handleCancelEditPhotoName = () => {
    setEditingPhotoName(null);
    setPhotoNameInput("");
  };

  // Handler para cancelar a edição do banner
  const handleCancelEdit = () => {
    if (bannerPreviewUrl) {
      URL.revokeObjectURL(bannerPreviewUrl);
    }

    setEditingBanner(false);
    setSelectedBannerFile(null);
    setBannerPreviewUrl(null);
  };

  // Handler para guardar o novo banner
  const handleSaveBanner = async () => {
    if (!selectedBannerFile) return;

    setUploadingBanner(true);
    try {
      const formData = new FormData();
      formData.append('banner', selectedBannerFile);

      const response = await api.post(`/enterprises/${data.id}/photos/banner`, formData, {
        headers: {
          'Content-Type': 'multipart/form-data',
        },
      });

      notificationService.success(t('enterpriseEdit.bannerUpdated'));

      const updatedEnterprise = response.data.enterprise || {
        ...data,
        bannerUrl: response.data.bannerUrl
      };

      onSave(updatedEnterprise);
      handleCancelEdit();

    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setUploadingBanner(false);
    }
  };

  // Handler para eliminar banner
  const handleDeleteBanner = async () => {
    try {
      await api.delete(`/enterprises/${data.id}/photos/banner`);

      notificationService.success(t('enterpriseEdit.bannerRemoved'));

      const response = await api.get(`/enterprises/${data.id}`);
      onSave(response.data);
    } catch (error) {
      ErrorHandler.handle(error);
    }
  };

  const confirmDeleteBanner = () => {
    confirm({
      title: t('enterpriseEdit.deleteBanner'),
      message: t('enterpriseGalleryEdit.deleteBannerConfirm'),
      actionLabel: t('enterpriseEdit.deleteBanner'),
      onConfirm: handleDeleteBanner,
    });
  };

  // Handler para guardar alterações da galeria
  const handleSaveGallery = async () => {
    setSavingGallery(true);
    try {
      // 1. Identificar fotos novas para upload
      const newPhotos = localGallery.filter(item => item.isNew);

      // 2. Fazer upload das novas fotos
      if (newPhotos.length > 0) {
        const formData = new FormData();
        newPhotos.forEach(item => {
          formData.append('photos', item.file!);
        });

        const uploadResponse = await api.post(`/enterprises/${data.id}/addPhotos`, formData, {
          headers: {
            'Content-Type': 'multipart/form-data',
          },
        });

        // 3. Atualizar os nomes das fotos novas que foram editadas
        const uploadedPhotos = uploadResponse.data; // Assumindo que a resposta é um array de media

        // Para cada foto carregada, verificar se precisa de atualizar o nome
        for (const uploadedPhoto of uploadedPhotos) {
          const localPhoto = newPhotos.find(p =>
            // Tentar encontrar pela correspondência do nome original ou pelo displayName
            p.originalName === uploadedPhoto.altText ||
            p.displayName === uploadedPhoto.altText
          );

          if (localPhoto && localPhoto.hasCustomName && localPhoto.displayName !== uploadedPhoto.altText) {
            await api.patch(`/enterprises/${data.id}/media/${uploadedPhoto.id}`, {
              altText: localPhoto.displayName
            });
          }
        }
      }

      // 4. Remover fotos eliminadas
      for (const photoId of removedOriginalPhotos) {
        await api.delete(`/enterprises/${data.id}/media/${photoId}`);
      }

      // 5. Atualizar nomes das fotos existentes que foram editados
      const updatedOriginalPhotos = localGallery.filter(item =>
        item.isOriginal && item.displayName !== item.originalName
      );

      for (const photo of updatedOriginalPhotos) {
        await api.patch(`/enterprises/${data.id}/media/${photo.id}`, {
          altText: photo.displayName
        });
      }

      notificationService.success(t('enterpriseEdit.galleryUpdated'));

      // 6. Recarregar os dados atualizados
      const response = await api.get(`/enterprises/${data.id}`);
      onSave(response.data);

      // 7. Limpar estado de fotos removidas após sucesso
      setRemovedOriginalPhotos([]);

    } catch (error) {
      ErrorHandler.handle(error);
    } finally {
      setSavingGallery(false);
    }
  };

  // Handler para cancelar alterações da galeria
  const handleCancelGallery = () => {
    const galleryItems = (data.media || []).filter((m: any) => m?.type === "image");
    const galleryResolved = galleryItems
      .map((m: any) => ({
        ...m,
        _url: resolveMediaUrl(m),
        isOriginal: true,
        displayName: m.altText || `Imagem ${m.sortOrder + 1}`,
        originalName: m.altText
      }))
      .filter((m: any) => !!m._url);

    setLocalGallery(galleryResolved);
    setRemovedOriginalPhotos([]);
    notificationService.info(t('enterpriseEdit.galleryCancelled'));
  };

  // Handler para guardar tudo
  const handleSaveAll = () => {
    handleSaveGallery();
  };

  // Handler para cancelar tudo
  const handleCancelAll = () => {
    handleCancelGallery();
    onCancel();
  };

  return (
    <Space direction="vertical" className="w-full" size={16} style={{ width: "100%" }}>
      {/* Input de ficheiro hidden para banner */}
      <input
        type="file"
        ref={fileInputRef}
        onChange={handleFileSelect}
        accept="image/*"
        style={{ display: 'none' }}
      />

      {/* Input de ficheiro hidden para galeria (múltiplos) */}
      <input
        type="file"
        ref={galleryFileInputRef}
        onChange={handleGalleryFileSelect}
        accept="image/*"
        multiple
        style={{ display: 'none' }}
      />

      {/* Modal para editar nome da foto */}
      <Modal
        title={t("enterpriseGalleryEdit.editPhotoTitle")}
        open={!!editingPhotoName}
        onOk={handleSavePhotoName}
        onCancel={handleCancelEditPhotoName}
        confirmLoading={savingPhotoName}
        okText={t('common.save')}
        cancelText={t('common.cancel')}
      >
        <div style={{ marginBottom: '16px' }}>
          <Text type="secondary">
            {t('enterpriseGalleryEdit.editPhotoDesc')}
          </Text>
        </div>
        <Form layout="vertical">
          <Form.Item label={t('enterpriseEdit.photoNameLabel')}>
            <Input
              value={photoNameInput}
              onChange={(e) => setPhotoNameInput(e.target.value)}
              placeholder={t('enterpriseGalleryEdit.photoNamePlaceholder')}
              onPressEnter={handleSavePhotoName}
              autoFocus
            />
          </Form.Item>
        </Form>
      </Modal>

      {/* =================== BANNER =================== */}
      <BlueprintCard kicker={t('enterpriseGalleryEdit.editBannerTitle')} style={{ padding: "13.6px", gap: "13.6px" }}>
        <Row gutter={[20, 20]} align="middle">
          <Col xs={24} lg={14}>
            {/* Container da imagem */}
            <div style={{
              position: 'relative',
              overflow: 'hidden',
              border: '1px solid var(--ind-color-divider)'
            }}>
              <Image
                src={currentBannerUrl}
                alt={data?.name}
                width="100%"
                height={240}
                style={{ objectFit: "cover" }}
                fallback={fallback}
                preview={{ mask: "🔍 Ampliar" }}
              />

              {/* Badge de tipo */}
              <span
                className="ind-tag ind-tag-accent"
                style={{ position: 'absolute', top: 12, left: 12 }}
              >
                {editingBanner ? t('enterpriseGalleryEdit.bannerPreviewBadge') : t('enterpriseGalleryEdit.bannerMainBadge')}
              </span>

              {/* Overlay de preview */}
              {editingBanner && (
                <div style={{
                  position: 'absolute',
                  top: 0,
                  left: 0,
                  right: 0,
                  bottom: 0,
                  background: 'rgba(0,0,0,0.3)',
                  display: 'flex',
                  alignItems: 'center',
                  justifyContent: 'center',
                  color: 'white',
                  fontSize: '16px',
                  fontWeight: 500
                }}>
                  {t('enterpriseGalleryEdit.newImageSelected')}
                </div>
              )}
            </div>

            {/* Botões de ação do banner */}
            <div style={{ marginTop: '16px' }}>
              {!editingBanner ? (
                <Space>
                  <Button
                    type="primary"
                    icon={<EditOutlined />}
                    onClick={handleChangeBanner}
                  >
                    {t('enterpriseGalleryEdit.changeBanner')}
                  </Button>
                  <Button
                    danger
                    icon={<DeleteOutlined />}
                    disabled={!data.media?.find((m: any) => m?.type === "banner")}
                    onClick={confirmDeleteBanner}
                  >
                    {t('enterpriseGalleryEdit.deleteBanner')}
                  </Button>
                </Space>
              ) : (
                <Space>
                  <Button
                    type="primary"
                    icon={<SaveOutlined />}
                    onClick={handleSaveBanner}
                    loading={uploadingBanner}
                  >
                    {t('enterpriseGalleryEdit.saveBanner')}
                  </Button>
                  <Button
                    icon={<CloseOutlined />}
                    onClick={handleCancelEdit}
                    disabled={uploadingBanner}
                  >
                    {t('common.cancel')}
                  </Button>
                </Space>
              )}
            </div>
          </Col>

          <Col xs={24} lg={10}>
            <div>
              {/* Seção de informações */}
              <div style={{ marginBottom: '20px' }}>
                <Text type="secondary" style={{ display: 'block', marginBottom: '8px', fontWeight: 500 }}>
                  {editingBanner ? t('enterpriseGalleryEdit.confirmChangeTitle') : t('enterpriseGalleryEdit.bannerInfoTitle')}
                </Text>
                <Text style={{ fontSize: '14px', lineHeight: '1.6' }}>
                  {editingBanner
                    ? t('enterpriseGalleryEdit.confirmChangeDesc')
                    : t('enterpriseGalleryEdit.bannerInfoDesc')
                  }
                </Text>
              </div>

              {/* Especificações técnicas */}
              <div style={{
                padding: '13.6px',
                background: 'var(--ind-color-surface)',
                border: '1px solid var(--ind-color-divider)'
              }}>
                <Text type="secondary" style={{ display: 'block', marginBottom: '12px', fontWeight: 500 }}>
                  {t('enterpriseGalleryEdit.techSpecs')}
                </Text>
                <List
                  size="small"
                  dataSource={[
                    { k: t('enterpriseGalleryEdit.specType'), v: "Banner/Capa" },
                    { k: t('enterpriseGalleryEdit.specFormat'), v: "JPEG/WEBP" },
                    { k: t('enterpriseGalleryEdit.specOrientation'), v: "Horizontal (16:9)" },
                    { k: t('enterpriseGalleryEdit.specMaxSize'), v: "10MB" },
                  ]}
                  renderItem={(it) => (
                    <List.Item style={{ padding: "6px 0", border: 'none' }}>
                      <div style={{ display: 'flex', alignItems: 'center', width: '100%' }}>
                        <Text strong style={{ minWidth: '100px' }}>{it.k}:</Text>
                        <Text type="secondary">{it.v}</Text>
                      </div>
                    </List.Item>
                  )}
                />
              </div>
            </div>
          </Col>
        </Row>
      </BlueprintCard>

      {/* =================== GALERIA =================== */}
      <BlueprintCard kicker={t('enterpriseGalleryEdit.editGalleryTitle')} style={{ padding: "13.6px", gap: "13.6px" }}>
        {/* Cabeçalho da galeria com botão adicionar */}
        <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
          <div>
            <Text strong style={{ fontSize: '14px' }}>
              {t('enterpriseGalleryEdit.galleryCount', { count: localGallery.length })}
              {hasChanges && ` ${t('enterpriseGalleryEdit.galleryPendingChanges')}`}
            </Text>
            <Text type="secondary" style={{ fontSize: '12px', display: 'block' }}>
              {t('enterpriseGalleryEdit.galleryManage')}
            </Text>
          </div>

          <Button
            type="primary"
            icon={<PlusOutlined />}
            onClick={handleAddPhotos}
          >
            {t('enterpriseGalleryEdit.addPhotos')}
          </Button>
        </div>

        {localGallery.length ? (
          <div
            style={{
              display: "grid",
              gridTemplateColumns: "repeat(auto-fill, minmax(240px, 1fr))",
              gap: 13.6,
            }}
          >
            {localGallery.map((m: any, index) => (
              <div
                key={m.id}
                style={{
                  overflow: "hidden",
                  border: `1px solid ${m.isNew ? "var(--ind-color-accent)" : "var(--ind-color-divider)"}`,
                  position: "relative",
                }}
              >
                <Image
                  src={m._url}
                  alt={m.displayName || `Imagem ${index + 1}`}
                  height={160}
                  width="100%"
                  style={{ objectFit: "cover" }}
                  preview={{ mask: "🔍 Ver" }}
                />

                {/* Badge para fotos novas */}
                {m.isNew && (
                  <span className="ind-tag ind-tag-accent" style={{ position: 'absolute', top: 8, left: 8 }}>
                    {t('enterpriseGalleryEdit.newImageBadge')}
                  </span>
                )}

                {/* Overlay de ações */}
                <div style={{
                  position: 'absolute',
                  top: 8,
                  right: 8,
                  display: 'flex',
                  flexDirection: 'column',
                  gap: 8,
                }}>
                  <Button
                    size="small"
                    shape="circle"
                    icon={<EditOutlined />}
                    onClick={() => handleEditPhotoName(m)}
                  />
                  <Button
                    danger
                    size="small"
                    shape="circle"
                    icon={<DeleteOutlined />}
                    onClick={() => confirmDeletePhoto(m.id)}
                  />
                </div>

                {/* Nome da imagem */}
                <div style={{
                  position: 'absolute',
                  bottom: 8,
                  left: 8,
                  right: 8,
                  background: 'rgba(0,0,0,0.7)',
                  color: 'white',
                  padding: '6px 8px',
                  fontSize: '12px',
                  fontWeight: 500,
                  textOverflow: 'ellipsis',
                  overflow: 'hidden',
                  whiteSpace: 'nowrap'
                }}>
                  {m.displayName}
                  {m.hasCustomName && " ✏️"}
                </div>

                {/* Número da imagem */}
                <div style={{
                  position: 'absolute',
                  top: 8,
                  left: 8,
                  background: 'rgba(0,0,0,0.7)',
                  color: 'white',
                  padding: '2px 8px',
                  fontSize: '12px',
                  fontWeight: 500
                }}>
                  #{index + 1}
                </div>
              </div>
            ))}
          </div>
        ) : (
          /* Estado vazio */
          <div style={{ textAlign: 'center', padding: '40px 20px' }}>
            <FileImageOutlined style={{ fontSize: '48px', marginBottom: '20px', opacity: 0.3 }} />
            <div style={{ fontSize: '16px', marginBottom: '8px' }}>{t('enterpriseGalleryEdit.emptyGallery')}</div>
            <Text type="secondary" style={{ display: 'block', marginBottom: '20px' }}>
              {t('enterpriseGalleryEdit.emptyGalleryDesc')}
            </Text>
            <Button
              type="primary"
              icon={<PlusOutlined />}
              onClick={handleAddPhotos}
            >
              {t('enterpriseGalleryEdit.addFirstPhoto')}
            </Button>
          </div>
        )}

        {/* Botões de ação globais */}
        <div style={{
          paddingTop: '13.6px',
          borderTop: '1px solid var(--ind-color-divider)',
          display: 'flex',
          justifyContent: 'space-between',
          alignItems: 'center'
        }}>
          <div>
            {hasChanges && (
              <Text type="warning">
                {t('enterpriseGalleryEdit.pendingChanges')}
                {removedOriginalPhotos.length > 0 && ` ${t('enterpriseGalleryEdit.photosRemoved', { count: removedOriginalPhotos.length })}`}
              </Text>
            )}
          </div>

          <Space>
            <Button onClick={handleCancelAll}>
              {t('common.cancel')}
            </Button>
            <Button
              type="primary"
              onClick={handleSaveAll}
              loading={savingGallery}
              disabled={!hasChanges}
            >
              {t('common.saveChanges')}
            </Button>
          </Space>
        </div>
      </BlueprintCard>
    </Space>
  );
};

export default EditEnterpriseGalleryCard;
