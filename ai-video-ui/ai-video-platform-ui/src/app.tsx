import type { ReactNode } from 'react';
import { App as AntdApp } from 'antd';
import { QueryClientProvider } from '@tanstack/react-query';
import { appEnv } from '@/utils/env';
import { queryClient } from '@/utils/queryClient';

export async function getInitialState() {
  return {
    name: appEnv.logoTitle
  };
}

export function rootContainer(container: ReactNode) {
  return (
    <AntdApp>
      <QueryClientProvider client={queryClient}>{container}</QueryClientProvider>
    </AntdApp>
  );
}
