import React, { useState } from 'react';
import { useOVHAuth } from '../contexts/OVHAuthContext';
import './Login.scss';

interface OVHLoginProps {
    onCancel?: () => void;
}

export const OVHLogin: React.FC<OVHLoginProps> = ({ onCancel }) => {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [isRegister, setIsRegister] = useState(false);
    const [confirmPassword, setConfirmPassword] = useState('');
    const [email, setEmail] = useState(''); // For registration only
    const [error, setError] = useState('');
    const { login, register, isLoading } = useOVHAuth();

    const handleSubmit = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');

        try {
            if (isRegister) {
                if (password !== confirmPassword) {
                    setError('Passwords do not match');
                    return;
                }
                await register({ email, username, password, confirmPassword });
                // Registration successful - switch to login mode
                setIsRegister(false);
                setError('');
                // Clear form fields for login
                setPassword('');
                setConfirmPassword('');
                // Show success message (optional)
                console.log('Registration successful! Please login.');
            } else {
                await login(username, password);
                // Login successful - component will re-render due to context update
            }
        } catch (err: any) {
            setError(err.response?.data?.message || err.message || 'An error occurred');
        }
    };

    const toggleMode = () => {
        setIsRegister(!isRegister);
        setError('');
        // Clear form fields
        setUsername('');
        setPassword('');
        setConfirmPassword('');
        setEmail('');
    };

    return (
        <div className="auth-container">
            <div>
                <h2>{isRegister ? 'Register' : 'Login'}</h2>
                
                {error && <div className="error">{error}</div>}
                
                <form onSubmit={handleSubmit}>
                    {isRegister && (
                        <div className="form-group">
                            <label htmlFor="email">Email</label>
                            <input
                                type="email"
                                id="email"
                                value={email}
                                onChange={(e) => setEmail(e.target.value)}
                                required
                            />
                        </div>
                    )}
                    
                    <div className="form-group">
                        <label htmlFor="username">Username</label>
                        <input
                            type="text"
                            id="username"
                            value={username}
                            onChange={(e) => setUsername(e.target.value)}
                            required
                        />
                    </div>
                    
                    <div className="form-group">
                        <label htmlFor="password">Password</label>
                        <input
                            type="password"
                            id="password"
                            value={password}
                            onChange={(e) => setPassword(e.target.value)}
                            required
                        />
                    </div>
                    
                    {isRegister && (
                        <div className="form-group">
                            <label htmlFor="confirmPassword">Confirm Password</label>
                            <input
                                type="password"
                                id="confirmPassword"
                                value={confirmPassword}
                                onChange={(e) => setConfirmPassword(e.target.value)}
                                required
                            />
                        </div>
                    )}
                    
                    <button type="submit" disabled={isLoading}>
                        {isLoading ? 'Processing...' : (isRegister ? 'Register' : 'Login')}
                    </button>
                    
                    <button type="button" onClick={toggleMode}>
                        {isRegister ? 'Already have an account? Login' : 'Need an account? Register'}
                    </button>
                    
                    {onCancel && (
                        <button type="button" onClick={onCancel} className="cancel-button">
                            Cancel
                        </button>
                    )}
                </form>
            </div>
        </div>
    );
};