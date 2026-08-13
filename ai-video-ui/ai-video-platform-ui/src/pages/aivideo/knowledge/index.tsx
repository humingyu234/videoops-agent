import { CloudUploadOutlined, DeleteOutlined, EditOutlined, EyeOutlined, PlusOutlined } from '@ant-design/icons';
import { PageContainer, ProTable, type ActionType, type ProColumns } from '@ant-design/pro-components';
import {
  Alert,
  App,
  Button,
  Descriptions,
  Drawer,
  Form,
  Input,
  List,
  Modal,
  Result,
  Select,
  Space,
  Spin,
  Tag,
  Typography,
  Upload,
  type UploadFile
} from 'antd';
import { useRef, useState } from 'react';
import type {
  KnowledgeItemAdmin,
  KnowledgeItemDetail,
  KnowledgeItemId,
  KnowledgeItemQuery,
  KnowledgeItemSaveForm,
  KnowledgeImportSummary,
  KnowledgeStatus,
  KnowledgeType
} from '@/api/aivideo/knowledge/types';
import {
  addKnowledgeItem,
  deleteKnowledgeItem,
  getKnowledgeItem,
  importKnowledgeItems,
  pageKnowledgeItems,
  updateKnowledgeItem,
  updateKnowledgeStatus
} from '@/api/aivideo/knowledge';
import RowActions from '@/components/common/RowActions';
import { useUserStore } from '@/stores/userStore';
import { hasPermi } from '@/utils/permission';

const knowledgeTypeOptions: Array<{ label: string; value: KnowledgeType }> = [
  { label: '基础模板', value: 'primary_template' },
  { label: '写作技巧', value: 'writing_technique' },
  { label: '心理策略', value: 'psychology' },
  { label: '案例参考', value: 'case' },
  { label: '强制规则', value: 'mandatory_rule' }
];

const statusOptions: Array<{ label: string; value: KnowledgeStatus }> = [
  { label: '草稿', value: 'draft' },
  { label: '审核中', value: 'reviewing' },
  { label: '已发布', value: 'published' },
  { label: '已停用', value: 'retired' }
];

const knowledgeTypeLabels = Object.fromEntries(knowledgeTypeOptions.map(item => [item.value, item.label]));
const statusLabels = Object.fromEntries(statusOptions.map(item => [item.value, item.label]));
const statusColors: Record<KnowledgeStatus, string> = {
  draft: 'default',
  published: 'success',
  retired: 'error',
  reviewing: 'processing'
};
const coloredStatusOptions = statusOptions.map(item => ({
  label: (
    <Tag color={statusColors[item.value]} data-testid={`knowledge-status-${item.value}`} style={{ marginInlineEnd: 0 }}>
      {item.label}
    </Tag>
  ),
  value: item.value
}));
const importResultMeta: Record<string, { color: string; label: string }> = {
  failed: { color: 'error', label: '失败' },
  skipped: { color: 'warning', label: '已跳过' },
  success: { color: 'success', label: '成功' }
};

const acceptedExtensions = ['.md', '.markdown', '.txt', '.text', '.json', '.csv', '.yaml', '.yml'];
const acceptValue = acceptedExtensions.join(',');
const maxFileCount = 20;
const maxSingleFileBytes = 10 * 1024 * 1024;
const maxTotalFileBytes = 20 * 1024 * 1024;

interface ImportEditableRow {
  file: File;
  knowledgeType: KnowledgeType;
  name: string;
  status: KnowledgeStatus;
  uid: string;
}

function hasAcceptedExtension(fileName: string) {
  const lowerName = fileName.toLowerCase();
  return acceptedExtensions.some(extension => lowerName.endsWith(extension));
}

function fileNameWithoutExtension(fileName: string) {
  const lowerName = fileName.toLowerCase();
  const extension = acceptedExtensions.find(item => lowerName.endsWith(item));
  return extension ? fileName.slice(0, -extension.length) : fileName;
}

function errorMessage(error: unknown) {
  return error instanceof Error && error.message ? error.message : '知识库请求失败，请稍后重试';
}

export default function KnowledgePage() {
  const { message } = App.useApp();
  const actionRef = useRef<ActionType | undefined>(undefined);
  const detailRequestGeneration = useRef(0);
  const editRequestGeneration = useRef(0);
  const [knowledgeForm] = Form.useForm<KnowledgeItemSaveForm>();
  const userInfo = useUserStore(state => state.userInfo);

  const [loadError, setLoadError] = useState<string>();
  const [detailOpen, setDetailOpen] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [detailError, setDetailError] = useState<string>();
  const [detail, setDetail] = useState<KnowledgeItemDetail>();
  const [detailId, setDetailId] = useState<KnowledgeItemId>();

  const [formOpen, setFormOpen] = useState(false);
  const [formLoading, setFormLoading] = useState(false);
  const [formSubmitting, setFormSubmitting] = useState(false);
  const [editingId, setEditingId] = useState<KnowledgeItemId>();

  const [importOpen, setImportOpen] = useState(false);
  const [importRows, setImportRows] = useState<ImportEditableRow[]>([]);
  const [importResult, setImportResult] = useState<KnowledgeImportSummary>();
  const [importing, setImporting] = useState(false);
  const [updatingStatusId, setUpdatingStatusId] = useState<KnowledgeItemId>();
  const [deletingId, setDeletingId] = useState<KnowledgeItemId>();

  const canQuery = hasPermi(userInfo, ['aivideo:knowledge:query']);
  const canAdd = hasPermi(userInfo, ['aivideo:knowledge:add']);
  const canEdit = hasPermi(userInfo, ['aivideo:knowledge:edit']);
  const canRemove = hasPermi(userInfo, ['aivideo:knowledge:remove']);
  const canImport = hasPermi(userInfo, ['aivideo:knowledge:import']);

  const reloadTable = () => actionRef.current?.reload();

  const loadDetail = async (id: KnowledgeItemId) => {
    const generation = ++detailRequestGeneration.current;
    setDetailLoading(true);
    setDetailError(undefined);
    try {
      const nextDetail = await getKnowledgeItem(id);
      if (detailRequestGeneration.current === generation) {
        setDetail(nextDetail);
      }
    } catch (error) {
      if (detailRequestGeneration.current === generation) {
        setDetail(undefined);
        setDetailError(errorMessage(error));
      }
    } finally {
      if (detailRequestGeneration.current === generation) {
        setDetailLoading(false);
      }
    }
  };

  const openDetail = (item: KnowledgeItemAdmin) => {
    setDetail(undefined);
    setDetailId(item.id);
    setDetailOpen(true);
    void loadDetail(item.id);
  };

  const openCreate = () => {
    editRequestGeneration.current += 1;
    setEditingId(undefined);
    setFormLoading(false);
    knowledgeForm.resetFields();
    knowledgeForm.setFieldsValue({
      content: '',
      knowledgeType: 'primary_template',
      name: '',
      status: 'draft',
      summary: ''
    });
    setFormOpen(true);
  };

  const openEdit = async (item: KnowledgeItemAdmin) => {
    const generation = ++editRequestGeneration.current;
    setEditingId(item.id);
    setFormOpen(true);
    setFormLoading(true);
    knowledgeForm.resetFields();
    try {
      const itemDetail = await getKnowledgeItem(item.id);
      if (editRequestGeneration.current !== generation) return;
      knowledgeForm.setFieldsValue({
        content: itemDetail.content,
        knowledgeType: itemDetail.knowledgeType,
        name: itemDetail.name,
        status: itemDetail.status,
        summary: itemDetail.summary || ''
      });
    } catch (error) {
      if (editRequestGeneration.current === generation) {
        void message.error(errorMessage(error));
        setFormOpen(false);
      }
    } finally {
      if (editRequestGeneration.current === generation) {
        setFormLoading(false);
      }
    }
  };

  const closeKnowledgeForm = (force = false) => {
    if (formSubmitting && !force) return;
    editRequestGeneration.current += 1;
    setFormOpen(false);
    setEditingId(undefined);
    knowledgeForm.resetFields();
  };

  const submitKnowledgeForm = async () => {
    try {
      const values = await knowledgeForm.validateFields();
      setFormSubmitting(true);
      if (editingId === undefined) {
        await addKnowledgeItem(values);
        void message.success('知识已新增');
      } else {
        await updateKnowledgeItem(editingId, values);
        void message.success('知识已更新');
      }
      closeKnowledgeForm(true);
      reloadTable();
    } catch (error) {
      if (error instanceof Error) {
        void message.error(errorMessage(error));
      }
    } finally {
      setFormSubmitting(false);
    }
  };

  const changeStatus = async (item: KnowledgeItemAdmin, status: KnowledgeStatus) => {
    setUpdatingStatusId(item.id);
    try {
      await updateKnowledgeStatus(item.id, status);
      void message.success(`“${item.name}”状态已改为${statusLabels[status]}`);
      reloadTable();
    } catch (error) {
      void message.error(errorMessage(error));
    } finally {
      setUpdatingStatusId(undefined);
    }
  };

  const removeItem = async (item: KnowledgeItemAdmin) => {
    setDeletingId(item.id);
    try {
      await deleteKnowledgeItem(item.id);
      void message.success(`“${item.name}”已删除`);
      reloadTable();
    } catch (error) {
      void message.error(errorMessage(error));
    } finally {
      setDeletingId(undefined);
    }
  };

  const openImport = () => {
    setImportRows([]);
    setImportResult(undefined);
    setImportOpen(true);
  };

  const closeImport = () => {
    if (importing) return;
    setImportOpen(false);
    setImportRows([]);
    setImportResult(undefined);
  };

  const updateImportFiles = (nextFiles: UploadFile[]) => {
    const currentByUid = new Map(importRows.map(item => [item.uid, item]));
    let rejectedType = false;
    let rejectedSingleSize = false;
    const candidates = nextFiles.flatMap(item => {
      if (!hasAcceptedExtension(item.name)) {
        rejectedType = true;
        return [];
      }
      const current = currentByUid.get(item.uid);
      if (current) return [current];
      if (!item.originFileObj) return [];
      if (item.originFileObj.size > maxSingleFileBytes) {
        rejectedSingleSize = true;
        return [];
      }
      return [
        {
          file: item.originFileObj,
          knowledgeType: 'primary_template' as const,
          name: fileNameWithoutExtension(item.name),
          status: 'draft' as const,
          uid: item.uid
        }
      ];
    });

    const rejectedCount = candidates.length > maxFileCount;
    const withinCount = candidates.slice(0, maxFileCount);
    let totalBytes = 0;
    let rejectedTotalSize = false;
    const acceptedRows = withinCount.filter(item => {
      if (totalBytes + item.file.size > maxTotalFileBytes) {
        rejectedTotalSize = true;
        return false;
      }
      totalBytes += item.file.size;
      return true;
    });

    if (rejectedType) {
      void message.warning({ content: '已忽略不支持的文件格式', key: 'knowledge-import-file-type' });
    }
    if (rejectedSingleSize) {
      void message.warning({ content: '单个文件不能超过 10 MB，超大文件已忽略', key: 'knowledge-import-file-size' });
    }
    if (rejectedCount) {
      void message.warning({ content: '一次最多导入 20 个文件，多余文件已忽略', key: 'knowledge-import-file-count' });
    }
    if (rejectedTotalSize) {
      void message.warning({
        content: '文件总大小不能超过 20 MB，超出部分已忽略',
        key: 'knowledge-import-total-size'
      });
    }
    setImportResult(undefined);
    setImportRows(acceptedRows);
  };

  const submitImport = async () => {
    const hasEmptyName = importRows.some(item => !item.name.trim());
    if (!importRows.length || hasEmptyName) {
      void message.warning(importRows.length ? '知识名称不能为空' : '请选择至少一个知识文件');
      return;
    }

    setImporting(true);
    setImportResult(undefined);
    try {
      const result = await importKnowledgeItems({
        rows: importRows.map(item => ({
          file: item.file,
          knowledgeType: item.knowledgeType,
          name: item.name.trim(),
          status: item.status
        }))
      });
      setImportResult(result);
      const summary = `导入完成：成功 ${result.successCount} 条，跳过 ${result.skippedCount} 条，失败 ${result.failedCount} 条`;
      if (result.failedCount > 0 || result.skippedCount > 0) {
        void message.warning(summary);
      } else {
        void message.success(summary);
      }
      if (result.successCount > 0) {
        actionRef.current?.reloadAndRest?.();
      }
    } catch (error) {
      void message.error(errorMessage(error));
    } finally {
      setImporting(false);
    }
  };

  const columns: ProColumns<KnowledgeItemAdmin>[] = [
    {
      dataIndex: 'name',
      ellipsis: true,
      title: '名称',
      render: (_, item) => (
        <Button
          aria-label={`查看“${item.name}”详情`}
          onClick={() => openDetail(item)}
          style={{ height: 'auto', padding: 0 }}
          type="link"
        >
          {item.name}
        </Button>
      )
    },
    {
      dataIndex: 'knowledgeType',
      fieldProps: { options: knowledgeTypeOptions },
      title: '知识类型',
      valueType: 'select',
      render: (_, item) => knowledgeTypeLabels[item.knowledgeType] || item.knowledgeType
    },
    {
      dataIndex: 'status',
      fieldProps: { options: coloredStatusOptions },
      title: '状态',
      valueType: 'select',
      width: 130,
      render: (_, item) => (
        <Select
          aria-label={`修改“${item.name}”的状态`}
          disabled={!canEdit || updatingStatusId !== undefined}
          loading={updatingStatusId === item.id}
          onChange={status => void changeStatus(item, status)}
          options={coloredStatusOptions}
          size="small"
          value={item.status}
          variant="borderless"
          style={{ minWidth: 104 }}
        />
      )
    },
    { dataIndex: 'updateTime', search: false, title: '更新时间', valueType: 'dateTime', width: 180 },
    {
      fixed: 'right',
      search: false,
      title: '操作',
      valueType: 'option',
      width: 130,
      render: (_, item) => (
        <RowActions
          actions={[
            { icon: <EyeOutlined />, key: 'view', label: '查看', onClick: () => openDetail(item) },
            canEdit && {
              icon: <EditOutlined />,
              key: 'edit',
              label: '修改',
              onClick: () => void openEdit(item)
            },
            canRemove && {
              confirm: `确认删除“${item.name}”吗？删除后不可恢复。`,
              confirmProps: { cancelText: '取消', okButtonProps: { danger: true }, okText: '删除' },
              danger: true,
              disabled: deletingId === item.id,
              icon: <DeleteOutlined />,
              key: 'delete',
              label: '删除',
              onClick: () => void removeItem(item)
            }
          ]}
        />
      )
    }
  ];

  if (!canQuery) {
    return (
      <PageContainer title="运营知识库">
        <Result status="403" title="403" subTitle="暂无知识库查询权限，请联系管理员授权" />
      </PageContainer>
    );
  }

  return (
    <PageContainer title="运营知识库">
      {loadError && (
        <Alert
          closable
          description={loadError}
          message="知识条目加载失败"
          onClose={() => setLoadError(undefined)}
          showIcon
          type="error"
        />
      )}

      <ProTable<KnowledgeItemAdmin, KnowledgeItemQuery>
        actionRef={actionRef}
        columns={columns}
        locale={{ emptyText: '暂无知识条目' }}
        pagination={{ defaultPageSize: 20, showQuickJumper: true, showSizeChanger: true }}
        request={async params => {
          setLoadError(undefined);
          try {
            return await pageKnowledgeItems(params);
          } catch (error) {
            setLoadError(errorMessage(error));
            return { data: [], success: false, total: 0 };
          }
        }}
        rowKey="id"
        search={{ labelWidth: 'auto' }}
        toolBarRender={() => [
          canAdd && (
            <Button aria-label="新增知识" icon={<PlusOutlined />} key="add" onClick={openCreate}>
              新增知识
            </Button>
          ),
          canImport && (
            <Button
              aria-label="导入知识库"
              icon={<CloudUploadOutlined />}
              key="import"
              onClick={openImport}
              type="primary"
            >
              导入知识库
            </Button>
          )
        ]}
      />

      <Drawer
        destroyOnHidden
        open={detailOpen}
        title="知识详情"
        size={760}
        onClose={() => {
          detailRequestGeneration.current += 1;
          setDetailOpen(false);
        }}
      >
        <Spin spinning={detailLoading}>
          {detailError && (
            <Alert
              action={
                <Button size="small" onClick={() => detailId !== undefined && void loadDetail(detailId)}>
                  重试
                </Button>
              }
              description={detailError}
              message="知识详情加载失败"
              showIcon
              type="error"
            />
          )}
          {detail && (
            <Space orientation="vertical" size="large" style={{ width: '100%' }}>
              <Descriptions bordered column={2} size="small">
                <Descriptions.Item label="名称" span={2}>
                  {detail.name}
                </Descriptions.Item>
                <Descriptions.Item label="知识类型">
                  {knowledgeTypeLabels[detail.knowledgeType] || detail.knowledgeType}
                </Descriptions.Item>
                <Descriptions.Item label="状态">
                  <Tag color={statusColors[detail.status]}>{statusLabels[detail.status] || detail.status}</Tag>
                </Descriptions.Item>
                <Descriptions.Item label="版本">版本 {detail.versionNo}</Descriptions.Item>
                <Descriptions.Item label="更新时间">{detail.updateTime || '-'}</Descriptions.Item>
                <Descriptions.Item label="摘要" span={2}>
                  {detail.summary || '暂无摘要'}
                </Descriptions.Item>
              </Descriptions>
              <div>
                <Typography.Title level={5}>正文</Typography.Title>
                <Typography.Paragraph
                  style={{ marginBottom: 0, maxHeight: '55vh', overflow: 'auto', whiteSpace: 'pre-wrap' }}
                >
                  {detail.content}
                </Typography.Paragraph>
              </div>
            </Space>
          )}
        </Spin>
      </Drawer>

      <Modal
        cancelButtonProps={{ disabled: formSubmitting }}
        cancelText="取消"
        closable={!formSubmitting}
        confirmLoading={formSubmitting}
        destroyOnHidden
        keyboard={!formSubmitting}
        mask={{ closable: !formSubmitting }}
        okText="保存"
        open={formOpen}
        title={editingId === undefined ? '新增知识' : '编辑知识'}
        width={760}
        onCancel={() => closeKnowledgeForm()}
        onOk={() => void submitKnowledgeForm()}
      >
        <Spin spinning={formLoading}>
          <Form form={knowledgeForm} layout="vertical" preserve={false}>
            <Form.Item label="名称" name="name" rules={[{ message: '请输入知识名称', required: true }]}>
              <Input maxLength={200} placeholder="请输入便于运营人员识别的名称" showCount />
            </Form.Item>
            <Space align="start" size="middle" style={{ width: '100%' }}>
              <Form.Item
                label="知识类型"
                name="knowledgeType"
                rules={[{ message: '请选择知识类型', required: true }]}
                style={{ minWidth: 240 }}
              >
                <Select options={knowledgeTypeOptions} />
              </Form.Item>
              <Form.Item
                label="状态"
                name="status"
                rules={[{ message: '请选择状态', required: true }]}
                style={{ minWidth: 180 }}
              >
                <Select options={coloredStatusOptions} />
              </Form.Item>
            </Space>
            <Form.Item label="摘要" name="summary">
              <Input.TextArea maxLength={500} placeholder="可选，用一句话说明这条知识的用途" rows={3} showCount />
            </Form.Item>
            <Form.Item label="正文" name="content" rules={[{ message: '请输入知识正文', required: true }]}>
              <Input.TextArea placeholder="请输入知识正文" rows={12} />
            </Form.Item>
          </Form>
        </Spin>
      </Modal>

      <Modal
        cancelButtonProps={{ disabled: importing }}
        cancelText="取消"
        closable={!importing}
        confirmLoading={importing}
        destroyOnHidden
        keyboard={!importing}
        mask={{ closable: !importing }}
        okButtonProps={{
          disabled: importResult ? false : importRows.length === 0 || importRows.some(item => !item.name.trim())
        }}
        okText={importResult ? '完成' : '开始导入'}
        open={importOpen}
        title="批量导入知识库"
        width={760}
        onCancel={closeImport}
        onOk={() => (importResult ? closeImport() : void submitImport())}
      >
        <Spin description="正在导入知识库" spinning={importing}>
          <div aria-busy={importing}>
            <Space orientation="vertical" size="middle" style={{ width: '100%' }}>
              <Upload.Dragger
                accept={acceptValue}
                beforeUpload={() => false}
                disabled={importing || Boolean(importResult)}
                fileList={importRows.map(item => ({ name: item.file.name, uid: item.uid }))}
                multiple
                showUploadList={false}
                onChange={({ fileList }) => updateImportFiles(fileList)}
              >
                <p className="ant-upload-drag-icon">
                  <CloudUploadOutlined />
                </p>
                <p className="ant-upload-text">点击或拖拽知识文件到此区域</p>
                <p className="ant-upload-hint">
                  支持 .md、.markdown、.txt、.text、.json、.csv、.yaml、.yml；最多 20 个，单个不超过 10 MB，总计不超过
                  20 MB
                </p>
              </Upload.Dragger>

              {importRows.length > 0 && (
                <Space orientation="vertical" size="small" style={{ width: '100%' }}>
                  <Typography.Text strong>逐个确认名称、知识类型和状态</Typography.Text>
                  {importRows.map(item => (
                    <div
                      key={item.uid}
                      style={{ border: '1px solid var(--ant-color-border-secondary)', borderRadius: 8, padding: 12 }}
                    >
                      <Space align="center" style={{ justifyContent: 'space-between', width: '100%' }}>
                        <Typography.Text strong>{item.file.name}</Typography.Text>
                        <Button
                          aria-label={`移除 ${item.file.name}`}
                          danger
                          disabled={importing || Boolean(importResult)}
                          icon={<DeleteOutlined />}
                          size="small"
                          type="link"
                          onClick={() => setImportRows(current => current.filter(row => row.uid !== item.uid))}
                        >
                          移除
                        </Button>
                      </Space>
                      <Space size="small" style={{ marginTop: 8, width: '100%' }} wrap>
                        <Input
                          aria-label={`${item.file.name} 的知识名称`}
                          disabled={importing || Boolean(importResult)}
                          maxLength={200}
                          onChange={event => {
                            const name = event.target.value;
                            setImportRows(current =>
                              current.map(row => (row.uid === item.uid ? { ...row, name } : row))
                            );
                          }}
                          placeholder="知识名称"
                          style={{ width: 260 }}
                          value={item.name}
                        />
                        <Select
                          aria-label={`${item.file.name} 的知识类型`}
                          disabled={importing || Boolean(importResult)}
                          onChange={knowledgeType =>
                            setImportRows(current =>
                              current.map(row => (row.uid === item.uid ? { ...row, knowledgeType } : row))
                            )
                          }
                          options={knowledgeTypeOptions}
                          style={{ width: 150 }}
                          value={item.knowledgeType}
                        />
                        <Select
                          aria-label={`${item.file.name} 的状态`}
                          disabled={importing || Boolean(importResult)}
                          onChange={status =>
                            setImportRows(current =>
                              current.map(row => (row.uid === item.uid ? { ...row, status } : row))
                            )
                          }
                          options={coloredStatusOptions}
                          style={{ width: 130 }}
                          value={item.status}
                        />
                      </Space>
                    </div>
                  ))}
                </Space>
              )}

              {importResult && (
                <Space orientation="vertical" size="small" style={{ width: '100%' }}>
                  <Alert
                    message={`导入结果：成功 ${importResult.successCount} 条，跳过 ${importResult.skippedCount} 条，失败 ${importResult.failedCount} 条`}
                    showIcon
                    type={
                      importResult.failedCount > 0 ? 'error' : importResult.skippedCount > 0 ? 'warning' : 'success'
                    }
                  />
                  <List
                    bordered
                    dataSource={importResult.files}
                    size="small"
                    renderItem={result => {
                      const meta = importResultMeta[result.status] || { color: 'default', label: result.status };
                      return (
                        <List.Item>
                          <Space align="start">
                            <Tag color={meta.color}>{meta.label}</Tag>
                            <Space orientation="vertical" size={0}>
                              <Typography.Text strong>{result.fileName}</Typography.Text>
                              <Typography.Text type="secondary">
                                {result.message || (result.status === 'success' ? '导入成功' : '未提供详情')}
                              </Typography.Text>
                            </Space>
                          </Space>
                        </List.Item>
                      );
                    }}
                  />
                </Space>
              )}
            </Space>
          </div>
        </Spin>
      </Modal>
    </PageContainer>
  );
}
