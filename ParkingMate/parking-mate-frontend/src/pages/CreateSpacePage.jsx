import { useState } from 'react';
import axios from 'axios';
import { useNavigate } from 'react-router-dom';
import '../App.css';

function CreateSpacePage() {
    const [formData, setFormData] = useState({
        address: '',
        pricePerHour: '',
        description: '',
    });
    const [errors, setErrors] = useState({});
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const navigate = useNavigate();

    const handleChange = (e) => {
        const { name, value } = e.target;
        setFormData(prev => ({ ...prev, [name]: value }));
        if (errors[name]) {
            setErrors(prev => ({ ...prev, [name]: '' }));
        }
    };

    const validate = () => {
        const newErrors = {};
        
        if (!formData.address.trim()) {
            newErrors.address = '주소를 입력해주세요.';
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

        if (!validate()) {
            return;
        }

        setLoading(true);

        try {
            await axios.post('http://localhost:8080/api/spaces', {
                address: formData.address.trim(),
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
                <div className="form-group">
                    <label htmlFor="address" className="form-label required">
                        주소
                    </label>
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

                <div className="form-group">
                    <label htmlFor="pricePerHour" className="form-label required">
                        시간당 가격 (원)
                    </label>
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

                <div className="form-group">
                    <label htmlFor="description" className="form-label">
                        상세 설명 (선택사항)
                    </label>
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
                            <>
                                <span className="spinner"></span>
                                등록 중...
                            </>
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
