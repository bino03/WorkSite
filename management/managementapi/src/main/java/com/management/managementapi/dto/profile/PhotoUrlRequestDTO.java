package com.management.managementapi.dto.profile;

public class PhotoUrlRequestDTO {
    
private String bucket;
    private String photoKey;
    private Integer expiresIn; // segundos (opcional). default: 600

    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }

    public String getPhotoKey() { return photoKey; }
    public void setPhotoKey(String photoKey) { this.photoKey = photoKey; }

    public Integer getExpiresIn() { return expiresIn; }
    public void setExpiresIn(Integer expiresIn) { this.expiresIn = expiresIn; }
}
