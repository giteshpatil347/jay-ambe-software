import type { ReactNode } from 'react';
import { Button, Drawer, Grid, Modal, Space } from 'antd';

interface Props {
  open: boolean;
  title: string;
  onClose: () => void;
  onSubmit: () => void;
  submitting?: boolean;
  submitText?: string;
  children: ReactNode;
}

/** Submit/cancel form container: Modal on >= md, full-screen Drawer with sticky footer on mobile. */
export function FormDialog({ open, title, onClose, onSubmit, submitting, submitText = 'Save', children }: Props) {
  const screens = Grid.useBreakpoint();
  if (screens.md) {
    return (
      <Modal open={open} title={title} onCancel={onClose} onOk={onSubmit} okText={submitText} confirmLoading={submitting}
        destroyOnHidden maskClosable={false}>
        {children}
      </Modal>
    );
  }
  return (
    <Drawer open={open} title={title} onClose={onClose} width="100%" destroyOnHidden
      footer={(
        <Space style={{ width: '100%', justifyContent: 'flex-end' }}>
          <Button onClick={onClose}>Cancel</Button>
          <Button type="primary" loading={submitting} onClick={onSubmit}>{submitText}</Button>
        </Space>
      )}>
      {children}
    </Drawer>
  );
}
