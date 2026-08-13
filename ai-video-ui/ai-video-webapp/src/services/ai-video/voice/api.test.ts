import { describe, expect, it, vi } from 'vitest';
import { createVoiceApi } from './api';

describe('voice api', () => {
  it('uploads the file and exact metadata as multipart data', async () => {
    const request = vi.fn().mockResolvedValue({ voiceId: '9' });
    const api = createVoiceApi({ request });
    const file = new File(['voice'], 'voice.wav', { type: 'audio/wav' });

    await api.upload(file, {
      idempotencyKey: 'idem-1',
      name: '亲切女声',
      gender: 'female',
      style: 'friendly',
      tags: ['直播'],
    });

    const body = request.mock.calls[0][1].data as FormData;
    expect(request).toHaveBeenCalledWith('/api/voices', expect.objectContaining({ method: 'POST' }));
    expect(body.get('file')).toBe(file);
    expect(JSON.parse(await (body.get('metadata') as Blob).text())).toMatchObject({ name: '亲切女声' });
  });

  it('normalizes RIFF/WAVE content mislabeled as mp3 before upload', async () => {
    const request = vi.fn().mockResolvedValue({ voiceId: '9' });
    const api = createVoiceApi({ request });
    const file = new File(
      [new Uint8Array([0x52, 0x49, 0x46, 0x46, 0, 0, 0, 0, 0x57, 0x41, 0x56, 0x45])],
      '明明想利用TikSe.mp3',
      { type: 'audio/mpeg' },
    );

    await api.upload(file, {
      idempotencyKey: 'idem-wav-as-mp3',
      name: '张良老师1',
      gender: 'unspecified',
      tags: [],
    });

    const body = request.mock.calls[0][1].data as FormData;
    const uploadedFile = body.get('file') as File;
    expect(uploadedFile.name).toBe('明明想利用TikSe.wav');
    expect(uploadedFile.type).toBe('audio/wav');
    expect(new Uint8Array(await uploadedFile.arrayBuffer())).toEqual(
      new Uint8Array(await file.arrayBuffer()),
    );
  });

  it('keeps a real mp3 file unchanged', async () => {
    const request = vi.fn().mockResolvedValue({ voiceId: '9' });
    const api = createVoiceApi({ request });
    const file = new File([new Uint8Array([0x49, 0x44, 0x33, 4, 0, 0, 0, 0, 0, 0])], 'voice.mp3', {
      type: 'audio/mpeg',
    });

    await api.upload(file, {
      idempotencyKey: 'idem-real-mp3',
      name: '真实 MP3',
      gender: 'unspecified',
      tags: [],
    });

    const body = request.mock.calls[0][1].data as FormData;
    expect(body.get('file')).toBe(file);
  });

  it('does not treat a generic ISO BMFF ftyp header as m4a', async () => {
    const request = vi.fn().mockResolvedValue({ voiceId: '9' });
    const api = createVoiceApi({ request });
    const file = new File(
      [new Uint8Array([0, 0, 0, 24, 0x66, 0x74, 0x79, 0x70, 0x69, 0x73, 0x6f, 0x6d])],
      'video.mp4',
      { type: 'video/mp4' },
    );

    await api.upload(file, {
      idempotencyKey: 'idem-video',
      name: '非音频容器',
      gender: 'unspecified',
      tags: [],
    });

    const body = request.mock.calls[0][1].data as FormData;
    expect(body.get('file')).toBe(file);
  });

  it('keeps transcription action paths inside the service', async () => {
    const request = vi.fn().mockResolvedValue({ voiceId: '9' });
    const api = createVoiceApi({ request });
    await api.updateTranscript('9', { transcriptText: '文本', expectedRevision: '2' });
    await api.retry('9', '3');
    await api.resync('9', '4');
    expect(request).toHaveBeenNthCalledWith(1, '/api/voices/9/transcript', expect.objectContaining({ method: 'PUT' }));
    expect(request).toHaveBeenNthCalledWith(2, '/api/voices/9/transcription/retry', expect.objectContaining({ method: 'POST' }));
    expect(request).toHaveBeenNthCalledWith(3, '/api/voices/9/transcription/resync', expect.objectContaining({
      method: 'POST', data: { expectedRevision: '4' },
    }));
  });

  it('deletes an encoded voice id through the exact endpoint', async () => {
    const request = vi.fn().mockResolvedValue(undefined);
    const api = createVoiceApi({ request });

    await api.delete('voice/你好');

    expect(request).toHaveBeenCalledWith(
      '/api/voices/voice%2F%E4%BD%A0%E5%A5%BD',
      { method: 'DELETE' },
    );
  });
});
