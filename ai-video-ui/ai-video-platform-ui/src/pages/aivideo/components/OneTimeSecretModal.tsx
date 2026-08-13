import { Alert, Input, Modal, Typography } from 'antd';

interface OneTimeSecretModalProps {
  title: string;
  label: string;
  value?: string;
  onClose: () => void;
}

export default function OneTimeSecretModal({ title, label, value, onClose }: OneTimeSecretModalProps) {
  return (
    <Modal
      destroyOnHidden
      footer={null}
      open={Boolean(value)}
      title={title}
      onCancel={onClose}
      afterClose={onClose}
    >
      <Alert showIcon title="关闭后不可再次查看" type="warning" />
      <Typography.Paragraph style={{ marginTop: 16 }}>{label}</Typography.Paragraph>
      <Input.Password readOnly value={value || ''} visibilityToggle={{ visible: true }} />
    </Modal>
  );
}
