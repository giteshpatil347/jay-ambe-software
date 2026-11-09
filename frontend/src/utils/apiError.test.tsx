import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { Button, Form, Input, notification } from 'antd';
import { AxiosError } from 'axios';
import { applyApiError } from './apiError';

const axiosErr = (status: number, data: object) =>
  new AxiosError('x', 'ERR', undefined, undefined, { status, data } as never);

function Harness({ error }: { error: unknown }) {
  const [form] = Form.useForm();
  return (
    <Form form={form}>
      <Form.Item name="mobile" label="Mobile"><Input /></Form.Item>
      <Button onClick={() => applyApiError(error, form)}>go</Button>
    </Form>
  );
}

describe('applyApiError', () => {
  it('maps 422 fieldErrors onto form fields', async () => {
    render(<Harness error={axiosErr(422, { message: 'Validation failed', fieldErrors: [{ field: 'mobile', message: 'must be 10 digits' }] })} />);
    await userEvent.click(screen.getByText('go'));
    expect(await screen.findByText('must be 10 digits')).toBeInTheDocument();
  });

  it('shows 409 message as a notification', async () => {
    const spy = vi.spyOn(notification, 'warning').mockImplementation(() => undefined);
    render(<Harness error={axiosErr(409, { message: 'Unit cannot change: stock exists' })} />);
    await userEvent.click(screen.getByText('go'));
    await waitFor(() => expect(spy).toHaveBeenCalledWith(expect.objectContaining({ description: 'Unit cannot change: stock exists' })));
  });
});
