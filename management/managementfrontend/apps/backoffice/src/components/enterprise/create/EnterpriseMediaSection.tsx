
import { Image as ImageIcon } from "lucide-react";
import type { UploadFile } from "antd";
import { useTranslation } from "react-i18next";

import SectionCard from "./ui/SectionCard";
import MediaUploadsSection, { type GalleryItem } from "@/components/upload/MediaUploadsSection";

interface EnterpriseMediaSectionProps {
  bannerFileList: UploadFile[];
  setBannerFileList: (files: UploadFile[]) => void;
  galleryItems: GalleryItem[];
  setGalleryItems: (items: GalleryItem[]) => void;
}

export default function EnterpriseMediaSection({
  bannerFileList,
  setBannerFileList,
  galleryItems,
  setGalleryItems,
}: EnterpriseMediaSectionProps) {
  const { t } = useTranslation();

  return (
    <SectionCard title={t('enterpriseCreate.media.title')} icon={<ImageIcon size={16} />}>
      <MediaUploadsSection
        bannerFileList={bannerFileList}
        setBannerFileList={setBannerFileList}
        galleryItems={galleryItems}
        setGalleryItems={setGalleryItems}
      />
    </SectionCard>
  );
}
