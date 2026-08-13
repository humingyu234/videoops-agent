import { Button, Flex, Tooltip } from 'antd';
import type { TimelineTrackType } from '@/services/ai-video/creation-timeline/types';

export type TimelineAddAction =
  | 'image'
  | 'picture-in-picture'
  | 'subtitle'
  | 'fancy-text'
  | 'background-music'
  | 'sound-effect'
  | 'visual-effect';

type AddButton = {
  action: TimelineAddAction;
  label: string;
  trackType: TimelineTrackType;
  hint: string;
};

const addButtons: readonly AddButton[] = [
  {
    action: 'image',
    label: '添加图片',
    trackType: 'image_overlay',
    hint: '从创作素材中添加图片',
  },
  {
    action: 'picture-in-picture',
    label: '添加画中画',
    trackType: 'pip_video',
    hint: '从创作素材中添加视频画中画',
  },
  {
    action: 'subtitle',
    label: '添加字幕',
    trackType: 'subtitle',
    hint: '添加或对齐字幕',
  },
  {
    action: 'fancy-text',
    label: '添加花字',
    trackType: 'fancy_text',
    hint: '添加可编辑花字',
  },
  {
    action: 'background-music',
    label: '添加背景音乐',
    trackType: 'background_music',
    hint: '添加背景音乐',
  },
  {
    action: 'sound-effect',
    label: '添加音效',
    trackType: 'sound_effect',
    hint: '添加音效',
  },
  {
    action: 'visual-effect',
    label: '添加特效',
    trackType: 'visual_effect',
    hint: '添加视觉特效',
  },
];

export default function ElementAddBar({
  onAddElement,
}: {
  onAddElement?: (
    action: TimelineAddAction,
    trackType: TimelineTrackType,
  ) => void;
}) {
  return (
    <div
      aria-label="添加时间轴元素"
      className="timeline-add-bar"
      role="toolbar"
    >
      <Flex gap="small" wrap>
        {addButtons.map((item) => (
          <Tooltip key={item.action} title={item.hint}>
            <Button onClick={() => onAddElement?.(item.action, item.trackType)}>
              {item.label}
            </Button>
          </Tooltip>
        ))}
      </Flex>
    </div>
  );
}
