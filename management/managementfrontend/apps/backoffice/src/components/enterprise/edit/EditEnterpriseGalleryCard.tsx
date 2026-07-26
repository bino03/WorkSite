// src/components/enterprise/edit/EditEnterpriseGalleryCard.tsx
/* eslint-disable @typescript-eslint/no-explicit-any */
import React, { useState, useRef, useEffect } from "react";
import { useTranslation } from "react-i18next";
import {
  Card,
  Row,
  Col,
  Button,
  Space,
  Image,
  List,
  Typography,
  message,
  Modal,
  Input,
  Form,
  Popconfirm,
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
      message.error(t('enterpriseEdit.selectImageFile'));
      return;
    }

    if (file.size > 10 * 1024 * 1024) {
      message.error(t('enterpriseEdit.imageSizeLimit'));
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
        message.error(t('enterpriseEdit.onlyImages'));
        return;
      }
      if (file.size > 10 * 1024 * 1024) {
        message.error(t('enterpriseEdit.imageTooLarge', { name: file.name }));
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
    message.info(t('enterpriseEdit.photosAddedLocal', { count: files.length }));

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
    message.info(t('enterpriseEdit.photoRemovedLocal'));
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

        message.success(t('enterpriseEdit.photoNameUpdated'));
      } else {
        message.success(t('enterpriseEdit.photoNameLocal'));
      }

      setEditingPhotoName(null);
      setPhotoNameInput("");

    } catch (error) {
      console.error('Erro ao atualizar nome da foto:', error);
      message.error(t('enterpriseEdit.photoNameError'));
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

      message.success(t('enterpriseEdit.bannerUpdated'));

      const updatedEnterprise = response.data.enterprise || {
        ...data,
        bannerUrl: response.data.bannerUrl
      };

      onSave(updatedEnterprise);
      handleCancelEdit();

    } catch (error) {
      console.error('Erro ao atualizar banner:', error);
      message.error(t('enterpriseEdit.bannerError'));
    } finally {
      setUploadingBanner(false);
    }
  };

  // Handler para eliminar banner
  const handleDeleteBanner = async () => {
    try {
      await api.delete(`/enterprises/${data.id}/photos/banner`);

      message.success(t('enterpriseEdit.bannerRemoved'));

      const response = await api.get(`/enterprises/${data.id}`);
      onSave(response.data);
    } catch (error) {
      console.error('Erro ao remover banner:', error);
      message.error(t('enterpriseEdit.bannerRemoveError'));
    }
  };

  // Handler para guardar alterações da galeria
  const handleSaveGallery = async () => {
    setSavingGallery(true);
    try {
      // 1. Identificar fotos novas para upload
      const newPhotos = localGallery.filter(item => item.isNew);

      console.log('DEBUG - Guardar galeria:', {
        newPhotos: newPhotos.map(p => ({ id: p.id, name: p.displayName, hasCustomName: p.hasCustomName })),
        removedPhotos: removedOriginalPhotos
      });

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

      message.success(t('enterpriseEdit.galleryUpdated'));

      // 6. Recarregar os dados atualizados
      const response = await api.get(`/enterprises/${data.id}`);
      onSave(response.data);

      // 7. Limpar estado de fotos removidas após sucesso
      setRemovedOriginalPhotos([]);

    } catch (error) {
      console.error('Erro ao guardar galeria:', error);
      message.error(t('enterpriseEdit.galleryError'));
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
    message.info(t('enterpriseEdit.galleryCancelled'));
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
    <Space direction="vertical" className="w-full" size={16}>
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

      {/* =================== CARD BANNER =================== */}
      <Card className="rounded-2xl shadow-sm" title={<span><EditOutlined /> {t('enterpriseGalleryEdit.editBannerTitle')}</span>}>
        {/* Cabeçalho informativo */}
        <div style={{
          padding: '12px 16px',
          backgroundColor: '#fafafa',
          borderRadius: '8px',
          marginBottom: '20px',
          border: '1px solid #e8e8e8'
        }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between' }}>
            <div>
              <Text strong style={{ fontSize: '14px' }}>{t('enterpriseGalleryEdit.bannerImage')}</Text>
              <Text type="secondary" style={{ fontSize: '12px', display: 'block' }}>
                {editingBanner ? t('enterpriseGalleryEdit.bannerPreviewDesc') : t('enterpriseGalleryEdit.bannerMainDesc')}
              </Text>
            </div>
            <FileImageOutlined style={{ fontSize: '20px', color: '#78716c', opacity: 0.6 }} />
          </div>
        </div>

        <Row gutter={[20, 20]} align="middle">
          <Col xs={24} lg={14}>
            {/* Container da imagem com efeitos */}
            <div style={{
              position: 'relative',
              borderRadius: '12px',
              overflow: 'hidden',
              boxShadow: '0 4px 12px rgba(0,0,0,0.1)',
              border: '1px solid #e8e8e8'
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
              <div style={{
                position: 'absolute',
                top: '12px',
                left: '12px',
                background: editingBanner ? '#52c41a' : 'rgba(120, 113, 108, 0.9)',
                color: 'white',
                padding: '4px 12px',
                borderRadius: '16px',
                fontSize: '12px',
                fontWeight: 500,
                backdropFilter: 'blur(4px)'
              }}>
                {editingBanner ? t('enterpriseGalleryEdit.bannerPreviewBadge') : t('enterpriseGalleryEdit.bannerMainBadge')}
              </div>

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
                  <Popconfirm
                    title={t('enterpriseEdit.deleteBanner')}
                    description={t('enterpriseGalleryEdit.deleteBannerConfirm')}
                    onConfirm={handleDeleteBanner}
                    okText={t('common.yes')}
                    cancelText={t('common.cancel')}
                    okButtonProps={{ danger: true }}
                  >
                    <Button
                      danger
                      icon={<DeleteOutlined />}
                      disabled={!data.media?.find((m: any) => m?.type === "banner")}
                    >
                      {t('enterpriseGalleryEdit.deleteBanner')}
                    </Button>
                  </Popconfirm>
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
            <div style={{ padding: '0 8px' }}>
              {/* Seção de informações */}
              <div style={{ marginBottom: '20px' }}>
                <Text type="secondary" style={{ display: 'block', marginBottom: '8px', fontWeight: 500 }}>
                  {editingBanner ? t('enterpriseGalleryEdit.confirmChangeTitle') : t('enterpriseGalleryEdit.bannerInfoTitle')}
                </Text>
                <Text style={{ fontSize: '14px', lineHeight: '1.6', color: '#595959' }}>
                  {editingBanner
                    ? t('enterpriseGalleryEdit.confirmChangeDesc')
                    : t('enterpriseGalleryEdit.bannerInfoDesc')
                  }
                </Text>
              </div>

              {/* Especificações técnicas */}
              <div style={{
                padding: '16px',
                backgroundColor: 'white',
                borderRadius: '8px',
                border: '1px solid #e8e8e8'
              }}>
                <Text type="secondary" style={{ display: 'block', marginBottom: '12px', fontWeight: 500 }}>
                  {t('enterpriseGalleryEdit.techSpecs')}
                </Text>
                <List
                  size="small"
                  dataSource={[
                    { k: t('enterpriseGalleryEdit.specType'), v: "Banner/Capa", icon: "🏷️" },
                    { k: t('enterpriseGalleryEdit.specFormat'), v: "JPEG/WEBP", icon: "📁" },
                    { k: t('enterpriseGalleryEdit.specOrientation'), v: "Horizontal (16:9)", icon: "📐" },
                    { k: t('enterpriseGalleryEdit.specMaxSize'), v: "10MB", icon: "💾" },
                  ]}
                  renderItem={(it) => (
                    <List.Item style={{ padding: "6px 0", border: 'none' }}>
                      <div style={{ display: 'flex', alignItems: 'center', width: '100%' }}>
                        <span style={{ marginRight: '8px' }}>{it.icon}</span>
                        <Text strong style={{ minWidth: '100px' }}>{it.k}:</Text>
                        <Text style={{ color: '#595959' }}>{it.v}</Text>
                      </div>
                    </List.Item>
                  )}
                />
              </div>
            </div>
          </Col>
        </Row>
      </Card>

      {/* =================== CARD GALERIA =================== */}
      <Card
        className="rounded-2xl shadow-sm"
        title={<span><FileImageOutlined /> {t('enterpriseGalleryEdit.editGalleryTitle')}</span>}
      >
        {/* Cabeçalho da galeria com botão adicionar */}
        <div style={{
          padding: '12px 16px',
          backgroundColor: '#fafafa',
          borderRadius: '8px',
          marginBottom: '20px',
          border: '1px solid #e8e8e8'
        }}>
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

            <Space>
              <Button
                type="primary"
                icon={<PlusOutlined />}
                onClick={handleAddPhotos}
              >
                {t('enterpriseGalleryEdit.addPhotos')}
              </Button>
            </Space>
          </div>
        </div>

        {localGallery.length ? (
          <>
            {/* Grid de imagens */}
            <div style={{ marginBottom: '16px' }}>
              <Text type="secondary" style={{ display: 'block', marginBottom: '12px', fontWeight: 500 }}>
                {t('enterpriseGalleryEdit.allImages')} {hasChanges && '📝'}
              </Text>
            </div>

            <div
              style={{
                display: "grid",
                gridTemplateColumns: "repeat(auto-fill, minmax(240px, 1fr))",
                gap: 16,
                padding: '16px',
                backgroundColor: 'white',
                borderRadius: '8px',
                border: '1px solid #e8e8e8'
              }}
            >
              {localGallery.map((m: any, index) => (
                <div
                  key={m.id}
                  style={{
                    borderRadius: 12,
                    overflow: "hidden",
                    border: "1px solid #e8e8e8",
                    position: "relative",
                    transition: 'all 0.3s ease',
                    boxShadow: '0 2px 8px rgba(0,0,0,0.06)',
                    borderColor: m.isNew ? '#78716c' : '#e8e8e8'
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
                    <div style={{
                      position: 'absolute',
                      top: '12px',
                      left: '12px',
                      background: '#52c41a',
                      color: 'white',
                      padding: '2px 8px',
                      borderRadius: '12px',
                      fontSize: '10px',
                      fontWeight: 500
                    }}>
                      {t('enterpriseGalleryEdit.newImageBadge')}
                    </div>
                  )}

                  {/* Overlay de ações */}
                  <div style={{
                    position: 'absolute',
                    top: 8,
                    right: 8,
                    display: 'flex',
                    flexDirection: 'column',
                    gap: 8,
                    opacity: 0.8,
                    transition: 'opacity 0.3s'
                  }}>
                    {/* Botão Editar Nome (Lápis Amarelo) */}
                    <Button
                      type="primary"
                      size="small"
                      icon={<EditOutlined />}
                      onClick={() => handleEditPhotoName(m)}
                      style={{
                        borderRadius: '50%',
                        width: '32px',
                        height: '32px',
                        display: 'flex',
                        alignItems: 'center',
                        justifyContent: 'center',
                        backgroundColor: '#faad14',
                        borderColor: '#faad14'
                      }}
                    />

                    {/* Botão Eliminar (Lixeira Vermelha) */}
                    <Popconfirm
                      title={t('enterpriseEdit.deletePhoto')}
                      description={t('common.cannotBeUndone')}
                      onConfirm={() => handleDeletePhoto(m.id)}
                      okText={t('common.yes')}
                      cancelText={t('common.cancel')}
                      okButtonProps={{ danger: true }}
                    >
                      <Button
                        type="primary"
                        danger
                        size="small"
                        icon={<DeleteOutlined />}
                        style={{
                          borderRadius: '50%',
                          width: '32px',
                          height: '32px',
                          display: 'flex',
                          alignItems: 'center',
                          justifyContent: 'center'
                        }}
                      />
                    </Popconfirm>
                  </div>

                  {/* Nome da imagem */}
                  <div style={{
                    position: 'absolute',
                    bottom: 8,
                    left: 8,
                    right: 8,
                    background: 'rgba(0,0,0,0.7)',
                    color: 'white',
                    borderRadius: '8px',
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
                    borderRadius: '12px',
                    padding: '2px 8px',
                    fontSize: '12px',
                    fontWeight: 500
                  }}>
                    #{index + 1}
                  </div>
                </div>
              ))}
            </div>
          </>
        ) : (
          /* Estado vazio */
          <div style={{
            textAlign: 'center',
            padding: '60px 20px',
            color: '#8c8c8c'
          }}>
            <FileImageOutlined style={{ fontSize: '64px', marginBottom: '20px', opacity: 0.3 }} />
            <div style={{ fontSize: '18px', marginBottom: '8px' }}>{t('enterpriseGalleryEdit.emptyGallery')}</div>
            <div style={{ fontSize: '14px', marginBottom: '20px' }}>
              {t('enterpriseGalleryEdit.emptyGalleryDesc')}
            </div>
            <Button
              type="primary"
              size="large"
              icon={<PlusOutlined />}
              onClick={handleAddPhotos}
              style={{ marginBottom: '20px' }}
            >
              {t('enterpriseGalleryEdit.addFirstPhoto')}
            </Button>
          </div>
        )}

        {/* Botões de ação globais */}
        <div style={{
          marginTop: '24px',
          paddingTop: '16px',
          borderTop: '1px solid #e8e8e8',
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
            <Button onClick={handleCancelAll} size="large">
              {t('common.cancel')}
            </Button>
            <Button
              type="primary"
              onClick={handleSaveAll}
              size="large"
              loading={savingGallery}
              disabled={!hasChanges}
            >
              {t('common.saveChanges')}
            </Button>
          </Space>
        </div>
      </Card>
    </Space>
  );
};

export default EditEnterpriseGalleryCard;