import { useState } from 'react';
import apiClient from '../api/axios';
import { useNavigate } from 'react-router-dom';
import '../App.css';

function CreateSpacePage() {
    const [formData, setFormData] = useState({
        address: '',
        latitude: '',
        longitude: '',
        pricePerHour: '',
        description: '',
    });
    const [errors, setErrors] = useState({});
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const [geoLoading, setGeoLoading] = useState(false);
    const navigate = useNavigate();

    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
        if (errors[name]) {
            setErrors(prev => ({ ...prev, [name]: '' }));
        }
    };

    const handleGetCurrentLocation = () => {
        if (!navigator.geolocation) {
            setError('이 브라우저는 위치 기능을 지원하지 않습니다.');
            return;
        }
        setGeoLoading(true);
        navigator.geolocation.getCurrentPosition(
            (pos) => {
                setFormData(prev => ({
                    ...prev,
                    latitude: pos.coords.latitude.toFixed(6),
                    longitude: pos.coords.longitude.toFixed(6),
                }));
                setGeoLoading(false);
            },
            () => {
                setError('위치 정보를 가져올 수 없습니다. 직접 입력해주세요.');
                setGeoLoading(false);
            }
        );
    };

    const validate = () => {
        const newErrors = {};

        if (!formData.address.trim()) {
            newErrors.address = '주소를 입력해주세요.';
        }
        if (!formData.latitude) {
            newErrors.latitude = '위도를 입력해주세요.';
        } else if (isNaN(formData.latitude) || formData.latitude < -90 || formData.latitude > 90) {
            newErrors.latitude = '위도는 -90 ~ 90 사이 숫자여야 합니다.';
        }
        if (!formData.longitude) {
            newErrors.longitude = '경도를 입력해주세요.';
        } else if (isNaN(formData.longitude) || formData.longitude < -180 || formData.longitude > 180) {
            newErrors.longitude = '경도는 -180 ~ 180 사이 숫자여야 합니다.';
        }
        if (!formData.pricePerHour) {
            newErrors.pricePerHour = '시간당 가격을 입력해주세요.';
        } else if (parseInt(formData.pricePerHour) < 0) {
            newErrors.pricePerHour = '가격은 0원 이상이어야 합니다.';
        }

        setErrors(newErrors);
        return Object.keys(newErrors).length === 0;
    };

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');

        if (!validate()) return;

        setLoading(true);
        try {
            await apiClient.post('/spaces', {
                address: formData.address.trim(),
                latitude: parseFloat(formData.latitude),
                longitude: parseFloat(formData.longitude),
                pricePerHour: parseInt(formData.pricePerHour, 10),
                description: formData.description.trim() || null,
            });
            navigate('/', {
                state: { message: '주차 공간이 성공적으로 등록되었습니다.' }
            });
        } catch (err) {
            const errorMessage = err.response?.data?.message || '주차 공간 등록에 실패했습니다.';
            setError(errorMessage);
        } finally {
            setLoading(false);
        }
    };

    return (
        <div style={{ maxWidth: '600px', margin: '0 auto' }}>
            <h1>새 주차 공간 등록</h1>
            <p style={{ color: 'var(--color-gray-500)', marginBottom: 'var(--spacing-6)' }}>
                공유할 주차 공간 정보를 입력해주세요
            </p>

            {error && (
                <div className="alert alert-error">
                    <span>⚠️</span>
                    <span>{error}</span>
                </div>
            )}

            <form onSubmit={handleSubmit}>
                {/* 주소 */}
                <div className="form-group">
                    <label htmlFor="address" className="form-label required">주소</label>
                    <input
                        id="address"
                        name="address"
                        type="text"
                        className="form-input"
                        value={formData.address}
                        onChange={handleChange}
                        placeholder="예: 서울시 강남구 테헤란로 123"
                        required
                        disabled={loading}
                    />
                    {errors.address && <div className="form-error">{errors.address}</div>}
                </div>

                {/* 위치 좌표 */}
                <div className="form-group">
                    <label className="form-label required">위치 좌표</label>
                    <button
                        type="button"
                        className="btn btn-outline btn-sm"
                        onClick={handleGetCurrentLocation}
                        disabled={loading || geoLoading}
                        style={{ marginBottom: 'var(--spacing-3)' }}
                    >
                        {geoLoading ? (
                            <><span className="spinner" style={{ width: 16, height: 16 }}></span> 위치 확인 중...</>
                        ) : (
                            '📍 현재 위치 자동 입력'
                        )}
                    </button>
                    <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: 'var(--spacing-3)' }}>
                        <div>
                            <input
                                id="latitude"
                                name="latitude"
                                type="number"
                                className="form-input"
                                value={formData.latitude}
                                onChange={handleChange}
                                placeholder="위도 (예: 37.4979)"
                                step="0.000001"
                                disabled={loading}
                            />
                            {errors.latitude && <div className="form-error">{errors.latitude}</div>}
                        </div>
                        <div>
                            <input
                                id="longitude"
                                name="longitude"
                                type="number"
                                className="form-input"
                                value={formData.longitude}
                                onChange={handleChange}
                                placeholder="경도 (예: 127.0276)"
                                step="0.000001"
                                disabled={loading}
                            />
                            {errors.longitude && <div className="form-error">{errors.longitude}</div>}
                        </div>
                    </div>
                    <p className="form-help">현재 위치 버튼을 누르거나 좌표를 직접 입력하세요. 위치 검색에 사용됩니다.</p>
                </div>

                {/* 시간당 가격 */}
                <div className="form-group">
                    <label htmlFor="pricePerHour" className="form-label required">시간당 가격 (원)</label>
                    <input
                        id="pricePerHour"
                        name="pricePerHour"
                        type="number"
                        className="form-input"
                        value={formData.pricePerHour}
                        onChange={handleChange}
                        placeholder="예: 2000"
                        min="0"
                        step="100"
                        required
                        disabled={loading}
                    />
                    {errors.pricePerHour && <div className="form-error">{errors.pricePerHour}</div>}
                    <div className="form-help">시간당 요금을 입력해주세요 (원 단위)</div>
                </div>

                {/* 상세 설명 */}
                <div className="form-group">
                    <label htmlFor="description" className="form-label">상세 설명 (선택사항)</label>
                    <textarea
                        id="description"
                        name="description"
                        className="form-input"
                        value={formData.description}
                        onChange={handleChange}
                        placeholder="주차 공간에 대한 추가 정보를 입력해주세요 (예: 주차 방법, 주의사항 등)"
                        rows={4}
                        disabled={loading}
                        style={{ resize: 'vertical' }}
                    />
                </div>

                <div style={{ display: 'flex', gap: 'var(--spacing-3)', marginTop: 'var(--spacing-6)' }}>
                    <button
                        type="button"
                        className="btn btn-secondary"
                        onClick={() => navigate('/')}
                        disabled={loading}
                        style={{ flex: 1 }}
                    >
                        취소
                    </button>
                    <button
                        type="submit"
                        className="btn btn-primary"
                        disabled={loading}
                        style={{ flex: 2 }}
                    >
                        {loading ? (
                            <><span className="spinner"></span> 등록 중...</>
                        ) : (
                            '등록하기'
                        )}
                    </button>
                </div>
            </form>
        </div>
    );
}

export default CreateSpacePage;
