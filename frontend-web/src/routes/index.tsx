import { createBrowserRouter, Navigate } from 'react-router-dom';
import Playground from '@/pages/Playground';

export const router = createBrowserRouter([
  {
    path: '/',
    element: <Navigate to="/playground" replace />,
  },
  {
    path: '/playground',
    element: <Playground />,
  },
]);
