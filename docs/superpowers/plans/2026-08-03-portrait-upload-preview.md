# 形象上传预览交互实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:executing-plans 在当前会话逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 新建人物形象时，选择图片后以可放大、可删除的单张预览替换上传入口，并允许删除后重新选择。

**架构：** 继续使用 `PortraitLibraryView` 现有受控 `fileList` 作为唯一待上传文件状态。未选择文件时渲染 `Upload.Dragger`，选择后渲染 Ant Design `Upload` 的 `picture-card` 列表；`onPreview` 打开只读大图 `Modal`，`onRemove` 仅清除本地状态。接口、数据结构和后端上传流程保持不变。

**技术栈：** React 19、TypeScript、Ant Design 6、Vitest、Testing Library、CSS

---

## 文件结构

- 修改 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/PortraitLibraryView.test.tsx`：覆盖选图后替换入口、放大预览、删除恢复入口。
- 修改 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/PortraitLibraryView.tsx`：增加图片预览状态、Data URL 读取、条件上传展示、删除和关闭清理。
- 修改 `ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/style.css`：让单张图片卡和大图预览适配弹窗宽度。
- 不修改 `docs/API_CONTRACT.md`、`docs/DOMAIN_MODEL.md`、前端接口类型或后端文件：本次不改变网络契约、领域数据与上传时机。

## 任务卡

- 风险：黄色；用户可感知交互变更，但无接口、安全、数据或资金规则变更。
- 范围：仅上述组件、组件测试和局部样式。
- 并发：单实现者串行执行，避免与当前脏工作区中的并行改动冲突。
- 审查：完成后只检查受影响文件的差异。
- 版本：遵循用户“不保留版本”的要求，不创建提交。

### 任务 1：单张人物照片的预览、放大与重新选择

**文件：**
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/PortraitLibraryView.test.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/components/PortraitLibraryView.tsx`
- 修改：`ai-video-ui/ai-video-webapp/src/pages/digital-human-studio/style.css`

- [ ] **步骤 1：编写失败的组件测试**

在现有主按钮测试后增加一次完整交互测试：打开“新增形象”，向文件输入框选择 `portrait.png`，断言拖拽文案消失、缩略图出现；点击缩略图后断言“图片预览”弹窗出现；点击 Upload 的删除操作后断言缩略图消失、拖拽文案恢复。

```tsx
it('replaces the uploader with a preview that can be enlarged and removed', async () => {
  render(<PortraitLibraryView onToast={vi.fn()} />);
  fireEvent.click(await screen.findByRole('button', { name: /新增形象/ }));

  const input = document.querySelector<HTMLInputElement>('input[type="file"]');
  expect(input).not.toBeNull();
  fireEvent.change(input as HTMLInputElement, {
    target: { files: [new File(['image'], 'portrait.png', { type: 'image/png' })] },
  });

  const thumbnail = await screen.findByRole('img', { name: 'portrait.png' });
  expect(screen.queryByText('点击或拖拽上传一张人物照片')).not.toBeInTheDocument();
  fireEvent.click(thumbnail);
  expect(await screen.findByText('图片预览')).toBeInTheDocument();

  fireEvent.click(screen.getByTitle(/删除文件|Remove file/i));
  expect(await screen.findByText('点击或拖拽上传一张人物照片')).toBeInTheDocument();
  expect(screen.queryByRole('img', { name: 'portrait.png' })).not.toBeInTheDocument();
});
```

- [ ] **步骤 2：运行定向测试并确认按预期失败**

运行：

```powershell
pnpm test src/pages/digital-human-studio/components/PortraitLibraryView.test.tsx
```

预期：新增交互测试失败，因为当前选择图片后仍显示拖拽上传入口，且尚无受控大图预览。

- [ ] **步骤 3：实现最小交互闭环**

在组件中增加预览状态和 Data URL 读取函数：

```tsx
const readAsDataUrl = (file: File) =>
  new Promise<string>((resolve, reject) => {
    const reader = new FileReader();
    reader.onload = () => resolve(String(reader.result ?? ''));
    reader.onerror = () => reject(reader.error);
    reader.readAsDataURL(file);
  });

const [previewOpen, setPreviewOpen] = useState(false);
const [previewImage, setPreviewImage] = useState('');
```

为 `UploadFile` 实现预览和本地清理：

```tsx
const previewFile = async (file: UploadFile) => {
  let source = file.url ?? file.preview ?? file.thumbUrl;
  if (!source && file.originFileObj) {
    source = await readAsDataUrl(file.originFileObj);
    file.preview = source;
  }
  setPreviewImage(source ?? '');
  setPreviewOpen(true);
};

const clearSelectedFile = () => {
  setFileList([]);
  setPreviewOpen(false);
  setPreviewImage('');
};
```

将原上传区改成条件渲染：`fileList` 为空时显示现有 `Upload.Dragger` 且隐藏默认列表；存在文件时显示 `listType="picture-card"` 的受控 `Upload`，配置 `onPreview` 和 `onRemove`，不提供新的上传触发子节点。关闭新建弹窗和创建成功时调用统一清理函数。增加标题为“图片预览”的无底部按钮 `Modal`，其中图片使用文件名作为替代文本。

- [ ] **步骤 4：增加局部样式**

```css
.portrait-upload-preview-list .ant-upload-list-picture-card,
.portrait-upload-preview-list .ant-upload-list-item-container {
  width: 100% !important;
}

.portrait-upload-preview-list .ant-upload-list-item-container {
  height: 300px !important;
}

.portrait-upload-preview-image {
  display: block;
  width: 100%;
  max-height: 70vh;
  object-fit: contain;
}
```

- [ ] **步骤 5：运行定向测试并确认通过**

运行：

```powershell
pnpm test src/pages/digital-human-studio/components/PortraitLibraryView.test.tsx
```

预期：既有白色主按钮测试和新增上传预览测试全部通过。

- [ ] **步骤 6：运行类型与构建验证**

运行：

```powershell
pnpm tsc
pnpm build
```

预期：两个命令均以退出码 0 完成；如仓库已有无关错误，则记录精确错误并确认本次定向测试仍通过。

- [ ] **步骤 7：检查受影响差异并手动验收**

仅检查三个实现文件的 `git diff`，确认没有接口和后端改动。在本地 `/studio` 分别点击“新增形象”和空态“上传第一张人物照片”，验证选图、放大、关闭预览、删除、重新选择和关闭弹窗后的状态清理。
