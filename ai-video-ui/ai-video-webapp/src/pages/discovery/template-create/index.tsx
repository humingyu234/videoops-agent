import { CloudUploadOutlined } from '@ant-design/icons';
import { useMutation } from '@tanstack/react-query';
import { history, useParams } from '@umijs/max';
import type { UploadFile, UploadProps } from 'antd';
import {
  Button,
  Checkbox,
  Form,
  Input,
  InputNumber,
  message,
  Select,
  Upload,
} from 'antd';
import { useEffect } from 'react';
import type {
  WorkflowCreationConfig,
  WorkflowInputField,
} from '@/services/ai-video/discovery/types';
import { workflowOrdersApi } from '@/services/ai-video/workflow-orders/api';
import type {
  ReadyAsset,
  WorkflowInputValue,
} from '@/services/ai-video/workflow-orders/types';
import { workflowUploadsApi } from '@/services/ai-video/workflow-uploads/api';
import styles from './index.module.css';

function idempotencyKey(): string {
  return globalThis.crypto?.randomUUID?.() ?? `workflow-${Date.now()}`;
}

function fileListToAssets(files?: UploadFile[]): ReadyAsset[] {
  return (files ?? []).flatMap((file) => {
    const assetId = (file.response as { assetId?: string } | undefined)
      ?.assetId;
    return assetId ? [{ assetId }] : [];
  });
}

function formValueToInput(
  field: WorkflowInputField,
  value: WorkflowInputValue | UploadFile[] | undefined,
): WorkflowInputValue | undefined {
  if (field.valueType === 'asset_array') {
    return fileListToAssets(value as UploadFile[] | undefined);
  }
  if (
    (field.valueType === 'integer' || field.valueType === 'decimal') &&
    typeof value === 'string'
  ) {
    return Number(value);
  }
  return value as WorkflowInputValue | undefined;
}

function fieldControl({
  field,
  templateId,
  schemaHash,
}: {
  field: WorkflowInputField;
  templateId: string;
  schemaHash: string;
}) {
  if (field.control === 'textarea')
    return <Input.TextArea placeholder={field.placeholder} rows={4} />;
  if (field.control === 'integer')
    return (
      <InputNumber
        className={styles.fullWidth}
        placeholder={field.placeholder}
        precision={0}
        stringMode
      />
    );
  if (field.control === 'decimal')
    return (
      <InputNumber
        className={styles.fullWidth}
        placeholder={field.placeholder}
        stringMode
      />
    );
  if (field.control === 'boolean') return <Checkbox>启用</Checkbox>;
  if (field.control === 'select' || field.control === 'multi_select')
    return (
      <Select
        mode={field.control === 'multi_select' ? 'multiple' : undefined}
        options={field.options}
        placeholder={field.placeholder}
      />
    );
  if (
    field.control === 'image' ||
    field.control === 'audio' ||
    field.control === 'video' ||
    field.control === 'file'
  ) {
    const customRequest: UploadProps['customRequest'] = async ({
      file,
      onError,
      onSuccess,
    }) => {
      try {
        const source = file as File;
        const session = await workflowUploadsApi.create({
          templateId,
          schemaHash,
          inputKey: field.inputKey,
          file: source,
          idempotencyKey: idempotencyKey(),
        });
        if (!session.singlePutUrl) throw new Error('上传会话未返回传输地址');
        await workflowUploadsApi.transfer(session.singlePutUrl, source);
        const completed = await workflowUploadsApi.complete(session.uploadId);
        if (!completed.assetId || completed.assetStatus !== 'ready')
          throw new Error('素材尚未准备完成');
        onSuccess?.({ assetId: completed.assetId });
      } catch (error) {
        onError?.(error instanceof Error ? error : new Error('素材上传失败'));
      }
    };
    return (
      <Upload
        accept={field.control === 'file' ? undefined : `${field.control}/*`}
        className={styles.assetUpload}
        customRequest={customRequest}
        data-testid="workflow-asset-upload"
        listType="picture-card"
        maxCount={field.constraints?.maxItems ?? 1}
        multiple={(field.constraints?.maxItems ?? 1) > 1}
      >
        <div
          className={styles.uploadTrigger}
          data-testid="workflow-upload-trigger"
        >
          <CloudUploadOutlined />
          <span>上传素材</span>
        </div>
      </Upload>
    );
  }
  return <Input placeholder={field.placeholder} />;
}

export function TemplateRunForm({
  config,
  templateId,
}: {
  config: WorkflowCreationConfig;
  templateId: string;
}) {
  const [form] =
    Form.useForm<Record<string, WorkflowInputValue | UploadFile[]>>();
  const createOrder = useMutation({
    mutationFn: ({
      inputs,
    }: {
      inputs: Record<string, WorkflowInputValue>;
      resultWindow: Window | null;
    }) =>
      workflowOrdersApi.create({
        templateId,
        schemaHash: config.schemaHash,
        inputs,
        idempotencyKey: idempotencyKey(),
      }),
    onSuccess: ({ orderId }, { resultWindow }) => {
      const resultPath = `/orders/${encodeURIComponent(orderId)}`;
      if (resultWindow && !resultWindow.closed) {
        resultWindow.location.replace(
          new URL(resultPath, window.location.origin).href,
        );
        return;
      }
      history.push(resultPath);
    },
    onError: (_error, { resultWindow }) => {
      if (resultWindow && !resultWindow.closed) resultWindow.close();
      void message.error('提交失败，请稍后重试');
    },
  });
  const submit = (
    values: Record<string, WorkflowInputValue | UploadFile[]>,
  ) => {
    const inputs = Object.fromEntries(
      config.fields.map((field) => [
        field.inputKey,
        formValueToInput(field, values[field.inputKey]),
      ]),
    ) as Record<string, WorkflowInputValue>;
    const resultWindow = window.open('about:blank', '_blank');
    if (resultWindow) resultWindow.opener = null;
    createOrder.mutate({ inputs, resultWindow });
  };
  return (
    <Form form={form} layout="vertical" onFinish={submit} scrollToFirstError>
      {config.fields.map((field) => {
        const upload = field.valueType === 'asset_array';
        return (
          <Form.Item
            key={field.inputKey}
            name={field.inputKey}
            label={field.label}
            extra={field.description}
            valuePropName={
              field.control === 'boolean'
                ? 'checked'
                : upload
                  ? 'fileList'
                  : 'value'
            }
            getValueFromEvent={
              upload
                ? (event: { fileList?: UploadFile[] }) => event.fileList
                : undefined
            }
            rules={
              field.required
                ? [{ required: true, message: `请填写或上传${field.label}` }]
                : undefined
            }
          >
            {fieldControl({
              field,
              schemaHash: config.schemaHash,
              templateId,
            })}
          </Form.Item>
        );
      })}
      <Button
        block
        className={styles.runButton}
        htmlType="submit"
        loading={createOrder.isPending}
        size="large"
      >
        立即运行
      </Button>
    </Form>
  );
}

export default function TemplateCreatePage() {
  const { templateId = '' } = useParams<{ templateId: string }>();
  useEffect(() => {
    history.replace(
      templateId
        ? `/discover/templates/${encodeURIComponent(templateId)}`
        : '/discover',
    );
  }, [templateId]);
  return null;
}
