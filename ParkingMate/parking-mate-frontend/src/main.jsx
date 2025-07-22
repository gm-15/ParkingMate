import React from 'react'
import ReactDOM from 'react-dom/client'
import { createBrowserRouter, RouterProvider } from 'react-router-dom'
import { AuthProvider } from './context/AuthContext';
import App from './App.jsx'
import HomePage from './pages/HomePage.jsx';
import SignUpPage from './pages/SignUpPage.jsx'
import LoginPage from './pages/LoginPage.jsx'
import MyPage from './pages/MyPage.jsx';
import CreateSpacePage from './pages/CreateSpacePage.jsx';
import SpaceDetailPage from './pages/SpaceDetailPage.jsx';
import ProtectedRoute from './components/ProtectedRoute.jsx';
import './index.css'

const router = createBrowserRouter([
  {
    path: "/",
    element: <App />,
    children: [
      { index: true, element: <HomePage /> },
      { path: "signup", element: <SignUpPage /> },
      { path: "login", element: <LoginPage /> },
      { path: "spaces/:id", element: <SpaceDetailPage /> },
      { path: "mypage", element: ( <ProtectedRoute><MyPage /></ProtectedRoute> ) },
      { path: "create-space", element: ( <ProtectedRoute><CreateSpacePage /></ProtectedRoute> ) },
    ],
  },
]);

ReactDOM.createRoot(document.getElementById('root')).render(
  <React.StrictMode>
    <AuthProvider>
      <RouterProvider router={router} />
    </AuthProvider>
  </React.StrictMode>,
)