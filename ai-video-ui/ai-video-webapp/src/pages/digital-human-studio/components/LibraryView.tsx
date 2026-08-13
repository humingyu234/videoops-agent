import React, { useMemo, useState } from 'react';
import { AVATARS, type StudioRoute, VOICES, WORKS } from '../model';
import type { AvatarSpaceSource } from '../avatar-space/model';
import VoiceLibraryView from '../voices/VoiceLibraryView';
import PortraitLibraryView from './PortraitLibraryView';
import ScriptLibraryView from './ScriptLibraryView';
import StudioIcon from './StudioIcon';

interface DetailRequest {
  title: string;
  subtitle: string;
  content: React.ReactNode;
  footer?: React.ReactNode;
}

interface LibraryViewProps {
  route: Exclude<StudioRoute, 'create'>;
  onDetail: (detail: DetailRequest) => void;
  onAddAvatar: () => void;
  onOpenAvatarSpace: (source?: AvatarSpaceSource) => void;
  onAddVoice: () => void;
  onNavigateCreate: (step: number) => void;
  onToast: (message: string, type?: 'success' | 'error') => void;
}

const FilterGroup: React.FC<{
  value: string;
  options: Array<[string, string]>;
  onChange: (value: string) => void;
}> = ({ value, options, onChange }) => (
  <div className="seg">
    {options.map(([key, label]) => (
      <button
        className={value === key ? 'active' : ''}
        key={key}
        type="button"
        onClick={() => onChange(key)}
      >
        {label}
      </button>
    ))}
  </div>
);

const DetailCells: React.FC<{
  items: Array<[string, string]>;
}> = ({ items }) => (
  <div className="detail-grid">
    {items.map(([label, value]) => (
      <div className="detail-cell" key={label}>
        <div className="detail-cell-label">{label}</div>
        <div className="detail-cell-value">{value}</div>
      </div>
    ))}
  </div>
);

const AssetLibraryView: React.FC<LibraryViewProps> = ({
  route,
  onDetail,
  onAddAvatar,
  onAddVoice,
  onNavigateCreate,
  onToast,
}) => {
  const [search, setSearch] = useState('');
  const [primaryFilter, setPrimaryFilter] = useState('all');
  const [secondaryFilter, setSecondaryFilter] = useState('all');

  const openAvatar = (avatar: (typeof AVATARS)[number]) =>
    onDetail({
      title: avatar.name,
      subtitle: `${avatar.id} · ${
        avatar.owner === 'custom' ? '我的形象' : '公共形象'
      }`,
      content: (
        <>
          <div
            className="detail-cover avatar-detail-cover"
            style={{ background: avatar.style }}
          >
            {avatar.gender === 'female' ? '♀' : '♂'}
          </div>
          <DetailCells
            items={[
              ['分辨率', avatar.owner === 'custom' ? '2048×2048' : '1536×1536'],
              ['校验状态', avatar.status === 'verified' ? '已校验' : '校验中'],
              [
                '文件格式',
                avatar.owner === 'custom' ? 'PNG · 4.2MB' : 'JPG · 1.6MB',
              ],
              [
                '创建时间',
                avatar.owner === 'custom' ? '2026-07-12' : '平台预置',
              ],
            ]}
          />
          <div className="drawer-section">
            <div className="prop-title">适用场景</div>
            <p>{avatar.scenes}</p>
          </div>
          <div className="drawer-section">
            <div className="prop-title">标签</div>
            <div className="chip-row">
              {avatar.scenes.split(' · ').map((tag) => (
                <span className="tag tag-soft" key={tag}>
                  <StudioIcon name="tag" /> {tag}
                </span>
              ))}
            </div>
          </div>
          <div className="drawer-section">
            <div className="prop-title">被引用项目</div>
            <div className="ref-item">
              <span className="list-thumb">
                <StudioIcon name="film" />
              </span>
              <div>
                <b>夏季新品口播</b>
                <small>P-20260726-01 · 底片 base-001</small>
              </div>
              <button type="button" onClick={() => onToast('已跳转到项目详情')}>
                查看 →
              </button>
            </div>
          </div>
        </>
      ),
      footer: (
        <>
          <button
            className="btn btn-outline btn-sm"
            type="button"
            onClick={() => onToast('已设为当前项目形象')}
          >
            <StudioIcon name="check" /> 选为当前
          </button>
          <button className="btn btn-ghost btn-sm" type="button">
            <StudioIcon name="edit" /> 重命名
          </button>
          <button className="btn btn-ghost btn-sm" type="button">
            <StudioIcon name="delete" /> 删除
          </button>
        </>
      ),
    });

  const openVoice = (voice: (typeof VOICES)[number]) =>
    onDetail({
      title: voice.name,
      subtitle: `${voice.id} · ${voice.meta}`,
      content: (
        <>
          <div className="detail-audio-card">
            <button className="voice-play" type="button">
              <StudioIcon name="play" />
            </button>
            <div>
              <small>时长</small>
              <b className="numeric">{voice.dur}</b>
            </div>
            <div className="voice-bars">
              {Array.from({ length: 48 }, (_, index) => ({
                id: `${voice.id}-detail-bar-${index + 1}`,
                height: 8 + Math.abs(Math.sin(index * 0.4)) * 16,
              })).map((bar) => (
                <i key={bar.id} style={{ height: bar.height }} />
              ))}
            </div>
          </div>
          <DetailCells
            items={[
              [
                '类型',
                voice.type === 'clone'
                  ? '克隆声音'
                  : voice.type === 'origin'
                    ? '原声音'
                    : '公共声音',
              ],
              ['采样率', voice.type === 'public' ? '平台预置' : '48kHz 单声道'],
              [
                '创建时间',
                voice.owner === 'public' ? '平台预置' : '2026-07-26',
              ],
              ['校验状态', voice.status === 'verified' ? '已校验' : '校验中'],
            ]}
          />
          <div className="drawer-section">
            <div className="prop-title">标签</div>
            <div className="chip-row">
              <span className="tag tag-soft">声音资产</span>
              <span className="tag tag-soft">
                {voice.type === 'clone' ? '克隆' : '参考'}
              </span>
            </div>
          </div>
          <div className="drawer-section">
            <div className="prop-title">生成历史</div>
            <div className="version-node current">
              <b>v2</b>
              <small>今天 14:20 · 文案微调后重新克隆</small>
            </div>
            <div className="version-node">
              <b>v1</b>
              <small>昨天 16:00 · 首次克隆</small>
            </div>
          </div>
        </>
      ),
      footer: (
        <>
          <button className="btn btn-outline btn-sm" type="button">
            <StudioIcon name="check" /> 选为当前
          </button>
          <button className="btn btn-ghost btn-sm" type="button">
            <StudioIcon name="edit" /> 重命名
          </button>
          <button className="btn btn-ghost btn-sm" type="button">
            <StudioIcon name="delete" /> 删除
          </button>
        </>
      ),
    });

  const openWork = (work: (typeof WORKS)[number]) =>
    onDetail({
      title: work.name,
      subtitle: 'P-20260726-01 · v3',
      content: (
        <>
          <div
            className="detail-cover work-detail-cover"
            style={{ background: work.cover }}
          >
            <button className="work-play" type="button">
              <StudioIcon name="play" />
            </button>
            <span className="work-duration">{work.dur}</span>
          </div>
          <DetailCells
            items={[
              [
                '状态',
                work.status === 'published'
                  ? '已发布'
                  : work.status === 'processing'
                    ? '处理中'
                    : '草稿',
              ],
              ['版本', 'v3'],
              ['分辨率', work.dim],
              ['文件大小', work.size],
            ]}
          />
          <div className="drawer-section">
            <div className="prop-title">关联资产</div>
            {[
              ['所属项目', 'P-20260726-01'],
              ['关联文案', '夏季新品口播 · v2'],
              ['人物形象', '亲切女主播'],
              ['克隆声音', 'vs-003 · 01:02'],
              ['数字人底片', 'base-001'],
            ].map(([meta, name]) => (
              <div className="ref-item" key={meta}>
                <span className="list-thumb">
                  <StudioIcon name="folder" />
                </span>
                <div>
                  <b>{name}</b>
                  <small>{meta}</small>
                </div>
                <button type="button">查看 →</button>
              </div>
            ))}
          </div>
        </>
      ),
      footer: (
        <>
          {work.status === 'published' && (
            <button className="btn btn-primary btn-sm" type="button">
              <StudioIcon name="download" /> 下载 MP4
            </button>
          )}
          {work.status === 'draft' && (
            <button
              className="btn btn-primary btn-sm"
              type="button"
              onClick={() => onNavigateCreate(6)}
            >
              <StudioIcon name="edit" /> 继续编辑
            </button>
          )}
          <button className="btn btn-ghost btn-sm" type="button">
            <StudioIcon name="edit" /> 重命名
          </button>
        </>
      ),
    });

  const count = useMemo(() => {
    if (route === 'avatars') return AVATARS.length;
    if (route === 'voices') return VOICES.length;
    return WORKS.length;
  }, [route]);

  const filterOptions: Array<[string, string]> =
    route === 'avatars'
      ? [
          ['all', '全部'],
          ['custom', '我的'],
          ['public', '公共'],
          ['verified', '已校验'],
          ['pending', '校验中'],
        ]
      : route === 'voices'
        ? [
            ['all', '全部类型'],
            ['clone', '克隆'],
            ['origin', '原声'],
            ['public', '公共'],
          ]
        : [
              ['all', '全部'],
              ['published', '已发布'],
              ['draft', '草稿'],
              ['processing', '处理中'],
            ];

  const avatars = AVATARS.filter((item) => {
    const filterMatched =
      primaryFilter === 'all' ||
      item.owner === primaryFilter ||
      item.status === primaryFilter;
    return (
      filterMatched &&
      (!search || item.name.includes(search) || item.scenes.includes(search))
    );
  });
  const voices = VOICES.filter((item) => {
    const typeMatched = primaryFilter === 'all' || item.type === primaryFilter;
    const statusMatched =
      secondaryFilter === 'all' || item.status === secondaryFilter;
    return (
      typeMatched &&
      statusMatched &&
      (!search || item.name.includes(search) || item.meta.includes(search))
    );
  });
  const works = WORKS.filter(
    (item) =>
      (primaryFilter === 'all' || item.status === primaryFilter) &&
      (!search || item.name.includes(search)),
  );

  return (
    <div className="library-page">
      <div className="page-toolbar">
        <label className="search-box">
          <StudioIcon name="search" />
          <input
            placeholder={
              route === 'avatars'
                ? '搜索形象名称或场景'
                : route === 'voices'
                  ? '搜索声音名称'
                  : '搜索作品名称'
            }
            value={search}
            onChange={(event) => setSearch(event.target.value)}
          />
        </label>
        <FilterGroup
          value={primaryFilter}
          options={filterOptions}
          onChange={setPrimaryFilter}
        />
        {route === 'voices' && (
          <FilterGroup
            value={secondaryFilter}
            options={[
              ['all', '全部状态'],
              ['verified', '已校验'],
              ['pending', '校验中'],
            ]}
            onChange={setSecondaryFilter}
          />
        )}
        {route === 'avatars' && (
          <>
            <button
              className="btn btn-outline btn-sm"
              type="button"
              onClick={onAddAvatar}
            >
              <StudioIcon name="plus" /> 创建形象
            </button>
            <button
              className="btn btn-ghost btn-sm"
              type="button"
              onClick={() => onToast('已打开批量上传')}
            >
              <StudioIcon name="upload" /> 批量上传
            </button>
          </>
        )}
        {route === 'voices' && (
          <button
            className="btn btn-outline btn-sm"
            type="button"
            onClick={onAddVoice}
          >
            <StudioIcon name="upload" /> 上传原声
          </button>
        )}
        {route === 'works' && (
          <button className="btn btn-outline btn-sm" type="button">
            <StudioIcon name="tool" /> 最新优先
          </button>
        )}
        <span className="spacer" />
        <span className="toolbar-count">
          共{' '}
          {route === 'avatars'
            ? avatars.length
            : route === 'voices'
              ? voices.length
              : works.length}{' '}
          {route === 'works' ? '个作品' : route === 'voices' ? '条' : '项'}
        </span>
      </div>

      {route !== 'works' && (
        <div className="library-list-title">
          <h3>
            {route === 'avatars'
              ? '形象列表'
              : route === 'voices'
                ? '声音列表'
                : '文案列表'}
          </h3>
          <span className="tag tag-soft">{count}</span>
        </div>
      )}

      {route === 'avatars' && (
        <div className="avatar-grid">
          {avatars.map((avatar) => (
            <button
              className="avatar-card"
              key={avatar.id}
              type="button"
              onClick={() => openAvatar(avatar)}
            >
              <div
                className="avatar-cover"
                style={{ background: avatar.style }}
              >
                <span className="avatar-gender">
                  {avatar.gender === 'female' ? '♀' : '♂'}
                </span>
                <span className="avatar-id">{avatar.id}</span>
              </div>
              <div className="avatar-info">
                <div>
                  <b>{avatar.name}</b>
                  <span
                    className={`tag ${
                      avatar.status === 'verified' ? 'tag-success' : 'tag-warn'
                    }`}
                  >
                    {avatar.status === 'verified' ? '已校验' : '校验中'}
                  </span>
                </div>
                <small>{avatar.scenes}</small>
                <div className="card-actions">
                  <span className="btn btn-ghost btn-sm">
                    <StudioIcon name="eye" /> 预览
                  </span>
                  <span className="btn btn-ghost btn-sm">
                    <StudioIcon name="more" /> 详情
                  </span>
                </div>
              </div>
            </button>
          ))}
        </div>
      )}

      {route === 'voices' && (
        <div className="voice-list">
          {voices.map((voice) => (
            <button
              className="voice-card"
              key={voice.id}
              type="button"
              onClick={() => openVoice(voice)}
            >
              <span className="voice-play">
                <StudioIcon name="play" />
              </span>
              <span className="voice-main">
                <span className="voice-title">
                  <b>{voice.name}</b>
                  <i
                    className={`tag ${
                      voice.status === 'verified' ? 'tag-success' : 'tag-warn'
                    }`}
                  >
                    {voice.status === 'verified' ? '已校验' : '校验中'}
                  </i>
                  <i className="tag tag-soft">
                    {voice.type === 'clone'
                      ? '克隆'
                      : voice.type === 'origin'
                        ? '原声'
                        : '公共'}
                  </i>
                </span>
                <small>{voice.meta}</small>
                <span className="voice-bars">
                  {Array.from({ length: 48 }, (_, index) => ({
                    id: `${voice.id}-list-bar-${index + 1}`,
                    height: 10 + Math.abs(Math.sin(index * 0.6)) * 14,
                  })).map((bar) => (
                    <i key={bar.id} style={{ height: bar.height }} />
                  ))}
                </span>
              </span>
              <span className="voice-side">
                <small className="numeric">{voice.dur}</small>
                <i className="icon-btn">
                  <StudioIcon name="more" />
                </i>
              </span>
            </button>
          ))}
        </div>
      )}

      {route === 'works' && (
        <div className="works-grid">
          {works.map((work) => (
            <button
              className="work-card"
              key={work.id}
              type="button"
              onClick={() => openWork(work)}
            >
              <div className="work-cover" style={{ background: work.cover }}>
                <span className="work-play">
                  <StudioIcon name="play" />
                </span>
                <span className="work-duration">{work.dur}</span>
                <span className={`work-badge ${work.status}`}>
                  {work.status === 'published'
                    ? '✓ 已发布'
                    : work.status === 'processing'
                      ? '↻ 重合成中 65%'
                      : '✎ 草稿'}
                </span>
              </div>
              <div className="work-info">
                <div>
                  <b>{work.name}</b>
                  <StudioIcon name="more" />
                </div>
                <small className="numeric">
                  {work.size} · {work.dim} · {work.updated}
                </small>
                <div className="card-actions">
                  <span className="btn btn-outline btn-sm">
                    <StudioIcon name="eye" /> 详情
                  </span>
                  {work.status === 'published' && (
                    <span className="btn btn-ghost btn-sm">
                      <StudioIcon name="download" /> 下载
                    </span>
                  )}
                  {work.status === 'draft' && (
                    <span className="btn btn-ghost btn-sm">
                      <StudioIcon name="edit" /> 继续编辑
                    </span>
                  )}
                </div>
              </div>
            </button>
          ))}
        </div>
      )}
    </div>
  );
};

export type { DetailRequest };
const LibraryView: React.FC<LibraryViewProps> = (props) => {
  if (props.route === 'avatars') {
    return (
      <PortraitLibraryView
        onOpenSpace={props.onOpenAvatarSpace}
        onToast={props.onToast}
      />
    );
  }
  if (props.route === 'voices') {
    return (
      <VoiceLibraryView
        onAddVoice={props.onAddVoice}
        onToast={props.onToast}
      />
    );
  }
  if (props.route === 'scripts') {
    return <ScriptLibraryView onToast={props.onToast} />;
  }
  return <AssetLibraryView {...props} />;
};

export default LibraryView;
