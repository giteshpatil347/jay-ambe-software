import { Link } from 'react-router-dom';
import { Button, Result } from 'antd';

const Back = <Link to="/"><Button type="primary">Back to home</Button></Link>;

export const ForbiddenPage = () => (
  <Result status="403" title="403" subTitle="You do not have permission to view this page." extra={Back} />
);
export const NotFoundPage = () => (
  <Result status="404" title="404" subTitle="Page not found." extra={Back} />
);
