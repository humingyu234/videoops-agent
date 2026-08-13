import type { FormInstance } from 'antd';
import { ModalForm, ProFormSelect, ProFormText } from '@ant-design/pro-components';
import { Alert, Checkbox, Form } from 'antd';
import type { AppRoleAdmin, AppUserFormValues } from '@/api/aivideo/identity/types';

interface AppUserFormModalProps {
  open: boolean;
  title: string;
  form: FormInstance<AppUserFormValues>;
  roleOptions: AppRoleAdmin[];
  contactPreview?: {
    maskedPhone?: string | null;
    maskedEmail?: string | null;
  };
  onClose: () => void;
  onFinish: (values: AppUserFormValues) => Promise<boolean>;
}

export default function AppUserFormModal({
  open,
  title,
  form,
  roleOptions,
  contactPreview,
  onClose,
  onFinish
}: AppUserFormModalProps) {
  const editingUser = Boolean(Form.useWatch('id', form));
  const clearPhone = Boolean(Form.useWatch('clearPhone', form));
  const clearEmail = Boolean(Form.useWatch('clearEmail', form));

  const closeModal = () => {
    form.resetFields();
    onClose();
  };

  return (
    <ModalForm<AppUserFormValues>
      form={form}
      layout="vertical"
      modalProps={{ destroyOnHidden: true, onCancel: closeModal }}
      open={open}
      title={title}
      width={680}
      onFinish={onFinish}
      onOpenChange={nextOpen => !nextOpen && closeModal()}
    >
      <ProFormText name="id" hidden />
      <ProFormText name="expectedIdentityRevision" hidden />
      {editingUser ? (
        <>
          <Alert
            description={
              <div>
                <div>当前手机号码（脱敏）：{contactPreview?.maskedPhone || '-'}</div>
                <div>当前邮箱（脱敏）：{contactPreview?.maskedEmail || '-'}</div>
              </div>
            }
            title="现有联系方式不会回填到表单；留空即保持原值。"
            showIcon
            type="info"
          />
          <div className="form-grid" style={{ marginTop: 16 }}>
            <ProFormText
              label="显示名称"
              name="displayName"
              rules={[{ required: true, message: '显示名称不能为空' }]}
            />
            <div />
            <ProFormText
              label="新的手机号码"
              name="phone"
              placeholder="留空保持原值"
              fieldProps={{ disabled: clearPhone }}
            />
            <Form.Item name="clearPhone" valuePropName="checked">
              <Checkbox
                onChange={event => {
                  if (event.target.checked) {
                    form.setFieldValue('phone', undefined);
                  }
                }}
              >
                清空现有手机号码
              </Checkbox>
            </Form.Item>
            <ProFormText
              label="新的邮箱"
              name="email"
              placeholder="留空保持原值"
              rules={[{ type: 'email', message: '请输入正确的邮箱地址' }]}
              fieldProps={{ disabled: clearEmail }}
            />
            <Form.Item name="clearEmail" valuePropName="checked">
              <Checkbox
                onChange={event => {
                  if (event.target.checked) {
                    form.setFieldValue('email', undefined);
                  }
                }}
              >
                清空现有邮箱
              </Checkbox>
            </Form.Item>
          </div>
        </>
      ) : (
        <>
          <div className="form-grid">
            <ProFormText
              label="登录账号"
              name="username"
              placeholder="请输入登录账号"
              rules={[{ required: true, message: '登录账号不能为空' }]}
            />
            <ProFormText
              label="显示名称"
              name="displayName"
              placeholder="请输入显示名称"
              rules={[{ required: true, message: '显示名称不能为空' }]}
            />
            <ProFormText label="手机号码" name="phone" placeholder="可选，创建后仅展示脱敏值" />
            <ProFormText label="邮箱" name="email" placeholder="可选，创建后仅展示脱敏值" rules={[{ type: 'email', message: '请输入正确的邮箱地址' }]} />
          </div>
          <ProFormSelect
            label="初始角色"
            mode="multiple"
            name="roleIds"
            options={roleOptions.map(role => ({
              disabled: role.status !== 'active',
              label: `${role.roleName}（${role.roleCode}）`,
              value: role.id
            }))}
            placeholder="请选择至少一个创作端角色"
            rules={[{ required: true, message: '初始角色不能为空' }]}
          />
        </>
      )}
    </ModalForm>
  );
}
