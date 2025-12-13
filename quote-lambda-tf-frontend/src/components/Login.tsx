import React, { useState } from 'react';
import { useAuth } from '../contexts/AuthContext';
import { signInWithRedirect } from 'aws-amplify/auth';
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

    const handleGoogleSignIn = async () => {
        try {
            console.log('Initiating Google OAuth sign-in...');
            await signInWithRedirect({
                provider: { custom: 'Google' }
            });
        } catch (err: any) {
            console.error('Google sign-in error:', err);
            setError(err.message || 'Failed to sign in with Google');
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

            {!isSignUp && (
                <>
                    <div className="divider">
                        <span>OR</span>
                    </div>
                    <button className="google-button" onClick={handleGoogleSignIn}>
                        <svg className="google-icon" viewBox="0 0 24 24" width="20" height="20">
                            <path fill="#4285F4" d="M22.56 12.25c0-.78-.07-1.53-.2-2.25H12v4.26h5.92c-.26 1.37-1.04 2.53-2.21 3.31v2.77h3.57c2.08-1.92 3.28-4.74 3.28-8.09z"/>
                            <path fill="#34A853" d="M12 23c2.97 0 5.46-.98 7.28-2.66l-3.57-2.77c-.98.66-2.23 1.06-3.71 1.06-2.86 0-5.29-1.93-6.16-4.53H2.18v2.84C3.99 20.53 7.7 23 12 23z"/>
                            <path fill="#FBBC05" d="M5.84 14.09c-.22-.66-.35-1.36-.35-2.09s.13-1.43.35-2.09V7.07H2.18C1.43 8.55 1 10.22 1 12s.43 3.45 1.18 4.93l2.85-2.22.81-.62z"/>
                            <path fill="#EA4335" d="M12 5.38c1.62 0 3.06.56 4.21 1.64l3.15-3.15C17.45 2.09 14.97 1 12 1 7.7 1 3.99 3.47 2.18 7.07l3.66 2.84c.87-2.6 3.3-4.53 6.16-4.53z"/>
                        </svg>
                        Sign in with Google
                    </button>
                </>
            )}

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