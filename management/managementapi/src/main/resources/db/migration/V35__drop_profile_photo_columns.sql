-- Fotos de perfil eliminadas por completo (UI + endpoints + colunas).
-- O substituto são as iniciais do nome, já usadas como fallback nos 3 sítios
-- que mostravam a foto. Os objetos já gravados em `private/profilephoto/**`
-- no Supabase Storage não são apagados por esta migração — são poucos e
-- apagam-se à mão.
ALTER TABLE worksite.profile
    DROP COLUMN photo_url,
    DROP COLUMN photo_bucket,
    DROP COLUMN photo_key;
