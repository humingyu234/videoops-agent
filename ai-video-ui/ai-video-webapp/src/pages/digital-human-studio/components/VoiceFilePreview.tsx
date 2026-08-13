import React, { useEffect, useState } from 'react';

interface VoiceFilePreviewProps {
  file: File;
}

const VoiceFilePreview: React.FC<VoiceFilePreviewProps> = ({ file }) => {
  const [previewUrl, setPreviewUrl] = useState('');

  useEffect(() => {
    const url = URL.createObjectURL(file);
    setPreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [file]);

  return (
    <div className="voice-file-preview">
      <div className="voice-file-preview-name" title={file.name}>{file.name}</div>
      {/* biome-ignore lint/a11y/useMediaCaption: Local upload previews do not have a timed-text source. */}
      <audio aria-label="播放所选声音" controls preload="metadata" src={previewUrl} />
    </div>
  );
};

export default VoiceFilePreview;
