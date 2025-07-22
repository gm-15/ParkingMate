import { createContext, useState, useContext, useEffect } from 'react';
import axios from 'axios';

// 방송국 채널 만들기
const AuthContext = createContext(null);

// 방송국 운영 컴포넌트
export function AuthProvider({ children }) {
    const [token, setToken] = useState(localStorage.getItem('token'));

    useEffect(() => {
        if (token) {
            // 토큰이 있으면, 모든 axios 요청 헤더에 자동으로 토큰을 포함시킴
            axios.defaults.headers.common['Authorization'] = `Bearer ${token}`;
            localStorage.setItem('token', token);
        } else {
            delete axios.defaults.headers.common['Authorization'];
            localStorage.removeItem('token');
        }
    }, [token]);

    const login = (newToken) => {
        setToken(newToken);
    };

    const logout = () => {
        setToken(null);
    };

    // 방송할 내용
    const value = {
        isLoggedIn: !!token,
        token,
        login,
        logout,
    };

    return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>;
}

// 다른 컴포넌트에서 방송 내용을 쉽게 들을 수 있게 하는 도구
export function useAuth() {
    return useContext(AuthContext);
}