import React, { useState } from 'react';
import { useAuth } from '../contexts/AuthContext';
import './Login.scss';

interface LoginProps {
    onCancel?: () => void;
}

export const Login: React.FC<LoginProps> = ({ onCancel }) => {
    const [email, setEmail] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [isSignUp, setIsSignUp] = useState(false);
    const [needsConfirmation, setNeedsConfirmation] = useState(false);
    const [confirmationCode, setConfirmationCode] = useState('');

    const { signIn, signUp, confirmSignUp } = useAuth();

    const handleSignIn = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');

        try {
            await signIn(email, password);
        } catch (err: any) {
            setError(err.message || 'Failed to sign in');
        }
    };

    const handleSignUp = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');

        try {
            await signUp(email, password, email);
            setNeedsConfirmation(true);
        } catch (err: any) {
            setError(err.message || 'Failed to sign up');
        }
    };

    const handleConfirmSignUp = async (e: React.FormEvent) => {
        e.preventDefault();
        setError('');

        try {
            await confirmSignUp(email, confirmationCode);
            setNeedsConfirmation(false);
            setIsSignUp(false);
            alert('Account confirmed! You can now sign in.');
        } catch (err: any) {
            setError(err.message || 'Failed to confirm account');
        }
    };

    if (needsConfirmation) {
        return (
            <div className="auth-container">
                <h2>Confirm Your Account</h2>
                <p>Please enter the confirmation code sent to your email.</p>
                <form onSubmit={handleConfirmSignUp}>
                    <input
                        type="text"
                        placeholder="Confirmation Code"
                        value={confirmationCode}
                        onChange={(e) => setConfirmationCode(e.target.value)}
                        required
                    />
                    {error && <p className="error">{error}</p>}
                    <button type="submit">Confirm</button>
                </form>
                {onCancel && (
                    <button className="cancel-button" onClick={onCancel}>
                        Cancel
                    </button>
                )}
            </div>
        );
    }

    return (
        <div className="auth-container">
            <h2>{isSignUp ? 'Sign Up' : 'Sign In'}</h2>
            <form onSubmit={isSignUp ? handleSignUp : handleSignIn}>
                <input
                    type="email"
                    placeholder="Email"
                    value={email}
                    onChange={(e) => setEmail(e.target.value)}
                    required
                />
                <input
                    type="password"
                    placeholder="Password"
                    value={password}
                    onChange={(e) => setPassword(e.target.value)}
                    required
                />
                {error && <p className="error">{error}</p>}
                <button type="submit">
                    {isSignUp ? 'Sign Up' : 'Sign In'}
                </button>
            </form>
            <button onClick={() => setIsSignUp(!isSignUp)}>
                {isSignUp ? 'Already have an account? Sign In' : "Don't have an account? Sign Up"}
            </button>
            {onCancel && (
                <button className="cancel-button" onClick={onCancel}>
                    Cancel
                </button>
            )}
        </div>
    );
};