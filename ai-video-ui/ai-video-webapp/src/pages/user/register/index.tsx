import { Alert } from 'antd';
import React from 'react';

const RegisterPlaceholder: React.FC = () => {
  return (
    <main aria-labelledby="register-placeholder-title">
      <h1 id="register-placeholder-title">注册功能准备中</h1>
      <Alert
        description="认证后端的注册协议接通后，将在这里提供完整的账号注册流程。"
        showIcon
        title="当前版本暂不开放注册"
        type="info"
      />
    </main>
  );
};

export default RegisterPlaceholder;
