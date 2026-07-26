// src/components/common/AuthenticatedImage.tsx
import React, { useEffect, useState } from 'react';
import { Image, Spin } from 'antd';
import type { ImageProps } from 'antd';
import api from '@/api';

interface AuthenticatedImageProps extends Omit<ImageProps, 'src'> {
  src: string;
  alt?: string;
}

/**
 * Componente que carrega imagens de endpoints protegidos com autenticação
 */
const AuthenticatedImage: React.FC<AuthenticatedImageProps> = ({ 
  src, 
  alt,
  fallback,
  ...imageProps 
}) => {
  const [imageUrl, setImageUrl] = useState<string | undefined>(undefined);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState(false);

  useEffect(() => {
    // Se a URL já é completa (http/https), não precisa de autenticação
    if (src.startsWith('http://') || src.startsWith('https://')) {
      // Verifica se é uma URL do Supabase (não precisa de token do backend)
      if (src.includes('supabase.co')) {
        setImageUrl(src);
        setLoading(false);
        return;
      }
    }

    // Se não é uma URL que precisa de autenticação via API
    if (!src.startsWith('/api/')) {
      setImageUrl(src);
      setLoading(false);
      return;
    }

    // Carregar imagem com autenticação
    const loadImage = async () => {
      try {
        setLoading(true);
        setError(false);
        
        console.debug('[AuthenticatedImage] Carregando com autenticação:', src);

        // Faz request com o token através do axios instance
        const response = await api.get(src, {
          responseType: 'blob', // Importante: receber como blob
        });

        // Converte blob para URL local
        const blob = response.data;
        const objectUrl = URL.createObjectURL(blob);
        
        console.debug('[AuthenticatedImage] ✅ Imagem carregada com sucesso');
        setImageUrl(objectUrl);
      } catch (err) {
        console.error('[AuthenticatedImage] ❌ Erro ao carregar imagem:', err);
        setError(true);
      } finally {
        setLoading(false);
      }
    };

    loadImage();

    // Cleanup: revogar URL quando componente desmontar
    return () => {
      if (imageUrl && imageUrl.startsWith('blob:')) {
        URL.revokeObjectURL(imageUrl);
      }
    };
  }, [src]);

  if (loading) {
    return (
      <div style={{ 
        display: 'flex', 
        alignItems: 'center', 
        justifyContent: 'center',
        minHeight: '200px',
        background: '#f5f5f5',
        borderRadius: '8px'
      }}>
        <Spin tip="Carregando imagem..." />
      </div>
    );
  }

  if (error || !imageUrl) {
    return (
      <Image
        {...imageProps}
        src={fallback || '/src/assets/images/property/default.jpg'}
        alt={alt || 'Imagem não disponível'}
      />
    );
  }

  return (
    <Image
      {...imageProps}
      src={imageUrl}
      alt={alt}
      fallback={fallback}
    />
  );
};

export default AuthenticatedImage;