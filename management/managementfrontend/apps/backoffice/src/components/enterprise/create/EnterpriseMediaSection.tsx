
import type { UploadFile } from "antd";

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
  return (
    <MediaUploadsSection
      bannerFileList={bannerFileList}
      setBannerFileList={setBannerFileList}
      galleryItems={galleryItems}
      setGalleryItems={setGalleryItems}
    />
  );
}
