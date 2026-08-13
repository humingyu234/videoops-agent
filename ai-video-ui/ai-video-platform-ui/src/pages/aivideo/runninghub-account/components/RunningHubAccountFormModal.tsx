import { ModalForm, ProFormText } from '@ant-design/pro-components';
import { Alert, Form } from 'antd';
import { useEffect } from 'react';
import type { RunningHubAccountDetail } from '@/api/aivideo/runninghub-account/types';
import { buildRunningHubAccountFormValues, type RunningHubAccountFormValues } from './accountFormModel';

interface RunningHubAccountFormModalProps {
  detail?: RunningHubAccountDetail;
  open: boolean;
  readonly?: boolean;
  submitting: boolean;
  onClose: () => void;
  onFinish: (values: RunningHubAccountFormValues) => Promise<boolean>;
}

export default function RunningHubAccountFormModal({
  detail,
  open,
  readonly = false,
  submitting,
  onClose,
  onFinish
}: RunningHubAccountFormModalProps) {
  const [form] = Form.useForm<RunningHubAccountFormValues>();
  const editing = Boolean(detail);

  useEffect(() => {
    if (open) {
      form.resetFields();
      form.setFieldsValue(buildRunningHubAccountFormValues(detail));
    } else {
      form.resetFields();
    }
  }, [detail, form, open]);

  const close = () => {
    if (submitting) return;
    form.resetFields();
    onClose();
  };

  return (
    <ModalForm<RunningHubAccountFormValues>
      form={form}
      layout="vertical"
      readonly={readonly}
      modalProps={{
        cancelButtonProps: { disabled: submitting },
        closable: !submitting,
        destroyOnHidden: true,
        maskClosable: !submitting,
        onCancel: close
      }}
      open={open}
      submitter={
        readonly
          ? false
          : {
              resetButtonProps: { disabled: submitting },
              submitButtonProps: { loading: submitting }
            }
      }
      title={
        readonly
          ? `查看 RunningHub 账号“${detail?.accountName || ''}”`
          : detail
            ? `修改 RunningHub 账号“${detail.accountName}”`
            : '新增 RunningHub 账号'
      }
      width={640}
      onFinish={onFinish}
      onOpenChange={nextOpen => !nextOpen && close()}
    >
      <ProFormText name="accountId" hidden />
      <ProFormText name="expectedRevision" hidden />
      <ProFormText
        label="账号名称"
        name="accountName"
        placeholder="请输入便于运营识别的账号名称"
        rules={[{ required: true, message: '账号名称不能为空' }]}
      />
      {detail && (
        <Alert
          description={
            readonly
              ? `当前密钥：${detail.apiKeyMasked || (detail.hasApiKey ? '已配置' : '未配置')}。密钥明文不会展示。`
              : `当前密钥：${detail.apiKeyMasked || (detail.hasApiKey ? '已配置' : '未配置')}。密钥不会回填，留空保持原值。`
          }
          showIcon
          type="info"
        />
      )}
      {!readonly && (
        <ProFormText.Password
          fieldProps={{ autoComplete: 'new-password' }}
          label={editing ? '新的 API Key' : 'API Key'}
          name="apiKey"
          placeholder={editing ? '留空保持原值' : '请输入 RunningHub API Key'}
          rules={editing ? [] : [{ required: true, message: 'API Key 不能为空' }]}
        />
      )}
    </ModalForm>
  );
}
