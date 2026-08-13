import { Form, Input, Modal } from 'antd';
import React, { useEffect, useRef, useState } from 'react';
import type { UserScriptInput } from '@/services/ai-video/script/types';

interface Values {
  displayTitle: string;
  scriptText: string;
}

interface ScriptEditorModalProps {
  open: boolean;
  idempotencyKey: string;
  initialValues?: Values;
  title?: string;
  onCancel: () => void;
  onSubmit: (input: UserScriptInput) => Promise<void> | void;
}

const codePointLength = (value: string) => Array.from(value).length;

const ScriptEditorModal: React.FC<ScriptEditorModalProps> = ({
  open,
  idempotencyKey,
  initialValues,
  title = '新建文案',
  onCancel,
  onSubmit,
}) => {
  const [form] = Form.useForm<Values>();
  const [saving, setSaving] = useState(false);
  const savingRef = useRef(false);

  useEffect(() => {
    if (!open) return;
    form.setFieldsValue(initialValues ?? { displayTitle: '', scriptText: '' });
  }, [form, initialValues, open]);

  const cancel = () => {
    if (!form.isFieldsTouched()) {
      onCancel();
      return;
    }
    Modal.confirm({
      title: '放弃未保存的修改？',
      content: '关闭后，本次输入的内容不会保留。',
      okText: '放弃修改',
      cancelText: '继续编辑',
      onOk: onCancel,
    });
  };

  const submit = async () => {
    if (savingRef.current) return;
    savingRef.current = true;
    setSaving(true);
    try {
      const values = await form.validateFields();
      await onSubmit({
        displayTitle: values.displayTitle.trim(),
        scriptText: values.scriptText.replace(/\r\n?/g, '\n').trim(),
        idempotencyKey,
      });
    } catch {
      // Validation and API errors are rendered by Form or the parent page.
    } finally {
      savingRef.current = false;
      setSaving(false);
    }
  };

  return (
    <Modal
      centered
      destroyOnHidden
      open={open}
      title={title}
      okText="保存"
      cancelText="取消"
      confirmLoading={saving}
      okButtonProps={{ disabled: saving }}
      onCancel={cancel}
      onOk={() => void submit()}
    >
      <Form form={form} layout="vertical" preserve>
        <Form.Item
          name="displayTitle"
          label="标题"
          rules={[
            { required: true, whitespace: true, message: '请输入标题' },
            {
              validator: (_, value: string = '') =>
                codePointLength(value.trim()) <= 100
                  ? Promise.resolve()
                  : Promise.reject(new Error('标题不能超过 100 个字符')),
            },
          ]}
        >
          <Input maxLength={200} placeholder="请输入文案标题" />
        </Form.Item>
        <Form.Item
          name="scriptText"
          label="文案正文"
          rules={[
            { required: true, whitespace: true, message: '请输入文案正文' },
            {
              validator: (_, value: string = '') =>
                codePointLength(value.trim()) <= 20_000
                  ? Promise.resolve()
                  : Promise.reject(new Error('文案正文不能超过 20000 个字符')),
            },
          ]}
        >
          <Input.TextArea
            autoSize={{ minRows: 10, maxRows: 20 }}
            placeholder="粘贴或输入文案正文"
            showCount
          />
        </Form.Item>
      </Form>
    </Modal>
  );
};

export default ScriptEditorModal;
