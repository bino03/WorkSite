// src/components/MediaUploadsSection.tsx

import { Upload, Input, Typography, message, Row, Col, Tag } from "antd";
import { useTranslation } from "react-i18next";
import type { UploadFile, UploadProps } from "antd";
import {
  PlusOutlined,
  PictureOutlined,
  VideoCameraOutlined,
} from "@ant-design/icons";
import BlueprintCard from "@/components/common/BlueprintCard";

const { Text } = Typography;

export type GalleryItem = {
  uid: string;            // do Upload
  file?: File;            // novo upload (originFileObj)
  name: string;
  mime?: string;
  type: "image" | "video";
  altText?: string;
  sortOrder: number;      // Agora obrigatório e automático
};

const IMG_ACCEPT = "image/jpeg,image/png,image/webp,image/avif";
const VID_ACCEPT = "video/mp4,video/webm";

const LIMIT_IMAGE_MB = 15;
const LIMIT_VIDEO_MB = 250;

function isImage(mime?: string) {
  return !!mime && mime.startsWith("image/");
}
function isVideo(mime?: string) {
  return !!mime && mime.startsWith("video/");
}

// beforeAnyUpload é criado dentro do componente para ter acesso a t()
// (mantemos a função aqui como fallback sem tradução, substituída abaixo no componente)

const getTypeIcon = (type: GalleryItem["type"]) => {
  switch (type) {
    case "image": return <PictureOutlined style={{ color: '#1890ff' }} />;
    case "video": return <VideoCameraOutlined style={{ color: '#52c41a' }} />;
    default: return <PictureOutlined />;
  }
};

// Função para obter cor do tipo
const getTypeColor = (type: GalleryItem["type"]) => {
  switch (type) {
    case "image": return "blue";
    case "video": return "green";
    default: return "default";
  }
};

// Função para obter label do tipo — lazy, retorna chave i18n
const getTypeLabelKey = (type: GalleryItem["type"]) => {
  switch (type) {
    case "image": return "upload.imageLabel";
    case "video": return "upload.videoLabel";
    default: return type;
  }
};

type Props = {
  // Banner
  bannerFileList: UploadFile[];
  setBannerFileList: (fl: UploadFile[]) => void;

  // Galeria
  galleryItems: GalleryItem[];
  setGalleryItems: (items: GalleryItem[]) => void;
};

export default function MediaUploadsSection({
  bannerFileList,
  setBannerFileList,
  galleryItems,
  setGalleryItems,
}: Props) {
  const { t } = useTranslation();

  const beforeAnyUpload: UploadProps["beforeUpload"] = (file) => {
    const mime = file.type;
    const sizeMB = file.size / 1024 / 1024;
    if (!isImage(mime) && !isVideo(mime)) {
      message.error(t('upload.onlyImagesVideos', 'Apenas imagens e vídeos são permitidos.'));
      return Upload.LIST_IGNORE;
    }
    if (isImage(mime) && sizeMB > LIMIT_IMAGE_MB) {
      message.error(`${t('upload.imageLabel')} > ${LIMIT_IMAGE_MB} MB.`);
      return Upload.LIST_IGNORE;
    }
    if (isVideo(mime) && sizeMB > LIMIT_VIDEO_MB) {
      message.error(`${t('upload.videoLabel')} > ${LIMIT_VIDEO_MB} MB.`);
      return Upload.LIST_IGNORE;
    }
    return false;
  };

  /* ------ Banner (1 ficheiro) ------ */
  const onChangeBanner: UploadProps["onChange"] = ({ fileList }) => {
    setBannerFileList(fileList.slice(0, 1));
  };

  /* ------ Galeria (N ficheiros) ------ */
  const onChangeGallery: UploadProps["onChange"] = ({ fileList }) => {
    // Map UploadFile -> GalleryItem (apenas os novos)
    const mapped: GalleryItem[] = fileList.map((f, idx) => {
      const origin = f.originFileObj as File | undefined;
      const mime = origin?.type || f.type;
      
      // Deteta automaticamente o tipo baseado no MIME type
      let detectedType: GalleryItem["type"] = "image"; // default
      if (isVideo(mime)) {
        detectedType = "video";
      }

      // Encontra o item existente para preservar o altText se já existir
      const existingItem = galleryItems.find(item => item.uid === f.uid);
      
      return {
        uid: f.uid,
        file: origin,
        name: f.name,
        mime,
        type: detectedType, // Tipo detetado automaticamente
        altText: existingItem?.altText ?? "",
        sortOrder: idx, // Ordem automática baseada no índice (0, 1, 2, ...)
      };
    });
    setGalleryItems(mapped);
  };

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '13.6px' }}>
      {/* BANNER */}
      <BlueprintCard kicker={t('upload.bannerPhoto')} style={{ padding: "13.6px", gap: "10.2px" }}>
        <div>
          <Upload
            listType="picture-card"
            accept={IMG_ACCEPT}
            beforeUpload={beforeAnyUpload}
            fileList={bannerFileList}
            maxCount={1}
            multiple={false}
            onChange={onChangeBanner}
            onPreview={(file) => {
              const url = file.url || (file.preview as string);
              if (url) window.open(url, "_blank", "noopener,noreferrer");
            }}
          >
            {bannerFileList.length >= 1 ? null : (
              <div>
                <PlusOutlined />
                <div style={{ marginTop: 8, fontSize: '12px' }}>{t('upload.addButton')}</div>
              </div>
            )}
          </Upload>
        </div>

        <Text style={{ fontSize: '11px', opacity: 0.6 }}>
          {t('upload.bannerHint', { limit: LIMIT_IMAGE_MB })}
        </Text>
      </BlueprintCard>

      {/* GALERIA */}
      <BlueprintCard kicker={t('upload.mediaGallery')} style={{ padding: "13.6px", gap: "10.2px" }}>
        <div>
          <Upload
            listType="picture-card"
            accept={[IMG_ACCEPT, VID_ACCEPT].filter(Boolean).join(",")}
            beforeUpload={beforeAnyUpload}
            multiple
            onChange={onChangeGallery}
            onPreview={(file) => {
              const url = file.url || (file.preview as string);
              if (url) window.open(url, "_blank", "noopener,noreferrer");
            }}
          >
            <div>
              <PlusOutlined />
              <div style={{ marginTop: 8, fontSize: '12px' }}>{t('upload.addButton')}</div>
            </div>
          </Upload>
        </div>

        {galleryItems.length > 0 && (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '12px' }}>
            {galleryItems.map((it, idx) => (
              <div
                key={it.uid}
                style={{
                  padding: '12px',
                  border: '1px solid var(--ind-color-divider)',
                }}
              >
                <Row gutter={[12, 8]} align="middle">
                  <Col xs={24} md={8}>
                    <div>
                      <Text type="secondary" style={{ fontSize: '10px', display: 'block', marginBottom: '4px' }}>
                        {t('upload.fileLabel')}
                      </Text>
                      <div style={{ display: 'flex', alignItems: 'center', gap: '8px' }}>
                        <div style={{
                          fontSize: '12px',
                          fontWeight: 500,
                          overflow: 'hidden',
                          textOverflow: 'ellipsis',
                          whiteSpace: 'nowrap',
                          flex: 1
                        }}>
                          {it.name}
                        </div>
                        <Tag
                          icon={getTypeIcon(it.type)}
                          color={getTypeColor(it.type)}
                          style={{ margin: 0, flexShrink: 0 }}
                        >
                          {t(getTypeLabelKey(it.type))}
                        </Tag>
                      </div>
                    </div>
                  </Col>

                  <Col xs={24} md={16}>
                    <div>
                      <Text type="secondary" style={{ fontSize: '10px', display: 'block', marginBottom: '4px' }}>
                        {t('upload.observationsLabel')}
                      </Text>
                      <Input
                        value={it.altText}
                        placeholder={t('upload.observationsPlaceholder')}
                        size="small"
                        onChange={(e) => {
                          const next = [...galleryItems];
                          next[idx] = { ...next[idx], altText: e.target.value };
                          setGalleryItems(next);
                        }}
                      />
                    </div>
                  </Col>
                </Row>
              </div>
            ))}
          </div>
        )}

        <Text style={{ fontSize: '11px', opacity: 0.6 }}>
          {t('upload.mediaHint', { imgLimit: LIMIT_IMAGE_MB, vidLimit: LIMIT_VIDEO_MB })}
        </Text>
      </BlueprintCard>
    </div>
  );
}