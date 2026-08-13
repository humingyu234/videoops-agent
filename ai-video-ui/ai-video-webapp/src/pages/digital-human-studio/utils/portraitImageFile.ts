export const PORTRAIT_IMAGE_ACCEPT =
  '.jpg,.jpeg,.png,.webp,.gif,image/jpeg,image/png,image/webp,image/gif';

export const PORTRAIT_IMAGE_FORMAT_MESSAGE = '仅支持 JPG、JPEG、PNG、WebP、GIF';

const MIME_BY_EXTENSION = {
  gif: 'image/gif',
  jpeg: 'image/jpeg',
  jpg: 'image/jpeg',
  png: 'image/png',
  webp: 'image/webp',
} as const;

export const isSupportedPortraitImage = (file: File) => {
  const name = file.name.trim();
  const dotIndex = name.lastIndexOf('.');
  if (dotIndex <= 0 || dotIndex >= name.length - 1) return false;
  const extension = name.slice(dotIndex + 1).toLowerCase();
  const mime = file.type.trim().toLowerCase();
  return (
    MIME_BY_EXTENSION[extension as keyof typeof MIME_BY_EXTENSION] === mime
  );
};
