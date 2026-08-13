const CONTROL_CHARACTERS = /[\u0000-\u001f\u0080-\u009f]/g;
const INVALID_FILE_NAME_CHARACTERS = /[<>:"|?*]/g;
const RESERVED_WINDOWS_NAME = /^(con|prn|aux|nul|clock\$|com[1-9]|lpt[1-9])$/i;
const MAX_FILE_NAME_LENGTH = 120;

function limitLength(fileName: string): string {
  if (fileName.length <= MAX_FILE_NAME_LENGTH) return fileName;

  const extensionIndex = fileName.lastIndexOf('.');
  const hasExtension = extensionIndex > 0 && extensionIndex >= fileName.length - 16;
  const extension = hasExtension ? fileName.slice(extensionIndex) : '';
  return `${fileName.slice(0, MAX_FILE_NAME_LENGTH - extension.length)}${extension}`;
}

export function sanitizeSuggestedFileName(raw: string): string {
  const leaf = raw.split(/[\\/]/).at(-1) ?? '';
  let fileName = leaf
    .replace(CONTROL_CHARACTERS, '')
    .replace(INVALID_FILE_NAME_CHARACTERS, '_')
    .trim()
    .replace(/[. ]+$/g, '')
    .replace(/^[. ]+/g, '');

  if (!fileName) return 'download';

  const extensionIndex = fileName.lastIndexOf('.');
  const stem = extensionIndex > 0 ? fileName.slice(0, extensionIndex) : fileName;
  if (RESERVED_WINDOWS_NAME.test(stem)) fileName = `_${fileName}`;

  return limitLength(fileName) || 'download';
}
